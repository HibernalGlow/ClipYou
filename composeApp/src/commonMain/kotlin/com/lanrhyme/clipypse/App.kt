package com.lanrhyme.clipypse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val viewModel = remember { MainViewModel() }
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ClipYou") },
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConnectionCard(
                    uiState = uiState,
                    onModeChange = viewModel::setMode,
                    onServerModeChange = viewModel::setServerMode,
                    onIpChange = viewModel::setIpAddress,
                    onPortChange = viewModel::setPort,
                    onStart = viewModel::startSync,
                    onStop = viewModel::stopSync
                )

                StatusCard(
                    uiState = uiState,
                    onAutoSyncChange = viewModel::setAutoSync
                )

                HistoryCard(
                    history = uiState.clipboardHistory,
                    onClear = viewModel::clearHistory,
                    onResend = viewModel::sendClipboardItem,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        uiState.errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun ConnectionCard(
    uiState: AppUiState,
    onModeChange: (ConnectionMode) -> Unit,
    onServerModeChange: (Boolean) -> Unit,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.isServer,
                    onClick = { onServerModeChange(true) },
                    label = { Text("Server") },
                    leadingIcon = if (uiState.isServer) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = !uiState.isServer,
                    onClick = { onServerModeChange(false) },
                    label = { Text("Client") },
                    leadingIcon = if (!uiState.isServer) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.mode == ConnectionMode.Wifi,
                    onClick = { onModeChange(ConnectionMode.Wifi) },
                    label = { Text("Wi-Fi") }
                )
                FilterChip(
                    selected = uiState.mode == ConnectionMode.Usb,
                    onClick = { onModeChange(ConnectionMode.Usb) },
                    label = { Text("USB") }
                )
                FilterChip(
                    selected = uiState.mode == ConnectionMode.Bluetooth,
                    onClick = { onModeChange(ConnectionMode.Bluetooth) },
                    label = { Text("Bluetooth") }
                )
            }

            if (!uiState.isServer) {
                OutlinedTextField(
                    value = uiState.ipAddress,
                    onValueChange = onIpChange,
                    label = { Text("IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = uiState.syncState == SyncState.Idle
                )
            }

            OutlinedTextField(
                value = uiState.port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = uiState.syncState == SyncState.Idle
            )

            Button(
                onClick = if (uiState.syncState == SyncState.Idle) onStart else onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = if (uiState.syncState == SyncState.Idle) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                }
            ) {
                when (uiState.syncState) {
                    SyncState.Idle -> Text("Start")
                    SyncState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    }
                    SyncState.Syncing -> Text("Stop")
                    SyncState.Error -> Text("Retry")
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    uiState: AppUiState,
    onAutoSyncChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .padding(2.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.small,
                            color = when (uiState.syncState) {
                                SyncState.Idle -> MaterialTheme.colorScheme.outline
                                SyncState.Connecting -> MaterialTheme.colorScheme.tertiary
                                SyncState.Syncing -> MaterialTheme.colorScheme.primary
                                SyncState.Error -> MaterialTheme.colorScheme.error
                            }
                        ) {}
                    }
                    Text(
                        text = when (uiState.syncState) {
                            SyncState.Idle -> "Idle"
                            SyncState.Connecting -> "Connecting"
                            SyncState.Syncing -> "Syncing"
                            SyncState.Error -> "Error"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            uiState.remoteDevice?.let { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Connected to:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${device.deviceName} (${device.platform})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto Sync", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = uiState.autoSync,
                    onCheckedChange = onAutoSyncChange,
                    enabled = uiState.syncState == SyncState.Syncing
                )
            }
        }
    }
}

@Composable
fun HistoryCard(
    history: List<ClipboardItem>,
    onClear: () -> Unit,
    onResend: (ClipboardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History (${history.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No clipboard history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history) { item ->
                        HistoryItem(
                            item = item,
                            onResend = { onResend(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    item: ClipboardItem,
    onResend: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = when (item.type) {
                    ClipboardType.Text -> String(item.data, Charsets.UTF_8).take(50)
                    ClipboardType.Image -> "Image (${item.data.size} bytes)"
                    ClipboardType.File -> item.fileName
                    ClipboardType.Uri -> String(item.data, Charsets.UTF_8).take(50)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
            )
        },
        leadingContent = {
            Icon(
                imageVector = when (item.type) {
                    ClipboardType.Text -> Icons.Default.TextFields
                    ClipboardType.Image -> Icons.Default.Image
                    ClipboardType.File -> Icons.Default.InsertDriveFile
                    ClipboardType.Uri -> Icons.Default.Link
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            IconButton(onClick = onResend) {
                Icon(Icons.Default.Send, contentDescription = "Resend")
            }
        }
    )
}
