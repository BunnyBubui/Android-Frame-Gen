package com.universalframegen.interpolation

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val W = 192
private const val H = 108

private fun syntheticFrame(shift: Int): IntArray {
    val pixels = IntArray(W * H) { at ->
        val x = at % W
        val y = at / W
        // Non-flat background makes the optical flow ambiguity visible.
        android.graphics.Color.rgb(14 + (x / 14) % 4, 20 + (y / 12) % 8, 34)
    }
    for (y in 33 until 75) for (x in (50 + shift) until (92 + shift)) {
        val checker = ((x + y) / 4) % 2 == 0
        val red = if (checker) 105 else 45
        val green = if (checker) 231 else 166
        pixels[y * W + x] = android.graphics.Color.rgb(red, green, 185)
    }
    return pixels
}

@Composable
fun InterpolationLab() {
    val frameA = remember { syntheticFrame(0) }
    val frameB = remember { syntheticFrame(6) }
    var position by remember { mutableFloatStateOf(.5f) }
    var output by remember { mutableStateOf<Bitmap?>(null) }
    var processingMs by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(position) {
        if (!NativeInterpolator.available) {
            error = "Native interpolation library could not be loaded"
            return@LaunchedEffect
        }
        try {
            val result = withContext(Dispatchers.Default) {
                val start = System.nanoTime()
                val blended = NativeInterpolator.interpolate(frameA, frameB, W, H, position)
                val ms = (System.nanoTime() - start) / 1_000_000L
                Bitmap.createBitmap(blended, W, H, Bitmap.Config.ARGB_8888) to ms
            }
            output = result.first
            processingMs = result.second
            error = null
        } catch (t: Exception) {
            error = t.message ?: t.javaClass.simpleName
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Native Interpolation Lab", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("ภาพสังเคราะห์ 192×108 · C++ block matching + motion compensation", fontSize = 12.sp, color = Color(0xFF9DABC1))
        Surface(color = Color(0xFF141E30), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF29364E))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (output != null) {
                    Image(
                        bitmap = output!!.asImageBitmap(),
                        contentDescription = "Native interpolated synthetic frame",
                        modifier = Modifier.fillMaxWidth().aspectRatio(W.toFloat() / H),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("กำลังเตรียมตัวอย่าง...", color = Color.White)
                }
                Text("ตำแหน่งระหว่างเฟรม: ${(position * 100).roundToInt()}%", color = Color.White)
                Slider(value = position, onValueChange = { position = it }, valueRange = 0f..1f)
                Text("เวลา CPU ต่อภาพทดสอบ: ${processingMs} ms (ไม่ใช่ latency ของเกม)", color = Color(0xFF9DABC1), fontSize = 11.sp)
                if (error != null) Text(error!!, color = Color(0xFFFFA79C), fontSize = 12.sp)
            }
        }
        Text(
            "เฟรม A เป็นภาพวัตถุที่ตำแหน่งเริ่มต้น เฟรม B เลื่อนขวา 6 พิกเซล ตัวเลื่อนใช้คำนวณเฟรมระหว่างกลางจริงใน Native C++ บน CPU แต่ยังไม่แสดงทับเกมและไม่ได้ใช้ Vulkan",
            color = Color(0xFF9DABC1), fontSize = 12.sp, lineHeight = 19.sp
        )
    }
}
