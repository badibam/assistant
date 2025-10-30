package com.assistant.core.ai.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import java.util.UUID
import androidx.compose.ui.draw.alpha

/**
 * TextBlock: represents one text segment with its associated enrichments
 * Each block is an independent unit that can be edited, deleted, or reordered
 */
data class TextBlock(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val enrichments: List<MessageSegment.EnrichmentBlock> = emptyList()
) {
    /**
     * Convert this block to MessageSegments for final message composition
     */
    fun toSegments(): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()
        if (text.isNotEmpty()) {
            segments.add(MessageSegment.Text(text))
        }
        segments.addAll(enrichments)
        return segments
    }
}

/**
 * Convert MessageSegments to TextBlocks for editing
 * Groups consecutive Text and EnrichmentBlock segments into TextBlocks
 */
private fun segmentsToBlocks(segments: List<MessageSegment>): List<TextBlock> {
    if (segments.isEmpty()) {
        return listOf(TextBlock()) // At least one empty block
    }

    val blocks = mutableListOf<TextBlock>()
    var currentText = ""
    val currentEnrichments = mutableListOf<MessageSegment.EnrichmentBlock>()

    for (segment in segments) {
        when (segment) {
            is MessageSegment.Text -> {
                // Start new block if we have accumulated content
                if (currentText.isNotEmpty() || currentEnrichments.isNotEmpty()) {
                    blocks.add(TextBlock(
                        text = currentText,
                        enrichments = currentEnrichments.toList()
                    ))
                    currentEnrichments.clear()
                }
                currentText = segment.content
            }
            is MessageSegment.EnrichmentBlock -> {
                currentEnrichments.add(segment)
            }
        }
    }

    // Add final block
    if (currentText.isNotEmpty() || currentEnrichments.isNotEmpty()) {
        blocks.add(TextBlock(
            text = currentText,
            enrichments = currentEnrichments.toList()
        ))
    }

    // Ensure at least one block exists
    if (blocks.isEmpty()) {
        blocks.add(TextBlock())
    }

    return blocks
}

/**
 * Convert TextBlocks back to MessageSegments
 */
private fun blocksToSegments(blocks: List<TextBlock>): List<MessageSegment> {
    return blocks.flatMap { it.toSegments() }
}

