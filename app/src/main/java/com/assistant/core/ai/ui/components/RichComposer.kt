package com.assistant.core.ai.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.*
import com.assistant.core.ai.enrichments.EnrichmentProcessor
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.ui.selectors.ZoneScopeSelector
import com.assistant.core.ui.components.PeriodRangeSelector
import com.assistant.core.ui.selectors.data.NavigationConfig
import com.assistant.core.ui.selectors.data.SelectionResult
import com.assistant.core.ui.selectors.data.FieldSpecificData
import com.assistant.core.ui.selectors.data.TimestampSelection
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.Period
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * RichComposer component following UI.* pattern
 * Simple UI with textarea + enrichment blocks list
 * But implements full inline logic for MessageSegment handling
 */
@Composable
fun UI.RichComposer(
    segments: List<MessageSegment>,
    onSegmentsChange: (List<MessageSegment>) -> Unit,
    onSend: (RichMessage) -> Unit,
    placeholder: String = "",
    showEnrichmentButtons: Boolean = true,
    showSendButton: Boolean = true,
    enabled: Boolean = true,
    enrichmentTypes: List<EnrichmentType> = EnrichmentType.values().toList(),
    modifier: Modifier = Modifier,
    sessionType: SessionType = SessionType.CHAT
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Extract text from segments for textarea
    val textContent = segments.filterIsInstance<MessageSegment.Text>()
        .joinToString("") { it.content }

    // Extract enrichment blocks
    val enrichmentBlocks = segments.filterIsInstance<MessageSegment.EnrichmentBlock>()

    var showEnrichmentDialog by remember { mutableStateOf<EnrichmentType?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Main textarea
        UI.FormField(
            label = placeholder.ifEmpty { s.shared("ai_composer_placeholder") },
            value = textContent,
            onChange = { newText ->
                // Update only text segments, keep enrichments
                val newSegments = if (newText.isEmpty()) {
                    enrichmentBlocks
                } else {
                    listOf(MessageSegment.Text(newText)) + enrichmentBlocks
                }
                onSegmentsChange(newSegments)
            },
            fieldType = FieldType.TEXT_LONG
        )

        // Enrichment blocks list
        if (enrichmentBlocks.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                enrichmentBlocks.forEach { block ->
                    EnrichmentBlockPreview(
                        block = block,
                        onEdit = { showEnrichmentDialog = block.type },
                        onRemove = {
                            val newSegments = segments.filter { it != block }
                            onSegmentsChange(newSegments)
                        }
                    )
                }
            }
        }

        // Enrichment buttons + Send button on same line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enrichment buttons (if enabled)
            if (showEnrichmentButtons) {
                enrichmentTypes.forEach { type ->
                    UI.ActionButton(
                        action = getEnrichmentButtonAction(type),
                        display = ButtonDisplay.ICON,
                        size = Size.M,
                        onClick = { showEnrichmentDialog = type }
                    )
                }
            }

            // Spacer to push send button to the right
            Spacer(modifier = Modifier.weight(1f))

            // Send button (conditionally shown and enabled)
            if (showSendButton) {
                UI.ActionButton(
                    action = ButtonAction.CONFIRM, // Use CONFIRM for send action
                    enabled = enabled,
                    onClick = {
                        LogManager.aiEnrichment("RichComposer Send button clicked with ${segments.size} segments")
                        val richMessage = createRichMessage(context, segments, sessionType)
                        LogManager.aiEnrichment("Calling onSend with RichMessage: linearText='${richMessage.linearText}', ${richMessage.dataCommands.size} commands")
                        onSend(richMessage)
                    }
                )
            }
        }
    }

    // Enrichment configuration dialog
    showEnrichmentDialog?.let { type ->
        EnrichmentConfigDialog(
            type = type,
            existingConfig = null, // TODO: Handle editing existing blocks
            onDismiss = { showEnrichmentDialog = null },
            onConfirm = { config, preview ->
                LogManager.aiEnrichment("RichComposer enrichment configured: type=$type, config length=${config.length}, preview='$preview'")

                // Use EnrichmentProcessor to generate proper summary if preview is empty or generic
                val enrichmentProcessor = EnrichmentProcessor(context)
                val finalPreview = if (preview.isBlank() || preview == "${getEnrichmentIcon(type)} Configuration") {
                    LogManager.aiEnrichment("Using EnrichmentProcessor to generate preview for $type")
                    enrichmentProcessor.generateSummary(type, config)
                } else {
                    LogManager.aiEnrichment("Using provided preview for $type: '$preview'")
                    preview
                }

                val newBlock = MessageSegment.EnrichmentBlock(
                    type = type,
                    config = config,
                    preview = finalPreview
                )

                LogManager.aiEnrichment("Created EnrichmentBlock: type=$type, preview='$finalPreview', config='$config'")

                val newSegments = segments + newBlock
                LogManager.aiEnrichment("Updated segments count: ${segments.size} -> ${newSegments.size}")
                onSegmentsChange(newSegments)
                showEnrichmentDialog = null
            },
            sessionType = sessionType
        )
    }
}

