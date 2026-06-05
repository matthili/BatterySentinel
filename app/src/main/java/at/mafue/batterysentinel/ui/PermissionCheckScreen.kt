package at.mafue.batterysentinel.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import at.mafue.batterysentinel.R

/**
 * Data class representing the status of a required permission.
 */
data class PermissionStatus(
    val name: String,
    val description: String,
    val isGranted: Boolean
)

/**
 * Checks all required permissions and returns their current status.
 */
fun checkAllPermissions(context: Context): List<PermissionStatus> {
    val permissions = mutableListOf<PermissionStatus>()
    
    // 1. POST_NOTIFICATIONS (required for Android 13+)
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Permission not needed before Android 13
    }
    
    permissions.add(
        PermissionStatus(
            name = context.getString(R.string.permission_notifications),
            description = context.getString(R.string.permission_notifications_desc),
            isGranted = notificationsGranted
        )
    )
    
    // 2. Battery Optimization Exception
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryOptExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    
    permissions.add(
        PermissionStatus(
            name = context.getString(R.string.permission_battery_optimization),
            description = context.getString(R.string.permission_battery_optimization_desc),
            isGranted = batteryOptExempt
        )
    )
    
    return permissions
}

/**
 * Permission check overlay displayed as an AlertDialog when permissions are missing.
 * Shows at app start when any required permission is not granted.
 * 
 * Lists each missing permission with a layman-friendly explanation and provides
 * a button to open the app's permission settings directly.
 */
@Composable
fun PermissionCheckOverlay(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Re-check permissions every time the Activity resumes
    // (e.g. after returning from permission dialogs or app settings)
    var permissions by remember { mutableStateOf(checkAllPermissions(context)) }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        permissions = checkAllPermissions(context)
        onPauseOrDispose { }
    }

    val missingPermissions = permissions.filter { !it.isGranted }
    
    // Don't show if all permissions are granted
    if (missingPermissions.isEmpty()) {
        onDismiss()
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.permissions_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.permissions_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                permissions.forEach { permission ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (permission.isGranted) 
                                Icons.Default.CheckCircle 
                            else 
                                Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (permission.isGranted) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = permission.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (permission.isGranted) 
                                    MaterialTheme.colorScheme.onSurface 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = permission.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Open app-specific settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }
            ) {
                Text(stringResource(R.string.permissions_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permissions_not_now))
            }
        }
    )
}
