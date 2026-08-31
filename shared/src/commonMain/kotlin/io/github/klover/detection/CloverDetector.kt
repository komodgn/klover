package io.github.klover.detection

import androidx.compose.runtime.Stable

/**
 * Finds four-leaf clovers in an image.
 *
 * This is the seam between the app and the ML backend. Today the only implementation is
 * [MockCloverDetector]; later this same interface can be fulfilled by:
 *  - an on-device model (TFLite on Android / CoreML on iOS via `expect`/`actual`), or
 *  - a remote inference endpoint (Ktor client calling a hosted YOLO/detection model).
 *
 * Keeping the UI depend only on this interface means swapping in a real model never touches the
 * screens or the ViewModel.
 */
@Stable
interface CloverDetector {
    suspend fun detect(image: CloverImage): DetectionResult
}
