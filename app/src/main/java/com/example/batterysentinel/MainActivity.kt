package com.example.batterysentinel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.batterysentinel.ui.MainScreen
import com.example.batterysentinel.ui.MainViewModel
import com.example.batterysentinel.ui.theme.BatterySentinelTheme

/**
 * The main entry point of the application UI. Provides the Jetpack Compose surface
 * and requests necessary permissions from the user.
 */
class MainActivity : ComponentActivity() {
    // Lazily instantiate the ViewModel scoped to this Activity's lifecycle
    private val viewModel: MainViewModel by viewModels()

    // Setup permission launcher using the modern ActivityResultContracts API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle result. We could show a toast here if permission was denied,
        // but for now we proceed silently.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ask the OS to exempt us from battery optimizations so background checks run reliably
        requestIgnoreBatteryOptimizations()
        // Ensure we can actually send the low-battery distress signal!
        requestNotificationPermission()

        setContent {
            BatterySentinelTheme {
                // Pass downstream interactions to our MainScreen composable
                MainScreen(
                    viewModel = viewModel,
                    onPowerSaveClick = { openPowerSaveSettings() }
                )
            }
        }
    }

    /**
     * Checks if notification permission is granted (required in Android 13/Tiramisu).
     * If not, it requests it from the user.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Prompts the user to ignore battery optimizations for this app, ensuring our background
     * receiver isn't entirely suspended by Android Doze mode.
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    /**
     * Launches the system's battery saver settings screen, so the user can easily
     * enable the battery saver manually after getting an alert.
     */
    private fun openPowerSaveSettings() {
        // Fallback directly to Battery Saver Settings since 3rd party apps cannot toggle it natively
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        startActivity(intent)
    }
}
