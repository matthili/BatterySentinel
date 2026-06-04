package at.mafue.batterysentinel.receiver

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import at.mafue.batterysentinel.data.BatteryPreferences
import at.mafue.batterysentinel.data.dataStore
import at.mafue.batterysentinel.firebase.MultiDeviceManager
import kotlinx.coroutines.flow.first

/**
 * Singleton object responsible for the core logic of checking the battery level
 * against the user-defined alarms. It determines whether an alarm should be triggered
 * or reset based on the current battery state.
 *
 * In v2.0, when an alarm is triggered and multi-device sending is enabled,
 * the alarm is also forwarded to the user's other devices via Firebase Cloud Functions.
 */
object BatteryChecker {

    private const val TAG = "BatteryChecker"

    /**
     * Suspending function called when a battery state change is detected.
     * Evaluates the new battery level and triggers notifications if thresholds are met.
     * 
     * IMPORTANT: The triggered state is saved to DataStore IMMEDIATELY after each 
     * local notification, BEFORE any network calls. This prevents duplicate notifications
     * if the process is killed during a network call.
     */
    suspend fun checkAlarmsSuspend(context: Context, intent: Intent) {
        // Extract raw battery level and scale from the intent
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        
        // Determine if the device is currently plugged in and charging or fully charged
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        // Safety check: ensure valid data is received
        if (level == -1 || scale == -1) return
        
        // Calculate the actual battery percentage
        val batteryPct = (level * 100) / scale.toFloat()
        val currentLevel = batteryPct.toInt()
        Log.d(TAG, "Checking battery: ${currentLevel}%, charging=$isCharging")

        // Load the configured alarms gracefully from DataStore
        val prefs = BatteryPreferences(context)
        val alarms = prefs.alarmsFlow.first()
        
        // Load multi-device settings
        val sendToOthersEnabled = prefs.sendToOthersFlow.first()
        val deviceName = prefs.deviceNameFlow.first()
        
        // Define preferences keys for maintaining the state across app restarts
        val triggeredPrefsKey = stringPreferencesKey("triggered_alarms")
        val lastLevelKey = intPreferencesKey("last_level")
        
        val ds = context.dataStore
        val currentData = ds.data.first()
        
        // Retrieve the last known battery level and the IDs of already triggered alarms
        val lastLevel = currentData[lastLevelKey] ?: 100
        val triggeredSet = currentData[triggeredPrefsKey]
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet() ?: mutableSetOf()

        // Logic block: When charging, check if we've surpassed an alarm threshold and reset its triggered state
        if (isCharging && currentLevel > lastLevel) {
            val alarmsToReset = alarms.filter { currentLevel > it.thresholdPercent }.map { it.id }
            if (alarmsToReset.any { triggeredSet.contains(it) }) {
                triggeredSet.removeAll(alarmsToReset.toSet())
                // Save reset state immediately
                ds.edit { p ->
                    p[lastLevelKey] = currentLevel
                    p[triggeredPrefsKey] = triggeredSet.joinToString(",")
                }
            }
        // Logic block: When discharging, check if battery dropped to or below an alarm threshold
        } else if (!isCharging && currentLevel <= lastLevel) {
            for (alarm in alarms) {
                // If the alarm is active, threshold is met/exceeded, and it hasn't been triggered yet
                if (alarm.isEnabled && currentLevel <= alarm.thresholdPercent && !triggeredSet.contains(alarm.id)) {
                    // Fire the high-priority local notification
                    NotificationHelper.sendNotification(
                        context, 
                        "Battery Sentinel: ${alarm.thresholdPercent}%", 
                        alarm.message
                    )
                    
                    // Mark as triggered and SAVE IMMEDIATELY – before any network calls!
                    // This prevents duplicate notifications if the process is killed
                    // during the multi-device network call.
                    triggeredSet.add(alarm.id)
                    ds.edit { p ->
                        p[lastLevelKey] = currentLevel
                        p[triggeredPrefsKey] = triggeredSet.joinToString(",")
                    }
                    
                    // If multi-device is enabled: notify other devices via Cloud Function
                    // This is fire-and-forget – failure must never affect local notifications
                    if (sendToOthersEnabled) {
                        try {
                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                            if (auth.currentUser == null) {
                                NotificationHelper.sendNotification(
                                    context,
                                    "BatterySentinel",
                                    "Multi-Device aktiv, aber nicht bei Google angemeldet. Bitte App öffnen und anmelden."
                                )
                            } else {
                                MultiDeviceManager.notifyOtherDevices(
                                    context, deviceName, alarm.message, alarm.thresholdPercent
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not notify other devices", e)
                            NotificationHelper.sendNotification(
                                context,
                                "BatterySentinel",
                                "Warnung konnte nicht an andere Geräte gesendet werden."
                            )
                        }
                    }
                }
            }
        }

        // Always update the last known battery level
        if (lastLevel != currentLevel) {
            ds.edit { p ->
                p[lastLevelKey] = currentLevel
            }
        }
    }
}
