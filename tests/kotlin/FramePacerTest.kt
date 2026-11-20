import com.universalframegen.pacing.FramePacer

fun main() {
    val p = FramePacer(30, 60)
    val start = 1_000_000_000L
    check(p.onVsync(start)?.let { it.slot == 0L && it.interpolation == 0f } == true)
    check(p.onVsync(start + 8_333_333L) == null) // 120-Hz panel, 60-Hz output
    check(p.onVsync(start + 16_666_667L)?.let { it.slot == 1L && it.interpolation == .5f && it.sourceIndex == 0L } == true)
    check(p.onVsync(start + 16_666_667L) == null)
    check(p.onVsync(start + 33_333_334L)?.let { it.slot == 2L && it.sourceIndex == 1L && it.interpolation == 0f } == true)
    check(p.onVsync(start + 100_000_000L)?.let { it.slot == 6L && it.skippedSlots == 3L } == true)
    check(p.onVsync(start - 1) == null)
    p.reset()
    check(p.onVsync(1L)?.slot == 0L)
    val q = FramePacer(30, 30)
    check(q.onVsync(0)?.slot == 0L)
    check(q.onVsync(16_666_667L) == null)
    check(q.onVsync(33_333_334L)?.let { it.slot == 1L && it.interpolation == 0f } == true)
    check(FramePacer(24, 120).onVsync(0)?.interpolation == 0f)
    try {
        FramePacer(30, 50)
        error("Expected invalid non-multiple FPS to be rejected")
    } catch (_: IllegalArgumentException) { }
    println("PASS: pure-Kotlin frame pacing cadence, duplicate suppression, dropped slots, restart, 30Hz mode")
}
