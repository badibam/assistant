package com.assistant.core.ui
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.themes.CurrentTheme
import org.json.JSONObject

/**
 * UI - Unified public API
 * ONLY VISUAL components (themed)
 * 
 * LAYOUTS: use Compose Row/Column/Box/Spacer directly
 * VISUALS: use UI.* for theming
 * 
 * Principle: UI.* → delegation to current theme via CurrentTheme.current
 */
object UI {
    
    // =====================================
    // LAYOUTS : USE COMPOSE DIRECTLY
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // NO wrappers - direct access for maximum flexibility
    
    // =====================================
    // INTERACTIVE
    // =====================================
    
    @Composable
    fun Button(
        type: ButtonType,
        size: Size = Size.M,
        state: ComponentState = ComponentState.NORMAL,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Button(type, size, state, onClick, content)
    
    @Composable
    fun ActionButton(
        action: ButtonAction,
        display: ButtonDisplay = ButtonDisplay.LABEL,
        size: Size = Size.M,
        type: ButtonType? = null,  // Optional override of default type
        enabled: Boolean = true,
        requireConfirmation: Boolean = false,  // Automatic confirmation dialog
        confirmMessage: String? = null,        // Custom message (null = default message)
        onClick: () -> Unit
    ) = CurrentTheme.current.ActionButton(action, display, size, type, enabled, requireConfirmation, confirmMessage, onClick)
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    fun Text(
        text: String,
        type: TextType,
        fillMaxWidth: Boolean = false,
        textAlign: TextAlign? = null,
        maxLines: Int = Int.MAX_VALUE
    ) {
        CurrentTheme.current.Text(text, type, fillMaxWidth, textAlign, maxLines)
    }
    
    /**
     * Centered text even if multiline - use in Box with contentAlignment = Alignment.Center
     */
    @Composable
    fun CenteredText(
        text: String,
        type: TextType
    ) {
        Text(text = text, type = type, fillMaxWidth = true, textAlign = TextAlign.Center)
    }
    
    
    @Composable
    fun Card(
        type: CardType,
        size: Size = Size.M,
        highlight: Boolean = false,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Card(type, size, highlight, content)

    /**
     * StatusIndicator - Colored circular indicator for status display
     * Used to show status (success/warning/error) with themed colors
     *
     * @param color The color of the indicator (from MaterialTheme.colorScheme)
     * @param size The diameter of the circular indicator (default 8dp)
     */
    @Composable
    fun StatusIndicator(
        color: androidx.compose.ui.graphics.Color,
        size: Dp = 8.dp
    ) = CurrentTheme.current.StatusIndicator(color, size)

    /**
     * A short colored label, such as an option of a colored CHOICE field.
     *
     * @param text The label
     * @param color The color's name; the theme decides what it looks like
     */
    @Composable
    fun Tag(
        text: String,
        color: com.assistant.core.themes.TagColor
    ) = CurrentTheme.current.Tag(text, color)

    /**
     * A horizontal gauge, filled to where a value stands between two bounds.
     *
     * @param fraction From 0 (empty) to 1 (full); anything outside is clamped
     */
    @Composable
    fun Gauge(fraction: Float) = CurrentTheme.current.Gauge(fraction.coerceIn(0f, 1f))

    /** A horizontal line parting two parts of a screen or a card, drawn by the theme. */
    @Composable
    fun Divider() = CurrentTheme.current.Divider()

    /**
     * A round swatch of a tag color, as the current palette draws it, for choosing one.
     *
     * @param color The color's name
     * @param size The diameter of the swatch
     */
    @Composable
    fun TagSwatch(
        color: com.assistant.core.themes.TagColor,
        size: Dp = 24.dp
    ) = CurrentTheme.current.StatusIndicator(
        CurrentTheme.current.getTagColor(color, CurrentTheme.currentPaletteId),
        size
    )

    // =====================================
    // FEEDBACK SYSTEM
    // =====================================
    
    fun Toast(
        context: Context,
        message: String,
        duration: Duration = Duration.SHORT
    ) = CurrentTheme.current.Toast(context, message, duration)
    
    @Composable
    fun Snackbar(
        type: FeedbackType,
        message: String,
        action: String? = null,
        onAction: (() -> Unit)? = null
    ) = CurrentTheme.current.Snackbar(type, message, action, onAction)

    /**
     * Confirmation dialog
     *
     * Displays a modal dialog asking for user confirmation.
     *
     * @param title Dialog title
     * @param message Dialog message
     * @param confirmText Text for confirm button (default: "Confirmer")
     * @param cancelText Text for cancel button (default: "Annuler")
     * @param onConfirm Callback when user confirms
     * @param onDismiss Callback when user cancels or dismisses
     */
    @Composable
    fun ConfirmDialog(
        title: String,
        message: String,
        confirmText: String? = null,
        cancelText: String? = null,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) = CurrentTheme.current.ConfirmDialog(title, message, confirmText, cancelText, onConfirm, onDismiss)

    // =====================================
    // SYSTEM
    // =====================================
    
    @Composable
    fun LoadingIndicator(size: Size = Size.M) = 
        CurrentTheme.current.LoadingIndicator(size)
    
    @Composable
    fun Icon(
        iconName: String,
        size: Dp = 24.dp,
        contentDescription: String? = null,
        tint: androidx.compose.ui.graphics.Color? = null,
        background: androidx.compose.ui.graphics.Color? = null
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val iconResource = com.assistant.core.icons.Icons.drawable(context, iconName)
        if (iconResource != null) {
            CurrentTheme.current.Icon(
                resourceId = iconResource,
                size = size,
                contentDescription = contentDescription ?: iconName,
                tint = tint,
                background = background
            )
        } else {
            // Fallback: display first 2 letters of name
            Text(
                text = iconName.take(2).uppercase(),
                type = TextType.CAPTION,
                fillMaxWidth = false
            )
        }
    }
    
    @Composable
    fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = { },
        confirmEnabled: Boolean = true,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Dialog(type, onConfirm, onCancel, confirmEnabled, content)
    
    @Composable
    fun DatePicker(
        selectedDate: String,
        onDateSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) = CurrentTheme.current.DatePicker(selectedDate, onDateSelected, onDismiss)
    
    @Composable
    fun TimePicker(
        selectedTime: String,
        onTimeSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) = CurrentTheme.current.TimePicker(selectedTime, onTimeSelected, onDismiss)
    
    // =====================================
    // UNIFIED FORMS
    // =====================================
    
    /** The label of a field, marked as the theme marks a field that must be answered. */
    @Composable
    fun FieldLabel(label: String, required: Boolean) = CurrentTheme.current.FieldLabel(label, required)

    /**
     * A text input on a plain string: the cursor and the keyboard's composition are kept here,
     * the caller holding only the text. A text changed from outside keeps the cursor where it
     * can stand in it.
     */
    @Composable
    fun FormField(
        label: String,
        value: String,
        onChange: (String) -> Unit,
        fieldType: FieldType = FieldType.TEXT,
        required: Boolean,
        state: ComponentState = ComponentState.NORMAL,
        readonly: Boolean = false,
        onClick: (() -> Unit)? = null,
        contentDescription: String? = null,
        fieldModifier: FieldModifier = FieldModifier()
    ) {
        var selection by remember { mutableStateOf(TextRange(value.length)) }
        var composition by remember { mutableStateOf<TextRange?>(null) }
        val shown = TextFieldValue(
            text = value,
            selection = TextRange(selection.start.coerceAtMost(value.length), selection.end.coerceAtMost(value.length)),
            composition = composition?.takeIf { it.max <= value.length }
        )
        FormField(
            label = label,
            value = shown,
            onChange = { next ->
                selection = next.selection
                composition = next.composition
                if (next.text != value) onChange(next.text)
            },
            fieldType = fieldType,
            required = required,
            state = state,
            readonly = readonly,
            onClick = onClick,
            contentDescription = contentDescription,
            fieldModifier = fieldModifier
        )
    }

    /** A text input whose caller holds the cursor too: to insert where it stands. */
    @Composable
    fun FormField(
        label: String,
        value: TextFieldValue,
        onChange: (TextFieldValue) -> Unit,
        fieldType: FieldType = FieldType.TEXT,
        required: Boolean,
        state: ComponentState = ComponentState.NORMAL,
        readonly: Boolean = false,
        onClick: (() -> Unit)? = null,
        contentDescription: String? = null,
        fieldModifier: FieldModifier = FieldModifier()
    ) = CurrentTheme.current.FormField(
        label = label,
        value = value,
        onChange = onChange,
        fieldType = fieldType,
        state = state,
        readonly = readonly,
        onClick = onClick,
        contentDescription = contentDescription,
        required = required,
        fieldModifier = fieldModifier
    )
    
    @Composable
    fun FormSelection(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
        required: Boolean
    ) = CurrentTheme.current.FormSelection(
        label = label,
        options = options,
        selected = selected, 
        onSelect = onSelect,
        required = required
    )
    
    @Composable
    fun FormActions(
        content: @Composable RowScope.() -> Unit
    ) = CurrentTheme.current.FormActions(content)
    
    @Composable
    fun Checkbox(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String? = null
    ) = CurrentTheme.current.Checkbox(checked, onCheckedChange, label)
    
    /**
     * A yes/no answer, [value] null while there is none: touching the chosen answer again
     * empties it unless it is [required]. The answers read "Yes" and "No" unless named.
     */
    @Composable
    fun BooleanField(
        label: String,
        value: Boolean?,
        onValueChange: (Boolean?) -> Unit,
        required: Boolean,
        trueLabel: String? = null,
        falseLabel: String? = null
    ) {
        val s = com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current)
        CurrentTheme.current.BooleanField(label, value, onValueChange, trueLabel ?: s.shared("label_yes"), falseLabel ?: s.shared("label_no"),
            required, emptiable = !required, compact = false)
    }

    /**
     * A state that always is one or the other: never marked, never emptied. It reads "On" and
     * "Off" and is compact, to sit beside what it switches (an automation on or off); named, it
     * is a choice between two modes and takes the whole width.
     */
    @Composable
    fun BooleanField(
        label: String,
        value: Boolean,
        onValueChange: (Boolean) -> Unit,
        trueLabel: String? = null,
        falseLabel: String? = null
    ) {
        val s = com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current)
        CurrentTheme.current.BooleanField(label, value, { it?.let(onValueChange) }, trueLabel ?: s.shared("label_on"), falseLabel ?: s.shared("label_off"),
            required = false, emptiable = false, compact = trueLabel == null && falseLabel == null)
    }

