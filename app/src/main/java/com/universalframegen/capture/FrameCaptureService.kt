package com.universalframegen.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.app.PendingIntent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.universalframegen.MainActivity
import com.universalframegen.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * PHASE 2: RGBA diagnostic or GPU-sampleable PRIVATE HardwareBuffer probe.
 * No image pixels are exported, retained, or interpolated in capture mode.
 * One consent result is used exactly once for one MediaProjection session.
 */
class FrameCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.universalframegen.START_CAPTURE"
        const val ACTION_STOP = "com.universalframegen.STOP_CAPTURE"
        const val EXTRA_CODE = "capture_result_code"
        const val EXTRA_DATA = "capture_result_data"
        const val EXTRA_GPU_MODE = "capture_gpu_mode"
        private const val CHANNEL = "capture_diagnostic"
        private const val NOTIFICATION_ID = 1102
        private const val MAX_EDGE = 1280 // diagnostic capture <= 720p in landscape
    }

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var worker: Handler? = null
    private var active = false
    private var stopping = false
    private var foregroundStarted = false

    private var gpuRequested = false
    private var gpuReaderActive = false
    private var modeWarning: String? = null
    private var gpuBuffers = 0L
    private var badTimestamps = 0L
    private var lastTimestampNs = 0L
    private var received = 0L
    private var windowFrames = 0L
    private var periodStartNs = 0L
    private var measuredFps = 0.0
    private var captureWidth = 0
    private var captureHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val handler = worker
            if (handler != null) handler.post { finishSession("IDLE"); stopSelf() }
            else { finishSession("IDLE"); stopSelf() }
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || active) return START_NOT_STICKY

        val code = intent.getIntExtra(EXTRA_CODE, android.app.Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (code != android.app.Activity.RESULT_OK || data == null) {
            CaptureTelemetry.publish(CaptureStatus(state = "ERROR", lastError = "Consent missing"))
            stopSelf()
            return START_NOT_STICKY
        }

        stopping = false
        gpuRequested = intent.getBooleanExtra(EXTRA_GPU_MODE, false)
        gpuBuffers = 0L
        badTimestamps = 0L
        lastTimestampNs = 0L
        modeWarning = null
        try {
            createNotificationChannel()
            startForeground(
                NOTIFICATION_ID,
                makeNotification("Diagnostic capture running"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
            foregroundStarted = true
            beginCapture(code, data)
        } catch (t: Exception) {
            finishSession("ERROR", "${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun beginCapture(code: Int, permissionData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureThread = HandlerThread("UfgCaptureReader").also { it.start() }
        worker = Handler(captureThread!!.looper)
        projection = manager.getMediaProjection(code, permissionData)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                // Consent revoked by system/user. Do not reuse the token.
                worker?.post {
                    finishSession("STOPPED")
                    stopSelf()
                } ?: run {
                    finishSession("STOPPED")
                    stopSelf()
                }
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                worker?.post { if (active) resizeCapture(width, height) }
            }
        }
        projectionCallback = callback
        projection!!.registerCallback(callback, worker)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.maximumWindowMetrics.bounds
        val initialWidth = max(1, bounds.width())
        val initialHeight = max(1, bounds.height())
        val scaled = scaledDimensions(initialWidth, initialHeight)
        captureWidth = scaled.first
        captureHeight = scaled.second
        createImageReader(captureWidth, captureHeight)

        // Android 14+: exactly one createVirtualDisplay per MediaProjection.
        display = projection!!.createVirtualDisplay(
            "UniversalFrameGen-Diagnostic",
            captureWidth,
            captureHeight,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            worker
        )
        active = true
        stopping = false
        received = 0
        windowFrames = 0
        measuredFps = 0.0
        periodStartNs = SystemClock.elapsedRealtimeNanos()
        CaptureTelemetry.publish(
            CaptureStatus(
                state = "CAPTURING", width = captureWidth, height = captureHeight,
                captureMode = if (gpuReaderActive) "GPU PRIVATE buffer" else "RGBA diagnostic",
                lastError = modeWarning
            )
        )
    }

    private fun createImageReader(width: Int, height: Int) {
        val created = if (gpuRequested) {
            // PRIVATE images do not expose pixel planes. An owned HardwareBuffer can be
            // imported by GPU APIs in a future phase, but is only checked/closed here.
            runCatching {
                ImageReader.newInstance(
                    width, height, ImageFormat.PRIVATE, 3,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                )
            }.onFailure {
                modeWarning = "GPU PRIVATE unsupported; using RGBA fallback: ${it.javaClass.simpleName}"
            }.getOrNull()
        } else null
        gpuReaderActive = created != null
        reader = (created ?: ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)).also { ir ->
            ir.setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: IllegalStateException) { null }
                if (image != null) {
                    try { onFrameReceived(image) } finally { image.close() }
                }
            }, worker)
        }
    }

    private fun onFrameReceived(image: Image) {
        if (!active) return
        received++
        windowFrames++
        if (gpuReaderActive) {
            // This proves buffer delivery only, NOT zero-copy Vulkan import or processing.
            runCatching {
                image.hardwareBuffer?.let { buffer ->
                    try { gpuBuffers++ } finally { buffer.close() }
                }
            }.onFailure { modeWarning = "HardwareBuffer access failed: ${it.javaClass.simpleName}" }
        }
        if (lastTimestampNs > 0 && image.timestamp <= lastTimestampNs) badTimestamps++
        lastTimestampNs = image.timestamp
        val now = SystemClock.elapsedRealtimeNanos()
        val elapsed = now - periodStartNs
        if (elapsed >= 1_000_000_000L) {
            measuredFps = windowFrames * 1_000_000_000.0 / elapsed.toDouble()
            windowFrames = 0L
            periodStartNs = now
            CaptureTelemetry.publish(
                CaptureStatus(
                    state = "CAPTURING",
                    framesReceived = received,
                    captureCallbackFps = measuredFps,
                    hardwareBuffersReceived = gpuBuffers,
                    timestampIssues = badTimestamps,
                    captureMode = if (gpuReaderActive) "GPU PRIVATE buffer" else "RGBA diagnostic",
                    width = captureWidth,
                    height = captureHeight,
                    lastError = modeWarning
                )
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                NOTIFICATION_ID, makeNotification("Capture callbacks: %.1f/s".format(measuredFps))
            )
        }
    }

    private fun resizeCapture(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || !active) return
        val newSize = scaledDimensions(width, height)
        if (newSize.first == captureWidth && newSize.second == captureHeight) return
        val vd = display ?: return
        // Listener and display operations are confined to the capture HandlerThread.
        vd.setSurface(null)
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        captureWidth = newSize.first
        captureHeight = newSize.second
        createImageReader(captureWidth, captureHeight)
        lastTimestampNs = 0L // orientation or capture target resize may change timestamp sequence
        vd.resize(captureWidth, captureHeight, resources.displayMetrics.densityDpi)
        vd.setSurface(reader!!.surface)
    }

    private fun scaledDimensions(width: Int, height: Int): Pair<Int, Int> {
        val factor = minOf(1.0, MAX_EDGE.toDouble() / max(width, height))
        // Align to even dimensions for future GPU processing.
        fun even(value: Int) = max(2, ((value * factor).roundToInt() / 2) * 2)
        return even(width) to even(height)
    }

    private fun finishSession(state: String, error: String? = null) {
        if (stopping) return
        stopping = true
        active = false
        runCatching { display?.release() }
        display = null
        runCatching { reader?.setOnImageAvailableListener(null, null) }
        runCatching { reader?.close() }
        reader = null
        val oldProjection = projection
        val oldCallback = projectionCallback
        projection = null
        projectionCallback = null
        if (oldProjection != null) {
            if (oldCallback != null) runCatching { oldProjection.unregisterCallback(oldCallback) }
            runCatching { oldProjection.stop() }
        }
        CaptureTelemetry.publish(
            CaptureStatus(
                state = state,
                framesReceived = received,
                captureCallbackFps = measuredFps,
                captureMode = if (gpuReaderActive) "GPU PRIVATE buffer" else "RGBA diagnostic",
                hardwareBuffersReceived = gpuBuffers,
                timestampIssues = badTimestamps,
                width = captureWidth,
                height = captureHeight,
                lastError = error ?: modeWarning
            )
        )
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        captureThread?.quitSafely()
        captureThread = null
        worker = null
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.capture_channel_description) }
        )
    }

    private fun makeNotification(content: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, FrameCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Universal FrameGen · Capture test")
            .setContentText(content)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        finishSession("STOPPED")
        super.onDestroy()
    }
}
