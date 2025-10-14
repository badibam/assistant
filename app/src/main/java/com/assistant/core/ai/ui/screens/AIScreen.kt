package com.assistant.core.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.*
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.orchestration.RoundReason
import com.assistant.core.ai.orchestration.WaitingState
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.commands.CommandStatus
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * AI Screen - Unified interface for all session types (CHAT, SEED, AUTOMATION)
 *
 * Architecture:
 * - CHAT: Interactive conversation with user, message sending, validation toggles
 * - SEED: Template editor for automations (message + schedule/triggers config)
 * - AUTOMATION: Read-only viewer for automation executions with interrupt capability
 *
 * This is a pure Composable (not a Dialog) for maximum flexibility:
 * - Used by AIFloatingChat as Dialog wrapper for active sessions
 * - Used directly by ZoneScreen for SEED editing (fullscreen)
 * - Can be embedded anywhere in the app
 */
@Composable
fun AIScreen(
    sessionId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Load session (once per sessionId)
    var session by remember(sessionId) { mutableStateOf<AISession?>(null) }
    var isLoadingSession by remember(sessionId) { mutableStateOf(true) }
    var errorMessage by remember(sessionId) { mutableStateOf<String?>(null) }

    // Load session data
    LaunchedEffect(sessionId) {
        try {
            isLoadingSession = true
            session = AIOrchestrator.loadSession(sessionId)
            LogManager.aiUI("AIScreen loaded session: ${session?.name} (type=${session?.type})")
        } catch (e: Exception) {
            LogManager.aiUI("Failed to load session $sessionId: ${e.message}", "ERROR")
            errorMessage = s.shared("ai_error_load_session")
        } finally {
            isLoadingSession = false
        }
    }

    // For CHAT/AUTOMATION: observe activeSessionId for eviction detection
    val orchestratorSessionId by AIOrchestrator.activeSessionId.collectAsState()
    LaunchedEffect(orchestratorSessionId) {
        if (orchestratorSessionId == sessionId && session != null) {
            // Session reactivated or state changed - reload
            try {
                session = AIOrchestrator.loadSession(sessionId)
                LogManager.aiUI("AIScreen reloaded session after activation change")
            } catch (e: Exception) {
                LogManager.aiUI("Failed to reload session: ${e.message}", "ERROR")
            }
        }
    }

    // Loading state
    if (isLoadingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY
            )
        }
        return
    }

    // Error state
    if (session == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = errorMessage ?: s.shared("ai_error_session_not_found"),
                    type = TextType.BODY
                )
                UI.ActionButton(
                    action = ButtonAction.BACK,
                    display = ButtonDisplay.LABEL,
                    onClick = onClose
                )
            }
        }
        return
    }

    // Route according to session type
    when (session!!.type) {
        SessionType.CHAT -> {
            ChatMode(
                session = session!!,
                onClose = onClose
            )
        }
        SessionType.SEED -> {
            SeedMode(
                session = session!!,
                onClose = onClose
            )
        }
        SessionType.AUTOMATION -> {
            AutomationMode(
                session = session!!,
                onClose = onClose
            )
        }
    }
}

/**
 * CHAT mode - Interactive conversation
 */
@Composable
private fun ChatMode(
    session: AISession,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Local states
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStats by remember { mutableStateOf(false) }

    // Observe orchestrator states
    val waitingState by AIOrchestrator.waitingState.collectAsState()
    val isRoundInProgress by AIOrchestrator.isRoundInProgress.collectAsState()
    val messages by AIOrchestrator.activeSessionMessages.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        ChatHeader(
            session = session,
            isLoading = isRoundInProgress,
            onClose = onClose,
            onShowStats = { showStats = true },
            onStopSession = {
                scope.launch {
                    AIOrchestrator.stopActiveSession()
                }
            },
            onToggleValidation = { requireValidation ->
                scope.launch {
                    AIOrchestrator.toggleValidation(session.id, requireValidation)
                }
            }
        )

        // Stats dialog
        if (showStats) {
            com.assistant.core.ai.ui.chat.SessionStatsDialog(
                sessionId = session.id,
                sessionName = session.name,
                onDismiss = { showStats = false }
            )
        }

        // Messages area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChatMessageList(
                messages = messages,
                isLoading = isRoundInProgress,
                waitingState = waitingState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Composer
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                UI.RichComposer(
                    segments = segments,
                    onSegmentsChange = { segments = it },
                    onSend = { richMessage ->
                        errorMessage = null
                        AIOrchestrator.sendMessageAsync(
                            richMessage = richMessage,
                            onComposerClear = { segments = emptyList() },
                            onError = { error -> errorMessage = error }
                        )
                    },
                    placeholder = s.shared("ai_composer_placeholder")
                )
            }
        }
    }

    // Error display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * SEED mode - Automation template editor
 * Full implementation with message editing, schedule and triggers configuration
 */
