package io.github.klover.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.klover.detection.CloverDetector
import io.github.klover.detection.Detection

/**
 * Result of analyzing a single live camera frame.
 *
 * [frameWidth]/[frameHeight] describe the **upright** (display-oriented) frame the detections are
 * normalized against, so the overlay can map boxes onto a center-cropped preview correctly.
 */
data class ScanFrameResult(
    val detections: List<Detection>,
    val fps: Int,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
)

/**
 * A full-bleed live camera preview that runs [detector] on frames and reports results via
 * [onResult]. Frames are throttled so only one inference runs at a time.
 *
 * Android uses CameraX. iOS is currently a placeholder.
 */
@Composable
expect fun CameraScanner(
    detector: CloverDetector,
    onResult: (ScanFrameResult) -> Unit,
    modifier: Modifier,
)
