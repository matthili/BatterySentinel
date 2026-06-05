package at.mafue.batterysentinel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import at.mafue.batterysentinel.ui.MainScreen
import at.mafue.batterysentinel.ui.MainViewModel
import at.mafue.batterysentinel.ui.PermissionCheckOverlay
import at.mafue.batterysentinel.ui.SettingsScreen
import at.mafue.batterysentinel.ui.SettingsViewModel
import at.mafue.batterysentinel.ui.LogScreen
import at.mafue.batterysentinel.ui.theme.BatterySentinelTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * The main entry point of the application UI. Provides the Jetpack Compose surface,
 * navigation between Main and Settings screens, Google Sign-In via Credential Manager,
 * and permission management.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    // Lazily instantiate ViewModels scoped to this Activity's lifecycle
    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    // Setup permission launcher using the modern ActivityResultContracts API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Permission result handled – the PermissionCheckOverlay will
        // re-evaluate on next app launch
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask the OS to exempt us from battery optimizations so background checks run reliably
        requestIgnoreBatteryOptimizations()
        // Ensure we can actually send the low-battery distress signal!
        requestNotificationPermission()

        setContent {
            BatterySentinelTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()

                // Permission check state - show overlay on app start if permissions are missing
                var showPermissionCheck by remember { mutableStateOf(true) }

                NavHost(navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            onPowerSaveClick = { openPowerSaveSettings() },
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        // Refresh sign-in state when navigating to settings
                        LaunchedEffect(Unit) {
                            settingsViewModel.refreshSignInState()
                        }
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                            onSignInClick = {
                                coroutineScope.launch {
                                    startGoogleSignIn()
                                }
                            },
                            onViewLogClick = { navController.navigate("log") }
                        )
                    }
                    composable("log") {
                        LogScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Permission-check overlay on first launch
                if (showPermissionCheck) {
                    PermissionCheckOverlay(
                        onDismiss = { showPermissionCheck = false }
                    )
                }
            }
        }
    }

    /**
     * Starts Google Sign-In using the modern Credential Manager API.
     * This replaces the deprecated GoogleSignIn/GoogleSignInClient approach.
     */
    private suspend fun startGoogleSignIn() {
        try {
            val credentialManager = CredentialManager.create(this)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                context = this,
                request = request
            )

            // Extract the Google ID token and authenticate with Firebase
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnSuccessListener {
                    Log.d(TAG, "Firebase sign-in successful")
                    settingsViewModel.onSignInSuccess()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Firebase sign-in failed", e)
                    settingsViewModel.onSignInFailed(e.localizedMessage ?: "Sign-in failed")
                }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager sign-in failed", e)
            settingsViewModel.onSignInFailed("Sign-in cancelled or failed")
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected sign-in error", e)
            settingsViewModel.onSignInFailed(e.localizedMessage ?: "Sign-in failed")
        }
    }

    /**
     * Checks if notification permission is granted (required in Android 13/Tiramisu).
     * If not, it requests it from the user.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Prompts the user to ignore battery optimizations for this app, ensuring our background
     * receiver isn't entirely suspended by Android Doze mode.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    /**
     * Launches the system's battery saver settings screen, so the user can easily
     * enable the battery saver manually after getting an alert.
     */
    private fun openPowerSaveSettings() {
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        startActivity(intent)
    }
}