/**
 * RichComposer component with multi-block support
 *
 * Architecture:
 * - Multiple TextBlocks (text + enrichments)
 * - One active block at a time (focus-based + clickable)
 * - Global enrichment buttons act on active block
 * - Visual highlight on active block
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
    sessionType: SessionType = SessionType.CHAT,
    statusContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxBlocksHeight = screenHeight / 3
    val keyboardController = LocalSoftwareKeyboardController.current

    // Convert segments to blocks for editing (initialize once, then manage locally)
    var blocks by remember {
        mutableStateOf(segmentsToBlocks(segments))
    }

    // Sync from parent only when segments change externally (not from our own updates)
    var lastSyncedSegments by remember { mutableStateOf(segments) }
    LaunchedEffect(segments) {
        // Only update if segments changed externally (not from our updateSegments call)
        if (segments != lastSyncedSegments && blocksToSegments(blocks) != segments) {
            blocks = segmentsToBlocks(segments)
        }
        // Always keep lastSyncedSegments in sync to avoid stale state
        lastSyncedSegments = segments
    }

    // Track active block ID
    var activeBlockId by remember { mutableStateOf(blocks.firstOrNull()?.id ?: "") }

    // Enrichment dialog state
    var showEnrichmentDialog by remember { mutableStateOf<EnrichmentDialogState?>(null) }

    // Update parent when blocks change
    val updateSegments = {
        val newSegments = blocksToSegments(blocks)
        onSegmentsChange(newSegments)
    }

    val blocksScrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Controls row: Status (if provided) + Send button
        // Placed at top so it stays visible when keyboard appears
        if (showSendButton || statusContent != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status content on the left (if provided)
                if (statusContent != null) {
                    Box(modifier = Modifier.weight(1f)) {
                        statusContent()
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Send button on the right
                if (showSendButton) {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        size = Size.M,
                        state = if (enabled) ComponentState.NORMAL else ComponentState.DISABLED,
                        onClick = {
                            // Hide keyboard when sending message
                            keyboardController?.hide()

                            LogManager.aiEnrichment("RichComposer Send button clicked with ${blocks.size} blocks")
                            val richMessage = createRichMessage(context, blocksToSegments(blocks), sessionType)
                            LogManager.aiEnrichment("Calling onSend with RichMessage: linearText='${richMessage.linearText}', ${richMessage.dataCommands.size} commands")
                            onSend(richMessage)
                        }
                    ) {
                        UI.Text(
                            text = s.shared("action_send"),
                            type = TextType.BODY
                        )
                    }
                }
            }
        }

        // Blocks area with scroll and max height (1/3 screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxBlocksHeight)
                .verticalScroll(blocksScrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            blocks.forEach { block ->
                TextBlockCard(
                block = block,
                isActive = (block.id == activeBlockId),
                placeholder = if (blocks.size == 1 && block.text.isEmpty()) {
                    placeholder.ifEmpty { s.shared("ai_composer_placeholder") }
                } else "",
                onActivate = { activeBlockId = block.id },
                onTextChange = { newText ->
                    blocks = blocks.map {
                        if (it.id == block.id) it.copy(text = newText) else it
                    }
                    updateSegments()
                },
                onEnrichmentEdit = { enrichment ->
                    // Open dialog for editing this enrichment
                    showEnrichmentDialog = EnrichmentDialogState(
                        blockId = block.id,
                        type = enrichment.type,
                        existingConfig = enrichment.config,
                        existingPreview = enrichment.preview
                    )
                },
                onEnrichmentRemove = { enrichment ->
                    blocks = blocks.map {
                        if (it.id == block.id) {
                            it.copy(enrichments = it.enrichments.filter { e -> e != enrichment })
                        } else it
                    }
                    updateSegments()
                },
                onDeleteBlock = {
                    // Remove this block (if not the last one)
                    if (blocks.size > 1) {
                        val index = blocks.indexOfFirst { it.id == block.id }
                        blocks = blocks.filter { it.id != block.id }

                        // Set active to previous block or first if deleting first
                        activeBlockId = if (index > 0) {
                            blocks[index - 1].id
                        } else {
                            blocks.firstOrNull()?.id ?: ""
                        }
                        updateSegments()
                    } else {
                        // Last block: clear it instead of deleting
                        blocks = listOf(TextBlock())
                        activeBlockId = blocks.first().id
                        updateSegments()
                    }
                }
            )
            }
        }

        // Controls row: Enrichment buttons + Add Text
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
                        onClick = {
                            showEnrichmentDialog = EnrichmentDialogState(
                                blockId = activeBlockId,
                                type = type,
                                existingConfig = null,
                                existingPreview = null
                            )
                        }
                    )
                }
            }

            // Add Text button (creates new block)
            UI.Button(
                type = ButtonType.DEFAULT,
                size = Size.M,
                onClick = {
                    val newBlock = TextBlock()
                    blocks = blocks + newBlock
                    activeBlockId = newBlock.id
                    updateSegments()
                }
            ) {
                UI.Text(
                    text = "+ ${s.shared("ai_composer_add_text")}",
                    type = TextType.BODY
                )
            }
        }
    }

    // Enrichment configuration dialog
    showEnrichmentDialog?.let { dialogState ->
        EnrichmentConfigDialog(
            type = dialogState.type,
            existingConfig = dialogState.existingConfig,
            onDismiss = { showEnrichmentDialog = null },
            onConfirm = { config, uiPreview, promptPreview ->
                LogManager.aiEnrichment("RichComposer enrichment configured: type=${dialogState.type}, config length=${config.length}, uiPreview='$uiPreview', promptPreview='$promptPreview'")

                // Use EnrichmentProcessor to generate proper summary if preview is empty or generic
                val enrichmentProcessor = EnrichmentProcessor(context)
                val finalUiPreview = if (uiPreview.isBlank() || uiPreview == "${getEnrichmentIcon(dialogState.type)} Configuration") {
                    LogManager.aiEnrichment("Using EnrichmentProcessor to generate preview for ${dialogState.type}")
                    enrichmentProcessor.generateSummary(dialogState.type, config)
                } else {
                    LogManager.aiEnrichment("Using provided uiPreview for ${dialogState.type}: '$uiPreview'")
                    uiPreview
                }

                val finalPromptPreview = if (promptPreview.isBlank() || promptPreview == "${getEnrichmentIcon(dialogState.type)} Configuration") {
                    LogManager.aiEnrichment("Using EnrichmentProcessor to generate promptPreview for ${dialogState.type}")
                    enrichmentProcessor.generateSummary(dialogState.type, config)
                } else {
                    LogManager.aiEnrichment("Using provided promptPreview for ${dialogState.type}: '$promptPreview'")
                    promptPreview
                }

                val newEnrichment = MessageSegment.EnrichmentBlock(
                    type = dialogState.type,
                    config = config,
                    preview = finalUiPreview,
                    promptPreview = finalPromptPreview
                )

                LogManager.aiEnrichment("Created EnrichmentBlock: type=${dialogState.type}, uiPreview='$finalUiPreview', promptPreview='$finalPromptPreview'")

                // Add or update enrichment in the target block
                blocks = blocks.map { block ->
                    if (block.id == dialogState.blockId) {
                        // If editing, replace existing; if new, add
                        val enrichments = if (dialogState.existingConfig != null) {
                            // Replace enrichment with same type and config
                            block.enrichments.map { e ->
                                if (e.type == dialogState.type && e.config == dialogState.existingConfig) {
                                    newEnrichment
                                } else e
                            }
                        } else {
                            // Add new enrichment
                            block.enrichments + newEnrichment
                        }
                        LogManager.aiEnrichment("Block ${block.id} now has ${enrichments.size} enrichments")
                        block.copy(enrichments = enrichments)
                    } else block
                }

                // Log all blocks state
                LogManager.aiEnrichment("Total blocks after enrichment: ${blocks.size}, enrichments count: ${blocks.map { it.enrichments.size }}")

                updateSegments()
                showEnrichmentDialog = null
            },
            sessionType = sessionType
        )
    }
}

/**
 * State for enrichment dialog
 */
