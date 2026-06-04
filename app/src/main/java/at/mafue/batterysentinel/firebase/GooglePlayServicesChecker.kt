package at.mafue.batterysentinel.firebase

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import at.mafue.batterysentinel.R

/**
 * Status of Google Play Services availability on this device.
 */
enum class GmsStatus {
    AVAILABLE,
    NEEDS_UPDATE,
    NOT_AVAILABLE,
    DISABLED
}

/**
 * Utility object for checking the availability of Google Play Services.
 * Required for FCM, Firebase Auth, and the multi-device feature.
 *
 * Devices without GMS (e.g., AOSP, Huawei without GMS) will receive
 * a user-friendly warning and the multi-device toggles will be disabled.
 */
object GooglePlayServicesChecker {

    /**
     * Checks whether Google Play Services is available and up-to-date.
     */
    fun checkAvailability(context: Context): GmsStatus {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        return when (resultCode) {
            ConnectionResult.SUCCESS -> GmsStatus.AVAILABLE
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> GmsStatus.NEEDS_UPDATE
            ConnectionResult.SERVICE_DISABLED -> GmsStatus.DISABLED
            else -> GmsStatus.NOT_AVAILABLE
        }
    }

    /**
     * Returns a user-friendly description of what's missing or needs to be done
     * to enable the multi-device feature.
     */
    fun getMissingComponentsDescription(context: Context): String {
        return when (checkAvailability(context)) {
            GmsStatus.AVAILABLE -> ""
            GmsStatus.NEEDS_UPDATE -> context.getString(R.string.gms_needs_update)
            GmsStatus.NOT_AVAILABLE -> context.getString(R.string.gms_not_available_desc)
            GmsStatus.DISABLED -> context.getString(R.string.gms_not_available_desc)
        }
    }

    /**
     * Attempts to show the Google Play Services error resolution dialog if available.
     */
    fun showResolutionDialog(context: Context): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        return if (availability.isUserResolvableError(resultCode)) {
            // For non-Activity contexts, we can't show the dialog directly.
            // The caller should handle this case.
            true
        } else {
            false
        }
    }
}