@Composable
private fun SeedMode(
    session: AISession,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val coordinator = remember { com.assistant.core.coordinator.Coordinator(context) }

    // Load automation associated with this SEED session
    var automation by remember { mutableStateOf<Automation?>(null) }
    var isLoadingAutomation by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Editor states
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) } // Composer editing (local temp state)
    var displaySegments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) } // Display from DB (source of truth)
    var scheduleConfig by remember { mutableStateOf<com.assistant.core.utils.ScheduleConfig?>(null) }
    var triggersCount by remember { mutableStateOf(0) }
    var userMessageId by remember { mutableStateOf<String?>(null) } // ID of the USER message in SEED session

    // Track if session needs reload after refresh
    var sessionReloadTrigger by remember { mutableStateOf(0) }

    // Dialogs
    var showScheduleEditor by remember { mutableStateOf(false) }
    var showTriggersEditor by remember { mutableStateOf(false) }

    // Load automation on mount
    LaunchedEffect(session.id) {
        try {
            isLoadingAutomation = true
            val result = coordinator.processUserAction(
                "automations.get_by_seed_session",
                mapOf("seed_session_id" to session.id)
            )

            if (result.status == CommandStatus.SUCCESS) {
                @Suppress("UNCHECKED_CAST")
                val automationMap = result.data?.get("automation") as? Map<String, Any>
                if (automationMap != null) {
                    // Parse automation data
                    val scheduleJson = automationMap["schedule"] as? String
                    automation = Automation(
                        id = automationMap["id"] as String,
                        name = automationMap["name"] as String,
                        zoneId = automationMap["zone_id"] as String,
                        seedSessionId = automationMap["seed_session_id"] as String,
                        schedule = scheduleJson?.let {
                            kotlinx.serialization.json.Json.decodeFromString(it)
                        },
                        triggerIds = (automationMap["trigger_ids"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        dismissOlderInstances = automationMap["dismiss_older_instances"] as? Boolean ?: false,
                        providerId = automationMap["provider_id"] as String,
                        isEnabled = automationMap["is_enabled"] as? Boolean ?: true,
                        createdAt = (automationMap["created_at"] as? Number)?.toLong() ?: 0L,
                        lastExecutionId = automationMap["last_execution_id"] as? String,
                        executionHistory = (automationMap["execution_history"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )

                    // Initialize states from automation
                    scheduleConfig = automation?.schedule
                    triggersCount = automation?.triggerIds?.size ?: 0

                    LogManager.aiUI("SeedMode loaded automation: ${automation?.id}", "DEBUG")
                }
            } else {
                errorMessage = result.error ?: s.shared("error_automation_not_found")
                LogManager.aiUI("Failed to load automation for SEED session ${session.id}: ${result.error}", "ERROR")
            }
        } catch (e: Exception) {
            errorMessage = s.shared("error_automation_load_failed")
            LogManager.aiUI("Exception loading automation: ${e.message}", "ERROR", e)
        } finally {
            isLoadingAutomation = false
        }
    }

    // Load segments from DB (display state = source of truth)
    LaunchedEffect(session.id, sessionReloadTrigger) {
        try {
            val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to session.id))
            if (result.status == CommandStatus.SUCCESS) {
                @Suppress("UNCHECKED_CAST")
                val messagesList = result.data?.get("messages") as? List<Map<String, Any>> ?: emptyList()
                val firstUserMessage = messagesList.firstOrNull { (it["sender"] as? String) == "USER" }

                // Store USER message ID for updates
                userMessageId = firstUserMessage?.get("id") as? String

                val richContentJson = firstUserMessage?.get("richContentJson") as? String

                val loadedSegments = if (richContentJson != null) {
                    com.assistant.core.ai.data.RichMessage.fromJson(richContentJson)?.segments ?: emptyList()
                } else {
                    emptyList()
                }

                // Update both display (from DB) and composer (for editing)
                displaySegments = loadedSegments
                segments = loadedSegments

                LogManager.aiUI("SeedMode loaded segments from DB: ${displaySegments.size} segments, messageId=$userMessageId", "DEBUG")
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to reload segments: ${e.message}", "ERROR", e)
        }
    }

    // Loading state
    if (isLoadingAutomation) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SeedHeader(
                session = session,
                onClose = onClose,
                onConfigureAutomation = { }
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY
                )
            }
        }
        return
    }

    // Error state
    if (automation == null) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SeedHeader(
                session = session,
                onClose = onClose,
                onConfigureAutomation = { }
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UI.Text(
                        text = errorMessage ?: s.shared("error_automation_not_found"),
                        type = TextType.BODY
                    )
                    UI.ActionButton(
                        action = ButtonAction.BACK,
                        display = ButtonDisplay.LABEL,
                        onClick = onClose
                    )
                }
            }
        }
        return
    }

    // Schedule editor dialog
    if (showScheduleEditor) {
        com.assistant.core.ai.ui.automation.ScheduleConfigEditor(
            existingConfig = scheduleConfig,
            onDismiss = { showScheduleEditor = false },
            onConfirm = { newSchedule ->
                scheduleConfig = newSchedule
                showScheduleEditor = false
                LogManager.aiUI("SeedMode: Schedule configured", "DEBUG")
            }
        )
    }

    // Triggers editor dialog (stub)
    if (showTriggersEditor) {
        UI.Dialog(
            type = DialogType.CONFIGURE,
            onConfirm = { showTriggersEditor = false },
            onCancel = { showTriggersEditor = false }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = s.shared("automation_triggers_config_title"),
                    type = TextType.TITLE
                )
                UI.Text(
                    text = s.shared("automation_triggers_coming_soon"),
                    type = TextType.BODY
                )
            }
        }
    }

    // Main editor
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        SeedHeader(
            session = session,
            onClose = onClose,
            onConfigureAutomation = {
                // TODO: Open general automation config (name, provider, etc.)
            }
        )

        // Message display area (read-only, shows current message)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(
                    text = s.shared("automation_seed_message_label"),
                    type = TextType.SUBTITLE
                )

                // Show current message preview (from DB state)
                if (displaySegments.isNotEmpty()) {
                    val textContent = displaySegments.filterIsInstance<MessageSegment.Text>()
                        .joinToString("") { it.content }
                    val enrichmentBlocks = displaySegments.filterIsInstance<MessageSegment.EnrichmentBlock>()

                    if (textContent.isNotEmpty()) {
                        UI.Card(type = CardType.DEFAULT) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                UI.Text(
                                    text = textContent,
                                    type = TextType.BODY
                                )
                            }
                        }
                    }

                    if (enrichmentBlocks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            UI.Text(
                                text = s.shared("automation_enrichments_label"),
                                type = TextType.LABEL
                            )
                            enrichmentBlocks.forEach { block ->
                                UI.Card(type = CardType.DEFAULT) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        UI.Text(text = getEnrichmentIcon(block.type), type = TextType.BODY)
                                        UI.Text(text = block.preview, type = TextType.BODY)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    UI.Text(
                        text = s.shared("automation_no_message"),
                        type = TextType.CAPTION
                    )
                }
            }
        }

        // Editor footer with composer and config buttons
        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp)) {
                com.assistant.core.ai.ui.automation.AutomationEditorFooter(
                    automation = automation,
                    segments = segments,
                    onSegmentsChange = { segments = it },
                    scheduleConfig = scheduleConfig,
                    onConfigureSchedule = { showScheduleEditor = true },
                    triggersCount = triggersCount,
                    onConfigureTriggers = { showTriggersEditor = true },
                    onRefresh = {
                        scope.launch {
                            try {
                                // Check if we have a USER message ID
                                if (userMessageId == null) {
                                    errorMessage = s.shared("ai_error_message_not_found").format(session.id)
                                    LogManager.aiUI("SeedMode: No USER message ID available", "ERROR")
                                    return@launch
                                }

                                // Build RichMessage from segments
                                val richMessage = com.assistant.core.ai.data.RichMessage(
                                    segments = segments,
                                    linearText = segments.joinToString(" ") { segment ->
                                        when (segment) {
                                            is MessageSegment.Text -> segment.content
                                            is MessageSegment.EnrichmentBlock -> segment.preview
                                        }
                                    }.trim(),
                                    dataCommands = emptyList() // Will be computed when automation executes
                                )

                                // Update message in DB
                                val updateResult = coordinator.processUserAction(
                                    "ai_sessions.update_message",
                                    mapOf(
                                        "messageId" to userMessageId!!,
                                        "richContentJson" to richMessage.toJson()
                                    )
                                )

                                if (updateResult.status == CommandStatus.SUCCESS) {
                                    // Trigger reload of segments from DB
                                    sessionReloadTrigger++
                                    UI.Toast(context, s.shared("automation_saved_success"), Duration.SHORT)
                                    LogManager.aiUI("SeedMode: Message refreshed successfully", "INFO")
                                } else {
                                    errorMessage = updateResult.error ?: s.shared("automation_save_failed")
                                    LogManager.aiUI("SeedMode: Refresh failed: ${updateResult.error}", "ERROR")
                                }
                            } catch (e: Exception) {
                                errorMessage = s.shared("automation_save_failed")
                                LogManager.aiUI("SeedMode: Refresh exception: ${e.message}", "ERROR", e)
                            }
                        }
                    },
                    onSave = {
                        scope.launch {
                            try {
                                // Step 1: Refresh message (update DB + reload)
                                if (userMessageId != null) {
                                    val richMessage = com.assistant.core.ai.data.RichMessage(
                                        segments = segments,
                                        linearText = segments.joinToString(" ") { segment ->
                                            when (segment) {
                                                is MessageSegment.Text -> segment.content
                                                is MessageSegment.EnrichmentBlock -> segment.preview
                                            }
                                        }.trim(),
                                        dataCommands = emptyList()
                                    )

                                    val updateMsgResult = coordinator.processUserAction(
                                        "ai_sessions.update_message",
                                        mapOf(
                                            "messageId" to userMessageId!!,
                                            "richContentJson" to richMessage.toJson()
                                        )
                                    )

                                    if (updateMsgResult.status != CommandStatus.SUCCESS) {
                                        errorMessage = updateMsgResult.error ?: s.shared("automation_save_failed")
                                        return@launch
                                    }
                                }

                                // Step 2: Update automation config (schedule, triggers)
                                val updateParams = mutableMapOf<String, Any>(
                                    "automation_id" to automation!!.id
                                )
                                scheduleConfig?.let {
                                    updateParams["schedule"] = kotlinx.serialization.json.Json.encodeToString(
                                        com.assistant.core.utils.ScheduleConfig.serializer(),
                                        it
                                    )
                                }

                                val result = coordinator.processUserAction("automations.update", updateParams)
                                if (result.status == CommandStatus.SUCCESS) {
                                    UI.Toast(context, s.shared("automation_saved_success"), Duration.SHORT)
                                    LogManager.aiUI("SeedMode: Automation saved successfully", "INFO")
                                    onClose()
                                } else {
                                    errorMessage = result.error ?: s.shared("automation_save_failed")
                                    LogManager.aiUI("SeedMode: Save failed: ${result.error}", "ERROR")
                                }
                            } catch (e: Exception) {
                                errorMessage = s.shared("automation_save_failed")
                                LogManager.aiUI("SeedMode: Save exception: ${e.message}", "ERROR", e)
                            }
                        }
                    },
                    onCancel = onClose
                )
            }
        }
    }

    // Error display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * Get icon for enrichment type (helper for preview)
 */
