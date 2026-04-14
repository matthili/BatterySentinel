package com.example.batterysentinel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver designed to wake up the application after a device reboot or an app update.
 * Triggered by the system broadcasts `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED`.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // No explicit logic needed here!
            // When the system calls this BroadcastReceiver, it automatically instantiates the Application class.
            // Our custom BatterySentinelApp executes its onCreate() method, setting up the dynamically
            // registered BatteryReceiver and the WorkManager periodic safety-net automatically.
            Log.d("BootReceiver", "Device booted or app updated. Application class has restored background monitoring.")
        }
    }
}
