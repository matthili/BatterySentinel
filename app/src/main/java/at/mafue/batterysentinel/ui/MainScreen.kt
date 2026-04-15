package at.mafue.batterysentinel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mafue.batterysentinel.R
import at.mafue.batterysentinel.data.BatteryAlarm

/**
 * The main Jetpack Compose UI for BatterySentinel.
 * Displays a grid of battery alarms and provides a FAB to add new ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPowerSaveClick: () -> Unit
) {
    // Observe the StateFlow from the ViewModel. The UI will automatically redraw
    // whenever the underlying list of alarms changes.
    val alarms by viewModel.alarmsFlow.collectAsState()
    
    // State to manage the visibility of the Add/Edit Alarm dialog
    val showDialog = remember { mutableStateOf(false) }
    
    // State to track which alarm is currently being edited.
    // If null, the dialog will run in "Create New" mode.
    val editingAlarm = remember { mutableStateOf<BatteryAlarm?>(null) } // null indicates creating new alarm

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    actionIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    // Reset editing state and show dialog for a new alarm
                    editingAlarm.value = null
                    showDialog.value = true 
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // A quick-access setting row that intents to the OS battery saver
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPowerSaveClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.battery_settings_desc),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Organize alarms cleanly from highest threshold to lowest
            val sortedAlarms = alarms.sortedByDescending { it.thresholdPercent }

            // A responsive grid layout that adapts to different screen sizes (phones vs foldables/tablets)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedAlarms) { alarm ->
                    AlarmTile(
                        alarm = alarm,
                        onToggle = { enabled -> viewModel.updateAlarm(alarm.copy(isEnabled = enabled)) },
                        onDelete = { viewModel.removeAlarm(alarm.id) },
                        onClick = {
                            // Populate state and launch dialog in "Edit" mode
                            editingAlarm.value = alarm
                            showDialog.value = true
                        }
                    )
                }
            }
        }
    }

    // Modal dialog logic for Creating/Editing
    if (showDialog.value) {
        // Pre-fill fields based on whether we are editing an existing alarm or creating a new one
        val defaultThreshold = editingAlarm.value?.thresholdPercent?.toString() ?: "20"
        val defaultMessage = editingAlarm.value?.message ?: ""
        
        // Local state for the text fields
        val threshold = remember { mutableStateOf(defaultThreshold) }
        val message = remember { mutableStateOf(defaultMessage) }
        
        val titleRes = if (editingAlarm.value == null) R.string.new_alarm else R.string.edit_alarm
        
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text(stringResource(titleRes)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = threshold.value,
                        // Ensure only digits can be entered since this is a percentage
                        onValueChange = { threshold.value = it.filter { char -> char.isDigit() } },
                        label = { Text(stringResource(R.string.threshold_percent)) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message.value,
                        onValueChange = { message.value = it },
                        label = { Text(stringResource(R.string.message)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pct = threshold.value.toIntOrNull() ?: 20
                    val currentEdit = editingAlarm.value
                    if (currentEdit != null) {
                        viewModel.updateAlarm(currentEdit.copy(thresholdPercent = pct, message = message.value))
                    } else {
                        viewModel.addAlarm(pct, message.value)
                    }
                    showDialog.value = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * A reusable Composable representing a single squared tile for an alarm.
 */
@Composable
fun AlarmTile(
    alarm: BatteryAlarm,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Make the tile roughly square for uniform aesthetics
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                // A quick toggle switch for the user to disable the alarm temporarily
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { enabled -> onToggle(enabled) } 
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // The main focal point of the tile
            Text(
                text = "${alarm.thresholdPercent}%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alarm.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.weight(1f) // Push the delete button to the bottom
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_desc),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
