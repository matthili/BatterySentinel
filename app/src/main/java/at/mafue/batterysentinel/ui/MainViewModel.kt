package at.mafue.batterysentinel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.mafue.batterysentinel.data.BatteryAlarm
import at.mafue.batterysentinel.data.BatteryPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the UI state of the Main Screen and handling
 * interactions (adding, updating, removing alarms). It survives configuration changes
 * like screen rotations.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = BatteryPreferences(application)

    init {
        // Automatically populate default alarms on the first run of the app.
        viewModelScope.launch {
            prefs.initializeDefaultsIfNeeded()
        }
    }
    
    /**
     * Exposes the continuous stream of alarms as a StateFlow, so Jetpack Compose can 
     * observe it and automatically trigger recomposition when data changes.
     */
    val alarmsFlow: StateFlow<List<BatteryAlarm>> = prefs.alarmsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep flow active shortly after UI disconnects to handle brief backgrounding
            initialValue = emptyList()
        )
        
    /**
     * Updates an existing alarm, or adds it if it doesn't exist yet, then saves it to DataStore.
     */
    fun updateAlarm(alarm: BatteryAlarm) {
        val currentList = alarmsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            currentList[index] = alarm // Replace the existing entry
        } else {
            currentList.add(alarm) // Add new if not found
        }
        
        // Launch in background scope to avoid blocking the main thread
        viewModelScope.launch {
            prefs.saveAlarms(currentList)
        }
    }
    
    /**
     * Removes an alarm by its unique ID and updates the persistent storage.
     */
    fun removeAlarm(id: String) {
        val currentList = alarmsFlow.value.filter { it.id != id }
        viewModelScope.launch {
            prefs.saveAlarms(currentList)
        }
    }
    
    /**
     * Creates a new alarm from raw details, assigns a timestamp-based unique ID, and saves it.
     */
    fun addAlarm(thresholdPercent: Int, message: String) {
        val id = System.currentTimeMillis().toString()
        val alarm = BatteryAlarm(id, thresholdPercent, message, true)
        val currentList = alarmsFlow.value.toMutableList()
        currentList.add(alarm)
        viewModelScope.launch {
            prefs.saveAlarms(currentList)
        }
    }
}
