package at.mafue.batterysentinel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.mafue.batterysentinel.data.BatteryPreferences
import at.mafue.batterysentinel.firebase.GooglePlayServicesChecker
import at.mafue.batterysentinel.firebase.GmsStatus
import at.mafue.batterysentinel.firebase.MultiDeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Manages device name, local-only mode, multi-device toggles,
 * Google Sign-In state, and Google Play Services availability.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = BatteryPreferences(application)

    val deviceNameFlow: StateFlow<String> = prefs.deviceNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), android.os.Build.MODEL)

    val localOnlyFlow: StateFlow<Boolean> = prefs.localOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sendToOthersFlow: StateFlow<Boolean> = prefs.sendToOthersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val receiveFromOthersFlow: StateFlow<Boolean> = prefs.receiveFromOthersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSignedIn = MutableStateFlow(MultiDeviceManager.isSignedIn())
    val isSignedIn: StateFlow<Boolean> = _isSignedIn

    private val _gmsStatus = MutableStateFlow(
        GooglePlayServicesChecker.checkAvailability(application)
    )
    val gmsStatus: StateFlow<GmsStatus> = _gmsStatus

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            prefs.setDeviceName(name)
            // Also update in Firestore if multi-device is active
            if (MultiDeviceManager.isSignedIn()) {
                MultiDeviceManager.registerDevice(getApplication())
            }
        }
    }

    /**
     * Toggles local-only mode. When enabled, disables all multi-device features
     * and signs out of Google.
     */
    fun toggleLocalOnly(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setLocalOnly(enabled)
            if (enabled) {
                // Disable multi-device and sign out
                prefs.setSendToOthers(false)
                prefs.setReceiveFromOthers(false)
                if (MultiDeviceManager.isSignedIn()) {
                    MultiDeviceManager.unregisterDevice(getApplication())
                    MultiDeviceManager.signOut()
                    _isSignedIn.value = false
                }
            }
        }
    }

    fun toggleSendToOthers(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSendToOthers(enabled)
            if (enabled && MultiDeviceManager.isSignedIn()) {
                MultiDeviceManager.registerDevice(getApplication())
            } else if (!enabled && !receiveFromOthersFlow.value) {
                // Both toggles off: unregister device
                MultiDeviceManager.unregisterDevice(getApplication())
            }
        }
    }

    fun toggleReceiveFromOthers(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setReceiveFromOthers(enabled)
            if (enabled && MultiDeviceManager.isSignedIn()) {
                MultiDeviceManager.registerDevice(getApplication())
            } else if (!enabled && !sendToOthersFlow.value) {
                // Both toggles off: unregister device
                MultiDeviceManager.unregisterDevice(getApplication())
            }
        }
    }

    /**
     * Called after successful Google Sign-In from the Activity.
     * Registers the device in Firestore.
     */
    fun onSignInSuccess() {
        _isSignedIn.value = true
        _signInError.value = null
        viewModelScope.launch {
            MultiDeviceManager.registerDevice(getApplication())
        }
    }

    fun onSignInFailed(error: String) {
        _signInError.value = error
    }

    fun signOut() {
        viewModelScope.launch {
            MultiDeviceManager.unregisterDevice(getApplication())
            MultiDeviceManager.signOut()
            prefs.setSendToOthers(false)
            prefs.setReceiveFromOthers(false)
            _isSignedIn.value = false
        }
    }

    fun refreshSignInState() {
        _isSignedIn.value = MultiDeviceManager.isSignedIn()
        _gmsStatus.value = GooglePlayServicesChecker.checkAvailability(getApplication())
    }
}
