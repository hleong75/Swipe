package com.hleong75.swipe.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hleong75.swipe.R
import com.hleong75.swipe.service.AutomationService
import com.hleong75.swipe.service.SwipeAccessibilityService

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = AutomationService.startIntent(this, result.resultCode, result.data!!)
            ContextCompat.startForegroundService(this, serviceIntent)
            statusText.setText(R.string.status_running)
        } else {
            toast("Capture d'écran refusée")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val accessibilityButton: Button = findViewById(R.id.accessibilityButton)
        val overlayButton: Button = findViewById(R.id.overlayButton)
        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        overlayButton.setOnClickListener {
            openOverlaySettings()
        }

        startButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled(this)) {
                toast("Active d'abord le service d'accessibilité Swipe")
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                toast("Autorise la superposition avant de démarrer")
                openOverlaySettings()
                return@setOnClickListener
            }
            requestScreenCapture()
        }

        stopButton.setOnClickListener {
            startService(AutomationService.stopIntent(this))
            statusText.setText(R.string.status_idle)
        }
    }

    override fun onResume() {
        super.onResume()
        val ready = isAccessibilityServiceEnabled(this) && Settings.canDrawOverlays(this)
        statusText.text = if (ready) "Prêt : permissions OK" else "Permissions incomplètes"
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, SwipeAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }
}
