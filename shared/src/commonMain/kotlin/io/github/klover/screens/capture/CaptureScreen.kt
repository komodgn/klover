package io.github.klover.screens.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klover.detection.Detection
import io.github.klover.platform.rememberGalleryLauncher
import klover.shared.generated.resources.Res
import klover.shared.generated.resources.action_detect
import klover.shared.generated.resources.action_pick_photo
import klover.shared.generated.resources.action_retry
import klover.shared.generated.resources.detection_item
import klover.shared.generated.resources.inference_time
import klover.shared.generated.resources.prompt_idle
import klover.shared.generated.resources.result_found
import klover.shared.generated.resources.result_not_found
import klover.shared.generated.resources.status_detecting
import klover.shared.generated.resources.top_bar_title
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val Grass = Color(0xFF3E7B37)
private val GrassDark = Color(0xFF2E5C29)
private val Highlight = Color(0xFFFFD54F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(viewModel: CaptureViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickPhoto = rememberGalleryLauncher(onImagePicked = viewModel::onImageSelected)

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(Res.string.top_bar_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val photo = state.photo
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CloverField(Modifier.fillMaxSize())
                }

                state.result?.let { DetectionOverlay(it.detections, Modifier.fillMaxSize()) }

                if (state.isDetecting) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            StatusArea(state, modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = pickPhoto,
                    enabled = !state.isDetecting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.action_pick_photo))
                }
                Button(
                    onClick = viewModel::detect,
                    enabled = !state.isDetecting,
                    modifier = Modifier.weight(1f),
                ) {
                    val label = if (state.result != null) Res.string.action_retry else Res.string.action_detect
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun StatusArea(state: CaptureUiState, modifier: Modifier = Modifier) {
    val result = state.result
    when {
        state.isDetecting -> Text(
            stringResource(Res.string.status_detecting),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )

        result == null -> Text(
            stringResource(Res.string.prompt_idle),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )

        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (result.found) {
                Text(
                    stringResource(Res.string.result_found, result.count),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                result.detections.forEachIndexed { i, d ->
                    Text(
                        stringResource(Res.string.detection_item, i + 1, (d.confidence * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Text(
                    stringResource(Res.string.result_not_found),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(Res.string.inference_time, result.inferenceTimeMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A decorative "grass field" shown until the user picks a real photo. */
@Composable
private fun CloverField(modifier: Modifier = Modifier) {
    val blades = remember {
        val rng = Random(42)
        List(140) {
            Triple(rng.nextFloat(), rng.nextFloat(), 0.006f + rng.nextFloat() * 0.010f)
        }
    }
    Canvas(modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(Grass, GrassDark)), size = size)
        blades.forEach { (x, y, r) ->
            drawCircle(
                color = GrassDark.copy(alpha = 0.6f),
                radius = r * size.minDimension,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

@Composable
private fun DetectionOverlay(detections: List<Detection>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        detections.forEach { d ->
            val topLeft = Offset(d.box.left * size.width, d.box.top * size.height)
            val boxSize = Size(d.box.width * size.width, d.box.height * size.height)
            drawRoundRect(
                color = Highlight.copy(alpha = 0.25f),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(12f, 12f),
            )
            drawRoundRect(
                color = Highlight,
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
