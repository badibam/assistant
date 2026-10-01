package com.assistant.core.ai.ui.components

import com.assistant.core.ai.data.RichMessage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.assistant.core.coordinator.isSuccess
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
import com.assistant.core.ui.selectors.PointerSelector
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import org.json.JSONArray
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
 * The blocks being composed, ids included. Rebuilding them from the segments on recreation
 * would give them new ids, and the open enrichment dialog names its block by id.
 */
private val TextBlocksSaver: Saver<List<TextBlock>, String> = Saver(
    save = { blocks ->
        JSONArray(blocks.map { block ->
            JSONObject()
                .put("id", block.id)
                .put("text", block.text)
                .put("enrichments", RichMessage(block.enrichments).toJson())
        }).toString()
    },
    restore = { saved ->
        val array = JSONArray(saved)
        (0 until array.length()).map { i ->
            val block = array.getJSONObject(i)
            val enrichments = RichMessage.fromJson(block.getString("enrichments"))?.segments
                ?: throw IllegalStateException("Saved enrichments of a block could not be parsed")
            TextBlock(
                id = block.getString("id"),
                text = block.getString("text"),
                enrichments = enrichments.filterIsInstance<MessageSegment.EnrichmentBlock>()
            )
        }
    }
)

/** The enrichment dialog left open, so a rotation reopens it on the same block. */
private val NullableEnrichmentDialogStateSaver: Saver<EnrichmentDialogState?, String> = Saver(
    save = { state ->
        state?.let {
            JSONObject()
                .put("block_id", it.blockId)
                .put("type", it.type.name)
                .put("existing_config", it.existingConfig ?: JSONObject.NULL)
                .toString()
        }
    },
    restore = { saved ->
        val json = JSONObject(saved)
        EnrichmentDialogState(
            blockId = json.getString("block_id"),
            type = EnrichmentType.valueOf(json.getString("type")),
            existingConfig = if (json.isNull("existing_config")) null else json.getString("existing_config")
        )
    }
)

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
    sessionId: String? = null,
    statusContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A file is kept with its session: without one, none can be joined
    val offeredTypes = enrichmentTypes.filter { it != EnrichmentType.FILE || sessionId != null }

    /** The files of [enrichments] deleted: taken off the composer, they will never go. */
    fun deleteFiles(enrichments: List<MessageSegment.EnrichmentBlock>) {
        val ids = enrichments.filter { it.type == EnrichmentType.FILE }.map { com.assistant.core.ai.enrichments.FileEnrichment.fromJson(it.config).fileId }
        if (ids.isEmpty()) return
        scope.launch {
            ids.forEach { id ->
                val result = com.assistant.core.coordinator.Coordinator(context).processUserAction("files.delete", mapOf("id" to id))
                if (!result.isSuccess) LogManager.aiEnrichment("File $id not deleted: ${result.error}", "ERROR")
            }
        }
    }
    val s = remember { Strings.`for`(context = context) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxBlocksHeight = screenHeight / 3
    val keyboardController = LocalSoftwareKeyboardController.current

    // Convert segments to blocks for editing (initialize once, then manage locally)
    var blocks by rememberSaveable(stateSaver = TextBlocksSaver) {
        mutableStateOf(segmentsToBlocks(segments))
    }

    // Track active block ID
    var activeBlockId by rememberSaveable { mutableStateOf(blocks.firstOrNull()?.id ?: "") }

    // Sync from parent only when segments change externally (not from our own updates)
    var lastSyncedSegments by remember { mutableStateOf(segments) }
    LaunchedEffect(segments) {
        // Only update if segments changed externally (not from our updateSegments call)
        if (segments != lastSyncedSegments && blocksToSegments(blocks) != segments) {
            blocks = segmentsToBlocks(segments)
            // Rebuilt blocks have new ids: the active one would name a block that is gone,
            // and the next enrichment would find nowhere to go (the composer emptied after a send)
            activeBlockId = blocks.first().id
        }
        // Always keep lastSyncedSegments in sync to avoid stale state
        lastSyncedSegments = segments
    }

    // Enrichment dialog state
    var showEnrichmentDialog by rememberSaveable(stateSaver = NullableEnrichmentDialogStateSaver) {
        mutableStateOf<EnrichmentDialogState?>(null)
    }

    // Update parent when blocks change
    val updateSegments = {
        val newSegments = blocksToSegments(blocks)
        onSegmentsChange(newSegments)
    }

    val blocksScrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
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
                            onSend(RichMessage(blocksToSegments(blocks)))
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
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
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
                        existingConfig = enrichment.config
                    )
                },
                onEnrichmentRemove = { enrichment ->
                    deleteFiles(listOf(enrichment))
                    blocks = blocks.map {
                        if (it.id == block.id) {
                            it.copy(enrichments = it.enrichments.filter { e -> e != enrichment })
                        } else it
                    }
                    updateSegments()
                },
                onDeleteBlock = {
                    deleteFiles(block.enrichments)
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
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enrichment buttons (if enabled)
            if (showEnrichmentButtons) {
                offeredTypes.forEach { type ->
                    UI.ActionButton(
                        action = getEnrichmentButtonAction(type),
                        display = ButtonDisplay.ICON,
                        size = Size.M,
                        onClick = {
                            showEnrichmentDialog = EnrichmentDialogState(
                                blockId = activeBlockId,
                                type = type,
                                existingConfig = null
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
            onConfirm = { config ->
                val newEnrichment = MessageSegment.EnrichmentBlock(type = dialogState.type, config = config)
                LogManager.aiEnrichment("Created EnrichmentBlock: type=${dialogState.type}")

                // Add or update enrichment in the target block, which must exist: a missing one
                // would drop the enrichment without a word
                check(blocks.any { it.id == dialogState.blockId }) {
                    "Enrichment aimed at block ${dialogState.blockId}, which the composer no longer holds"
                }
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
            sessionType = sessionType,
            sessionId = sessionId
        )
    }
}

/**
 * State for enrichment dialog
 */
private data class EnrichmentDialogState(
    val blockId: String,
    val type: EnrichmentType,
    val existingConfig: String?
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
                    .padding(UI.Space.M),
                verticalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {

                // Text field (delete button is positioned absolute)
                UI.FormField(
                    required = false,
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
                        verticalArrangement = Arrangement.spacedBy(UI.Space.S)
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
                .padding(UI.Space.S)
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
                .padding(UI.Space.M),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text section with icon - compressible to make room for buttons
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UI.Icon(iconName = block.type.iconName, size = 20.dp)
                UI.Text(
                    text = rememberDisplayText(block),
                    type = TextType.BODY
                )
            }

            // Buttons section - fixed size, always visible
            Row(
                horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)
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
 * Get button action for enrichment type
 */
private fun getEnrichmentButtonAction(type: EnrichmentType): ButtonAction {
    return when (type) {
        EnrichmentType.POINTER -> ButtonAction.SELECT
        EnrichmentType.USE -> ButtonAction.EDIT
        EnrichmentType.CREATE -> ButtonAction.ADD
        EnrichmentType.MODIFY_CONFIG -> ButtonAction.CONFIGURE
        EnrichmentType.FILE -> ButtonAction.ATTACH
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
    onConfirm: (config: String) -> Unit,
    sessionType: SessionType = SessionType.CHAT,
    sessionId: String? = null
) {
    when (type) {
        EnrichmentType.FILE -> FileEnrichmentDialog(
            existingConfig = existingConfig,
            sessionId = checkNotNull(sessionId) { "a file is joined within a session" },
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
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
 * The POINTER enrichment's dialog: the pointer selector, its dates relative to the scheduled time
 * of the run for a starting message that is replayed later (SEED), fixed for a chat.
 */
@Composable
private fun PointerEnrichmentDialog(
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit,
    sessionType: SessionType = SessionType.CHAT
) {
    val context = LocalContext.current
    PointerSelector(
        reference = if (sessionType == SessionType.SEED) Strings.`for`(context = context).shared("instant_reference_scheduled_run") else null,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * Placeholder dialog for other enrichment types
 */
@Composable
private fun PlaceholderEnrichmentDialog(
    type: EnrichmentType,
    existingConfig: String?,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var config by rememberSaveable { mutableStateOf(existingConfig ?: "{}") }

    UI.Dialog(
        type = DialogType.CONFIGURE,
        onConfirm = {
            onConfirm(config)
        },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(UI.Space.L),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            UI.Text(
                text = s.shared("ai_enrichment_config"),
                type = TextType.TITLE
            )

            UI.Text(
                text = s.shared("ai_enrichment_todo_implement").format(type.name),
                type = TextType.BODY
            )
        }
    }
}

