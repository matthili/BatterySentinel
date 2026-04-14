package com.example.batterysentinel.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Utility object for streamlining the creation and dispatch of notifications.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "battery_alerts_high"
    
    /**
     * Creates and displays a high-priority notification to the user.
     * Also handles the creation of the required Notification Channel on Android 8.0+.
     * 
     * @param context Application context
     * @param title Title of the notification (e.g. Battery percentage)
     * @param message Body text of the notification
     */
    fun sendNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Starting with Android Oreo (API 26), notifications require channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Battery Alerts", // User-visible name in system settings
                NotificationManager.IMPORTANCE_HIGH // ensures it peeks on screen or alerts wristbound wearables
            ).apply {
                description = "High priority battery alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Build the notification structure
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For broad compatibility below API 26
            .setAutoCancel(true) // Dismiss the notification when the user taps it
            .build()
            
        // Use the current time to generate a unique ID so multiple notifications don't overwrite each other
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
