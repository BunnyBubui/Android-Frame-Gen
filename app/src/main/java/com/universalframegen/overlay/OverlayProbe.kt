package com.universalframegen.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Separate touch feasibility probes; no captured content is displayed here.
 * The full-screen probe deliberately keeps window alpha <= Android 12 SAW
 * obscuring-opacity limit. Full-opaque framegen and generic input passthrough
 * remain unproven and may be impossible for an arbitrary target app.
 */
class OverlayProbe(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var fullScreen = false

    val shown: Boolean get() = view != null
    val isFullScreen: Boolean get() = shown && fullScreen

    fun show(fullscreen: Boolean = false): Boolean {
        if (view != null && fullScreen == fullscreen) return true
        if (!Settings.canDrawOverlays(context)) return false
        hide()
        val label = TextView(context).apply {
            text = if (fullscreen) {
                "FRAMEGEN INPUT TEST · TRANSLUCENT OVERLAY\nSwipe / drag / hold / multi-touch in the game\nNOT a live FG preview"
            } else "UFG · OVERLAY TEST\nNO FRAME GEN"
            setTextColor(Color.WHITE)
            textSize = if (fullscreen) 13f else 12f
            gravity = if (fullscreen) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            setPadding(dp(10), if (fullscreen) dp(100) else 0, dp(10), 0)
            background = GradientDrawable().apply {
                setColor(if (fullscreen) Color.rgb(20, 36, 62) else Color.rgb(24, 48, 78))
                cornerRadius = if (fullscreen) 0f else dp(20).toFloat()
                setStroke(dp(2), Color.rgb(70, 151, 240))
            }
        }
        val params = WindowManager.LayoutParams(
            if (fullscreen) WindowManager.LayoutParams.MATCH_PARENT else dp(190),
            if (fullscreen) WindowManager.LayoutParams.MATCH_PARENT else dp(70),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (fullscreen) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
            if (!fullscreen) { x = dp(12); y = dp(88) }
            alpha = if (fullscreen) 0.35f else 0.5f
        }
        return runCatching {
            manager.addView(label, params)
            view = label
            fullScreen = fullscreen
            true
        }.getOrDefault(false)
    }

    fun hide() {
        val old = view ?: return
        view = null
        fullScreen = false
        runCatching { manager.removeViewImmediate(old) }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
