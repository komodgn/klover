package io.github.klover.screens.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klover.detection.CloverDetector
import io.github.klover.detection.Detection
import io.github.klover.platform.CameraScanner
import io.github.klover.ui.theme.DetectionRose
import klover.shared.generated.resources.Res
import klover.shared.generated.resources.brand_label
import klover.shared.generated.resources.fps_format
import klover.shared.generated.resources.scan_hint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ScanScreen(
    viewModel: ScanViewModel = koinViewModel(),
    detector: CloverDetector = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CameraScanner(
            detector = detector,
            onResult = viewModel::onFrameResult,
            modifier = Modifier.fillMaxSize(),
        )

        DetectionOverlay(
            detections = state.detections,
            frameWidth = state.frameWidth,
            frameHeight = state.frameHeight,
            modifier = Modifier.fillMaxSize(),
        )

        TopScanBar(
            fps = state.fps,
            count = state.detections.size,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        Text(
            text = stringResource(Res.string.scan_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun TopScanBar(fps: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.brand_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("🍀 $count", if (count > 0) DetectionRose else Color.Black.copy(alpha = 0.45f))
            Chip(stringResource(Res.string.fps_format, fps), Color.Black.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/**
 * Draws rose bounding boxes + a confidence chip above each detection (FOUR-LEAF style).
 *
 * Detections are normalized to the upright camera frame ([frameWidth]×[frameHeight]). The preview
 * uses FILL_CENTER (scale-to-fill, center-crop), so we apply the same transform here to keep boxes
 * on the real clover.
 */
@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }

        // FILL_CENTER: scale the frame to cover the view, then center it (overflow is cropped).
        val hasFrame = frameWidth > 0 && frameHeight > 0
        val scale = if (hasFrame) maxOf(cw / frameWidth, ch / frameHeight) else 1f
        val dispW = if (hasFrame) frameWidth * scale else cw
        val dispH = if (hasFrame) frameHeight * scale else ch
        val offX = (cw - dispW) / 2f
        val offY = (ch - dispH) / 2f

        Canvas(Modifier.fillMaxSize()) {
            detections.forEach { d ->
                val topLeft = Offset(d.box.left * dispW + offX, d.box.top * dispH + offY)
                val boxSize = Size(d.box.width * dispW, d.box.height * dispH)
                drawRoundRect(
                    color = DetectionRose,
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        detections.forEach { d ->
            val xPx = d.box.left * dispW + offX
            val yPx = d.box.top * dispH + offY
            val xDp = with(density) { xPx.toDp() }
            val yDp = with(density) { yPx.toDp() } - 24.dp
            Box(
                Modifier
                    .offset(x = xDp, y = if (yDp > 0.dp) yDp else 0.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DetectionRose)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${(d.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
