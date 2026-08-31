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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
    // Stable reference so CameraScanner is skipped on state-driven recompositions.
    // Without this, every new frame result creates a new lambda and forces CameraScanner
    // (and the UIKitView inside it) to recompose — causing visible camera freezes on iOS.
    val onFrameResult = remember(viewModel) { viewModel::onFrameResult }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CameraScanner(
            detector = detector,
            onResult = onFrameResult,
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
            Chip("$fps fps", Color.Black.copy(alpha = 0.45f))
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
/**
 * A single tracked box. [cur] is the position drawn this frame; it eases toward [tgt] every frame.
 * The detector only updates [tgt] ~1.5×/sec, but [cur] interpolates at display refresh rate so the
 * box glides after the clover instead of jumping.
 */
private class BoxTrack(
    var curLeft: Float, var curTop: Float, var curW: Float, var curH: Float,
    var tgtLeft: Float, var tgtTop: Float, var tgtW: Float, var tgtH: Float,
    var confidence: Float,
    var missing: Int = 0,
)

/** Squared distance between a track's target center and a detection center (normalized coords). */
private fun centerDistSq(t: BoxTrack, d: Detection): Float {
    val dx = (t.tgtLeft + t.tgtW / 2f) - (d.box.left + d.box.width / 2f)
    val dy = (t.tgtTop + t.tgtH / 2f) - (d.box.top + d.box.height / 2f)
    return dx * dx + dy * dy
}

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier,
) {
    val tracks = remember { mutableListOf<BoxTrack>() }
    // Bumped every display frame to invalidate the Canvas/labels as cur positions ease.
    var frame by remember { mutableStateOf(0) }

    // Match new detections to existing tracks (nearest center) so a box keeps its identity and
    // eases from its old spot instead of popping to the new one.
    LaunchedEffect(detections) {
        val used = BooleanArray(tracks.size)
        val fresh = mutableListOf<BoxTrack>()
        for (d in detections) {
            var best = -1
            var bestDist = Float.MAX_VALUE
            for (i in tracks.indices) {
                if (used[i]) continue
                val dist = centerDistSq(tracks[i], d)
                if (dist < bestDist) { bestDist = dist; best = i }
            }
            // 0.02 ≈ (0.14 normalized)² — close enough to be the same clover.
            if (best >= 0 && bestDist < 0.02f) {
                val t = tracks[best]
                used[best] = true
                t.tgtLeft = d.box.left; t.tgtTop = d.box.top
                t.tgtW = d.box.width; t.tgtH = d.box.height
                t.confidence = d.confidence
                t.missing = 0
            } else {
                fresh += BoxTrack(
                    d.box.left, d.box.top, d.box.width, d.box.height,
                    d.box.left, d.box.top, d.box.width, d.box.height,
                    d.confidence,
                )
            }
        }
        // Drop tracks that went unmatched for two updates (~1.3s) so stale boxes fade out.
        for (i in tracks.indices) if (!used[i]) tracks[i].missing++
        tracks.removeAll { it.missing >= 2 }
        tracks.addAll(fresh)
    }

    // Ease every track toward its target once per display frame.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            if (tracks.isNotEmpty()) {
                val a = 0.25f
                for (t in tracks) {
                    t.curLeft += (t.tgtLeft - t.curLeft) * a
                    t.curTop += (t.tgtTop - t.curTop) * a
                    t.curW += (t.tgtW - t.curW) * a
                    t.curH += (t.tgtH - t.curH) * a
                }
            }
            frame++
        }
    }

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
            frame // read to invalidate the draw as cur positions ease
            tracks.forEach { t ->
                val topLeft = Offset(t.curLeft * dispW + offX, t.curTop * dispH + offY)
                val boxSize = Size(t.curW * dispW, t.curH * dispH)
                drawRoundRect(
                    color = DetectionRose,
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        frame // read to recompose labels as cur positions ease
        tracks.forEach { t ->
            val xPx = t.curLeft * dispW + offX
            val yPx = t.curTop * dispH + offY
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
                    text = "${(t.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