/**
 * Preview component for enrichment blocks
 */
@Composable
private fun EnrichmentBlockPreview(
    block: MessageSegment.EnrichmentBlock,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    UI.Card(type = CardType.DEFAULT) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = getEnrichmentIcon(block.type),
                    type = TextType.BODY
                )
                UI.Text(
                    text = block.preview,
                    type = TextType.BODY
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onEdit
                )
                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onRemove
                )
            }
        }
    }
}

/**
 * Create RichMessage from segments with computed linearText
 * DataCommands are left empty and will be regenerated by AIEventProcessor during execution
 */
private fun createRichMessage(context: Context, segments: List<MessageSegment>, sessionType: SessionType = SessionType.CHAT): RichMessage {
    LogManager.aiEnrichment("RichComposer.createRichMessage() called with ${segments.size} segments, sessionType=$sessionType")

    // Compute linearText by joining all content
    val linearText = segments.joinToString(" ") { segment ->
        when (segment) {
            is MessageSegment.Text -> segment.content
            is MessageSegment.EnrichmentBlock -> segment.preview
        }
    }.trim()

    LogManager.aiEnrichment("Generated linearText: '$linearText'")

    // Count enrichment blocks for logging
    val enrichmentBlocks = segments.filterIsInstance<MessageSegment.EnrichmentBlock>()
    LogManager.aiEnrichment("Found ${enrichmentBlocks.size} enrichment blocks in message")

    // DataCommands are always regenerated during prompt building by AIEventProcessor
    // No need to generate them here for preview - they will be computed with fresh data and resolved schema IDs
    val dataCommands = emptyList<DataCommand>()

    val richMessage = RichMessage(
        segments = segments,
        linearText = linearText,
        dataCommands = dataCommands
    )

    LogManager.aiEnrichment("Created RichMessage with linearText='$linearText', enrichments=${enrichmentBlocks.size}")
    return richMessage
}



/**
 * Get button action for enrichment type
 */
private fun getEnrichmentButtonAction(type: EnrichmentType): ButtonAction {
    return when (type) {
        EnrichmentType.POINTER -> ButtonAction.SELECT // 🔍
        EnrichmentType.USE -> ButtonAction.EDIT       // 📝
        EnrichmentType.CREATE -> ButtonAction.ADD     // ✨
        EnrichmentType.MODIFY_CONFIG -> ButtonAction.CONFIGURE // 🔧
    }
}

/**
 * Get icon for enrichment type
 */
private fun getEnrichmentIcon(type: EnrichmentType): String {
    return when (type) {
        EnrichmentType.POINTER -> "🔍"
        EnrichmentType.USE -> "📝"
        EnrichmentType.CREATE -> "✨"
        EnrichmentType.MODIFY_CONFIG -> "🔧"
    }
}

/**
 * Enrichment configuration dialog with specific UI for each enrichment type
 */
@Composable
private fun EnrichmentConfigDialog(
    type: EnrichmentType,
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String, preview: String) -> Unit,
    sessionType: SessionType = SessionType.CHAT
) {
    when (type) {
        EnrichmentType.POINTER -> {
            PointerEnrichmentDialog(
                existingConfig = existingConfig,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                sessionType = sessionType
            )
        }
        else -> {
            // Placeholder for other enrichment types
            PlaceholderEnrichmentDialog(
                type = type,
                existingConfig = existingConfig,
                onDismiss = onDismiss,
                onConfirm = onConfirm
            )
        }
    }
}

