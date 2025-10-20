package com.assistant.core.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assistant.core.ai.data.*
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.domain.WaitingContext
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.ai.ui.components.SessionStatusBar
import com.assistant.core.commands.CommandStatus
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Helper function to map Phase enum to localized UI string.
 */
private fun getPhaseLabel(phase: Phase, s: com.assistant.core.strings.StringsContext): String {
    return when (phase) {
        Phase.IDLE -> s.shared("ai_phase_idle")
        Phase.EXECUTING_ENRICHMENTS -> s.shared("ai_phase_executing_enrichments")
        Phase.CALLING_AI -> s.shared("ai_phase_calling_ai")
        Phase.PARSING_AI_RESPONSE -> s.shared("ai_phase_parsing")
        Phase.PREPARING_CONTINUATION -> s.shared("ai_phase_preparing_continuation")
        Phase.EXECUTING_DATA_QUERIES -> s.shared("ai_phase_executing_queries")
        Phase.EXECUTING_ACTIONS -> s.shared("ai_phase_executing_actions")
        Phase.WAITING_VALIDATION -> s.shared("ai_phase_waiting_validation")
        Phase.WAITING_COMMUNICATION_RESPONSE -> s.shared("ai_phase_waiting_communication")
        Phase.WAITING_COMPLETION_CONFIRMATION -> s.shared("ai_phase_waiting_completion")
        Phase.WAITING_NETWORK_RETRY -> s.shared("ai_phase_waiting_network")
        Phase.RETRYING_AFTER_FORMAT_ERROR,
        Phase.RETRYING_AFTER_ACTION_FAILURE -> s.shared("ai_phase_retrying")
        Phase.INTERRUPTED -> s.shared("ai_phase_interrupted")
        Phase.AWAITING_SESSION_CLOSURE -> s.shared("ai_phase_awaiting_closure")
        Phase.CLOSED -> s.shared("ai_phase_completed")
    }
}

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
    val coordinator = remember { com.assistant.core.coordinator.Coordinator(context) }

    // Observe AI state for active session detection
    val aiState by AIOrchestrator.currentState.collectAsState()

    // Determine if this is the active session
    val isActiveSession = aiState.sessionId == sessionId

    // Session state
    var session by remember(sessionId) { mutableStateOf<AISession?>(null) }
    var isLoadingSession by remember(sessionId) { mutableStateOf(!isActiveSession) }
    var errorMessage by remember(sessionId) { mutableStateOf<String?>(null) }

    // Load session data - ONLY if not the active session (to avoid race condition)
    LaunchedEffect(sessionId, isActiveSession, aiState.sessionType) {
        if (!isActiveSession) {
            // Non-active session: load from DB
            try {
                isLoadingSession = true
                val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))
                if (result.status == CommandStatus.SUCCESS) {
                    @Suppress("UNCHECKED_CAST")
                    val sessionData = result.data?.get("session") as? Map<String, Any>
                    if (sessionData != null) {
                        session = AISession(
                            id = sessionData["id"] as String,
                            name = sessionData["name"] as String,
                            type = SessionType.valueOf(sessionData["type"] as String),
                            requireValidation = sessionData["requireValidation"] as? Boolean ?: false,
                            waitingStateJson = sessionData["waitingStateJson"] as? String,
                            automationId = sessionData["automationId"] as? String,
                            scheduledExecutionTime = (sessionData["scheduledExecutionTime"] as? Number)?.toLong(),
                            providerId = sessionData["providerId"] as String,
                            providerSessionId = sessionData["providerSessionId"] as String,
                            createdAt = (sessionData["createdAt"] as Number).toLong(),
                            lastActivity = (sessionData["lastActivity"] as Number).toLong(),
                            messages = emptyList(),
                            isActive = sessionData["isActive"] as? Boolean ?: false
                        )
                        LogManager.aiUI("AIScreen loaded non-active session from DB: ${session?.name} (type=${session?.type})")
                    } else {
                        errorMessage = s.shared("ai_error_session_not_found")
                    }
                } else {
                    errorMessage = result.error ?: s.shared("ai_error_load_session")
                }
            } catch (e: Exception) {
                LogManager.aiUI("Failed to load session $sessionId: ${e.message}", "ERROR")
                errorMessage = s.shared("ai_error_load_session")
            } finally {
                isLoadingSession = false
            }
        } else {
            // Active session: use reactive type from aiState (immediate, no DB read needed)
            if (aiState.sessionType != null) {
                session = AISession(
                    id = sessionId,
                    name = "", // Name not needed for routing
                    type = aiState.sessionType!!,
                    requireValidation = false,
                    waitingStateJson = null,
                    automationId = null,
                    scheduledExecutionTime = null,
                    providerId = "claude",
                    providerSessionId = "",
                    createdAt = System.currentTimeMillis(),
                    lastActivity = System.currentTimeMillis(),
                    messages = emptyList(),
                    isActive = true
                )
                isLoadingSession = false
                LogManager.aiUI("AIScreen using active session type from aiState: ${aiState.sessionType}")
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

    // Observe AIState from orchestrator
    val aiState by AIOrchestrator.currentState.collectAsState()

    // Derive UI states from aiState.phase
    val isLoading = aiState.phase in listOf(
        Phase.CALLING_AI,
        Phase.EXECUTING_ENRICHMENTS,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.PARSING_AI_RESPONSE,
        Phase.RETRYING_AFTER_FORMAT_ERROR,
        Phase.RETRYING_AFTER_ACTION_FAILURE
    )

    // Disable composer during processing, waiting phases, and interrupt
    // Only IDLE phase allows sending new messages
    val isComposerEnabled = aiState.phase !in listOf(
        Phase.CALLING_AI,
        Phase.EXECUTING_ENRICHMENTS,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.PARSING_AI_RESPONSE,
        Phase.WAITING_VALIDATION,
        Phase.WAITING_COMMUNICATION_RESPONSE,
        Phase.WAITING_COMPLETION_CONFIRMATION,
        Phase.WAITING_NETWORK_RETRY,
        Phase.RETRYING_AFTER_FORMAT_ERROR,
        Phase.RETRYING_AFTER_ACTION_FAILURE,
        Phase.PREPARING_CONTINUATION,
        Phase.INTERRUPTED,
        Phase.AWAITING_SESSION_CLOSURE,
        Phase.CLOSED
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        ChatHeader(
            session = session,
            phase = aiState.phase,
            onClose = onClose,
            onShowStats = { showStats = true },
            onStopSession = {
                scope.launch {
                    AIOrchestrator.stopActiveSession()
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

        // Messages area (includes inline validation/communication)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChatMessageList(
                aiState = aiState,
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
                        scope.launch {
                            try {
                                AIOrchestrator.sendMessage(richMessage)
                                segments = emptyList() // Clear composer on success
                            } catch (e: Exception) {
                                errorMessage = e.message ?: s.shared("ai_error_send_message")
                                LogManager.aiUI("ChatMode sendMessage failed: ${e.message}", "ERROR", e)
                            }
                        }
                    },
                    placeholder = s.shared("ai_composer_placeholder"),
                    enabled = isComposerEnabled
                )
            }
        }

        // Status bar (always visible at bottom)
        SessionStatusBar(
            phase = aiState.phase,
            sessionType = session.type,
            context = context
        )
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

    // Local state
    var showChatOptionsDialog by remember { mutableStateOf(false) }

    // Observe AIState from orchestrator
    val aiState by AIOrchestrator.currentState.collectAsState()

    // Determine if this automation is the active session
    val isActiveSession = aiState.sessionId == session.id

    // Chat options dialog
    if (showChatOptionsDialog) {
        ChatOptionsDialog(
            onDismiss = { showChatOptionsDialog = false },
            onInterruptAndChat = {
                showChatOptionsDialog = false
                scope.launch {
                    AIOrchestrator.interruptAutomationForChat()
                }
            },
            onChatAfter = {
                showChatOptionsDialog = false
                scope.launch {
                    AIOrchestrator.requestChatSession()
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        AutomationHeader(
            session = session,
            aiState = aiState,
            isActiveSession = isActiveSession,
            onClose = onClose,
            onChatRequest = { showChatOptionsDialog = true },
            onStop = {
                scope.launch {
                    AIOrchestrator.stopActiveSession()
                }
            }
        )

        // Messages area (read-only)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChatMessageList(
                aiState = aiState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Status bar (always visible at bottom)
        SessionStatusBar(
            phase = aiState.phase,
            sessionType = session.type,
            context = context
        )
    }
}

// ================================================================================================
// Headers (3 variants)
// ================================================================================================

/**
 * Chat options dialog - Shown when user clicks Chat button during automation
 * Allows choosing between interrupting immediately or waiting for completion
 */
@Composable
private fun ChatOptionsDialog(
    onDismiss: () -> Unit,
    onInterruptAndChat: () -> Unit,
    onChatAfter: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                UI.Text(
                    text = s.shared("ai_chat_options_dialog_title"),
                    type = TextType.TITLE
                )

                // Description
                UI.Text(
                    text = s.shared("ai_chat_options_dialog_description"),
                    type = TextType.BODY
                )

                // Option A: Interrupt immediately
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInterruptAndChat() }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UI.Text(
                            text = s.shared("ai_chat_option_interrupt_title"),
                            type = TextType.SUBTITLE
                        )
                        UI.Text(
                            text = s.shared("ai_chat_option_interrupt_description"),
                            type = TextType.CAPTION
                        )
                    }
                }

                // Option B: Wait for completion
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatAfter() }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UI.Text(
                            text = s.shared("ai_chat_option_after_title"),
                            type = TextType.SUBTITLE
                        )
                        UI.Text(
                            text = s.shared("ai_chat_option_after_description"),
                            type = TextType.CAPTION
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chat header - Session controls, validation toggle, stats
 */
@Composable
private fun ChatHeader(
    session: AISession,
    phase: Phase,
    onClose: () -> Unit,
    onShowStats: () -> Unit,
    onStopSession: () -> Unit
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
        val scope = rememberCoroutineScope()
        val coordinator = remember { com.assistant.core.coordinator.Coordinator(context) }

        com.assistant.core.ai.ui.chat.SessionSettingsDialog(
            session = session,
            onDismiss = { showSessionSettings = false },
            onToggleValidation = { enabled ->
                scope.launch {
                    val result = coordinator.processUserAction(
                        "ai_sessions.update_validation",
                        mapOf(
                            "sessionId" to session.id,
                            "requireValidation" to enabled
                        )
                    )

                    if (result.status == CommandStatus.SUCCESS) {
                        LogManager.aiUI("Session validation updated: $enabled", "INFO")
                    } else {
                        LogManager.aiUI("Failed to update validation: ${result.error}", "ERROR")
                    }
                }
            }
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
        // Title with phase status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
            UI.Text(
                text = getPhaseLabel(phase, s),
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
 * Automation header - Execution status with STOP button
 */
@Composable
private fun AutomationHeader(
    session: AISession,
    aiState: com.assistant.core.ai.domain.AIState,
    isActiveSession: Boolean,
    onClose: () -> Unit,
    onChatRequest: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Determine if controls should be shown
    val showControls = isActiveSession && aiState.phase != Phase.CLOSED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chat button (always visible for active session)
        if (showControls) {
            UI.ActionButton(
                action = ButtonAction.AI_CHAT,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onChatRequest
            )
        }

        // Title with phase status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
            UI.Text(
                text = if (isActiveSession) getPhaseLabel(aiState.phase, s) else s.shared("ai_phase_completed"),
                type = TextType.CAPTION
            )
        }

        // Control button (only for active session)
        if (showControls) {
            UI.ActionButton(
                action = ButtonAction.STOP,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onStop
            )
        }

        // Close button (always visible)
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
 * Made public for use in AIFloatingChat automation view
 */
@Composable
fun ChatMessageList(
    aiState: com.assistant.core.ai.domain.AIState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val listState = rememberLazyListState()

    // Observe messages for active session
    val messages by remember(aiState.sessionId) {
        if (aiState.sessionId != null) {
            AIOrchestrator.observeMessages(aiState.sessionId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Determine if loading spinner should show (for all session types)
    val showLoadingSpinner = aiState.phase in listOf(
        Phase.CALLING_AI,
        Phase.EXECUTING_ENRICHMENTS,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.PARSING_AI_RESPONSE,
        Phase.WAITING_NETWORK_RETRY
    )

    // Determine if interrupt button should show (CHAT only, for long AI calls)
    val showInterruptButton = aiState.sessionType == SessionType.CHAT && aiState.phase in listOf(
        Phase.CALLING_AI,
        Phase.WAITING_NETWORK_RETRY
    )

    // Find last AI message index for inline validation/communication display
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
                    aiState = aiState,
                    isLastAIMessage = index == lastAIMessageIndex
                )
            }

            // AI thinking indicator (spinner + optional interrupt button)
            if (showLoadingSpinner) {
                item {
                    com.assistant.core.ai.ui.chat.AILoadingSpinner()
                }
            }

            if (showInterruptButton) {
                item {
                    com.assistant.core.ai.ui.chat.ChatInterruptButton()
                }
            }
        }
    }
}

