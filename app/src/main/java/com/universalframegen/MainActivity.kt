package com.universalframegen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import com.universalframegen.capture.FrameCaptureService
import com.universalframegen.capture.CaptureStatus
import com.universalframegen.capture.CaptureTelemetry
import com.universalframegen.diagnostics.DeviceDiagnostics
import com.universalframegen.games.GameCatalog
import com.universalframegen.overlay.OverlayProbe
import com.universalframegen.ui.FrameGenScreen

class MainActivity : ComponentActivity() {
    private val overlayPermissionGranted = mutableStateOf(false)
    private lateinit var overlay: OverlayProbe
    private var pendingGpuMode = false

    private val requestCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, FrameCaptureService::class.java)
                .setAction(FrameCaptureService.ACTION_START)
                .putExtra(FrameCaptureService.EXTRA_CODE, result.resultCode)
                .putExtra(FrameCaptureService.EXTRA_GPU_MODE, pendingGpuMode)
                .putExtra(FrameCaptureService.EXTRA_DATA, data)
            runCatching { ContextCompat.startForegroundService(this, service) }
                .onFailure { CaptureTelemetry.publish(CaptureStatus(state = "ERROR", lastError = it.message ?: "Unable to start capture service")) }
        } else {
            CaptureTelemetry.publish(CaptureStatus(state = "IDLE", lastError = "User cancelled capture consent"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overlay = OverlayProbe(applicationContext)
        overlayPermissionGranted.value = Settings.canDrawOverlays(this)
        setContent {
            FrameGenScreen(
                report = DeviceDiagnostics.scan(this),
                installedApps = { GameCatalog.installed(this) },
                launchApp = { GameCatalog.launch(this, it) },
                startCapture = { gpuMode ->
                    CaptureTelemetry.publish(CaptureStatus(state = "REQUESTING"))
                    pendingGpuMode = gpuMode
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    requestCapture.launch(mgr.createScreenCaptureIntent())
                },
                stopCapture = {
                    startService(Intent(this, FrameCaptureService::class.java)
                        .setAction(FrameCaptureService.ACTION_STOP))
                },
                overlayAllowed = { Settings.canDrawOverlays(this) },
                overlayPermissionGranted = overlayPermissionGranted.value,
                openAppInfo = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
                requestOverlayPermission = {
                    val settings = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(settings)
                },
                overlayVisible = { overlay.shown },
                toggleOverlay = { enabled ->
                    if (enabled) overlay.show(fullscreen = false) else { overlay.hide(); true }
                },
                fullscreenVisible = { overlay.isFullScreen },
                toggleFullscreenOverlay = { enabled ->
                    if (enabled) overlay.show(fullscreen = true) else { overlay.hide(); true }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check after returning from ColorOS / Android special access settings.
        overlayPermissionGranted.value = Settings.canDrawOverlays(this)
    }

    override fun onDestroy() {
        overlay.hide()
        super.onDestroy()
    }
}
