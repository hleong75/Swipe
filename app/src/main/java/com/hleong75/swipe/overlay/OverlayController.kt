package com.hleong75.swipe.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hleong75.swipe.Constants
import com.hleong75.swipe.detection.DetectionType
import kotlin.math.max

class OverlayController(
    private val context: Context,
    private val callback: Callback
) {
    interface Callback {
        fun onPauseResumeRequested()
        fun onDebugRequested()
        fun onQuitRequested()
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var stateText: TextView? = null
    private var detectionText: TextView? = null
    private var swipeCountText: TextView? = null
    private var pauseButton: Button? = null

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun showIfAllowed() {
        if (!canDrawOverlays() || overlayView != null) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC111111.toInt())
            setPadding(24, 24, 24, 24)
        }

        stateText = TextView(context).also { it.setTextColor(0xFFFFFFFF.toInt()) }
        detectionText = TextView(context).also { it.setTextColor(0xFFFFFFFF.toInt()) }
        swipeCountText = TextView(context).also { it.setTextColor(0xFFFFFFFF.toInt()) }

        pauseButton = Button(context).apply {
            text = "Pause"
            setOnClickListener { callback.onPauseResumeRequested() }
        }
        val debugButton = Button(context).apply {
            text = "Debug"
            setOnClickListener { callback.onDebugRequested() }
        }
        val quitButton = Button(context).apply {
            text = "Quitter"
            setOnClickListener { callback.onQuitRequested() }
        }

        container.addView(stateText)
        container.addView(detectionText)
        container.addView(swipeCountText)
        container.addView(pauseButton)
        container.addView(debugButton)
        container.addView(quitButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = overlayTopOffset()
        }

        runCatching {
            overlayView = container
            windowManager.addView(container, params)
            updateState(false, null, 0)
        }.onFailure {
            overlayView = null
        }
    }

    fun updateState(paused: Boolean, detection: DetectionSnapshot?, swipeCount: Int) {
        stateText?.text = if (paused) "État : en pause" else "État : en cours"
        detectionText?.text = detection?.let {
            val label = if (it.type == DetectionType.CREAM_LOGO) "Logo" else "Chrono"
            "Dernière détection : $label | x=${it.x} | pixels=${it.score}"
        } ?: "Dernière détection : aucune"
        swipeCountText?.text = "Swipes : $swipeCount"
        pauseButton?.text = if (paused) "Reprendre" else "Pause"
    }

    fun dismiss() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
    }

    private fun overlayTopOffset(): Int {
        val metrics = context.resources.displayMetrics
        val beltY = (Constants.BELT_Y_RATIO * metrics.heightPixels).toInt()
        val estimatedHeight = (180 * metrics.density).toInt()
        return max(24, beltY - estimatedHeight)
    }
}

data class DetectionSnapshot(
    val type: DetectionType,
    val x: Int,
    val score: Int
)