private fun getEnrichmentIcon(type: com.assistant.core.ai.data.EnrichmentType): String {
    return when (type) {
        com.assistant.core.ai.data.EnrichmentType.POINTER -> "🔍"
        com.assistant.core.ai.data.EnrichmentType.USE -> "📝"
        com.assistant.core.ai.data.EnrichmentType.CREATE -> "✨"
        com.assistant.core.ai.data.EnrichmentType.MODIFY_CONFIG -> "🔧"
    }
}

/**
 * AUTOMATION mode - Execution viewer (read-only)
 */
@Composable
private fun AutomationMode(
    session: AISession,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Observe orchestrator states
    val messages by AIOrchestrator.activeSessionMessages.collectAsState()
    val isRoundInProgress by AIOrchestrator.isRoundInProgress.collectAsState()
    val waitingState by AIOrchestrator.waitingState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        AutomationHeader(
            session = session,
            isRunning = isRoundInProgress,
            onClose = onClose,
            onInterrupt = {
                AIOrchestrator.interruptActiveRound()
            }
        )

        // Messages area (read-only)
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatMessageList(
                messages = messages,
                isLoading = isRoundInProgress,
                waitingState = waitingState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ================================================================================================
// Headers (3 variants)
// ================================================================================================

/**
 * Chat header - Session controls, validation toggle, stats
 */
@Composable
private fun ChatHeader(
    session: AISession,
    isLoading: Boolean,
    onClose: () -> Unit,
    onShowStats: () -> Unit,
    onStopSession: () -> Unit,
    onToggleValidation: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSessionSettings by remember { mutableStateOf(false) }

    // Stop confirmation dialog
    if (showStopConfirmation) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                showStopConfirmation = false
                onStopSession()
            },
            onCancel = { showStopConfirmation = false }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = s.shared("ai_session_stop_title"),
                    type = TextType.TITLE
                )
                UI.Text(
                    text = s.shared("ai_session_stop_message"),
                    type = TextType.BODY
                )
            }
        }
    }

    // Settings menu dialog
    if (showSettingsMenu) {
        com.assistant.core.ai.ui.chat.SettingsMenuDialog(
            session = session,
            onDismiss = { showSettingsMenu = false },
            onShowCosts = {
                showSettingsMenu = false
                onShowStats()
            },
            onShowSessionSettings = {
                showSettingsMenu = false
                showSessionSettings = true
            }
        )
    }

    // Session settings dialog
    if (showSessionSettings) {
        com.assistant.core.ai.ui.chat.SessionSettingsDialog(
            session = session,
            onDismiss = { showSessionSettings = false },
            onToggleValidation = onToggleValidation
        )
    }

    // Header row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
            UI.Text(
                text = when {
                    isLoading -> s.shared("ai_status_processing")
                    else -> s.shared("ai_status_ready")
                },
                type = TextType.CAPTION
            )
        }

        // Stop button
        UI.ActionButton(
            action = ButtonAction.STOP,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = { showStopConfirmation = true }
        )

        // Settings button
        UI.ActionButton(
            action = ButtonAction.CONFIGURE,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = { showSettingsMenu = true }
        )

        // Close button
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onClose
        )
    }
}

