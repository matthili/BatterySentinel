package at.mafue.batterysentinel.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import at.mafue.batterysentinel.R

/**
 * Utility object for streamlining the creation and dispatch of notifications.
 * Supports both local battery alerts and remote alerts from other devices.
 */
object NotificationHelper {
    private const val LOCAL_CHANNEL_ID = "battery_alerts_high"
    private const val REMOTE_CHANNEL_ID = "battery_alerts_remote"
    
    /**
     * Creates and displays a high-priority notification for a local battery alarm.
     * Also handles the creation of the required Notification Channel on Android 8.0+.
     * 
     * @param context Application context
     * @param title Title of the notification (e.g. Battery percentage)
     * @param message Body text of the notification
     */
    fun sendNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Starting with Android Oreo (API 26), notifications require channels
        val channel = NotificationChannel(
            LOCAL_CHANNEL_ID,
            "Battery Alerts", // User-visible name in system settings
            NotificationManager.IMPORTANCE_HIGH // ensures it peeks on screen or alerts wristbound wearables
        ).apply {
            description = "High priority battery alerts"
        }
        notificationManager.createNotificationChannel(channel)
        
        // Build the notification structure
        val notification = NotificationCompat.Builder(context, LOCAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For broad compatibility below API 26
            .setAutoCancel(true) // Dismiss the notification when the user taps it
            .build()
            
        // Use the current time to generate a unique ID so multiple notifications don't overwrite each other
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Creates and displays a notification for a battery alarm received from another device.
     * Uses a separate notification channel so the user can independently control
     * remote alert behavior in Android system settings.
     *
     * @param context Application context
     * @param deviceName Name of the remote device (e.g., "Tablet")
     * @param message The alarm message from the remote device
     * @param threshold The battery threshold that was triggered
     */
    fun sendRemoteNotification(context: Context, deviceName: String, message: String, threshold: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            REMOTE_CHANNEL_ID,
            context.getString(R.string.remote_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Battery alerts from other devices in your device group"
        }
        notificationManager.createNotificationChannel(channel)

        val title = context.getString(R.string.remote_notification_title, deviceName, threshold)

        val notification = NotificationCompat.Builder(context, REMOTE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
