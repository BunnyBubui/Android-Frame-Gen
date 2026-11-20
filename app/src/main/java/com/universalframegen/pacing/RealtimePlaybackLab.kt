package com.universalframegen.pacing

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.Choreographer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalframegen.interpolation.NativeInterpolator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Phase 4 VISUAL pacing test. Synthetic 30 Hz source is precomputed into a
 * 60-frame loop, with the midpoints computed by the CPU reference engine.
 * Choreographer requests display updates near 30/60 Hz. No MediaProjection
 * pixels, overlay, games or true GPU frame generation are involved.
 */
@Composable
fun RealtimePlaybackLab() {
    var fps by remember { mutableIntStateOf(60) }
    var playing by remember { mutableStateOf(false) }
    var frames by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("Preparing synthetic reference frames…") }
    var callbacksPerSecond by remember { mutableDoubleStateOf(0.0) }
    var skippedSlots by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        if (!NativeInterpolator.available) {
            status = "Native library unavailable; playback preview disabled"
            return@LaunchedEffect
        }
        try {
            val generated = withContext(Dispatchers.Default) { createSequence() }
            frames = generated
            frame = generated.firstOrNull()
            playing = generated.isNotEmpty()
            status = "Ready · Synthetic 30 Hz input · C++ CPU midpoint interpolation"
        } catch (ex: Exception) {
            status = "Precompute failed: ${ex.javaClass.simpleName}: ${ex.message}"
        }
    }

    DisposableEffect(playing, fps, frames) {
        if (!playing || frames.isEmpty()) {
            onDispose { }
        } else {
            val choreographer = Choreographer.getInstance()
            val pacer = FramePacer(sourceFps = 30, targetFps = fps)
            var updatedInWindow = 0
            var startedWindow = -1L
            var totalSkipped = 0L
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val decision = pacer.onVsync(frameTimeNanos)
                    if (decision != null) {
                        val index = if (fps == 30) {
                            ((decision.sourceIndex % 30L) * 2L).toInt()
                        } else (decision.slot % 60L).toInt()
                        frame = frames[index]
                        totalSkipped += decision.skippedSlots
                        updatedInWindow++
                    }
                    if (startedWindow < 0L) startedWindow = frameTimeNanos
                    val elapsed = frameTimeNanos - startedWindow
                    if (elapsed >= 1_000_000_000L) {
                        callbacksPerSecond = updatedInWindow * 1_000_000_000.0 / elapsed
                        skippedSlots = totalSkipped
                        updatedInWindow = 0
                        startedWindow = frameTimeNanos
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(callback)
            onDispose { choreographer.removeFrameCallback(callback) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("VSync Playback Lab · Phase 4", fontSize = 20.sp)
        Text(
            "จำลองอินพุต 30 FPS และแสดงเฟรมต้นฉบับ/เฟรมกลางตามจังหวะหน้าจอ โดยใช้ภาพที่คำนวณล่วงหน้า",
            fontSize = 12.sp, color = Color(0xFF9DABC1)
        )
        Surface(color = Color(0xFF141E30), shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF29364E))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                frame?.let {
                    Image(bitmap = it, contentDescription = "Precomputed synthetic frame playback",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentScale = ContentScale.Fit)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = fps == 30, onClick = { fps = 30 }, label = { Text("30 Hz") })
                    FilterChip(selected = fps == 60, onClick = { fps = 60 }, label = { Text("60 Hz · x2") })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { playing = !playing }, enabled = frames.isNotEmpty()) {
                        Text(if (playing) "Pause" else "Play")
                    }
                }
                Text(status, color = Color(0xFFAFC5E4), fontSize = 12.sp)
                Text("Requested frame updates: ${"%.1f".format(callbacksPerSecond)}/s",
                    color = Color.White, fontSize = 12.sp)
                Text("Missed presentation slots: $skippedSlots",
                    color = Color.White, fontSize = 12.sp)
                Text(
                    "ตัวเลขคือการร้องขออัปเดตตาม VSync ไม่ใช่ FPS ที่ SurfaceFlinger แสดงจริงหรือ FPS ของเกม",
                    color = Color(0xFF9DABC1), fontSize = 11.sp
                )
            }
        }
    }
}

private const val WIDTH = 128
private const val HEIGHT = 72

private fun sampleSynthetic(index: Int): IntArray {
    val shift = (sin(index * (2.0 * Math.PI / 30)) * 28).roundToInt()
    val out = IntArray(WIDTH * HEIGHT) { at ->
        val x = at % WIDTH
        val y = at / WIDTH
        AndroidColor.rgb(13 + (x % 8), 25 + (y % 10), 48)
    }
    for (y in 19 until 53) for (x in (45 + shift) until (79 + shift)) {
        if (x in 0 until WIDTH) {
            val isLight = ((x - shift + y) / 5) % 2 == 0
            out[y * WIDTH + x] = if (isLight) 0xFF6BE5C9.toInt() else 0xFF3394E8.toInt()
        }
    }
    return out
}

private fun createSequence(): List<ImageBitmap> {
    val sources = Array(30) { sampleSynthetic(it) }
    val result = ArrayList<ImageBitmap>(60)
    for (i in 0 until 30) {
        val left = sources[i]
        val right = sources[(i + 1) % 30]
        result += Bitmap.createBitmap(left, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
        val middle = NativeInterpolator.interpolate(left, right, WIDTH, HEIGHT, .5f)
        result += Bitmap.createBitmap(middle, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    return result
}
