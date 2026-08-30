@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.klover.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import io.github.klover.detection.BoundingBox
import io.github.klover.detection.CloverDetector
import io.github.klover.detection.Detection
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreML.MLModel
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.QuartzCore.CATransaction
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIView
import platform.Vision.VNCoreMLModel
import platform.Vision.VNCoreMLRequest
import platform.Vision.VNImageCropAndScaleOptionCenterCrop
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizedObjectObservation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.Foundation.NSBundle

/** CoreML class label that counts as a four-leaf clover (matches the trained dataset). */
private const val TARGET_LABEL = "4-Leaf-Clovers"

/**
 * iOS live camera scanner: `AVCaptureSession` preview (hosted via [UIKitView]) plus per-frame
 * CoreML/Vision inference. Detections are converted to the shared [Detection] model so the same
 * Compose overlay draws boxes exactly like Android.
 *
 * The CoreML model must be added to the iOS app target as `clover.mlpackage` (Xcode compiles it to
 * `clover.mlmodelc`). If it's missing, the preview still runs but no boxes are drawn.
 */
@Composable
actual fun CameraScanner(
    detector: CloverDetector,
    onResult: (ScanFrameResult) -> Unit,
    modifier: Modifier,
) {
    val session = remember { AVCaptureSession() }
    val previewLayer = remember {
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }
    val delegate = remember { CloverVisionDelegate(onResult) }
    delegate.onResult = onResult

    DisposableEffect(Unit) {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            if (granted) {
                configureSession(session, delegate)
                dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
                    session.startRunning()
                }
            }
        }
        onDispose {
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL)) {
                session.stopRunning()
            }
        }
    }

    UIKitView(
        factory = { CameraPreviewUIView(previewLayer) },
        modifier = modifier,
    )
}

/** A UIView that keeps [previewLayer] sized to its bounds as Compose lays it out. */
private class CameraPreviewUIView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        CATransaction.commit()
    }
}

private fun configureSession(session: AVCaptureSession, delegate: CloverVisionDelegate) {
    session.beginConfiguration()
    if (session.canSetSessionPreset(AVCaptureSessionPresetHigh)) {
        session.setSessionPreset(AVCaptureSessionPresetHigh)
    }
    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    if (device != null) {
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
        if (input != null && session.canAddInput(input)) {
            session.addInput(input)
        }
    }
    val output = AVCaptureVideoDataOutput().apply {
        alwaysDiscardsLateVideoFrames = true
    }
    val queue = dispatch_queue_create("io.github.klover.camera", null)
    output.setSampleBufferDelegate(delegate, queue)
    if (session.canAddOutput(output)) {
        session.addOutput(output)
    }
    session.commitConfiguration()
}

/**
 * Runs Vision (CoreML) on each frame on a serial queue (late frames are dropped by the output),
 * maps detections into the shared model, and reports them on the main thread.
 */
private class CloverVisionDelegate(
    var onResult: (ScanFrameResult) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    private val request: VNCoreMLRequest? = loadRequest()
    private val inferenceQueue = dispatch_queue_create("io.github.klover.inference", null)
    private var processing = false
    private var lastFrameTime = 0.0
    private var lastStartTime = 0.0

    // Run at most ~3 inferences/sec so the Neural Engine leaves the GPU free for a smooth preview.
    private val minInterval = 0.30

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val request = request ?: return
        // Skip while a previous frame is still being analyzed — this returns immediately so the
        // capture pipeline (and thus the preview) is never blocked by inference.
        if (processing) return
        val startNow = CACurrentMediaTime()
        if (startNow - lastStartTime < minInterval) return
        lastStartTime = startNow
        val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
        processing = true
        CVPixelBufferRetain(pixelBuffer)

        dispatch_async(inferenceQueue) {
            // Back camera in portrait: the buffer is landscape, so tell Vision it's rotated right.
            val handler = VNImageRequestHandler(
                cVPixelBuffer = pixelBuffer,
                orientation = kCGImagePropertyOrientationRight,
                options = emptyMap<Any?, Any?>(),
            )
            handler.performRequests(listOf(request), null)

            val observations = request.results
                ?.filterIsInstance<VNRecognizedObjectObservation>()
                .orEmpty()

            val detections = observations.mapNotNull { obs ->
                val label = obs.labels.firstOrNull() as? platform.Vision.VNClassificationObservation
                    ?: return@mapNotNull null
                if (label.identifier != TARGET_LABEL) return@mapNotNull null
                obs.boundingBox.useContents {
                    Detection(
                        box = BoundingBox(
                            left = origin.x.toFloat(),
                            // Vision's origin is bottom-left; flip Y to our top-left convention.
                            top = (1.0 - (origin.y + size.height)).toFloat(),
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                        ),
                        confidence = label.confidence,
                    )
                }
            }

            // Upright dimensions (orientation right swaps width/height).
            val uprightWidth = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val uprightHeight = CVPixelBufferGetWidth(pixelBuffer).toInt()
            CVPixelBufferRelease(pixelBuffer)

            val now = CACurrentMediaTime()
            val fps = if (lastFrameTime > 0.0) (1.0 / (now - lastFrameTime)).toInt() else 0
            lastFrameTime = now

            dispatch_async(dispatch_get_main_queue()) {
                onResult(ScanFrameResult(detections, fps, uprightWidth, uprightHeight))
            }
            processing = false
        }
    }
}

private fun loadRequest(): VNCoreMLRequest? {
    val url = NSBundle.mainBundle.URLForResource("clover", withExtension = "mlmodelc") ?: return null
    val mlModel = MLModel.modelWithContentsOfURL(url, null) ?: return null
    val visionModel = VNCoreMLModel.modelForMLModel(mlModel, null) ?: return null
    return VNCoreMLRequest(visionModel).apply {
        imageCropAndScaleOption = VNImageCropAndScaleOptionCenterCrop
    }
}
