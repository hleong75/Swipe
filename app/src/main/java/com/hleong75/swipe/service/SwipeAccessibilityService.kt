package com.hleong75.swipe.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.hleong75.swipe.swipe.SwipeAccessibilityGateway

class SwipeAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        SwipeAccessibilityGateway.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (SwipeAccessibilityGateway.service === this) {
            SwipeAccessibilityGateway.service = null
        }
        super.onDestroy()
    }
}
