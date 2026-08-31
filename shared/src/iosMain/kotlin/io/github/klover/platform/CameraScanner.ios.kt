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
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureSessionPreset1280x720
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
import platform.CoreML.MLModelConfiguration
import platform.CoreML.MLComputeUnitsCPUAndNeuralEngine
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
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference

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
    // Prefer HD for a crisp preview; fall back to lower presets on devices that can't do 1080p.
    // Inference still center-crops/scales to 640×640 internally, so the higher preview
    // resolution doesn't change model input size.
    val preset = listOf(
        AVCaptureSessionPreset1280x720,
        AVCaptureSessionPreset640x480,
    ).firstOrNull { session.canSetSessionPreset(it) }
    if (preset != null) {
        session.setSessionPreset(preset)
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

    private val inferenceQueue = dispatch_queue_create("io.github.klover.inference", null)
    // AtomicInt(0=idle, 1=busy) — read on camera queue, written on inferenceQueue, so must be atomic.
    private val processing = AtomicInt(0)
    // Loaded on inferenceQueue (background) to avoid blocking Main thread during composition.
    // Stores the request and its pre-wrapped list together to avoid per-frame List allocation.
    private val loadedRef = AtomicReference<Pair<VNCoreMLRequest, List<VNCoreMLRequest>>?>(null)

    // Cached once so the hot path doesn't allocate a new Map on every frame.
    private val emptyOptions: Map<Any?, Any?> = emptyMap()

    init {
        dispatch_async(inferenceQueue) {
            val req = loadRequest() ?: return@dispatch_async
            loadedRef.value = Pair(req, listOf(req))
        }
    }
    private var lastFrameTime = 0.0
    private var lastStartTime = 0.0

    // ~10 inferences/sec. Safe now that inference runs on the Neural Engine (not the GPU), so it
    // no longer contends with the GPU-bound camera preview. Combined with render-side box
    // interpolation this tracks the clover smoothly. Lower the number for an even higher rate.
    private val minInterval = 0.1

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val (request, requestList) = loadedRef.value ?: return
        val startNow = CACurrentMediaTime()
        if (startNow - lastStartTime < minInterval) return
        // Atomically acquire the inference slot — returns false if already busy.
        if (!processing.compareAndSet(0, 1)) return
        lastStartTime = startNow
        val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: run {
            processing.value = 0
            return
        }
        CVPixelBufferRetain(pixelBuffer)

        dispatch_async(inferenceQueue) {
            // Back camera in portrait: the buffer is landscape, so tell Vision it's rotated right.
            val handler = VNImageRequestHandler(
                cVPixelBuffer = pixelBuffer,
                orientation = kCGImagePropertyOrientationRight,
                options = emptyOptions,
            )
            handler.performRequests(requestList, null)

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

            // Call onResult directly from the inference queue.
            // MutableStateFlow.value is thread-safe — the coroutine machinery dispatches
            // the collector to Dispatchers.Main without creating a GCD lambda each cycle.
            // Dispatching a Kotlin lambda to GCD's main queue every inference was causing
            // Kotlin/Native GC to accumulate lambda roots and trigger STW pauses on the
            // main thread, freezing the UI for several seconds.
            processing.value = 0
            onResult(ScanFrameResult(detections, fps, uprightWidth, uprightHeight))
        }
    }
}

private fun loadRequest(): VNCoreMLRequest? {
    val url = NSBundle.mainBundle.URLForResource("clover", withExtension = "mlmodelc") ?: return null
    // Run on the Neural Engine (+ CPU) but NOT the GPU. The camera preview compositing is
    // GPU-bound, so letting inference also use the GPU makes the two contend and freezes the
    // preview. Keeping inference off the GPU frees it for smooth preview rendering.
    val config = MLModelConfiguration().apply {
        computeUnits = MLComputeUnitsCPUAndNeuralEngine
    }
    val mlModel = MLModel.modelWithContentsOfURL(url, config, null) ?: return null
    val visionModel = VNCoreMLModel.modelForMLModel(mlModel, null) ?: return null
    return VNCoreMLRequest(visionModel).apply {
        imageCropAndScaleOption = VNImageCropAndScaleOptionCenterCrop
    }
}
