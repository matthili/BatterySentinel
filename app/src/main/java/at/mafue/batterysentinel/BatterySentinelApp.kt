package at.mafue.batterysentinel

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import at.mafue.batterysentinel.firebase.AppCheckInitializer
import at.mafue.batterysentinel.firebase.GooglePlayServicesChecker
import at.mafue.batterysentinel.firebase.GmsStatus
import at.mafue.batterysentinel.firebase.MultiDeviceManager
import at.mafue.batterysentinel.receiver.BatteryReceiver
import at.mafue.batterysentinel.worker.BatteryWorker
import com.google.firebase.FirebaseApp
import java.util.concurrent.TimeUnit

/**
 * Application class for BatterySentinel. This acts as the global initialization point.
 * Its primary purpose is to bootstrap the battery monitoring logic as soon as the app process is created.
 *
 * In v2.0, also initializes Firebase (App Check, FCM token refresh) when GMS is available.
 */
class BatterySentinelApp : Application() {
    // Keep a single persistent instance of our BroadcastReceiver
    private val batteryReceiver = BatteryReceiver()

    /**
     * Called when the application is starting, before any activity, service, or receiver 
     * objects (excluding content providers) have been created.
     */
    override fun onCreate() {
        super.onCreate()

        // 1. Register the battery receiver FIRST – this is the most critical operation.
        //    Doing this before Firebase ensures battery monitoring works even if Firebase fails.
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d("BatterySentinelApp", "Battery receiver registered")
        } catch (e: Exception) {
            Log.e("BatterySentinelApp", "CRITICAL: Failed to register battery receiver", e)
        }

        // 2. Setup WorkManager safety net – runs every 30 minutes as a fallback.
        //    UPDATE ensures the schedule is always current after app updates.
        try {
            val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "BatterySafetyNet",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d("BatterySentinelApp", "WorkManager scheduled (30 min)")
        } catch (e: Exception) {
            Log.e("BatterySentinelApp", "Failed to schedule WorkManager", e)
        }

        // 3. Initialize Firebase LAST – this is optional and must never block battery monitoring.
        try {
            if (GooglePlayServicesChecker.checkAvailability(this) == GmsStatus.AVAILABLE) {
                FirebaseApp.initializeApp(this)
                AppCheckInitializer.initialize(this)
                MultiDeviceManager.refreshTokenIfNeeded(this)
                Log.d("BatterySentinelApp", "Firebase initialized")
            }
        } catch (e: Exception) {
            Log.w("BatterySentinelApp", "Firebase initialization failed (battery monitoring unaffected)", e)
        }
    }
}
