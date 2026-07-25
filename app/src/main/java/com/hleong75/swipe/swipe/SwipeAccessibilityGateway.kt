package com.hleong75.swipe.swipe

import com.hleong75.swipe.service.SwipeAccessibilityService

object SwipeAccessibilityGateway {
    @Volatile
    var service: SwipeAccessibilityService? = null
}
