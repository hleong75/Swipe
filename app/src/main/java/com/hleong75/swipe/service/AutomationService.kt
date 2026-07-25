package com.hleong75.swipe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Rect
import android.media.Image
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hleong75.swipe.Constants
import com.hleong75.swipe.R
import com.hleong75.swipe.capture.ScreenCaptureManager
import com.hleong75.swipe.detection.BeltDetector
import com.hleong75.swipe.detection.DetectionResult
import com.hleong75.swipe.overlay.DetectionSnapshot
import com.hleong75.swipe.overlay.OverlayController
import com.hleong75.swipe.swipe.SwipeDispatcher
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class AutomationService : Service(), OverlayController.Callback {
    private val detector = BeltDetector()
    private val swipeDispatcher = SwipeDispatcher()
    private val debugCaptureRequested = AtomicBoolean(false)

    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var overlayController: OverlayController

    private var paused = false
    private var swipeCount = 0
    private var lastSwipeTimestamp = 0L
    private var lastDetection: DetectionResult? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID, buildNotification())

        captureManager = ScreenCaptureManager(this) { image ->
            processFrame(image)
        }
        overlayController = OverlayController(this, this)
        overlayController.showIfAllowed()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode == Int.MIN_VALUE || resultData == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                captureManager.start(resultCode, resultData)
                paused = false
                updateOverlay()
            }
            ACTION_PAUSE_RESUME -> {
                paused = !paused
                updateOverlay()
            }
            ACTION_DEBUG -> {
                debugCaptureRequested.set(true)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun processFrame(image: Image) {
        if (!paused) {
            val hit = detector.detect(image)
            if (hit != null) {
                lastDetection = hit
                if (System.currentTimeMillis() - lastSwipeTimestamp >= Constants.COOLDOWN_MS) {
                    val didSwipe = swipeDispatcher.dispatchSwipe(
                        startX = hit.x,
                        startY = hit.y,
                        distance = Constants.SWIPE_DISTANCE,
                        durationMs = Constants.SWIPE_DURATION_MS
                    )
                    if (didSwipe) {
                        swipeCount += 1
                        lastSwipeTimestamp = System.currentTimeMillis()
                    }
                }
            }
        }

        if (debugCaptureRequested.compareAndSet(true, false)) {
            saveDebugFrame(image)
            val stats = detector.lastStats
            Log.d(TAG, "Debug stats - cream(col=${stats.creamColumn},count=${stats.creamCount}) green(col=${stats.greenColumn},count=${stats.greenCount})")
        }

        updateOverlay()
    }

    private fun updateOverlay() {
        overlayController.updateState(
            paused = paused,
            detection = lastDetection?.let { DetectionSnapshot(it.type, it.x, it.score) },
            swipeCount = swipeCount
        )
    }

    private fun saveDebugFrame(image: Image) {
        runCatching {
            val bitmap = image.toBitmap(Rect(0, 0, image.width, image.height))
            val pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            val file = File(pictures, "debug_frame_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            Log.d(TAG, "Debug frame saved: ${file.absolutePath}")
        }.onFailure {
            Log.e(TAG, "Failed to save debug frame", it)
        }
    }

    private fun Image.toBitmap(cropRect: Rect): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        buffer.rewind()
        val wideBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Config.ARGB_8888
        )
        wideBitmap.copyPixelsFromBuffer(buffer)
        val result = Bitmap.createBitmap(wideBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        wideBitmap.recycle()
        return result
    }

    override fun onDestroy() {
        captureManager.stop()
        overlayController.dismiss()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onPauseResumeRequested() {
        paused = !paused
        updateOverlay()
    }

    override fun onDebugRequested() {
        debugCaptureRequested.set(true)
    }

    override fun onQuitRequested() {
        stopSelf()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Swipe Automation",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AutomationService"
        const val ACTION_START = "com.hleong75.swipe.action.START"
        const val ACTION_PAUSE_RESUME = "com.hleong75.swipe.action.PAUSE_RESUME"
        const val ACTION_DEBUG = "com.hleong75.swipe.action.DEBUG"
        const val ACTION_STOP = "com.hleong75.swipe.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun startIntent(context: Context, resultCode: Int, resultData: Intent): Intent {
            return Intent(context, AutomationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, AutomationService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
