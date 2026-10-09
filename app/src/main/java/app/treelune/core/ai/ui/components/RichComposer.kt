package app.treelune.core.ai.ui.components

import app.treelune.core.ai.data.RichMessage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.treelune.core.ai.data.*
import app.treelune.core.ai.enrichments.EnrichmentProcessor
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.ui.selectors.PointerSelector
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The blocks being composed, ids included. Rebuilding them from the segments on recreation
 * would give them new ids, and the active block and the open dialog name theirs by id. Empty
 * texts are kept: they are blocks the user made, even if they do not go with the message.
 */
private val ComposerBlocksSaver: Saver<List<ComposerBlock>, String> = Saver(
    save = { blocks ->
        JSONObject()
            .put("ids", JSONArray(blocks.map { it.id }))
            .put("message", RichMessage(blocks.map { it.segment }).toJson())
            .toString()
    },
    restore = { saved ->
        val json = JSONObject(saved)
        val ids = json.getJSONArray("ids")
        val segments = RichMessage.fromJson(json.getString("message"))?.segments
            ?: throw IllegalStateException("Saved composer blocks could not be parsed")
        check(segments.size == ids.length()) { "Saved composer blocks: ${ids.length()} ids for ${segments.size} segments" }
        segments.mapIndexed { i, segment -> ComposerBlock(segment, ids.getString(i)) }
    }
)

/** The enrichment dialog left open, so a rotation reopens it on the same block. */
private val NullableEnrichmentDialogStateSaver: Saver<EnrichmentDialogState?, String> = Saver(
    save = { state ->
        state?.let {
            JSONObject()
                .put("type", it.type.name)
                .put("edited_block_id", it.editedBlockId ?: JSONObject.NULL)
                .toString()
        }
    },
    restore = { saved ->
        val json = JSONObject(saved)
        EnrichmentDialogState(
            type = EnrichmentType.valueOf(json.getString("type")),
            editedBlockId = if (json.isNull("edited_block_id")) null else json.getString("edited_block_id")
        )
    }
)