/**
 * Seed header - Automation name and config button
 */
@Composable
private fun SeedHeader(
    session: AISession,
    onClose: () -> Unit,
    onConfigureAutomation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Box(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
        }

        // Config button
        UI.ActionButton(
            action = ButtonAction.CONFIGURE,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onConfigureAutomation
        )

        // Close button
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onClose
        )
    }
}

/**
 * Automation header - Execution status and interrupt button
 */
@Composable
private fun AutomationHeader(
    session: AISession,
    isRunning: Boolean,
    onClose: () -> Unit,
    onInterrupt: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
            UI.Text(
                text = if (isRunning) s.shared("ai_status_processing") else s.shared("ai_status_completed"),
                type = TextType.CAPTION
            )
        }

        // Interrupt button (only if running)
        if (isRunning) {
            UI.ActionButton(
                action = ButtonAction.INTERRUPT,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onInterrupt
            )
        }

        // Close button
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onClose
        )
    }
}

// ================================================================================================
// Shared Components (copied from AIFloatingChat, will be extracted to separate file later)
// ================================================================================================

/**
 * Message list - shared by CHAT and AUTOMATION modes
 */
@Composable
private fun ChatMessageList(
    messages: List<SessionMessage>,
    isLoading: Boolean,
    waitingState: WaitingState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Determine which message should show active module (last AI message if waiting for response)
    val lastAIMessageIndex = messages.indexOfLast { it.sender == MessageSender.AI }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text(
                        text = s.shared("ai_chat_start_conversation"),
                        type = TextType.BODY
                    )
                }
            }
        } else {
            itemsIndexed(messages) { index, message ->
                com.assistant.core.ai.ui.chat.ChatMessageBubble(
                    message = message,
                    waitingState = waitingState,
                    isLastAIMessage = index == lastAIMessageIndex
                )
            }

            // AI thinking indicator (don't show during communication module or validation)
            if (isLoading && waitingState !is WaitingState.WaitingResponse && waitingState !is WaitingState.WaitingValidation) {
                item {
                    com.assistant.core.ai.ui.chat.ChatLoadingIndicator()
                }
            }
        }
    }
}

