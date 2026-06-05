package at.mafue.batterysentinel.firebase

import android.util.Log
import at.mafue.batterysentinel.data.BatteryPreferences
import at.mafue.batterysentinel.receiver.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import at.mafue.batterysentinel.util.EventLogger
import at.mafue.batterysentinel.R

/**
 * Firebase Cloud Messaging service that handles incoming data messages
 * from other devices in the user's device group.
 *
 * When a battery alarm is triggered on another device, this service
 * receives the FCM data message and displays a local notification,
 * which is then automatically forwarded to any connected wearable.
 */
class FcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FcmService"
    }

    /**
     * Called when a new FCM message is received.
     * Handles battery alert messages from other devices.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        val data = message.data
        val type = data["type"]
        
        if (type == "battery_alert") {
            val deviceName = data["deviceName"] ?: return
            val alertMessage = data["message"] ?: return
            val threshold = data["threshold"]?.toIntOrNull() ?: return
            
            Log.d(TAG, "Received battery alert from $deviceName: $threshold%")
            
            // Check if "receive from other devices" is enabled
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = BatteryPreferences(applicationContext)
                    val receiveEnabled = prefs.receiveFromOthersFlow.first()
                    
                    if (receiveEnabled) {
                        EventLogger.logEvent(
                            applicationContext,
                            applicationContext.getString(R.string.log_action_cloud_received),
                            deviceName
                        )
                        EventLogger.logEvent(
                            applicationContext,
                            "${applicationContext.getString(R.string.log_action_cloud_warning_issued)} $deviceName",
                            alertMessage
                        )
                        NotificationHelper.sendRemoteNotification(
                            applicationContext,
                            deviceName,
                            alertMessage,
                            threshold
                        )
                    } else {
                        Log.d(TAG, "Receive from other devices is disabled, ignoring alert")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error handling remote battery alert", e)
                }
            }
        }
    }

    /**
     * Called when the FCM registration token is refreshed.
     * Updates the token in Firestore if multi-device is enabled.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        
        // Re-register the device with the new token
        MultiDeviceManager.refreshTokenIfNeeded(applicationContext)
    }
}
