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
import java.util.concurrent.atomic.AtomicReference

class ScreenCaptureManager(
    private val context: Context,
    private val onFrame: (Image) -> Unit
) {
    private val pendingImage = AtomicReference<Image?>(null)
    private val processing = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var callbackThread: HandlerThread? = null
    private var callbackHandler: Handler? = null

    fun start(resultCode: Int, resultData: android.content.Intent) {
        stop()

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        callbackThread = HandlerThread("CaptureThread").also { it.start() }
        callbackHandler = Handler(callbackThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val latest = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            pendingImage.getAndSet(latest)?.close()
            if (processing.compareAndSet(false, true)) {
                callbackHandler?.post { drainLatestFrames() }
            }
        }, callbackHandler)

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(resultCode, resultData)

        virtualDisplay = projection?.createVirtualDisplay(
            "SwipeVirtualDisplay",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            callbackHandler
        )
    }

    private fun drainLatestFrames() {
        while (true) {
            val image = pendingImage.getAndSet(null) ?: break
            try {
                onFrame(image)
            } finally {
                image.close()
            }
        }
        processing.set(false)
        if (pendingImage.get() != null && processing.compareAndSet(false, true)) {
            callbackHandler?.post { drainLatestFrames() }
        }
    }

    fun stop() {
        pendingImage.getAndSet(null)?.close()
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
        processing.set(false)
    }
}
