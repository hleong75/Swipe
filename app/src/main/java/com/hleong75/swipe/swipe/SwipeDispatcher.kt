package com.hleong75.swipe.swipe

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.math.max

data class SwipeTarget(val x: Int, val y: Int)

class SwipeDispatcher {
    fun dispatchSwipe(startX: Int, startY: Int, distance: Int, durationMs: Long): Boolean {
        return dispatchSwipes(
            targets = listOf(SwipeTarget(startX, startY)),
            distance = distance,
            durationMs = durationMs
        )
    }

    fun dispatchSwipes(targets: List<SwipeTarget>, distance: Int, durationMs: Long): Boolean {
        if (targets.isEmpty()) return false
        val service = SwipeAccessibilityGateway.service ?: return false

        val gestureBuilder = GestureDescription.Builder()
        targets.forEach { target ->
            val path = Path().apply {
                moveTo(target.x.toFloat(), target.y.toFloat())
                lineTo(target.x.toFloat(), max(0, target.y + distance).toFloat())
            }
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
        }
        val gesture = gestureBuilder.build()
        return service.dispatchGesture(gesture, null, null)
    }
}
