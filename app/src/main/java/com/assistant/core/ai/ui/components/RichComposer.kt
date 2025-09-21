package com.assistant.core.ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.*
import com.assistant.core.ai.enrichments.EnrichmentSummarizer
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

        // Enrichment buttons (if enabled)
        if (showEnrichmentButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                enrichmentTypes.forEach { type ->
                    UI.ActionButton(
                        action = getEnrichmentButtonAction(type),
                        display = ButtonDisplay.ICON,
                        size = Size.M,
                        onClick = { showEnrichmentDialog = type }
                    )
                }
            }
        }

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

        // Send button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            UI.ActionButton(
                action = ButtonAction.CONFIRM, // Use CONFIRM for send action
                onClick = {
                    LogManager.aiEnrichment("RichComposer Send button clicked with ${segments.size} segments")
                    val richMessage = createRichMessage(segments, sessionType)
                    LogManager.aiEnrichment("Calling onSend with RichMessage: linearText='${richMessage.linearText}', ${richMessage.dataQueries.size} queries")
                    onSend(richMessage)
                }
            )
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

                // Use EnrichmentSummarizer to generate proper summary if preview is empty or generic
                val enrichmentSummarizer = EnrichmentSummarizer()
                val finalPreview = if (preview.isBlank() || preview == "${getEnrichmentIcon(type)} Configuration") {
                    LogManager.aiEnrichment("Using EnrichmentSummarizer to generate preview for $type")
                    enrichmentSummarizer.generateSummary(type, config)
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
 * Create RichMessage from segments with computed linearText and dataQueries
 * Uses EnrichmentSummarizer for proper query generation according to specs
 */
private fun createRichMessage(segments: List<MessageSegment>, sessionType: SessionType = SessionType.CHAT): RichMessage {
    LogManager.aiEnrichment("RichComposer.createRichMessage() called with ${segments.size} segments, sessionType=$sessionType")

    val enrichmentSummarizer = EnrichmentSummarizer()

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
    LogManager.aiEnrichment("Found ${enrichmentBlocks.size} enrichment blocks to process")

    // Compute dataQueries from enrichment blocks using EnrichmentSummarizer
    val dataQueries = enrichmentBlocks.flatMapIndexed { index, block ->
        LogManager.aiEnrichment("Processing enrichment block $index: type=${block.type}, preview='${block.preview}'")

        try {
            // Use EnrichmentSummarizer to check if block should generate queries and create them
            val isRelative = (sessionType == SessionType.AUTOMATION)
            LogManager.aiEnrichment("Calling EnrichmentSummarizer for block $index with isRelative=$isRelative")

            val queries = enrichmentSummarizer.generateQueries(block.type, block.config, isRelative)
            LogManager.aiEnrichment("Block $index generated ${queries.size} queries")
            queries.forEach { query ->
                LogManager.aiEnrichment("  - Query: ${query.id} (type: ${query.type})")
            }
            queries
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to generate queries for enrichment block $index: ${e.message}", "ERROR", e)
            emptyList() // Skip invalid configs
        }
    }

    LogManager.aiEnrichment("Generated ${dataQueries.size} DataQueries from ${enrichmentBlocks.size} enrichment blocks")
    dataQueries.forEachIndexed { index, query ->
        LogManager.aiEnrichment("DataQuery $index: id='${query.id}', type='${query.type}', isRelative=${query.isRelative}")
    }

    val richMessage = RichMessage(
        segments = segments,
        linearText = linearText,
        dataQueries = dataQueries
    )

    LogManager.aiEnrichment("Created RichMessage with linearText='$linearText' and ${dataQueries.size} queries")
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
                allowFieldSelection = true,
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
        UI.Dialog(
            type = DialogType.CONFIGURE,
            onConfirm = {
                selectionResult?.let { result ->
                    val config = createPointerConfig(result, importance, timestampSelection, description)
                    val preview = createPointerPreview(result, timestampSelection, importance)
                    onConfirm(config, preview)
                }
            },
            onCancel = onDismiss
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = s.shared("pointer_enrichment_config"),
                    type = TextType.TITLE
                )

                // Show selected path with human-readable labels
                selectionResult?.let { result ->
                    UI.Text(
                        text = if (result.displayChain.isNotEmpty()) {
                            "Sélection: ${result.displayChain.joinToString(" → ")}"
                        } else {
                            "Sélection: ${result.selectedPath}"
                        },
                        type = TextType.BODY
                    )
                }

                // Importance selector
                UI.FormSelection(
                    label = "Importance",
                    options = listOf("optionnel", "important", "essentiel"),
                    selected = importance,
                    onSelect = { importance = it }
                )

                // Timestamp selection section
                PeriodRangeSelector(
                    startPeriodType = timestampSelection.minPeriodType,
                    startPeriod = timestampSelection.minPeriod,
                    startCustomDate = timestampSelection.minCustomDateTime,
                    endPeriodType = timestampSelection.maxPeriodType,
                    endPeriod = timestampSelection.maxPeriod,
                    endCustomDate = timestampSelection.maxCustomDateTime,
                    onStartTypeChange = { newType ->
                        timestampSelection = timestampSelection.copy(minPeriodType = newType)
                    },
                    onStartPeriodChange = { newPeriod ->
                        timestampSelection = timestampSelection.copy(minPeriod = newPeriod)
                    },
                    onStartCustomDateChange = { newDate ->
                        timestampSelection = timestampSelection.copy(minCustomDateTime = newDate)
                    },
                    onEndTypeChange = { newType ->
                        timestampSelection = timestampSelection.copy(maxPeriodType = newType)
                    },
                    onEndPeriodChange = { newPeriod ->
                        timestampSelection = timestampSelection.copy(maxPeriod = newPeriod)
                    },
                    onEndCustomDateChange = { newDate ->
                        timestampSelection = timestampSelection.copy(maxCustomDateTime = newDate)
                    },
                    useOnlyRelativeLabels = useRelativeLabels
                )

                // Description field
                UI.FormField(
                    label = "Description (optionnel)",
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
                text = "TODO: Implement ${type.name} configuration UI",
                type = TextType.BODY
            )

            UI.FormField(
                label = "Preview",
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
    description: String
): String {
    return JSONObject().apply {
        put("selectedPath", selectionResult.selectedPath)
        put("selectedValues", selectionResult.selectedValues)
        put("selectionLevel", selectionResult.selectionLevel.name)
        put("importance", importance)
        if (timestampSelection.isComplete) {
            put("timestampSelection", JSONObject().apply {
                timestampSelection.minPeriodType?.let { put("minPeriodType", it.name) }
                timestampSelection.minPeriod?.let { put("minTimestamp", it.timestamp) }
                timestampSelection.minCustomDateTime?.let { put("minCustomDateTime", it) }
                timestampSelection.maxPeriodType?.let { put("maxPeriodType", it.name) }
                timestampSelection.maxPeriod?.let { put("maxTimestamp", it.timestamp) }
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
    selectionResult: SelectionResult,
    timestampSelection: TimestampSelection,
    importance: String
): String {
    val baseText = when (selectionResult.selectionLevel.name) {
        "ZONE" -> "données zone"
        "INSTANCE" -> "données outil"
        "FIELD" -> "champ données"
        else -> "données"
    }

    val pathParts = selectionResult.selectedPath.split("/").filter { it.isNotBlank() }
    val displayName = pathParts.lastOrNull() ?: "sélection"

    val périodeText = if (timestampSelection.isComplete) {
        " (période filtrée)"
    } else {
        ""
    }

    val importanceIcon = when (importance) {
        "optionnel" -> "⚪"
        "important" -> "🔵"
        "essentiel" -> "🔴"
        else -> ""
    }

    return "$importanceIcon $baseText $displayName$périodeText"
}