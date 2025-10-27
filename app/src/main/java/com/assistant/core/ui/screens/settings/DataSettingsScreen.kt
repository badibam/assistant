package com.assistant.core.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import kotlin.system.exitProcess
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Restart the application
 * Used after import/reset to reload all data with clean state
 */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    exitProcess(0)
}

/**
 * Data Management Settings Screen - Backup & Restore
 *
 * Features:
 * - Export: Generate JSON backup via SAF document creation
 * - Import: Read JSON from SAF document picker with confirmation
 * - Reset: Wipe all data with confirmation
 *
 * Architecture:
 * - UI handles SAF (file I/O via Android intents)
 * - Service handles logic (JSON generation/parsing)
 * - App restart after import/reset via Intent + exitProcess
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)
    val coordinator = Coordinator(context)
    val scope = rememberCoroutineScope()

    // UI states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Toast for errors
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            errorMessage = null
        }
    }

    // SAF launcher for export (create document)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    // Call export service
                    val result = coordinator.processUserAction(
                        "backup.export",
                        emptyMap()
                    )

                    if (result.status == CommandStatus.SUCCESS) {
                        val jsonData = result.data?.get("json_data") as? String
                        if (jsonData != null) {
                            // Write to SAF uri with explicit error handling
                            val outputStream = context.contentResolver.openOutputStream(uri)
                                ?: throw Exception("Failed to open output stream (permission denied or invalid URI)")

                            outputStream.use {
                                it.write(jsonData.toByteArray())
                            }

                            Toast.makeText(context, s.shared("backup_export_success"), Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = s.shared("backup_export_no_data")
                        }
                    } else {
                        errorMessage = result.error ?: s.shared("backup_export_failed")
                    }
                } catch (e: Exception) {
                    errorMessage = "${s.shared("backup_export_failed")}: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // SAF launcher for import (pick document)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    // Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("settings_backup"),
            subtitle = null,
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = null
        )

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY
                )
            }
        }

        // Export button
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = s.shared("backup_export"),
                    type = TextType.SUBTITLE
                )
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            .format(Date())
                        val version = com.assistant.BuildConfig.VERSION_NAME
                        exportLauncher.launch("assistant_backup_${timestamp}_v$version.json")
                    }
                ) {
                    UI.Text(
                        text = s.shared("backup_export"),
                        type = TextType.BODY
                    )
                }
            }
        }

        // Import button
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = s.shared("backup_import"),
                    type = TextType.SUBTITLE
                )
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                ) {
                    UI.Text(
                        text = s.shared("backup_import"),
                        type = TextType.BODY
                    )
                }
            }
        }

        // Reset button
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = s.shared("backup_reset"),
                    type = TextType.SUBTITLE
                )
                UI.Button(
                    type = ButtonType.SECONDARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = {
                        showResetConfirm = true
                    }
                ) {
                    UI.Text(
                        text = s.shared("backup_reset"),
                        type = TextType.BODY
                    )
                }
            }
        }
    }

    // Import confirmation dialog
    if (showImportConfirm) {
        UI.ConfirmDialog(
            title = s.shared("backup_import"),
            message = s.shared("backup_import_confirm"),
            confirmText = s.shared("action_confirm"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                showImportConfirm = false
                pendingImportUri?.let { uri ->
                    isLoading = true
                    scope.launch {
                        try {
                            // Read JSON from SAF uri
                            val jsonData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                inputStream.bufferedReader().readText()
                            }

                            if (jsonData != null) {
                                // Call import service
                                val result = coordinator.processUserAction(
                                    "backup.import",
                                    mapOf("json_data" to jsonData)
                                )

                                if (result.status == CommandStatus.SUCCESS) {
                                    Toast.makeText(
                                        context,
                                        s.shared("backup_import_success"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Restart app with clean state
                                    restartApp(context)
                                } else {
                                    errorMessage = result.error ?: s.shared("backup_import_failed")
                                }
                            } else {
                                errorMessage = s.shared("backup_import_read_failed")
                            }
                        } catch (e: Exception) {
                            errorMessage = "${s.shared("backup_import_failed")}: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportUri = null
            }
        )
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        UI.ConfirmDialog(
            title = s.shared("backup_reset"),
            message = s.shared("backup_reset_confirm"),
            confirmText = s.shared("action_confirm"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                showResetConfirm = false
                isLoading = true
                scope.launch {
                    try {
                        // Call reset service
                        val result = coordinator.processUserAction(
                            "backup.reset",
                            emptyMap()
                        )

                        if (result.status == CommandStatus.SUCCESS) {
                            Toast.makeText(
                                context,
                                s.shared("backup_reset_success"),
                                Toast.LENGTH_SHORT
                            ).show()
                            // Restart app with clean state
                            restartApp(context)
                        } else {
                            errorMessage = result.error ?: s.shared("backup_reset_failed")
                        }
                    } catch (e: Exception) {
                        errorMessage = "${s.shared("backup_reset_failed")}: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            onDismiss = {
                showResetConfirm = false
            }
        )
    }
}