private data class EnrichmentDialogState(
    val blockId: String,
    val type: EnrichmentType,
    val existingConfig: String?,
    val existingPreview: String?
)

/**
 * Card component for one text block
 * Shows text field + enrichments + controls
 */
@Composable
private fun TextBlockCard(
    block: TextBlock,
    isActive: Boolean,
    placeholder: String,
    onActivate: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnrichmentEdit: (MessageSegment.EnrichmentBlock) -> Unit,
    onEnrichmentRemove: (MessageSegment.EnrichmentBlock) -> Unit,
    onDeleteBlock: () -> Unit
) {
    val s = Strings.`for`(context = LocalContext.current)

    // Debug log
    LogManager.aiEnrichment("TextBlockCard rendering: block ${block.id}, enrichments: ${block.enrichments.size}")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() }
    ) {
        UI.Card(
            type = CardType.DEFAULT,
            highlight = isActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Text field (delete button is positioned absolute)
                UI.FormField(
                    label = placeholder,
                    value = block.text,
                    onChange = { newText ->
                        onTextChange(newText)
                        onActivate() // Activate on typing
                    },
                    fieldType = FieldType.TEXT_UNLIMITED,
                    fieldModifier = FieldModifier(
                        onFocusChanged = { focusState ->
                            if (focusState.isFocused) {
                                onActivate()
                            }
                        }
                    )
                )

                // Enrichments list
                if (block.enrichments.isNotEmpty()) {
                    LogManager.aiEnrichment("Rendering ${block.enrichments.size} enrichments for block ${block.id}")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UI.Text(
                            text = s.shared("ai_composer_enrichments_label"),
                            type = TextType.CAPTION
                        )

                        block.enrichments.forEach { enrichment ->
                            EnrichmentBlockPreview(
                                block = enrichment,
                                onEdit = { onEnrichmentEdit(enrichment) },
                                onRemove = { onEnrichmentRemove(enrichment) }
                            )
                        }
                    }
                }
            }
        }

        // Delete button positioned absolutely at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                display = ButtonDisplay.ICON,
                size = Size.S,
                onClick = onDeleteBlock
            )
        }
    }
}

