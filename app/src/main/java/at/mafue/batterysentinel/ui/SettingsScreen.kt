package at.mafue.batterysentinel.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mafue.batterysentinel.BuildConfig
import at.mafue.batterysentinel.R
import at.mafue.batterysentinel.data.dataStore
import at.mafue.batterysentinel.firebase.GmsStatus
import at.mafue.batterysentinel.util.GitHubUpdater
import kotlinx.coroutines.launch
import java.io.File

/**
 * Settings screen for BatterySentinel.
 * 
 * Organized in sections:
 * 1. This Device (device name)
 * 2. Device Group (local-only toggle, multi-device toggles + Google Sign-In)
 * 3. Language (per-app language selection)
 * 4. About (version, license)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
    onViewLogClick: () -> Unit
) {
    val deviceName by viewModel.deviceNameFlow.collectAsState()
    val localOnly by viewModel.localOnlyFlow.collectAsState()
    val sendToOthers by viewModel.sendToOthersFlow.collectAsState()
    val receiveFromOthers by viewModel.receiveFromOthersFlow.collectAsState()
    val isSignedIn by viewModel.isSignedIn.collectAsState()
    val gmsStatus by viewModel.gmsStatus.collectAsState()
    val signInError by viewModel.signInError.collectAsState()
    val context = LocalContext.current

    var editingDeviceName by remember { mutableStateOf(deviceName) }

    // Sync the editing field when the actual device name changes
    LaunchedEffect(deviceName) {
        editingDeviceName = deviceName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Section 1: This Device ---
            SectionHeader(stringResource(R.string.settings_device_section))

            OutlinedTextField(
                value = editingDeviceName,
                onValueChange = { editingDeviceName = it },
                label = { Text(stringResource(R.string.settings_device_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Save button for device name (only shows when changed)
            if (editingDeviceName != deviceName) {
                Button(
                    onClick = { viewModel.updateDeviceName(editingDeviceName) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            HorizontalDivider()

            // --- Section 2: Device Group ---
            SectionHeader(stringResource(R.string.settings_multi_device_section))

            // Local-only toggle (on by default)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_local_only),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = localOnly,
                    onCheckedChange = { viewModel.toggleLocalOnly(it) }
                )
            }

            // Only show multi-device options when local-only is OFF
            if (!localOnly) {
                // GMS Warning
                if (gmsStatus != GmsStatus.AVAILABLE) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.gms_not_available),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.gms_not_available_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Sign-In state
                if (gmsStatus == GmsStatus.AVAILABLE) {
                    if (isSignedIn) {
                        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        val email = user?.email ?: ""
                        Text(
                            text = stringResource(R.string.settings_signed_in_as, email),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.signOut() }) {
                            Text(stringResource(R.string.settings_sign_out))
                        }
                    } else {
                        Button(onClick = onSignInClick) {
                            Text(stringResource(R.string.settings_sign_in))
                        }
                        signInError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Multi-device toggles (disabled if GMS not available or not signed in)
                val togglesEnabled = gmsStatus == GmsStatus.AVAILABLE && isSignedIn

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_send_to_others),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = sendToOthers,
                        onCheckedChange = { viewModel.toggleSendToOthers(it) },
                        enabled = togglesEnabled
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_receive_from_others),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = receiveFromOthers,
                        onCheckedChange = { viewModel.toggleReceiveFromOthers(it) },
                        enabled = togglesEnabled
                    )
                }

                // Info text
                Text(
                    text = stringResource(R.string.settings_multi_device_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // --- Section 3: Language ---
            SectionHeader(stringResource(R.string.settings_language_section))

            LanguageSelector()

            HorizontalDivider()

            // --- Section 4: About ---
            SectionHeader(stringResource(R.string.settings_about_section))

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.settings_license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --- Update Checker ---
            UpdateSection(context)

            // Worker diagnostics
            WorkerDiagnostics()

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onViewLogClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.log_view_button))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Language selector using AndroidX Per-App Language API.
 * Displays a dropdown menu with all supported languages.
 */
