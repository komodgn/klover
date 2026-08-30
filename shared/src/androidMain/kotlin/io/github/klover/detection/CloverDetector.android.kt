package io.github.klover.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import klover.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.time.TimeSource

actual fun createCloverDetector(): CloverDetector = OnnxCloverDetector()

/**
 * On-device four-leaf clover detector backed by ONNX Runtime.
 *
 * Loads a YOLOv8-style single-class model from `files/clover.onnx`. When the model is absent or
 * anything goes wrong (decode/inference), it delegates to [MockCloverDetector] so the app keeps
 * working end-to-end without a trained model.
 */
private const val TAG = "Klover"

class OnnxCloverDetector : CloverDetector {

    private val fallback = MockCloverDetector()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionResolved = false

    private suspend fun ensureSession(): OrtSession? {
        if (sessionResolved) return session
        sessionResolved = true
        session = runCatching {
            val bytes = Res.readBytes(YoloConfig.MODEL_PATH)
            val environment = OrtEnvironment.getEnvironment()
            env = environment
            environment.createSession(bytes, OrtSession.SessionOptions()).also {
                Log.i(TAG, "ONNX model loaded (${bytes.size} bytes) — running on-device inference")
            }
        }.onFailure {
            Log.w(TAG, "ONNX model unavailable — falling back to mock detector", it)
        }.getOrNull()
        return session
    }

    override suspend fun detect(image: CloverImage): DetectionResult {
        val ortSession = ensureSession() ?: return fallback.detect(image)
        val environment = env ?: return fallback.detect(image)
        val input = preprocess(image.bytes) ?: return fallback.detect(image)

        return withContext(Dispatchers.Default) {
            val mark = TimeSource.Monotonic.markNow()
            val n = YoloConfig.INPUT_SIZE.toLong()
            val tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                longArrayOf(1, 3, n, n),
            )
            val detections = tensor.use { t ->
                val inputName = ortSession.inputNames.first()
                ortSession.run(mapOf(inputName to t)).use { result ->
                    val onnx: OnnxValue = result.iterator().next().value
                    val decoded = flattenOutput(onnx.value)
                    if (decoded == null) return@use null
                    val (flat, channels, anchors) = decoded
                    decodeYoloOutput(flat, channels, anchors)
                }
            } ?: return@withContext fallback.detect(image)

            val elapsed = mark.elapsedNow().inWholeMilliseconds
            Log.d(TAG, "inference ${elapsed}ms -> ${detections.size} four-leaf detection(s)")
            DetectionResult(detections, elapsed)
        }
    }

    /** Decode encoded image bytes and produce a normalized CHW float buffer for YOLO input. */
    private fun preprocess(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty()) return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val n = YoloConfig.INPUT_SIZE
        val scaled = Bitmap.createScaledBitmap(bmp, n, n, true)
        val pixels = IntArray(n * n)
        scaled.getPixels(pixels, 0, n, 0, 0, n, n)

        val area = n * n
        val chw = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f          // R plane
            chw[area + i] = ((p shr 8) and 0xFF) / 255f     // G plane
            chw[2 * area + i] = (p and 0xFF) / 255f          // B plane
        }
        return chw
    }

    /**
     * Flattens an ONNX output object (expected `float[1][channels][anchors]`) into a channel-major
     * [FloatArray] plus its dimensions. Returns null for unexpected shapes.
     */
    private fun flattenOutput(raw: Any?): Triple<FloatArray, Int, Int>? {
        val batch = raw as? Array<*> ?: return null
        val channelsArr = batch.firstOrNull() as? Array<*> ?: return null
        val channels = channelsArr.size
        val firstRow = channelsArr.firstOrNull() as? FloatArray ?: return null
        val anchors = firstRow.size
        val flat = FloatArray(channels * anchors)
        for (c in 0 until channels) {
            val row = channelsArr[c] as? FloatArray ?: return null
            row.copyInto(flat, c * anchors)
        }
        return Triple(flat, channels, anchors)
    }
}
