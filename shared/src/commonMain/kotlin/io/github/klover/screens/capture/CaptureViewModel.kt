package io.github.klover.screens.capture

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klover.detection.CloverDetector
import io.github.klover.detection.CloverImage
import io.github.klover.detection.DetectionResult
import io.github.klover.platform.decodeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property photo the picked photo to scan, or null to show the decorative sample field.
 * @property isDetecting whether inference is currently running.
 * @property result the latest detection result, or null before the first scan.
 */
data class CaptureUiState(
    val photo: ImageBitmap? = null,
    val isDetecting: Boolean = false,
    val result: DetectionResult? = null,
)

class CaptureViewModel(
    private val detector: CloverDetector,
) : ViewModel() {

    val state: StateFlow<CaptureUiState>
        field = MutableStateFlow(CaptureUiState())

    private var currentBytes: ByteArray? = null

    /** Called when the user picks a photo from the gallery. Decodes it, then scans it. */
    fun onImageSelected(bytes: ByteArray) {
        currentBytes = bytes
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { decodeImageBitmap(bytes) }
            state.value = CaptureUiState(photo = bitmap)
            detect()
        }
    }

    fun detect() {
        if (state.value.isDetecting) return
        state.value = state.value.copy(isDetecting = true, result = null)
        viewModelScope.launch {
            val bytes = currentBytes
            val image = if (bytes != null) CloverImage(bytes, 0, 0) else CloverImage.placeholder()
            val result = detector.detect(image)
            state.value = state.value.copy(isDetecting = false, result = result)
        }
    }

    fun reset() {
        currentBytes = null
        state.value = CaptureUiState()
    }
}
