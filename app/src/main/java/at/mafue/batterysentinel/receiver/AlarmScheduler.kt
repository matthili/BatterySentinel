package at.mafue.batterysentinel.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Schedules a repeating AlarmManager alarm that fires even during Doze mode.
 * 
 * This is the most reliable background mechanism for battery monitoring:
 * - Unlike WorkManager, AlarmManager with setAndAllowWhileIdle() fires during Doze
 * - Unlike a dynamic BroadcastReceiver, it survives process death
 * - Each alarm reschedules the next one (self-repeating chain)
 *
 * Uses setAndAllowWhileIdle() instead of setExactAndAllowWhileIdle() to avoid
 * needing the SCHEDULE_EXACT_ALARM permission. The timing is approximate (~20 min)
 * but sufficient for battery monitoring.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val REQUEST_CODE = 9001
    
    // Interval in milliseconds (30 minutes – same as WorkManager)
    private const val INTERVAL_MS = 30 * 60 * 1000L

    /**
     * Schedules the next battery check alarm. 
     * Safe to call repeatedly – it replaces any existing alarm.
     */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager not available")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "at.mafue.batterysentinel.BATTERY_CHECK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule for ~20 minutes from now, even if device is in Doze
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            pendingIntent
        )
        
        Log.d(TAG, "Next battery check alarm scheduled in ${INTERVAL_MS / 60000} minutes")
    }

    /**
     * Cancels any pending battery check alarm.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
