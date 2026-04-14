package com.example.batterysentinel.receiver

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.batterysentinel.data.BatteryPreferences
import com.example.batterysentinel.data.dataStore
import kotlinx.coroutines.flow.first

/**
 * Singleton object responsible for the core logic of checking the battery level
 * against the user-defined alarms. It determines whether an alarm should be triggered
 * or reset based on the current battery state.
 */
object BatteryChecker {

    /**
     * Suspending function called when a battery state change is detected.
     * Evaluates the new battery level and triggers notifications if thresholds are met.
     * 
     * @param context The application context needed for accessing DataStore and NotificationManager.
     * @param intent The broadcast intent containing the battery state extras.
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
        Log.d("BatteryChecker", "Checking battery: ${batteryPct.toInt()}%")

        val currentLevel = batteryPct.toInt()

        // Load the configured alarms gracefully from DataStore
        val prefs = BatteryPreferences(context)
        val alarms = prefs.alarmsFlow.first()
        
        // Define preferences keys for maintaining the state across app restarts
        val triggeredPrefsKey = stringPreferencesKey("triggered_alarms")
        val lastLevelKey = intPreferencesKey("last_level")
        
        val ds = context.dataStore
        val currentData = ds.data.first()
        
        // Retrieve the last known battery level and the IDs of already triggered alarms
        val lastLevel = currentData[lastLevelKey] ?: 100
        val triggeredSet = currentData[triggeredPrefsKey]?.split(",")?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()

        var stateChanged = false

        // Logic block: When charging, check if we've surpassed an alarm threshold and reset its triggered state
        if (isCharging && currentLevel > lastLevel) {
            val alarmsToReset = alarms.filter { currentLevel > it.thresholdPercent }.map { it.id }
            if (alarmsToReset.any { triggeredSet.contains(it) }) {
                triggeredSet.removeAll(alarmsToReset.toSet())
                stateChanged = true
            }
        // Logic block: When discharging, check if battery dropped to or below an alarm threshold
        } else if (!isCharging && currentLevel <= lastLevel) {
            for (alarm in alarms) {
                // If the alarm is active, threshold is met/exceeded, and it hasn't been triggered yet
                if (alarm.isEnabled && currentLevel <= alarm.thresholdPercent && !triggeredSet.contains(alarm.id)) {
                    // Fire the high-priority notification!
                    NotificationHelper.sendNotification(context, "Battery Sentinel: ${alarm.thresholdPercent}%", alarm.message)
                    // Mark this alarm as triggered so it won't fire repeatedly
                    triggeredSet.add(alarm.id)
                    stateChanged = true
                }
            }
        }

        // Save the updated state to DataStore only if something changed or the battery level moved
        if (stateChanged || lastLevel != currentLevel) {
            ds.edit { p ->
                p[lastLevelKey] = currentLevel
                p[triggeredPrefsKey] = triggeredSet.joinToString(",")
            }
        }
    }
}
