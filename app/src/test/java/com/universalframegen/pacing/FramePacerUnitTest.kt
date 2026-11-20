package com.universalframegen.pacing

import org.junit.Assert.*
import org.junit.Test

class FramePacerUnitTest {
    @Test fun frameRateDouble() {
        val p = FramePacer(30, 60)
        assertEquals(0L, p.onVsync(0)!!.slot)
        assertNull(p.onVsync(8_333_333L))
        assertEquals(.5f, p.onVsync(16_666_667L)!!.interpolation, 0f)
        val pair = p.onVsync(33_333_334L)!!
        assertEquals(1L, pair.sourceIndex)
        assertEquals(0f, pair.interpolation, 0f)
    }

    @Test fun skippedFrameSlotsAndRestart() {
        val p = FramePacer(30, 60)
        p.onVsync(100L)
        assertEquals(5L, p.onVsync(100_000_100L)!!.skippedSlots)
        assertNull(p.onVsync(100L))
        p.reset()
        assertEquals(0L, p.onVsync(100L)!!.slot)
    }

    @Test fun normalRateAndInvalidRatio() {
        val p = FramePacer(30, 30)
        p.onVsync(0)
        assertNull(p.onVsync(16_666_667L))
        assertEquals(0f, p.onVsync(33_333_334L)!!.interpolation, 0f)
        try {
            FramePacer(30, 50)
            fail("non-integer ratio should fail")
        } catch (_: IllegalArgumentException) { }
    }
}