@Composable
fun LanguageSelector() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    // Get the current app locale
    val currentLocale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocale.isEmpty) "system" else currentLocale.toLanguageTags()

    // Available languages (tag -> display name resource ID)
    val languages = listOf(
        "system" to R.string.settings_language_system,
        "en" to R.string.lang_en,
        "de" to R.string.lang_de,
        "es" to R.string.lang_es,
        "fr" to R.string.lang_fr,
        "it" to R.string.lang_it,
        "nl" to R.string.lang_nl,
        "pl" to R.string.lang_pl,
        "pt" to R.string.lang_pt,
        "tr" to R.string.lang_tr
    )

    val currentDisplayName = languages.find { it.first == currentTag }?.second
        ?: R.string.settings_language_system

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(currentDisplayName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { (tag, nameRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(nameRes)) },
                    onClick = {
                        expanded = false
                        if (tag == "system") {
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                            )
                        } else {
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Diagnostic display for the background worker.
 * Shows when the worker last ran and how many times total.
 * Helps diagnose WorkManager issues.
 */
@Composable
fun WorkerDiagnostics() {
    val context = LocalContext.current
    val prefs by context.dataStore.data.collectAsState(
        initial = androidx.datastore.preferences.core.emptyPreferences()
    )
    
    val workerLastRunMs = prefs[at.mafue.batterysentinel.worker.BatteryWorker.WORKER_LAST_RUN_KEY] ?: 0L
    val alarmLastRunMs = prefs[at.mafue.batterysentinel.worker.BatteryWorker.ALARM_LAST_RUN_KEY] ?: 0L
    val workerRunCount = prefs[at.mafue.batterysentinel.worker.BatteryWorker.RUN_COUNT_KEY] ?: 0L
    val alarmRunCount = prefs[at.mafue.batterysentinel.worker.BatteryWorker.ALARM_RUN_COUNT_KEY] ?: 0L
    
    val lastRunMs = maxOf(workerLastRunMs, alarmLastRunMs)
    
    val lastRunText = if (lastRunMs > 0) {
        val ago = (System.currentTimeMillis() - lastRunMs) / 1000 / 60
        if (ago < 1) stringResource(R.string.settings_worker_just_now) 
        else stringResource(R.string.settings_worker_mins_ago, ago)
    } else {
        stringResource(R.string.settings_worker_never)
    }
    
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_worker_runs, lastRunText, workerRunCount, alarmRunCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
}

/**
 * Reusable section header for settings groups.
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Self-contained update checker UI.
 *
 * States: Idle → Checking → UpdateAvailable / UpToDate / Error → Downloading → ReadyToInstall.
 * Handles the "Install from Unknown Sources" permission flow inline.
 */
@Composable
fun UpdateSection(context: android.content.Context) {
    val coroutineScope = rememberCoroutineScope()

    // UI state
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadProgress by remember { mutableStateOf(0f) }

    when (val state = updateState) {
        is UpdateUiState.Idle -> {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    updateState = UpdateUiState.Checking
                    coroutineScope.launch {
                        val result = GitHubUpdater.checkForUpdate()
                        updateState = when (result) {
                            is GitHubUpdater.UpdateResult.UpToDate -> UpdateUiState.UpToDate
                            is GitHubUpdater.UpdateResult.UpdateAvailable ->
                                UpdateUiState.Available(result.newVersion, result.downloadUrl)
                            is GitHubUpdater.UpdateResult.Error ->
                                UpdateUiState.Error(result.message)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_check_button))
            }
        }

        is UpdateUiState.Checking -> {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.update_checking))
            }
        }

        is UpdateUiState.UpToDate -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { updateState = UpdateUiState.Idle }) {
                Text(stringResource(R.string.update_check_button))
            }
        }

        is UpdateUiState.Available -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_available, state.version),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Check if permission to install packages is granted
            val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }

            if (!canInstall) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.update_permission_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.update_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.update_permission_button))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    updateState = UpdateUiState.Downloading
                    downloadProgress = 0f
                    coroutineScope.launch {
                        try {
                            val file = GitHubUpdater.downloadApk(
                                context,
                                state.downloadUrl
                            ) { progress ->
                                if (progress >= 0f) downloadProgress = progress
                            }
                            updateState = UpdateUiState.ReadyToInstall(file)
                        } catch (e: Exception) {
                            updateState = UpdateUiState.Error(
                                e.localizedMessage ?: e.toString()
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_download))
            }
        }

        is UpdateUiState.Downloading -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_downloading),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    GitHubUpdater.installApk(context, state.file)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_install))
            }
        }

        is UpdateUiState.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.update_error, state.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { updateState = UpdateUiState.Idle }) {
                Text(stringResource(R.string.update_check_button))
            }
        }
    }
}

/**
 * Represents the possible states of the update UI.
 */
private sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Available(val version: String, val downloadUrl: String) : UpdateUiState()
    data object Downloading : UpdateUiState()
    data class ReadyToInstall(val file: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
