package at.mafue.batterysentinel.firebase

import android.content.Context
import android.util.Log
import at.mafue.batterysentinel.BuildConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Initializes Firebase App Check with the appropriate provider:
 * - Release builds: Play Integrity (requires Play Store distribution)
 * - Debug builds: Debug provider (generates a debug token for testing)
 *
 * Must be called BEFORE any other Firebase SDK calls in BatterySentinelApp.onCreate().
 */
object AppCheckInitializer {
    private const val TAG = "AppCheckInitializer"

    fun initialize(context: Context) {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            // Debug builds: use the debug provider.
            // On first run, a debug token is printed to Logcat.
            // Register it in Firebase Console → App Check → Manage debug tokens.
            try {
                val debugFactory = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    .getMethod("getInstance")
                    .invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                firebaseAppCheck.installAppCheckProviderFactory(debugFactory)
                Log.d(TAG, "App Check initialized with DEBUG provider")
            } catch (e: Exception) {
                Log.w(TAG, "Debug App Check provider not available, falling back to Play Integrity", e)
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } else {
            // Release builds: use Play Integrity
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "App Check initialized with Play Integrity provider")
        }
    }
}
