package io.github.klover.detection

/**
 * A platform-agnostic image handed to a [CloverDetector].
 *
 * For now this only carries the encoded bytes (PNG/JPEG) and the source dimensions. When we wire
 * up a real on-device model, the platform camera/gallery layer (`expect`/`actual`) will produce
 * one of these from a captured photo, and the detector's `actual` side will decode it.
 */
class CloverImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
) {
    companion object {
        /**
         * A stand-in image used by the mock pipeline before real capture exists.
         * Carries no pixels — just a square canvas the mock detector can fabricate boxes over.
         */
        fun placeholder(size: Int = 1000): CloverImage = CloverImage(ByteArray(0), size, size)
    }
}
