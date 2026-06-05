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
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * Adaptive Doze detection: if the worker detects that its previous
 * WorkManager run was delayed significantly (> 45 minutes for a 30-minute
 * interval), it activates AlarmManager as a more reliable backup.
 * If the worker runs on time again, AlarmManager is deactivated.
 *
 * Key design decisions:
 * - WORKER_LAST_RUN_KEY tracks only WorkManager runs (not AlarmManager)
 *   so that the gap calculation accurately reflects whether WorkManager
 *   is being delayed by Doze. If AlarmManager wrote to this key too,
 *   the gap would always appear short and Doze would never be detected.
 * - ALARM_ACTIVE_KEY is a boolean flag that tracks whether AlarmManager
 *   mode is currently active. This allows proper logging of transitions
 *   and prevents the worker from cancelling an alarm that was never set.
 * - The AlarmReceiver does NOT reschedule itself. Only the worker
 *   decides whether AlarmManager should remain active, preventing an
 *   unstoppable self-rescheduling chain.
 */
class BatteryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BatteryWorker"
        
        // WorkManager-only timestamp – NOT written by AlarmReceiver
        val WORKER_LAST_RUN_KEY = longPreferencesKey("worker_last_run_ms")
        val RUN_COUNT_KEY = longPreferencesKey("worker_run_count")
        
        // AlarmManager specific counters
        val ALARM_LAST_RUN_KEY = longPreferencesKey("alarm_last_run_ms")
        val ALARM_RUN_COUNT_KEY = longPreferencesKey("alarm_run_count")
        
        // Tracks whether AlarmManager backup mode is currently active
        val ALARM_ACTIVE_KEY = booleanPreferencesKey("alarm_active")
        
        // Legacy key alias kept for DataStore migration compatibility
        @Deprecated("Use WORKER_LAST_RUN_KEY", replaceWith = ReplaceWith("WORKER_LAST_RUN_KEY"))
        val LAST_RUN_KEY = WORKER_LAST_RUN_KEY
        
        // If the gap between WorkManager runs exceeds this, Doze is delaying us
        private const val DOZE_THRESHOLD_MS = 45 * 60 * 1000L  // 45 minutes
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== BatteryWorker STARTING ===")
        
        return try {
            val now = System.currentTimeMillis()
            
            // Read last WorkManager run time
            val currentData = context.dataStore.data.first()
            val lastWorkerRunMs = currentData[WORKER_LAST_RUN_KEY] ?: 0L
            val runCount = currentData[RUN_COUNT_KEY] ?: 0L
            val alarmCurrentlyActive = currentData[ALARM_ACTIVE_KEY] ?: false
            
            if (lastWorkerRunMs > 0) {
                val gap = now - lastWorkerRunMs
                val uptimeMs = SystemClock.elapsedRealtime()
                Log.d(TAG, "Gap since last WorkManager run: ${gap / 60000} min, uptime: ${uptimeMs / 60000} min, alarm active: $alarmCurrentlyActive")
                
                if (gap > DOZE_THRESHOLD_MS && uptimeMs > DOZE_THRESHOLD_MS) {
                    // WorkManager is being delayed by Doze → activate AlarmManager
                    if (!alarmCurrentlyActive) {
                        Log.d(TAG, "Doze detected. Activating AlarmManager backup.")
                        EventLogger.logEvent(
                            context,
                            context.getString(R.string.log_action_doze_detected),
                            context.getString(R.string.log_value_doze_detected)
                        )
                        AlarmScheduler.schedule(context)
                        context.dataStore.edit { prefs ->
                            prefs[ALARM_ACTIVE_KEY] = true
                        }
                    } else {
                        // AlarmManager already active, just reschedule it
                        Log.d(TAG, "Doze still active. Refreshing AlarmManager schedule.")
                        AlarmScheduler.schedule(context)
                    }
                } else if (alarmCurrentlyActive) {
                    // WorkManager is running on schedule again → deactivate AlarmManager
                    Log.d(TAG, "WorkManager on schedule again. Deactivating AlarmManager.")
                    EventLogger.logEvent(
                        context,
                        context.getString(R.string.log_action_doze_ended),
                        context.getString(R.string.log_value_doze_ended)
                    )
                    AlarmScheduler.cancel(context)
                    context.dataStore.edit { prefs ->
                        prefs[ALARM_ACTIVE_KEY] = false
                    }
                }
            }
            
            // Log the cyclic check
            EventLogger.logEvent(
                context,
                context.getString(R.string.log_action_cyclic_check),
                context.getString(R.string.log_value_via_workmanager)
            )
            
            // Record that the WorkManager ran (this timestamp is ONLY for WorkManager)
            context.dataStore.edit { prefs ->
                prefs[WORKER_LAST_RUN_KEY] = now
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
