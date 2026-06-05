package at.mafue.batterysentinel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import at.mafue.batterysentinel.data.dataStore
import at.mafue.batterysentinel.worker.BatteryWorker
import at.mafue.batterysentinel.util.EventLogger
import at.mafue.batterysentinel.R

/**
 * BroadcastReceiver triggered by AlarmManager.setAndAllowWhileIdle().
 * 
 * This receiver is ONLY active when the BatteryWorker detects that
 * Doze mode is delaying its execution. It fires during Doze and
 * reschedules itself for the next check.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "AlarmManager fired – checking battery during Doze")
        
        // Reschedule next alarm immediately
        AlarmScheduler.schedule(context)
        
        // Check battery in a coroutine (onReceive has ~10 seconds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Record AlarmManager run
                val now = System.currentTimeMillis()
                val currentData = context.dataStore.data.first()
                val runCount = currentData[BatteryWorker.ALARM_RUN_COUNT_KEY] ?: 0L
                
                context.dataStore.edit { prefs ->
                    prefs[BatteryWorker.ALARM_LAST_RUN_KEY] = now
                    prefs[BatteryWorker.ALARM_RUN_COUNT_KEY] = runCount + 1
                }
                
                EventLogger.logEvent(
                    context,
                    context.getString(R.string.log_action_cyclic_check),
                    context.getString(R.string.log_value_via_alarmmanager)
                )

                val batteryStatus: Intent? = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                if (batteryStatus != null) {
                    BatteryChecker.checkAlarmsSuspend(context, batteryStatus)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Battery check failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
