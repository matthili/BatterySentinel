package at.mafue.batterysentinel.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.mafue.batterysentinel.receiver.BatteryChecker

/**
 * A background worker managed by WorkManager. 
 * This acts as a reliable safety net that periodically captures the internal battery state independently
 * of Android's Broadcast system, which can sometimes rate-limit broadcasts.
 */
class BatteryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * Executes the background work asynchronously.
     */
    override suspend fun doWork(): Result {
        Log.d("BatteryWorker", "Running 30m periodic boundary check")
        
        // We can't request battery state directly on demand via typical APIs, 
        // but we can request a sticky broadcast via registerReceiver with null receiver.
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        // Send this intent through our central validator
        batteryStatus?.let { intent ->
            BatteryChecker.checkAlarmsSuspend(context, intent)
        }
        
        return Result.success()
    }
}
