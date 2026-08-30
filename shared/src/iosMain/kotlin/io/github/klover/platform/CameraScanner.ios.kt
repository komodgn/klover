package io.github.klover.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.klover.detection.CloverDetector
import klover.shared.generated.resources.Res
import klover.shared.generated.resources.ios_camera_todo
import org.jetbrains.compose.resources.stringResource

/**
 * iOS placeholder. TODO: wrap an AVCaptureSession preview in a `UIKitView`, run inference on
 * sample buffers, and forward [ScanFrameResult]s to [onResult]. Post-processing is already shared.
 */
@Composable
actual fun CameraScanner(
    detector: CloverDetector,
    onResult: (ScanFrameResult) -> Unit,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.ios_camera_todo), Modifier.padding(24.dp))
    }
}
