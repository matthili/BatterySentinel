package at.mafue.batterysentinel.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import at.mafue.batterysentinel.data.BatteryPreferences
import at.mafue.batterysentinel.data.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Central manager for all multi-device functionality.
 *
 * Handles:
 * - FCM token registration and refresh in Firestore
 * - Device registration/unregistration
 * - Calling the Cloud Function to notify other devices
 * - Google Sign-In state management
 *
 * All operations are designed to be fire-and-forget with proper error handling,
 * so network failures never impact local battery monitoring.
 */
object MultiDeviceManager {
    private const val TAG = "MultiDeviceManager"
    private const val USERS_COLLECTION = "users"
    private const val DEVICES_COLLECTION = "devices"

    /**
     * Registers or updates this device in Firestore with the current FCM token.
     * Called when multi-device is first enabled or when the FCM token changes.
     */
    suspend fun registerDevice(context: Context) {
        try {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: return
            
            val prefs = BatteryPreferences(context)
            val deviceId = prefs.deviceIdFlow.first()
            val deviceName = prefs.deviceNameFlow.first()
            
            val token = FirebaseMessaging.getInstance().token.await()
            
            val deviceData = hashMapOf(
                "name" to deviceName,
                "fcmToken" to token,
                "lastSeen" to com.google.firebase.Timestamp.now(),
                "platform" to "android",
                "model" to Build.MODEL,
                "appVersion" to at.mafue.batterysentinel.BuildConfig.VERSION_NAME
            )
            
            FirebaseFirestore.getInstance()
                .collection(USERS_COLLECTION).document(uid)
                .collection(DEVICES_COLLECTION).document(deviceId)
                .set(deviceData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Device registered: $deviceName ($deviceId)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register device", e)
        }
    }

    /**
     * Removes this device from Firestore when multi-device is disabled.
     */
    suspend fun unregisterDevice(context: Context) {
        try {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: return
            
            val prefs = BatteryPreferences(context)
            val deviceId = prefs.deviceIdFlow.first()
            
            FirebaseFirestore.getInstance()
                .collection(USERS_COLLECTION).document(uid)
                .collection(DEVICES_COLLECTION).document(deviceId)
                .delete()
                .await()
            
            Log.d(TAG, "Device unregistered: $deviceId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister device", e)
        }
    }

    /**
     * Calls the Cloud Function to notify all other devices of a battery alarm.
     * This is called from BatteryChecker when an alarm threshold is reached
     * and "send to other devices" is enabled.
     *
     * Designed to never throw – all errors are caught and logged.
     */
    suspend fun notifyOtherDevices(context: Context, deviceName: String, message: String, threshold: Int) {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                Log.d(TAG, "Not signed in, skipping remote notification")
                return
            }
            
            val prefs = BatteryPreferences(context)
            val deviceId = prefs.deviceIdFlow.first()
            
            val data = hashMapOf(
                "deviceId" to deviceId,
                "deviceName" to deviceName,
                "message" to message,
                "threshold" to threshold
            )
            
            val result = FirebaseFunctions.getInstance()
                .getHttpsCallable("notifyOtherDevices")
                .call(data)
                .await()
            
            val sent = (result.getData() as? Map<*, *>)?.get("sent") ?: 0
            Log.d(TAG, "Notified other devices: sent=$sent")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to notify other devices", e)
        }
    }

    /**
     * Refreshes the FCM token in Firestore if multi-device is enabled.
     * Called at app startup and after device reboots.
     */
    fun refreshTokenIfNeeded(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = BatteryPreferences(context)
                val sendEnabled = prefs.sendToOthersFlow.first()
                val receiveEnabled = prefs.receiveFromOthersFlow.first()
                
                if (sendEnabled || receiveEnabled) {
                    val auth = FirebaseAuth.getInstance()
                    if (auth.currentUser != null) {
                        registerDevice(context)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh token", e)
            }
        }
    }

    /**
     * Checks if the user is currently signed in with Firebase Auth.
     */
    fun isSignedIn(): Boolean {
        return try {
            FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Signs out the user from Firebase Auth.
     */
    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sign out", e)
        }
    }

    /**
     * Ensures this device has a unique device ID stored in DataStore.
     * Generates one if not present.
     */
    suspend fun ensureDeviceId(context: Context) {
        val prefs = BatteryPreferences(context)
        val currentId = prefs.deviceIdFlow.first()
        if (currentId.isEmpty()) {
            prefs.setDeviceId(UUID.randomUUID().toString())
        }
    }
}
