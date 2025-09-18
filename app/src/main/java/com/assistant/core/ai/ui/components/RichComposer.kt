package com.assistant.core.ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.*
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*

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
    modifier: Modifier = Modifier
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
                    val richMessage = createRichMessage(segments)
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
                val newBlock = MessageSegment.EnrichmentBlock(
                    type = type,
                    config = config,
                    preview = preview
                )
                val newSegments = segments + newBlock
                onSegmentsChange(newSegments)
                showEnrichmentDialog = null
            }
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
 */
private fun createRichMessage(segments: List<MessageSegment>): RichMessage {
    // Compute linearText by joining all content
    val linearText = segments.joinToString(" ") { segment ->
        when (segment) {
            is MessageSegment.Text -> segment.content
            is MessageSegment.EnrichmentBlock -> segment.preview
        }
    }.trim()

    // Compute dataQueries from enrichment blocks
    val dataQueries = segments.filterIsInstance<MessageSegment.EnrichmentBlock>()
        .filter { it.type == EnrichmentType.POINTER } // Only POINTER blocks generate queries for now
        .mapNotNull { block ->
            // TODO: Parse block.config JSON and create DataQuery
            try {
                createDataQueryFromConfig(block.config)
            } catch (e: Exception) {
                null // Skip invalid configs
            }
        }

    return RichMessage(
        segments = segments,
        linearText = linearText,
        dataQueries = dataQueries
    )
}

/**
 * Parse enrichment config JSON and create DataQuery
 * TODO: Implement based on actual config structures
 */
private fun createDataQueryFromConfig(config: String): DataQuery? {
    // Placeholder implementation
    return DataQuery(
        id = "placeholder_query_${System.currentTimeMillis()}",
        type = "PLACEHOLDER",
        params = emptyMap()
    )
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
 * Enrichment configuration dialog
 * TODO: Implement specific UIs for each enrichment type
 */
@Composable
private fun EnrichmentConfigDialog(
    type: EnrichmentType,
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String, preview: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Placeholder implementation - just show type name
    var config by remember { mutableStateOf(existingConfig ?: "{}") }
    var preview by remember { mutableStateOf("${getEnrichmentIcon(type)} Configuration") }
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        UI.Dialog(
            type = DialogType.CONFIGURE,
            onConfirm = {
                onConfirm(config, preview)
                showDialog = false
            },
            onCancel = {
                onDismiss()
                showDialog = false
            }
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
}