/**
 * RichComposer: the message as a list of typed blocks — texts, pointers, files — in the order
 * they go.
 *
 * - One block is active at a time (the one touched, typed in, or last added).
 * - A new block goes right after the active one and becomes active; a new text takes the focus.
 * - Blocks are reordered by their handle; the active one stays active wherever it goes.
 * - Deleting the active block activates the one before it (the one after when it was the
 *   first); the list is never empty: an empty text replaces the last block deleted.
 * - An empty text does not go with the message.
 * - An image, taken with the phone's camera or picked in its photos, is a block of its own, kept
 *   with the session; with none, none can be joined. The block says when the model does not
 *   read images, and the message is refused while it holds one then.
 * The rules live in [ComposerBlocks]; this composable shows the blocks and wires the gestures.
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
    enrichmentTypes: List<EnrichmentType> = EnrichmentType.entries,
    modifier: Modifier = Modifier,
    sessionType: SessionType = SessionType.CHAT,
    sessionId: String? = null,
    statusContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A file is kept with its session: without one, none can be joined
    val offeredTypes = enrichmentTypes.filter { it != EnrichmentType.FILE || sessionId != null }

    /** The file or the image of [block], if it is one, deleted: taken off the composer, it will never go. */
    fun deleteFile(block: ComposerBlock) {
        val (operation, id) = when (val segment = block.segment) {
            is MessageSegment.Image -> "files.delete_image" to segment.imageId
            is MessageSegment.EnrichmentBlock ->
                if (segment.type == EnrichmentType.FILE) "files.delete" to app.treelune.core.ai.enrichments.FileEnrichment.fromJson(segment.config).fileId
                else return
            is MessageSegment.Text -> return
        }
        scope.launch {
            val result = Coordinator(context).processUserAction(operation, mapOf("id" to id))
            if (!result.isSuccess) LogManager.aiEnrichment("$operation $id failed: ${result.error}", "ERROR")
        }
    }
    val s = remember { Strings.`for`(context = context) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxBlocksHeight = screenHeight / 3
    val keyboardController = LocalSoftwareKeyboardController.current

    // The blocks, built once from the segments, then managed here
    var blocks by rememberSaveable(stateSaver = ComposerBlocksSaver) {
        mutableStateOf(ComposerBlocks.fromSegments(segments))
    }

    var activeBlockId by rememberSaveable { mutableStateOf(blocks.first().id) }

    // The text block just added, which takes the focus once shown; not kept across a rotation,
    // where the field that had the focus gets it back by itself
    var focusBlockId by remember { mutableStateOf<String?>(null) }

    // Sync from parent only when segments change externally (not from our own updates)
    var lastSyncedSegments by remember { mutableStateOf(segments) }
    LaunchedEffect(segments) {
        if (segments != lastSyncedSegments && ComposerBlocks.toSegments(blocks) != segments) {
            blocks = ComposerBlocks.fromSegments(segments)
            // Rebuilt blocks have new ids: the active one would name a block that is gone,
            // and the next block would find nowhere to go (the composer emptied after a send)
            activeBlockId = blocks.first().id
        }
        lastSyncedSegments = segments
    }

    var showEnrichmentDialog by rememberSaveable(stateSaver = NullableEnrichmentDialogStateSaver) {
        mutableStateOf<EnrichmentDialogState?>(null)
    }

    /** The blocks replaced by [newBlocks], and the parent told of the message they make. */
    fun update(newBlocks: List<ComposerBlock>) {
        blocks = newBlocks
        onSegmentsChange(ComposerBlocks.toSegments(newBlocks))
    }

    /** [block] added after the active one, and made active. */
    fun add(block: ComposerBlock) {
        update(ComposerBlocks.insertAfter(blocks, activeBlockId, block))
        activeBlockId = block.id
    }

    // Whether the model the message would go to reads images: read once an image is in the
    // composer (null until then, or when nothing says)
    val hasImages = blocks.any { it.segment is MessageSegment.Image }
    var readsImages by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hasImages) {
        if (hasImages) readsImages = app.treelune.core.ai.providers.ImageInput.activeModelReadsImages(context)
    }

    /** The photo at [uri] prepared and kept with the session, then added as a block. */
    fun attachImage(uri: android.net.Uri, afterwards: () -> Unit = {}) {
        val session = checkNotNull(sessionId) { "an image is joined within a session" }
        scope.launch {
            val result = Coordinator(context).processUserAction("files.attach_image", mapOf("session_id" to session, "uri" to uri.toString()))
            afterwards()
            if (result.isSuccess) add(ComposerBlock(MessageSegment.Image(result.data!!["id"] as String)))
            else UI.Toast(context, result.error ?: "", Duration.LONG)
        }
    }

    // The camera writes into a file of the app's cache, handed to it by the FileProvider; the
    // file is kept until the photo is prepared, then deleted. Its path survives the app being
    // stopped while the camera is open.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = capturePath?.let { java.io.File(it) } ?: return@rememberLauncherForActivityResult
        capturePath = null
        if (taken) attachImage(android.net.Uri.fromFile(file)) { file.delete() } else file.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachImage(uri)
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
                            // A message with images waits for a model that reads them
                            val imageCount = blocks.count { it.segment is MessageSegment.Image }
                            val refusal = app.treelune.core.ai.providers.ImageInput.refusal(imageCount, readsImages) { s.shared(it) }
                            if (refusal != null) UI.Toast(context, refusal, Duration.LONG)
                            else onSend(RichMessage(ComposerBlocks.toSegments(blocks)))
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
                .verticalScroll(blocksScrollState)
        ) {
            UI.ReorderableColumn(
                items = blocks,
                key = { it.id },
                spacing = UI.Space.L,
                onMove = { from, to -> update(ComposerBlocks.move(blocks, from, to)) }
            ) { _, block ->
                BlockCard(
                    isActive = block.id == activeBlockId,
                    onActivate = { activeBlockId = block.id },
                    onDelete = {
                        deleteFile(block)
                        val deletion = ComposerBlocks.delete(blocks, block.id, activeBlockId)
                        update(deletion.blocks)
                        activeBlockId = deletion.activeId
                    },
                    handle = { DragHandle() }
                ) {
                    when (val segment = block.segment) {
                        is MessageSegment.Text -> TextBlockContent(
                            text = segment.content,
                            placeholder = if (blocks.size == 1 && segment.content.isEmpty()) {
                                placeholder.ifEmpty { s.shared("ai_composer_placeholder") }
                            } else "",
                            takeFocus = block.id == focusBlockId,
                            onFocusTaken = { focusBlockId = null },
                            onActivate = { activeBlockId = block.id },
                            onTextChange = { newText ->
                                update(ComposerBlocks.replace(blocks, block.id, MessageSegment.Text(newText)))
                            }
                        )
                        is MessageSegment.Image -> ImageBlockContent(imageId = segment.imageId, readsImages = readsImages)
                        is MessageSegment.EnrichmentBlock -> EnrichmentBlockContent(
                            block = segment,
                            onEdit = {
                                activeBlockId = block.id
                                showEnrichmentDialog = EnrichmentDialogState(type = segment.type, editedBlockId = block.id)
                            }
                        )
                    }
                }
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
                            showEnrichmentDialog = EnrichmentDialogState(type = type, editedBlockId = null)
                        }
                    )
                }
            }

            // Images: the phone's camera and its photo picker, within a session alone
            if (showEnrichmentButtons && sessionId != null) {
                UI.ActionButton(
                    action = ButtonAction.PHOTO,
                    display = ButtonDisplay.ICON,
                    size = Size.M,
                    onClick = {
                        val file = java.io.File(context.cacheDir, "camera/${java.util.UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
                        capturePath = file.path
                        try {
                            camera.launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        } catch (e: android.content.ActivityNotFoundException) {
                            capturePath = null
                            file.delete()
                            UI.Toast(context, s.shared("ai_image_no_camera"), Duration.LONG)
                        }
                    }
                )
                UI.ActionButton(
                    action = ButtonAction.GALLERY,
                    display = ButtonDisplay.ICON,
                    size = Size.M,
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
            }

            // Add Text button: a new text block after the active one, which takes the focus
            UI.Button(
                type = ButtonType.DEFAULT,
                size = Size.M,
                onClick = {
                    val newBlock = ComposerBlock(MessageSegment.Text(""))
                    add(newBlock)
                    focusBlockId = newBlock.id
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
        val edited = dialogState.editedBlockId?.let { id ->
            checkNotNull(blocks.firstOrNull { it.id == id }) { "Enrichment dialog on block $id, which the composer no longer holds" }
        }
        EnrichmentConfigDialog(
            type = dialogState.type,
            existingConfig = (edited?.segment as? MessageSegment.EnrichmentBlock)?.config,
            onDismiss = { showEnrichmentDialog = null },
            onConfirm = { config ->
                val enrichment = MessageSegment.EnrichmentBlock(type = dialogState.type, config = config)
                LogManager.aiEnrichment("Enrichment confirmed: type=${dialogState.type}, edited block=${edited?.id}")
                // An edited block keeps its place; a new one goes after the active block
                if (edited != null) update(ComposerBlocks.replace(blocks, edited.id, enrichment))
                else add(ComposerBlock(enrichment))
                showEnrichmentDialog = null
            },
            sessionType = sessionType,
            sessionId = sessionId
        )
    }
}

/**
 * The enrichment dialog open: the type it configures, and the block it edits — none for a new
 * block, which goes after the active one on confirm.
 */
private data class EnrichmentDialogState(
    val type: EnrichmentType,
    val editedBlockId: String?
)

/**
 * The frame every block shares, whatever its type: the handle it is dragged by, its content,
 * and its delete button; highlighted when active, activated when touched.
 */
@Composable
private fun BlockCard(
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    handle: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() }
    ) {
        UI.Card(
            type = CardType.DEFAULT,
            highlight = isActive
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.S),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                handle()
                Box(modifier = Modifier.weight(1f)) { content() }
                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onDelete
                )
            }
        }
    }
}

