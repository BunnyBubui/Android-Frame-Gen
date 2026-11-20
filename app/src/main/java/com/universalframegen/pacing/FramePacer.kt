package com.universalframegen.pacing

/**
 * Pure Kotlin deterministic frame scheduler, no Android calls. A decision means a
 * frame was DUE at a vsync timestamp, NOT that SurfaceFlinger presented it.
 * Source frames are 30 Hz synthetic frames; target slots may be 30 or 60 Hz.
 */
data class FrameDecision(
    val slot: Long,
    val sourceIndex: Long,
    val interpolation: Float,
    val skippedSlots: Long
)

class FramePacer(val sourceFps: Int = 30, val targetFps: Int = 60) {
    init {
        require(sourceFps in 1..240 && targetFps in 1..240)
        require(targetFps >= sourceFps && targetFps % sourceFps == 0)
    }

    private val ratio = targetFps / sourceFps
    private var startedAtNs: Long? = null
    private var lastSlot = -1L

    fun reset() {
        startedAtNs = null
        lastSlot = -1L
    }

    fun onVsync(frameTimeNs: Long): FrameDecision? {
        val start = startedAtNs ?: frameTimeNs.also { startedAtNs = it }
        if (frameTimeNs < start) return null // clock reset: no retrograde output
        val elapsed = frameTimeNs - start
        // Integer time mapping avoids long-term drift from rounded ns periods.
        // Capture sessions never run long enough for the multiplication to overflow.
        val slot = (elapsed / 1_000_000_000L) * targetFps +
            (elapsed % 1_000_000_000L) * targetFps / 1_000_000_000L
        if (slot <= lastSlot) return null // no duplicate output on 120-Hz display
        val skipped = (slot - lastSlot - 1).coerceAtLeast(0)
        lastSlot = slot
        return FrameDecision(
            slot = slot,
            sourceIndex = slot / ratio,
            interpolation = (slot % ratio).toFloat() / ratio,
            skippedSlots = skipped
        )
    }
}
