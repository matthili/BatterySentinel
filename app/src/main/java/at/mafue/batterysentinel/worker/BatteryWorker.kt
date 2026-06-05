package at.mafue.batterysentinel.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.mafue.batterysentinel.data.dataStore
import at.mafue.batterysentinel.receiver.AlarmScheduler
import at.mafue.batterysentinel.receiver.BatteryChecker
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import at.mafue.batterysentinel.util.EventLogger
import at.mafue.batterysentinel.R
import kotlinx.coroutines.flow.first

/**
 * A background worker managed by WorkManager. 
 * Periodically checks the battery level as a fallback when the dynamic
 * BroadcastReceiver is not alive.
 *
 * Adaptive Doze detection: if the worker detects that its previous run
 * was delayed significantly (> 45 minutes for a 30-minute interval),
 * it activates AlarmManager as a more reliable backup that fires during Doze.
 * If the worker runs on time again, AlarmManager is deactivated (saves battery).
 *
 * To avoid false positives after a device reboot (where the gap is caused
 * by the device being off, not by Doze), the detection only triggers when
 * the device has been running longer than the threshold.
 */
class BatteryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BatteryWorker"
        val LAST_RUN_KEY = longPreferencesKey("worker_last_run_ms")
        val RUN_COUNT_KEY = longPreferencesKey("worker_run_count")
        
        // AlarmManager specific counters
        val ALARM_LAST_RUN_KEY = longPreferencesKey("alarm_last_run_ms")
        val ALARM_RUN_COUNT_KEY = longPreferencesKey("alarm_run_count")
        
        // If the gap between runs exceeds this, Doze is delaying us
        private const val DOZE_THRESHOLD_MS = 45 * 60 * 1000L  // 45 minutes
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== BatteryWorker STARTING ===")
        
        return try {
            val now = System.currentTimeMillis()
            
            // Read last run time and check for Doze delay
            val currentData = context.dataStore.data.first()
            val lastRunMs = currentData[LAST_RUN_KEY] ?: 0L
            val runCount = currentData[RUN_COUNT_KEY] ?: 0L
            
            if (lastRunMs > 0) {
                val gap = now - lastRunMs
                val uptimeMs = SystemClock.elapsedRealtime()
                Log.d(TAG, "Gap since last run: ${gap / 60000} min, uptime: ${uptimeMs / 60000} min")
                
                if (gap > DOZE_THRESHOLD_MS && uptimeMs > DOZE_THRESHOLD_MS) {
                    // Device has been running long enough AND the gap is too large → Doze
                    Log.d(TAG, "Doze detected. Activating AlarmManager backup.")
                    EventLogger.logEvent(
                        context,
                        context.getString(R.string.log_action_doze_detected),
                        context.getString(R.string.log_value_doze_detected)
                    )
                    AlarmScheduler.schedule(context)
                } else {
                    // Either running on time, or device just booted (gap from being off)
                    Log.d(TAG, "Running on schedule. AlarmManager not needed.")
                    // Only log deactivation if it was previously active (optional optimization, but we can just log if we cancel)
                    // We don't have an easy way to check if it's active here without querying AlarmManager, 
                    // so we just cancel. We'll only log if we know for sure, but for now we just cancel.
                    // Actually, if last run was very late, we might have activated it. 
                    if (gap <= DOZE_THRESHOLD_MS && gap > 0) {
                        // Just running on schedule
                    }
                    AlarmScheduler.cancel(context)
                }
            }
            
            // Record that the worker ran
            EventLogger.logEvent(
                context,
                context.getString(R.string.log_action_cyclic_check),
                context.getString(R.string.log_value_via_workmanager)
            )
            context.dataStore.edit { prefs ->
                prefs[LAST_RUN_KEY] = now
                prefs[RUN_COUNT_KEY] = runCount + 1
            }

            // Check battery
            val batteryStatus: Intent? = context.registerReceiver(
                null, 
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            
            if (batteryStatus != null) {
                BatteryChecker.checkAlarmsSuspend(context, batteryStatus)
            } else {
                Log.w(TAG, "Battery sticky broadcast returned null")
            }
            
            Log.d(TAG, "=== BatteryWorker COMPLETED (run #${runCount + 1}) ===")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "=== BatteryWorker FAILED ===", e)
            Result.success()
        }
    }
}
