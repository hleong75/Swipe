package com.hleong75.swipe.swipe

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.math.max

class SwipeDispatcher {
    fun dispatchSwipe(startX: Int, startY: Int, distance: Int, durationMs: Long): Boolean {
        val service = SwipeAccessibilityGateway.service ?: return false
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat(), max(0, startY + distance).toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }
}
