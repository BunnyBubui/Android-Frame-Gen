package com.universalframegen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalframegen.capture.CaptureTelemetry
import com.universalframegen.diagnostics.DeviceReport
import com.universalframegen.games.GameEntry
import com.universalframegen.interpolation.InterpolationLab
import com.universalframegen.pacing.RealtimePlaybackLab
import com.universalframegen.diagnostics.NativeVulkanProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF0A0F1B)
private val Panel = Color(0xFF141E30)
private val Border = Color(0xFF29364E)
private val Blue = Color(0xFF76AFFF)
private val Secondary = Color(0xFF9DABC1)
private val Green = Color(0xFF70D8AC)

private enum class Page { HOME, APPS, LAB, DIAGNOSTICS }

@Composable
fun FrameGenScreen(
    report: DeviceReport,
    installedApps: () -> List<GameEntry>,
    launchApp: (GameEntry) -> Boolean,
    startCapture: (Boolean) -> Unit,
    stopCapture: () -> Unit,
    overlayAllowed: () -> Boolean,
    overlayPermissionGranted: Boolean,
    openAppInfo: () -> Unit,
    requestOverlayPermission: () -> Unit,
    overlayVisible: () -> Boolean,
    toggleOverlay: (Boolean) -> Boolean,
    fullscreenVisible: () -> Boolean,
    toggleFullscreenOverlay: (Boolean) -> Boolean
) {
    val status by CaptureTelemetry.status.collectAsState()
    var page by remember { mutableStateOf(Page.HOME) }
    val allowed = overlayPermissionGranted
    var overlayOn by remember { mutableStateOf(overlayVisible() && !fullscreenVisible()) }
    var fullscreenOn by remember { mutableStateOf(fullscreenVisible()) }
    var vulkanDetails by remember { mutableStateOf("Not probed; tap Run GPU probe") }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(installedApps()) }
    var showGamesOnly by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var gpuCaptureMode by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, background = Background, surface = Panel,
            onSurface = Color.White, onBackground = Color.White,
            secondary = Secondary, outline = Border
        )
    ) {
        Scaffold(
            containerColor = Background,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).background(Blue.copy(alpha = .13f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.AutoAwesomeMotion, null, tint = Blue) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("UNIVERSAL", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Text("FRAMEGEN LAB", color = Secondary, fontSize = 11.sp, letterSpacing = 1.1.sp)
                    }
                    Surface(color = Blue.copy(alpha = .13f), shape = RoundedCornerShape(30.dp)) {
                        Text("v0.3.1 ALPHA", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF101929)) {
                    listOf(
                        Triple(Page.HOME, "Overview", Icons.Rounded.SpaceDashboard),
                        Triple(Page.APPS, "Games", Icons.Rounded.SportsEsports),
                        Triple(Page.LAB, "FG Lab", Icons.Rounded.AutoAwesomeMotion),
                        Triple(Page.DIAGNOSTICS, "Diagnostics", Icons.Rounded.Tune)
                    ).forEach { (tab, label, graphic) ->
                        NavigationBarItem(
                            selected = page == tab,
                            onClick = { page = tab },
                            icon = { Icon(graphic, null) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Blue, selectedTextColor = Blue,
                                indicatorColor = Blue.copy(alpha = .14f),
                                unselectedIconColor = Secondary, unselectedTextColor = Secondary
                            )
                        )
                    }
                }
            }
        ) { insets ->
            when (page) {
                Page.HOME -> Column(
                    Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderCard(report.model)
                    SectionTitle("Live capture", "Capture / GPU-buffer feasibility · no game FG")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("CAPTURE CALLBACK", "%.1f".format(status.captureCallbackFps), "per second", Modifier.weight(1f))
                        MetricCard("RECEIVED FRAMES", "${status.framesReceived}", "since session start", Modifier.weight(1f))
                    }
                    PanelCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(status.state == "CAPTURING")
                            Spacer(Modifier.width(8.dp))
                            Text(status.state, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${status.width} × ${status.height}", color = Secondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "จำนวนภาพที่ MediaProjection ส่งเข้าแอป ไม่ใช่ FPS ของเกมหรือ Output FPS",
                            color = Secondary, fontSize = 12.sp, lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        InfoLine("CAPTURE FORMAT", status.captureMode)
                        InfoLine("GPU BUFFERS", "${status.hardwareBuffersReceived}")
                        InfoLine("NONMONOTONIC TIMESTAMPS", "${status.timestampIssues}")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("GPU buffer probe (PRIVATE)", color = Secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = gpuCaptureMode, onCheckedChange = { gpuCaptureMode = it }, enabled = status.state != "CAPTURING")
                        }
                        Text("GPU buffer mode only checks capture and buffer metadata; Vulkan importing and real-time FG are not implemented.", color = Secondary, fontSize = 11.sp)
                        Spacer(Modifier.height(16.dp))
                        if (status.state == "CAPTURING") {
                            OutlinedButton(onClick = stopCapture, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.StopCircle, null, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(8.dp)); Text("หยุด Capture")
                            }
                        } else {
                            Button(onClick = { startCapture(gpuCaptureMode) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp)); Text("เริ่ม Capture Test")
                            }
                        }
                        if (status.lastError != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(status.lastError ?: "", color = Color(0xFFFFA79C), fontSize = 12.sp)
                        }
                    }
                    SectionTitle("Phase 0 checklist", "ทดสอบฮาร์ดแวร์ก่อนพัฒนา FG Engine")
                    PanelCard {
                        CheckLine("Android 12+", true)
                        CheckLine("Vulkan feature advertised", report.vulkanAdvertised)
                        CheckLine("MediaProjection session", status.framesReceived > 0)
                        CheckLine("GPU buffer capture callbacks", status.hardwareBuffersReceived > 0)
                        CheckLine("Fullscreen FG + touch-through", false)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Page.APPS -> Column(
                    Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)
                ) {
                    SectionTitle("Installed apps", "เลือกแอปเพื่อเปิดทดสอบ หลังอนุญาต Capture")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("เฉพาะเกม", color = Secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = showGamesOnly, onCheckedChange = { showGamesOnly = it })
                        IconButton(onClick = { apps = installedApps() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = Blue, fontSize = 12.sp)
                    val visibleApps = if (showGamesOnly) apps.filter { it.markedGame } else apps
                    if (visibleApps.isEmpty()) {
                        PanelCard {
                            Text("ไม่พบแอปในหมวดที่เลือก", fontWeight = FontWeight.SemiBold)
                            Text("บางเกมไม่ได้ระบุหมวด Game — ลองปิดตัวกรอง 'เฉพาะเกม'", color = Secondary, fontSize = 12.sp)
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(visibleApps, key = { it.packageName }) { app ->
                            Surface(
                                color = Panel, shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Border)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).background(Blue.copy(alpha = .15f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                                        Text(app.label.take(1).uppercase(), color = Blue, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                        Text(app.packageName, color = Secondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { message = if (launchApp(app)) "เปิด ${app.label} แล้ว" else "เปิด ${app.label} ไม่สำเร็จ" }) {
                                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Launch", tint = Blue)
                                    }
                                }
                            }
                        }
                    }
                }
                Page.LAB -> Column(
                    Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    RealtimePlaybackLab()
                    Spacer(Modifier.height(24.dp))
                    InterpolationLab()
                }
                Page.DIAGNOSTICS -> Column(
                    Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionTitle("Device diagnostics", "ใช้ตรวจสอบความพร้อมสำหรับ Vulkan engine")
                    PanelCard {
                        InfoLine("DEVICE", report.model)
                        InfoLine("MANUFACTURER", report.manufacturer)
                        InfoLine("ANDROID", report.androidVersion)
                        InfoLine("CHIPSET", report.soc)
                        InfoLine("VULKAN API", report.vulkanApi)
                        InfoLine("VULKAN LEVEL", report.vulkanLevel)
                        InfoLine("DISPLAY MODES", report.refreshRates)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            vulkanDetails = "Probing Vulkan driver…"
                            scope.launch {
                                vulkanDetails = withContext(Dispatchers.Default) { NativeVulkanProbe.report() }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Run GPU probe (native Vulkan)") }
                        Text(vulkanDetails, color = Secondary, fontSize = 12.sp)
                    }
                    SectionTitle("Overlay touch probe", "ทดสอบหน้าต่างลอยขนาดเล็ก")
                    PanelCard {
                        Text("Overlay นี้ไม่สร้างเฟรม ไม่บังคับสัมผัส และไม่ได้ยืนยันว่า Overlay เต็มจอจะใช้กับเกมได้", color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(12.dp))
                        InfoLine("Permission", if (allowed) "Granted" else "Not granted")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Show test overlay", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Switch(checked = overlayOn, onCheckedChange = { desired ->
                                if (desired && !overlayAllowed()) requestOverlayPermission()
                                else {
                                    overlayOn = toggleOverlay(desired)
                                    fullscreenOn = fullscreenVisible()
                                }
                            })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Fullscreen transparent touch test", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Switch(checked = fullscreenOn, onCheckedChange = { desired ->
                                if (desired && !overlayAllowed()) requestOverlayPermission()
                                else {
                                    fullscreenOn = toggleFullscreenOverlay(desired)
                                    overlayOn = overlayVisible() && !fullscreenVisible()
                                }
                            })
                        }
                        Text("Fullscreen mode uses a translucent 0.35-opacity, NOT_TOUCHABLE overlay. Try swipe and multitouch in a benign game and record whether the game still responds. Only one overlay at a time.", color = Secondary, fontSize = 11.sp)
                        if (!allowed) {
                            Spacer(Modifier.height(8.dp))
                            Text("Android / ColorOS อาจบล็อก Overlay สำหรับ APK ที่ติดตั้งภายนอก Play Store", fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD49C), fontSize = 12.sp)
                            Text(
                                "ถ้าพบข้อความ 'แอปถูกปฏิเสธไม่ให้เข้าถึง': ไปที่ ข้อมูลแอป > เมนู ⋮ > อนุญาตการตั้งค่าที่จำกัด (ถ้ามี) > ยืนยัน PIN/ลายนิ้วมือ แล้วกลับมาเปิดสิทธิ์แสดงผลอยู่บนแอปอื่นอีกครั้ง",
                                color = Secondary, fontSize = 12.sp, lineHeight = 19.sp
                            )
                            Text(
                                "อนุญาตเฉพาะ APK ที่คุณเชื่อถือ การเปิดสิทธิ์นี้ไม่จำเป็นสำหรับ Capture Test หรือ FG Lab และแอปไม่สามารถข้ามข้อจำกัดของระบบโดยอัตโนมัติได้",
                                color = Secondary, fontSize = 11.sp, lineHeight = 17.sp
                            )
                            OutlinedButton(onClick = openAppInfo, modifier = Modifier.fillMaxWidth()) {
                                Text("เปิดข้อมูลแอป (App info)")
                            }
                            TextButton(onClick = { requestOverlayPermission() }) {
                                Text("ลองเปิดการตั้งค่า Overlay อีกครั้ง")
                            }
                        }
                    }
                    PanelCard {
                        Icon(Icons.Rounded.Info, null, tint = Blue)
                        Text("Native CPU reference + GPU capture probe", fontWeight = FontWeight.SemiBold)
                        Text("FG Lab มี Native CPU interpolation บนภาพสังเคราะห์; GPU buffer probe ยังไม่ใช่ Vulkan import / Fullscreen FG", color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(model: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = Panel) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1B3E73), Color(0xFF141F34), Color(0xFF10192B)))).padding(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Green.copy(alpha = .15f), shape = RoundedCornerShape(20.dp)) {
                    Text("PHASE 4 · PACING / TOUCH PROBES", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
                Text("Prepare for\nFrame Generation", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 32.sp)
                Text(model, color = Color(0xFFB8D0F2), fontSize = 12.sp)
                Text("This version adds VSync pacing & fullscreen touch probes. No live game FPS boost.", color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun MetricCard(label: String, value: String, footer: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(footer, color = Secondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(Modifier.size(9.dp).background(if (active) Green else Color(0xFF8A93A3), CircleShape))
}

@Composable
private fun CheckLine(label: String, passed: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (passed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (passed) Green else Secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (passed) Color.White else Secondary, fontSize = 13.sp)
    }
}

@Composable
private fun InfoLine(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(title, modifier = Modifier.width(126.dp), color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(detail, modifier = Modifier.weight(1f), color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