/**
 * Preview component for enrichment blocks
 * Layout ensures buttons stay visible even with long preview text
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text section with icon - compressible to make room for buttons
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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

            // Buttons section - fixed size, always visible
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

    // Compute linearText by joining all content with structured format
    // Text segments and enrichments previews on separate lines with brackets
    val linearText = segments.joinToString("\n") { segment ->
        when (segment) {
            is MessageSegment.Text -> segment.content
            is MessageSegment.EnrichmentBlock -> "[${segment.promptPreview}]"
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
    onConfirm: (config: String, uiPreview: String, promptPreview: String) -> Unit,
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
    onConfirm: (config: String, uiPreview: String, promptPreview: String) -> Unit,
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

    // INSTANCE toggles (all unchecked by default)
    var includeSchemaConfig by remember { mutableStateOf(false) }
    var includeSchemaData by remember { mutableStateOf(false) }
    var includeToolConfig by remember { mutableStateOf(false) }
    var includeDataSample by remember { mutableStateOf(false) }
    var includeStats by remember { mutableStateOf(false) }
    var includeData by remember { mutableStateOf(false) }

    // ZONE toggles (all unchecked by default)
    var includeZoneConfig by remember { mutableStateOf(false) }
    var includeToolsList by remember { mutableStateOf(false) }
    var includeToolsConfig by remember { mutableStateOf(false) }
    var includeToolsData by remember { mutableStateOf(false) }  // TODO: will be disabled

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
                title = s.shared("pointer_enrichment_selector_title"),
                useRelativeLabels = (sessionType == SessionType.AUTOMATION)
            ),
            onDismiss = onDismiss,
            onConfirm = { result ->
                selectionResult = result
                showZoneScopeSelector = false
            }
        )
    } else {
        // Additional configuration fields after selection
        val useRelativeLabels = (sessionType == SessionType.AUTOMATION)
        val scrollState = rememberScrollState()

        UI.Dialog(
            type = DialogType.CONFIGURE,
            onConfirm = {
                selectionResult?.let { result ->
                    val config = createPointerConfig(
                        selectionResult = result,
                        importance = importance,
                        timestampSelection = timestampSelection,
                        description = description,
                        includeData = includeData,
                        // INSTANCE toggles
                        includeSchemaConfig = includeSchemaConfig,
                        includeSchemaData = includeSchemaData,
                        includeToolConfig = includeToolConfig,
                        includeDataSample = includeDataSample,
                        includeStats = includeStats,
                        // ZONE toggles
                        includeZoneConfig = includeZoneConfig,
                        includeToolsList = includeToolsList,
                        includeToolsConfig = includeToolsConfig,
                        includeToolsData = includeToolsData
                    )
                    val (uiPreview, promptPreview) = createPointerPreview(context, result, timestampSelection, importance)
                    onConfirm(config, uiPreview, promptPreview)
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

                // Data inclusion toggles (different for ZONE vs INSTANCE)
                selectionResult?.let { result ->
                    UI.Text(
                        text = s.shared("ai_enrichment_data_inclusion_title"),
                        type = TextType.SUBTITLE
                    )

                    when (result.selectionLevel.name) {
                        "INSTANCE" -> {
                            // INSTANCE-level toggles (6 toggles)
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_schema_config"),
                                checked = includeSchemaConfig,
                                onCheckedChange = { includeSchemaConfig = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_schema_data"),
                                checked = includeSchemaData,
                                onCheckedChange = { includeSchemaData = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_tool_config"),
                                checked = includeToolConfig,
                                onCheckedChange = { includeToolConfig = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_data_sample"),
                                checked = includeDataSample,
                                onCheckedChange = { includeDataSample = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_stats"),
                                checked = includeStats,
                                onCheckedChange = { includeStats = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_data"),
                                checked = includeData,
                                onCheckedChange = { includeData = it }
                            )
                        }
                        "ZONE" -> {
                            // ZONE-level toggles (4 toggles)
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_zone_config"),
                                checked = includeZoneConfig,
                                onCheckedChange = { includeZoneConfig = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_tools_list"),
                                checked = includeToolsList,
                                onCheckedChange = { includeToolsList = it }
                            )
                            UI.ToggleField(
                                label = s.shared("ai_enrichment_include_tools_config"),
                                checked = includeToolsConfig,
                                onCheckedChange = { includeToolsConfig = it }
                            )
                            // TODO: Tools data toggle - disabled until implementation
                            Box(modifier = androidx.compose.ui.Modifier.alpha(0.5f)) {
                                UI.ToggleField(
                                    label = "${s.shared("ai_enrichment_include_tools_data")} (${s.shared("label_coming_soon")})",
                                    checked = false,
                                    onCheckedChange = { /* Disabled - no-op */ }
                                )
                            }
                        }
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
    onConfirm: (config: String, uiPreview: String, promptPreview: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var config by remember { mutableStateOf(existingConfig ?: "{}") }
    var preview by remember { mutableStateOf("${getEnrichmentIcon(type)} Configuration") }

    UI.Dialog(
        type = DialogType.CONFIGURE,
        onConfirm = {
            // For placeholder, use same preview for UI and prompt
            onConfirm(config, preview, preview)
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
    includeData: Boolean = false,
    // INSTANCE toggles
    includeSchemaConfig: Boolean = false,
    includeSchemaData: Boolean = false,
    includeToolConfig: Boolean = false,
    includeDataSample: Boolean = false,
    includeStats: Boolean = false,
    // ZONE toggles
    includeZoneConfig: Boolean = false,
    includeToolsList: Boolean = false,
    includeToolsConfig: Boolean = false,
    includeToolsData: Boolean = false
): String {
    return JSONObject().apply {
        put("selectedPath", selectionResult.selectedPath)
        put("selectedValues", selectionResult.selectedValues)
        put("selectionLevel", selectionResult.selectionLevel.name)
        put("importance", importance)

        // Toggles configuration based on selection level
        when (selectionResult.selectionLevel.name) {
            "INSTANCE" -> {
                // Instance-level toggles
                put("includeSchemaConfig", includeSchemaConfig)
                put("includeSchemaData", includeSchemaData)
                put("includeToolConfig", includeToolConfig)
                put("includeDataSample", includeDataSample)
                put("includeStats", includeStats)
                put("includeData", includeData)  // Full data toggle
            }
            "ZONE" -> {
                // Zone-level toggles
                put("includeZoneConfig", includeZoneConfig)
                put("includeToolsList", includeToolsList)
                put("includeToolsConfig", includeToolsConfig)
                put("includeToolsData", includeToolsData)  // TODO: disabled for now
            }
        }
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
 * Create human-readable previews from POINTER enrichment data
 * Returns Pair(uiPreview, promptPreview)
 *
 * UI: "Santé"
 * Prompt: "Santé (id = zones/zone_123)"
 */
private fun createPointerPreview(
    context: Context,
    selectionResult: SelectionResult,
    timestampSelection: TimestampSelection,
    importance: String
): Pair<String, String> {
    val s = Strings.`for`(context = context)

    // Display name from displayChain (human-readable names)
    val displayName = if (selectionResult.displayChain.isNotEmpty()) {
        selectionResult.displayChain.last()
    } else {
        val pathParts = selectionResult.selectedPath.split("/").filter { it.isNotBlank() }
        pathParts.lastOrNull() ?: "sélection"
    }

    // Period text (if filtered)
    val périodeText = if (timestampSelection.isComplete) {
        " (${s.shared("ai_period_filtered")})"
    } else {
        ""
    }

    // UI version: just the name and period
    val uiPreview = "$displayName$périodeText"

    // Prompt version: name + ID + period
    val promptPreview = "$displayName (id = ${selectionResult.selectedPath})$périodeText"

    return Pair(uiPreview, promptPreview)
}
