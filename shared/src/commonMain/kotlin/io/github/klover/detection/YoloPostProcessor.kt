package io.github.klover.detection

/** Shared YOLOv8-style detection config and post-processing (pure Kotlin, platform-independent). */
object YoloConfig {
    const val INPUT_SIZE = 640
    const val CONF_THRESHOLD = 0.35f
    const val IOU_THRESHOLD = 0.45f
    /** Path (relative to composeResources) where the bundled model is expected. */
    const val MODEL_PATH = "files/clover.onnx"

    /**
     * Which class index counts as a "four-leaf clover".
     * - Single-class models (4-leaf only): leave as 0.
     * - Multi-class models (e.g. 3/4/5-leaf): set to the 4-leaf index printed by the training
     *   notebook, so 3-leaf and 5-leaf detections are ignored.
     * - null: keep every class.
     */
    val TARGET_CLASS_INDEX: Int? = 0
}

/**
 * Decodes a raw YOLOv8 detection head into normalized [Detection]s, then applies NMS.
 *
 * @param output flattened output laid out as `[channel * numAnchors + anchor]`
 *   (i.e. channel-major, matching an ONNX tensor of shape `[1, channels, anchors]`).
 * @param channels `4 + numClasses` — first 4 rows are `cx, cy, w, h` in input-pixel space
 *   (0..[YoloConfig.INPUT_SIZE]); remaining rows are per-class scores.
 * @param anchors number of candidate boxes (e.g. 8400 for a 640 model).
 */
fun decodeYoloOutput(output: FloatArray, channels: Int, anchors: Int): List<Detection> {
    require(channels >= 5) { "Expected at least 5 channels (4 box + >=1 class), got $channels" }
    val numClasses = channels - 4
    val size = YoloConfig.INPUT_SIZE.toFloat()
    val target = YoloConfig.TARGET_CLASS_INDEX
    val candidates = ArrayList<Detection>()

    for (a in 0 until anchors) {
        // Best score across the classes we care about (or a single target class).
        var best = 0f
        if (target != null) {
            if (target in 0 until numClasses) best = output[(4 + target) * anchors + a]
        } else {
            for (c in 0 until numClasses) {
                val s = output[(4 + c) * anchors + a]
                if (s > best) best = s
            }
        }
        if (best < YoloConfig.CONF_THRESHOLD) continue

        val cx = output[a]
        val cy = output[anchors + a]
        val w = output[2 * anchors + a]
        val h = output[3 * anchors + a]

        val left = ((cx - w / 2f) / size).coerceIn(0f, 1f)
        val top = ((cy - h / 2f) / size).coerceIn(0f, 1f)
        val right = ((cx + w / 2f) / size).coerceIn(0f, 1f)
        val bottom = ((cy + h / 2f) / size).coerceIn(0f, 1f)

        candidates += Detection(
            box = BoundingBox(left, top, right - left, bottom - top),
            confidence = best,
        )
    }
    return nonMaxSuppression(candidates, YoloConfig.IOU_THRESHOLD)
}

/** Greedy non-max suppression: keep highest-confidence boxes, drop overlaps above [iouThreshold]. */
internal fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
    val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
    val kept = ArrayList<Detection>()
    while (sorted.isNotEmpty()) {
        val best = sorted.removeAt(0)
        kept += best
        sorted.removeAll { iou(best.box, it.box) > iouThreshold }
    }
    return kept
}

internal fun iou(a: BoundingBox, b: BoundingBox): Float {
    val interLeft = maxOf(a.left, b.left)
    val interTop = maxOf(a.top, b.top)
    val interRight = minOf(a.right, b.right)
    val interBottom = minOf(a.bottom, b.bottom)
    val interW = (interRight - interLeft).coerceAtLeast(0f)
    val interH = (interBottom - interTop).coerceAtLeast(0f)
    val interArea = interW * interH
    val union = a.width * a.height + b.width * b.height - interArea
    return if (union <= 0f) 0f else interArea / union
}