/** A text block's content: its field, which activates the block when focused or typed in. */
@Composable
private fun TextBlockContent(
    text: String,
    placeholder: String,
    takeFocus: Boolean,
    onFocusTaken: () -> Unit,
    onActivate: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    if (takeFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusTaken()
        }
    }
    // The card says what the field is for: its placeholder stands inside it alone
    UI.FormField(
        required = false,
        label = placeholder,
        labelAbove = false,
        value = text,
        onChange = { newText ->
            onTextChange(newText)
            onActivate()
        },
        fieldType = FieldType.TEXT_UNLIMITED,
        fieldModifier = FieldModifier(
            focusRequester = focusRequester,
            onFocusChanged = { focusState ->
                if (focusState.isFocused) onActivate()
            }
        )
    )
}

/**
 * An image's content: its thumbnail, and what keeps it from going when the model does not read
 * images or nothing says it does.
 */
@Composable
private fun ImageBlockContent(imageId: String, readsImages: Boolean?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        AttachedImageThumbnail(imageId)
        when (readsImages) {
            true -> {}
            false -> UI.Text(s.shared("ai_image_model_does_not_read"), TextType.WARNING)
            null -> UI.Text(s.shared("ai_image_model_unknown"), TextType.WARNING)
        }
    }
}

/**
 * A pointer's or a file's content: its icon and its text, which stays readable while the edit
 * button keeps its room.
 */
@Composable
private fun EnrichmentBlockContent(
    block: MessageSegment.EnrichmentBlock,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Icon(iconName = block.type.iconName, size = 20.dp)
            UI.Text(
                text = rememberDisplayText(block),
                type = TextType.BODY
            )
        }
        UI.ActionButton(
            action = ButtonAction.EDIT,
            display = ButtonDisplay.ICON,
            size = Size.S,
            onClick = onEdit
        )
    }
}

/**
 * Get button action for enrichment type
 */
private fun getEnrichmentButtonAction(type: EnrichmentType): ButtonAction {
    return when (type) {
        EnrichmentType.POINTER -> ButtonAction.POINTER
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
        EnrichmentType.POINTER -> PointerEnrichmentDialog(
            existingConfig = existingConfig,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            sessionType = sessionType
        )
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
