package io.github.klover.detection

/**
 * A rectangle in **normalized** image coordinates, where (0,0) is the top-left of the
 * source image and (1,1) is the bottom-right.
 *
 * Using normalized coordinates keeps detections independent of the actual pixel size, so the
 * same result can be overlaid on a thumbnail or a full-screen preview without recomputation.
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    init {
        require(width >= 0f && height >= 0f) { "BoundingBox size must be non-negative" }
    }
}