    /**
     * A slider that stops every [step] from [min], decimals included (0 to 5 by 0.5), [value]
     * null while there is no answer (see ThemeContract.SliderField).
     */
    @Composable
    fun SliderField(
        label: String,
        value: Double?,
        onValueChange: (Double?) -> Unit,
        min: Double,
        max: Double,
        step: Double,
        required: Boolean,
        minLabel: String = "",
        maxLabel: String = ""
    ) = CurrentTheme.current.SliderField(label, value, onValueChange, min, max, step, minLabel, maxLabel, required)
    
    // =====================================
    // BUTTONS WITH AUTOMATIC ICONS
    // =====================================
    
    // Old predefined buttons removed - use UI.ActionButton instead
    
    // =====================================
    // SPECIALIZED COMPONENTS
    // =====================================
    
    /** The theme's mark that something waits for the user (ToolTypeContract.getWaiting). */
    @Composable
    fun WaitingMark() = CurrentTheme.current.WaitingMark()

    /** The theme's mark that a stopwatch runs on an entry (tools.running). */
    @Composable
    fun RunningMark() = CurrentTheme.current.RunningMark()

    /**
     * A tool's or a zone's icon with its two marks, each in its corner: something waiting at the
     * top, a stopwatch running at the bottom. The marks show without an icon too.
     */
    @Composable
    fun MarkedIcon(iconName: String?, waiting: Boolean, running: Boolean, size: Dp = 24.dp) {
        Box(modifier = Modifier.size(size)) {
            if (!iconName.isNullOrBlank()) Icon(iconName = iconName, size = size, contentDescription = null)
            if (waiting) Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) { WaitingMark() }
            if (running) Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) { RunningMark() }
        }
    }

    /** Something waiting or a stopwatch running in one of its tools (LocalWaiting, LocalRunning) is marked on its icon. */
    @Composable
    fun ZoneCard(
        zone: Zone,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { }
    ) {
        val waiting = LocalWaiting.current.zone(zone.id)
        val running = LocalRunning.current.zone(zone.id)
        // Themed container + standard content with UI.*
        CurrentTheme.current.ZoneCardContainer(onClick = onClick, onLongClick = onLongClick) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MarkedIcon(zone.icon_name, waiting, running)
                    Text(zone.name, TextType.TITLE)
                }
                zone.description?.let { desc ->
                    Text(desc, TextType.BODY)
                }
            }
        }
    }
    
    @Composable
    fun PageHeader(
        title: String,
        subtitle: String? = null,
        icon: String? = null,
        leftButton: ButtonAction? = null,
        rightButton: ButtonAction? = null,
        onLeftClick: (() -> Unit)? = null,
        onRightClick: (() -> Unit)? = null
    ) {
        // The phone's back key does what the header's back button does. Only the screen on
        // display is composed, so its header is the one that answers, and the key walks back
        // up the same way the button does -- tool, zone, home.
        if (leftButton == ButtonAction.BACK && onLeftClick != null) {
            BackHandler(onBack = onLeftClick)
        }
        CurrentTheme.current.PageHeader(title, subtitle, icon, leftButton, rightButton, onLeftClick, onRightClick)
    }
    
    /** The header of a tool's tile: its icon with its marks, and its name. */
    @Composable
    fun ToolCardHeader(
        tool: ToolInstance,
        context: android.content.Context,
        waiting: Boolean,
        running: Boolean
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val settings = com.assistant.core.tools.ToolConfigSettings.read(tool.tooltype, JSONObject(tool.config_json), context)
            MarkedIcon(settings.string("icon_name"), waiting, running)
            Text(settings.string("name")!!, TextType.BODY, maxLines = 2)
        }
    }

    /**
     * A tool's tile, laid out by its display mode: the header (icon and name) the core draws, the
     * summary and the body its tool type draws (ToolTile), each on whole cells of the grid.
     *
     * @param onOpenEntry Opens the tool on one of its entries, touched on the tile
     */
    @Composable
    fun ToolCard(
        tool: ToolInstance,
        displayMode: DisplayMode,
        context: android.content.Context,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { },
        onOpenEntry: (com.assistant.core.tools.EntryToOpen) -> Unit = { }
    ) {
        // Something waiting among its entries, or a stopwatch running on one, is marked on its icon
        val waiting = LocalWaiting.current.tool(tool.id)
        val running = LocalRunning.current.tool(tool.id)
        val toolType = requireNotNull(ToolTypeManager.getToolType(tool.tooltype)) { "No tool type '${tool.tooltype}' for tool ${tool.id}" }
        val tile = toolType.rememberTile(tool, onOpenEntry)
        CurrentTheme.current.ToolCardContainer(
            displayMode = displayMode,
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            // The header and the summary side by side, each on half the width
            @Composable
            fun HeaderAndSummary(modifier: Modifier) = Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    ToolCardHeader(tool, context, waiting, running)
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    tile.Summary()
                }
            }

            when (displayMode) {
                DisplayMode.ICON -> {
                    // The icon alone, centered in its cell
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MarkedIcon(com.assistant.core.tools.ToolConfigSettings.read(tool.tooltype, JSONObject(tool.config_json), context).string("icon_name"), waiting, running)
                    }
                }
                DisplayMode.MINIMAL -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        ToolCardHeader(tool, context, waiting, running)
                    }
                }
                DisplayMode.LINE -> HeaderAndSummary(Modifier.fillMaxSize())
                DisplayMode.CONDENSED -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            ToolCardHeader(tool, context, waiting, running)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            tile.Summary()
                        }
                    }
                }
                DisplayMode.EXTENDED, DisplayMode.SQUARE -> {
                    // One row of cells for the header and the summary, the others for the body
                    val rows = if (displayMode == DisplayMode.EXTENDED) 1 else 3
                    Column(modifier = Modifier.fillMaxSize()) {
                        HeaderAndSummary(Modifier.weight(1f).fillMaxWidth())
                        Box(modifier = Modifier.weight(rows.toFloat()).fillMaxWidth()) {
                            tile.Body(rows)
                        }
                    }
                }
                DisplayMode.FULL -> {
                    // As tall as the body needs; the grid rounds the tile up to whole cells
                    Column(modifier = Modifier.fillMaxSize()) {
                        HeaderAndSummary(Modifier.fillMaxWidth().height(IntrinsicSize.Min))
                        tile.Body(null)
                    }
                }
            }
        }
    }

    // =====================================
    // REUSABLE COMPONENTS
    // =====================================
    
    
    @Composable
    fun IconSelector(
        current: String,
        suggested: List<String> = emptyList(),
        onChange: (String) -> Unit
    ) = com.assistant.core.ui.components.IconSelector(current, suggested, onChange)
    
    
    /** A column whose items are reordered by dragging their handle; see [com.assistant.core.ui.components.ReorderableColumn]. */
    @Composable
    fun <T> ReorderableColumn(
        items: List<T>,
        onMove: (from: Int, to: Int) -> Unit,
        modifier: Modifier = Modifier,
        spacing: Dp = 0.dp,
        key: (T) -> Any = { it as Any },
        itemContent: @Composable com.assistant.core.ui.components.ReorderItemScope.(index: Int, item: T) -> Unit
    ) = com.assistant.core.ui.components.ReorderableColumn(items, onMove, modifier, spacing, key, itemContent)

    @Composable
    fun ToolConfigActions(
        isEditing: Boolean,
        onSave: () -> Unit,
        onCancel: () -> Unit,
        onDelete: (() -> Unit)? = null,
        onReset: (() -> Unit)? = null,
        saveEnabled: Boolean = true
    ) = com.assistant.core.ui.components.ToolConfigActions(
        isEditing, onSave, onCancel, onDelete, onReset, saveEnabled
    )
    
    @Composable
    fun Pagination(
        currentPage: Int,
        totalPages: Int,
        onPageChange: (Int) -> Unit,
        showPageInfo: Boolean = true
    ) = com.assistant.core.ui.components.Pagination(
        currentPage, totalPages, onPageChange, showPageInfo
    )

    @Composable
    fun AIThinkingIndicator() = CurrentTheme.current.AIThinkingIndicator()

    // =====================================
    // AI MESSAGING COMPONENTS
    // =====================================

    @Composable
    fun MessageBubble(
        sender: com.assistant.core.ai.data.MessageSender,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.MessageBubble(sender, content)

    @Composable
    fun InteractionCard(
        title: String,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
        actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
    ) = CurrentTheme.current.InteractionCard(title, content, actions)
}