/**
 * Specific dialog for POINTER enrichment with ZoneScopeSelector
 */
@Composable
private fun PointerEnrichmentDialog(
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String, preview: String) -> Unit,
    sessionType: SessionType = SessionType.CHAT
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var showZoneScopeSelector by remember { mutableStateOf(true) }
    var selectionResult by remember { mutableStateOf<SelectionResult?>(null) }

    // Additional fields
    var importance by remember { mutableStateOf("important") }
    var timestampSelection by remember { mutableStateOf(TimestampSelection()) }
    var description by remember { mutableStateOf("") }
    var includeData by remember { mutableStateOf(false) }  // Toggle for real data inclusion

    // App configuration for timestamp handling
    var dayStartHour by remember { mutableStateOf(0) }
    var weekStartDay by remember { mutableStateOf("monday") }

    // Load app configuration
    LaunchedEffect(Unit) {
        try {
            val coordinator = Coordinator(context)
            val configResult = coordinator.processUserAction("app_config.get", emptyMap())
            if (configResult.isSuccess) {
                val config = configResult.data?.get("settings") as? Map<String, Any>
                dayStartHour = (config?.get("day_start_hour") as? Number)?.toInt() ?: 0
                weekStartDay = config?.get("week_start_day") as? String ?: "monday"
            }
        } catch (e: Exception) {
            // Use defaults if config loading fails
            dayStartHour = 0
            weekStartDay = "monday"
        }
    }

    if (showZoneScopeSelector) {
        ZoneScopeSelector(
            config = NavigationConfig(
                allowZoneSelection = true,
                allowInstanceSelection = true,
                allowFieldSelection = false,  // Limited to instance level only
                allowValueSelection = false,
                title = s.shared("pointer_enrichment_selector_title")
            ),
            onDismiss = onDismiss,
            onConfirm = { result ->
                selectionResult = result
                showZoneScopeSelector = false
            },
            useOnlyRelativeLabels = (sessionType == SessionType.AUTOMATION)
        )
    } else {
        // Additional configuration fields after selection
        val useRelativeLabels = (sessionType == SessionType.AUTOMATION)
        val scrollState = rememberScrollState()

        UI.Dialog(
            type = DialogType.CONFIGURE,
            onConfirm = {
                selectionResult?.let { result ->
                    val config = createPointerConfig(result, importance, timestampSelection, description, includeData)
                    val preview = createPointerPreview(context, result, timestampSelection, importance)
                    onConfirm(config, preview)
                }
            },
            onCancel = onDismiss
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = s.shared("pointer_enrichment_config"),
                    type = TextType.TITLE
                )

                // Show selected path with human-readable labels and change button
                selectionResult?.let { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            UI.Text(
                                text = if (result.displayChain.isNotEmpty()) {
                                    "${s.shared("ai_enrichment_selection_label")} ${result.displayChain.joinToString(" → ")}"
                                } else {
                                    "${s.shared("ai_enrichment_selection_label")} ${result.selectedPath}"
                                },
                                type = TextType.BODY
                            )
                        }
                        UI.ActionButton(
                            action = ButtonAction.EDIT,
                            display = ButtonDisplay.ICON,
                            onClick = {
                                showZoneScopeSelector = true
                            }
                        )
                    }
                }

                // Importance selector
                val importanceOptions = listOf("optional", "important", "essential")
                val importanceLabels = listOf(
                    s.shared("ai_importance_optional"),
                    s.shared("ai_importance_important"),
                    s.shared("ai_importance_essential")
                )

                UI.FormSelection(
                    label = s.shared("ai_enrichment_importance"),
                    options = importanceLabels,
                    selected = importanceLabels[importanceOptions.indexOf(importance)],
                    onSelect = { selectedLabel ->
                        importance = importanceOptions[importanceLabels.indexOf(selectedLabel)]
                    }
                )

                // Include data toggle (only visible for instance selections)
                selectionResult?.let { result ->
                    if (result.selectionLevel.name == "INSTANCE") {
                        UI.ToggleField(
                            label = s.shared("ai_enrichment_include_real_data"),
                            checked = includeData,
                            onCheckedChange = { checked ->
                                includeData = checked
                                // TODO: Recalculate token count when filters change (if toggle enabled)
                                // Should trigger token estimation based on current timestamp filters
                                // and show warning/confirmation if exceeding limits
                            }
                        )
                    }
                }

                // Timestamp selection section
                PeriodRangeSelector(
                    startPeriodType = timestampSelection.minPeriodType,
                    startPeriod = timestampSelection.minPeriod,
                    startCustomDate = timestampSelection.minCustomDateTime,
                    endPeriodType = timestampSelection.maxPeriodType,
                    endPeriod = timestampSelection.maxPeriod,
                    endCustomDate = timestampSelection.maxCustomDateTime,
                    onStartTypeChange = { newType ->
                        // Clear custom date when switching to period type, clear period/relativePeriod when switching to custom
                        timestampSelection = if (newType != null) {
                            timestampSelection.copy(minPeriodType = newType, minCustomDateTime = null)
                        } else {
                            timestampSelection.copy(minPeriodType = null, minPeriod = null, minRelativePeriod = null)
                        }
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    onStartPeriodChange = { newPeriod ->
                        timestampSelection = timestampSelection.copy(minPeriod = newPeriod, minRelativePeriod = null)
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    onStartCustomDateChange = { newDate ->
                        timestampSelection = timestampSelection.copy(minCustomDateTime = newDate)
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    onEndTypeChange = { newType ->
                        // Clear custom date when switching to period type, clear period/relativePeriod when switching to custom
                        timestampSelection = if (newType != null) {
                            timestampSelection.copy(maxPeriodType = newType, maxCustomDateTime = null)
                        } else {
                            timestampSelection.copy(maxPeriodType = null, maxPeriod = null, maxRelativePeriod = null)
                        }
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    onEndPeriodChange = { newPeriod ->
                        timestampSelection = timestampSelection.copy(maxPeriod = newPeriod, maxRelativePeriod = null)
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    onEndCustomDateChange = { newDate ->
                        timestampSelection = timestampSelection.copy(maxCustomDateTime = newDate)
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    useOnlyRelativeLabels = useRelativeLabels,
                    returnRelative = (sessionType == SessionType.AUTOMATION),
                    startRelativePeriod = timestampSelection.minRelativePeriod,
                    endRelativePeriod = timestampSelection.maxRelativePeriod,
                    onStartRelativePeriodChange = { newRelativePeriod ->
                        timestampSelection = timestampSelection.copy(minRelativePeriod = newRelativePeriod, minPeriod = null)
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    },
                    onEndRelativePeriodChange = { newRelativePeriod ->
                        timestampSelection = timestampSelection.copy(maxRelativePeriod = newRelativePeriod, maxPeriod = null)
                        // TODO: Recalculate token count when filters change (if includeData toggle enabled)
                    }
                )

                // Description field
                UI.FormField(
                    label = s.shared("ai_enrichment_description_optional"),
                    value = description,
                    onChange = { description = it },
                    fieldType = FieldType.TEXT_MEDIUM
                )

                // Button to go back to selector
                UI.ActionButton(
                    action = ButtonAction.BACK,
                    onClick = { showZoneScopeSelector = true }
                )
            }
        }
    }
}

/**
 * Placeholder dialog for other enrichment types
 */
@Composable
private fun PlaceholderEnrichmentDialog(
    type: EnrichmentType,
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String, preview: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var config by remember { mutableStateOf(existingConfig ?: "{}") }
    var preview by remember { mutableStateOf("${getEnrichmentIcon(type)} Configuration") }

    UI.Dialog(
        type = DialogType.CONFIGURE,
        onConfirm = {
            onConfirm(config, preview)
        },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(
                text = s.shared("ai_enrichment_config"),
                type = TextType.TITLE
            )

            UI.Text(
                text = s.shared("ai_enrichment_todo_implement").format(type.name),
                type = TextType.BODY
            )

            UI.FormField(
                label = s.shared("label_preview"),
                value = preview,
                onChange = { preview = it },
                fieldType = FieldType.TEXT
            )
        }
    }
}

/**
 * Create JSON config from POINTER enrichment data
 */
private fun createPointerConfig(
    selectionResult: SelectionResult,
    importance: String,
    timestampSelection: TimestampSelection,
    description: String,
    includeData: Boolean = false
): String {
    return JSONObject().apply {
        put("selectedPath", selectionResult.selectedPath)
        put("selectedValues", selectionResult.selectedValues)
        put("selectionLevel", selectionResult.selectionLevel.name)
        put("importance", importance)
        put("includeData", includeData)  // Toggle for real data inclusion
        if (timestampSelection.isComplete) {
            put("timestampSelection", JSONObject().apply {
                // Store min period (start of range)
                timestampSelection.minPeriodType?.let { put("minPeriodType", it.name) }

                // Absolute period (CHAT)
                timestampSelection.minPeriod?.let { period ->
                    put("minPeriod", JSONObject().apply {
                        put("timestamp", period.timestamp)
                        put("type", period.type.name)
                    })
                }
                // Relative period (AUTOMATION)
                timestampSelection.minRelativePeriod?.let { relativePeriod ->
                    put("minRelativePeriod", JSONObject().apply {
                        put("offset", relativePeriod.offset)
                        put("type", relativePeriod.type.name)
                    })
                }
                // Custom date (always absolute)
                timestampSelection.minCustomDateTime?.let { put("minCustomDateTime", it) }

                // Store max period (end of range)
                timestampSelection.maxPeriodType?.let { put("maxPeriodType", it.name) }

                // Absolute period (CHAT)
                timestampSelection.maxPeriod?.let { period ->
                    put("maxPeriod", JSONObject().apply {
                        put("timestamp", period.timestamp)
                        put("type", period.type.name)
                    })
                }
                // Relative period (AUTOMATION)
                timestampSelection.maxRelativePeriod?.let { relativePeriod ->
                    put("maxRelativePeriod", JSONObject().apply {
                        put("offset", relativePeriod.offset)
                        put("type", relativePeriod.type.name)
                    })
                }
                // Custom date (always absolute)
                timestampSelection.maxCustomDateTime?.let { put("maxCustomDateTime", it) }
            })
        }
        if (description.isNotBlank()) put("description", description)

        // Add field-specific data if present
        selectionResult.fieldSpecificData?.let { fieldData ->
            put("fieldSpecificData", JSONObject().apply {
                when (fieldData) {
                    is FieldSpecificData.TimestampData -> {
                        put("type", "timestamp")
                        put("minTimestamp", fieldData.minTimestamp)
                        put("maxTimestamp", fieldData.maxTimestamp)
                        put("description", fieldData.description)
                    }
                    is FieldSpecificData.NameData -> {
                        put("type", "name")
                        put("selectedNames", fieldData.selectedNames)
                        put("availableNames", fieldData.availableNames)
                    }
                    is FieldSpecificData.DataValues -> {
                        put("type", "data")
                        put("values", fieldData.values)
                    }
                }
            })
        }
    }.toString()
}

/**
 * Create human-readable preview from POINTER enrichment data
 */
private fun createPointerPreview(
    context: Context,
    selectionResult: SelectionResult,
    timestampSelection: TimestampSelection,
    importance: String
): String {
    val s = Strings.`for`(context = context)

    val baseText = when (selectionResult.selectionLevel.name) {
        "ZONE" -> s.shared("ai_data_zone")
        "INSTANCE" -> s.shared("ai_data_tool")
        "FIELD" -> s.shared("ai_data_field")
        else -> s.shared("ai_data_generic")
    }

    val pathParts = selectionResult.selectedPath.split("/").filter { it.isNotBlank() }
    val displayName = pathParts.lastOrNull() ?: "sélection"

    val périodeText = if (timestampSelection.isComplete) {
        " (${s.shared("ai_period_filtered")})"
    } else {
        ""
    }

    // Match importance parameter (not localized - internal value)
    val importanceIcon = when (importance) {
        "optional" -> "⚪"
        "essential" -> "🔴"
        "important" -> "🔵"
        else -> ""
    }

    return "$importanceIcon $baseText $displayName$périodeText"
}