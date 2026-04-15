package at.mafue.batterysentinel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A BroadcastReceiver that listens for battery state changes (ACTION_BATTERY_CHANGED).
 * Note: This action can only be registered dynamically (in BatterySentinelApp),
 * not statically in the AndroidManifest.xml.
 */
class BatteryReceiver : BroadcastReceiver() {
    
    /**
     * This method is triggered whenever the system broadcasts a battery change.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            // goAsync allow us to safely handle asynchronous operations inside a BroadcastReceiver
            // without the system killing the receiver prematurely.
            val pendingResult = goAsync()
            
            // Launch a coroutine on the IO dispatcher to do the DataStore checks without blocking the main thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    BatteryChecker.checkAlarmsSuspend(context, intent)
                } finally {
                    // Indicate to the system that the background work is finished
                    pendingResult.finish()
                }
            }
        }
    }
}
