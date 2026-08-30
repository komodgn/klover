package io.github.klover.screens.scan

import androidx.lifecycle.ViewModel
import io.github.klover.detection.Detection
import io.github.klover.platform.ScanFrameResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScanUiState(
    val detections: List<Detection> = emptyList(),
    val fps: Int = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
)

class ScanViewModel : ViewModel() {

    val state: StateFlow<ScanUiState>
        field = MutableStateFlow(ScanUiState())

    fun onFrameResult(result: ScanFrameResult) {
        state.value = ScanUiState(
            detections = result.detections,
            fps = result.fps,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight,
        )
    }
}
