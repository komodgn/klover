package io.github.klover.detection

/** A single four-leaf clover found in an image. */
data class Detection(
    val box: BoundingBox,
    /** Model confidence in the range 0..1. */
    val confidence: Float,
    val label: String = "four_leaf_clover",
)

/** The outcome of running the detector over one image. */
data class DetectionResult(
    val detections: List<Detection>,
    /** How long inference took, in milliseconds. Useful for showing/benchmarking model speed. */
    val inferenceTimeMs: Long,
) {
    val found: Boolean get() = detections.isNotEmpty()
    val count: Int get() = detections.size
}
