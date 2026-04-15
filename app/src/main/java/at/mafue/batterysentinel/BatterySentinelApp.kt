package at.mafue.batterysentinel

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import at.mafue.batterysentinel.receiver.BatteryReceiver
import at.mafue.batterysentinel.worker.BatteryWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for BatterySentinel. This acts as the global initialization point.
 * Its primary purpose is to bootstrap the battery monitoring logic as soon as the app process is created.
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
        
        // Dynamically register the receiver. Android doesn't allow ACTION_BATTERY_CHANGED
        // to be registered in the Manifest, so it MUST be done dynamically.
        // Doing this here ensures it runs as long as our app process is alive.
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        
        // Setup WorkManager safety net
        // This scheduling ensures that a periodic check occurs every 30 minutes, 
        // acting as a fallback should the dynamic receiver somehow miss states/fall asleep.
        val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BatterySafetyNet",
            ExistingPeriodicWorkPolicy.KEEP, // Don't replace an existing schedule if one is already ticking
            workRequest
        )
    }
}
