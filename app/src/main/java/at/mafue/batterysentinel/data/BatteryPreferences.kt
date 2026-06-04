package at.mafue.batterysentinel.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Singleton DataStore instance for the entire application, preventing multiple active instances.
val Context.dataStore by preferencesDataStore(name = "battery_prefs")

/**
 * Data class representing a single battery alarm.
 * @param id Unique identifier for the alarm.
 * @param thresholdPercent The battery percentage below which the alarm will trigger.
 * @param message The message to display when the alarm is triggered.
 * @param isEnabled Whether the alarm is actively monitored.
 */
data class BatteryAlarm(
    val id: String,
    val thresholdPercent: Int,
    val message: String,
    val isEnabled: Boolean
)

/**
 * Helper class for managing the parsing, saving, and observing of battery alarms
 * and device settings using AndroidX DataStore for persistent storage.
 */
class BatteryPreferences(private val context: Context) {
    companion object {
        // Alarm keys
        private val ALARMS_KEY = stringPreferencesKey("alarms_json")
        private val FIRST_RUN_KEY = booleanPreferencesKey("is_first_run")

        // Multi-device settings keys
        private val DEVICE_NAME_KEY = stringPreferencesKey("device_name")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val SEND_TO_OTHERS_KEY = booleanPreferencesKey("send_to_other_devices")
        private val RECEIVE_FROM_OTHERS_KEY = booleanPreferencesKey("receive_from_other_devices")
        private val LOCAL_ONLY_KEY = booleanPreferencesKey("local_only_mode")
    }

    // ---- Alarm Flows (unchanged from v1.0) ----

    /**
     * A continuous Flow of the currently saved alarms. It automatically parses the JSON
     * string stored in DataStore into a list of [BatteryAlarm] objects.
     */
    val alarmsFlow: Flow<List<BatteryAlarm>> = context.dataStore.data.map { prefs ->
        // Default to empty array if no alarms are set yet
        val jsonString = prefs[ALARMS_KEY] ?: "[]"
        parseAlarms(jsonString)
    }

    // ---- Multi-Device Settings Flows ----

    /**
     * The user-editable device name. Defaults to the device's model name.
     */
    val deviceNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_NAME_KEY] ?: Build.MODEL
    }

    /**
     * Unique device identifier, persisted across app restarts.
     * Generated once on first access.
     */
    val deviceIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_ID_KEY] ?: ""
    }

    /**
     * Whether this device should send battery warnings to other devices.
     */
    val sendToOthersFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SEND_TO_OTHERS_KEY] ?: false
    }

    /**
     * Whether this device should display battery warnings from other devices.
     */
    val receiveFromOthersFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[RECEIVE_FROM_OTHERS_KEY] ?: false
    }

    /**
     * Whether this device operates in local-only mode (no multi-device features).
     * Default is true – multi-device is opt-in.
     */
    val localOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOCAL_ONLY_KEY] ?: true
    }

    // ---- Alarm Save/Load ----

    /**
     * Serializes a list of [BatteryAlarm] objects into a JSON string and saves it to DataStore.
     * @param alarms The list of alarms to save.
     */
    suspend fun saveAlarms(alarms: List<BatteryAlarm>) {
        val jsonArray = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject()
            obj.put("id", alarm.id)
            obj.put("thresholdPercent", alarm.thresholdPercent)
            obj.put("message", alarm.message)
            obj.put("isEnabled", alarm.isEnabled)
            jsonArray.put(obj)
        }
        // Save the JSON representation atomically
        context.dataStore.edit { prefs ->
            prefs[ALARMS_KEY] = jsonArray.toString()
        }
    }

    /**
     * Checks if the app is being run for the first time. If so, it populates the DataStore
     * with common default alarms (e.g., 40% and 25%) to help the user get started quickly.
     * Also generates a unique device ID on first run.
     */
    suspend fun initializeDefaultsIfNeeded() {
        context.dataStore.edit { prefs ->
            val isFirstRun = prefs[FIRST_RUN_KEY] ?: true
            if (isFirstRun) {
                // Mark that we've initialized the app
                prefs[FIRST_RUN_KEY] = false

                // Generate a unique device ID
                if (prefs[DEVICE_ID_KEY] == null) {
                    prefs[DEVICE_ID_KEY] = UUID.randomUUID().toString()
                }
                
                // Set up recommended default alarms
                val defaultAlarms = listOf(
                    BatteryAlarm(
                        id = System.currentTimeMillis().toString() + "_1",
                        thresholdPercent = 40,
                        message = context.getString(at.mafue.batterysentinel.R.string.default_msg_40),
                        isEnabled = true
                    ),
                    BatteryAlarm(
                        id = System.currentTimeMillis().toString() + "_2",
                        thresholdPercent = 25,
                        message = context.getString(at.mafue.batterysentinel.R.string.default_msg_25),
                        isEnabled = true
                    )
                )
                
                // Quickly serialize defaults
                val jsonArray = JSONArray()
                for (alarm in defaultAlarms) {
                    val obj = JSONObject()
                    obj.put("id", alarm.id)
                    obj.put("thresholdPercent", alarm.thresholdPercent)
                    obj.put("message", alarm.message)
                    obj.put("isEnabled", alarm.isEnabled)
                    jsonArray.put(obj)
                }
                prefs[ALARMS_KEY] = jsonArray.toString()
            }
        }
    }

    // ---- Multi-Device Settings Save ----

    suspend fun setDeviceName(name: String) {
        context.dataStore.edit { prefs -> prefs[DEVICE_NAME_KEY] = name }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { prefs -> prefs[DEVICE_ID_KEY] = id }
    }

    suspend fun setSendToOthers(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SEND_TO_OTHERS_KEY] = enabled }
    }

    suspend fun setReceiveFromOthers(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[RECEIVE_FROM_OTHERS_KEY] = enabled }
    }

    suspend fun setLocalOnly(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[LOCAL_ONLY_KEY] = enabled }
    }

    // ---- Private Helpers ----

    /**
     * Manually parses a JSON array string into a list of [BatteryAlarm]s.
     */
    private fun parseAlarms(jsonString: String): List<BatteryAlarm> {
        val list = mutableListOf<BatteryAlarm>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BatteryAlarm(
                        id = obj.getString("id"),
                        thresholdPercent = obj.getInt("thresholdPercent"),
                        message = obj.getString("message"),
                        isEnabled = obj.getBoolean("isEnabled")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
