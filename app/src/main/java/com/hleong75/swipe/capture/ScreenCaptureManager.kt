package com.hleong75.swipe.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureManager(
    private val context: Context,
    private val onFrame: (Image) -> Unit
) {
    private val running = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stop()
        }
    }

    @Synchronized
    fun start(resultCode: Int, resultData: android.content.Intent) {
        stop()

        runCatching {
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val densityDpi = metrics.densityDpi
            require(width > 0 && height > 0 && densityDpi > 0)

            callbackThread = HandlerThread("CaptureThread").also { it.start() }
            callbackHandler = Handler(callbackThread!!.looper)
            running.set(true)

            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                if (!running.get()) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    onFrame(image)
                } finally {
                    image.close()
                }
            }, callbackHandler)

            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection unavailable")
            mediaProjection.registerCallback(projectionCallback, callbackHandler)
            projection = mediaProjection

            virtualDisplay = projection?.createVirtualDisplay(
                "SwipeVirtualDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                callbackHandler
            ) ?: error("VirtualDisplay creation failed")
        }.onFailure {
            stop()
            throw it
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.unregisterCallback(projectionCallback)
        runCatching {
            projection?.stop()
        }
        projection = null
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
    }
}
