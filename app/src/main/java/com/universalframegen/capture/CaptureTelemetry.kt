package com.universalframegen.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The capture callback rate is NOT the game's true render FPS. */
data class CaptureStatus(
    val state: String = "IDLE",
    val framesReceived: Long = 0,
    val captureCallbackFps: Double = 0.0,
    val captureMode: String = "RGBA diagnostic",
    val hardwareBuffersReceived: Long = 0,
    val timestampIssues: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val lastError: String? = null
)

object CaptureTelemetry {
    private val _status = MutableStateFlow(CaptureStatus())
    val status: StateFlow<CaptureStatus> = _status

    fun publish(value: CaptureStatus) { _status.value = value }
}
