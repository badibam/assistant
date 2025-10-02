package com.assistant.core.ui
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.themes.ThemeIconManager
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
    
    @Composable
    fun TextField(
        fieldType: FieldType,
        state: ComponentState = ComponentState.NORMAL,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String
    ) = CurrentTheme.current.TextField(fieldType, state, value, onChange, placeholder, FieldModifier())
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    fun Text(
        text: String,
        type: TextType,
        fillMaxWidth: Boolean = false,
        textAlign: TextAlign? = null
    ) {
        CurrentTheme.current.Text(text, type, fillMaxWidth, textAlign)
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
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Card(type, size, content)
    
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
    
    // =====================================
    // SYSTEM
    // =====================================
    
    @Composable
    fun LoadingIndicator(size: Size = Size.M) = 
        CurrentTheme.current.LoadingIndicator(size)
    
    @Composable
    fun Icon(
        iconName: String,
        themeName: String = "default",
        size: Dp = 24.dp,
        contentDescription: String? = null,
        tint: androidx.compose.ui.graphics.Color? = null,
        background: androidx.compose.ui.graphics.Color? = null
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        if (ThemeIconManager.iconExists(context, themeName, iconName)) {
            val iconResource = ThemeIconManager.getIconResource(context, themeName, iconName)
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
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Dialog(type, onConfirm, onCancel, content)
    
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
    
    @Composable
    fun FormField(
        label: String,
        value: String,
        onChange: (String) -> Unit,
        fieldType: FieldType = FieldType.TEXT,
        required: Boolean = true,
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
        required: Boolean = true
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
    
    @Composable
    fun ToggleField(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        trueLabel: String? = null,
        falseLabel: String? = null,
        required: Boolean = true
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        CurrentTheme.current.ToggleField(
            label, 
            checked, 
            onCheckedChange, 
            trueLabel ?: s.shared("ui_toggle_enabled"), 
            falseLabel ?: s.shared("ui_toggle_disabled"), 
            required
        )
    }
    
    @Composable
    fun SliderField(
        label: String,
        value: Int,
        onValueChange: (Int) -> Unit,
        range: IntRange,
        minLabel: String = "",
        maxLabel: String = "",
        required: Boolean = true
    ) = CurrentTheme.current.SliderField(label, value, onValueChange, range, minLabel, maxLabel, required)
    
    @Composable
    fun CounterField(
        label: String,
        incrementButtons: List<Pair<String, Int>>, // Pairs of (displayText, incrementValue)
        decrementButtons: List<Pair<String, Int>> = emptyList(),
        onIncrement: (Int) -> Unit,
        required: Boolean = true
    ) = CurrentTheme.current.CounterField(label, incrementButtons, decrementButtons, onIncrement, required)
    
    @Composable
    fun DynamicList(
        label: String,
        items: List<String>,
        onItemsChanged: (List<String>) -> Unit,
        placeholder: String? = null,
        required: Boolean = true,
        minItems: Int = 0,
        maxItems: Int = Int.MAX_VALUE
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        CurrentTheme.current.DynamicList(
            label, 
            items, 
            onItemsChanged, 
            placeholder ?: s.shared("ui_new_item_placeholder"), 
            required, 
            minItems, 
            maxItems
        )
    }
    
    // =====================================
    // BUTTONS WITH AUTOMATIC ICONS
    // =====================================
    
    // Old predefined buttons removed - use UI.ActionButton instead
    
    // =====================================
    // SPECIALIZED COMPONENTS
    // =====================================
    
    @Composable
    fun ZoneCard(
        zone: Zone,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { }
    ) {
        // Themed container + standard content with UI.*
        CurrentTheme.current.ZoneCardContainer(onClick = onClick, onLongClick = onLongClick) {
            Column {
                Text(zone.name, TextType.TITLE)
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
    ) = CurrentTheme.current.PageHeader(title, subtitle, icon, leftButton, rightButton, onLeftClick, onRightClick)
    
    @Composable
    fun ToolCardHeader(
        tool: ToolInstance,
        context: android.content.Context
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Real icon
            val iconName = JSONObject(tool.config_json).optString("icon_name", "activity")
            Icon(
                iconName = iconName,
                size = 24.dp,
                contentDescription = null
            )
            
            // Instance name
            val toolInstanceName = JSONObject(tool.config_json).optString("name", "Unnamed")
            Text(toolInstanceName, TextType.BODY)
        }
    }
    
    @Composable
    fun ToolCard(
        tool: ToolInstance,
        displayMode: DisplayMode,
        context: android.content.Context,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { }
    ) {
        // Content defined at core level + tool types with UI.*
        CurrentTheme.current.ToolCardContainer(
            displayMode = displayMode,
            onClick = onClick, 
            onLongClick = onLongClick
        ) {
            when (displayMode) {
                DisplayMode.ICON -> {
                    // TODO: Icon only via tool type
                    Text("T", TextType.BODY) // Placeholder
                }
                DisplayMode.MINIMAL -> {
                    ToolCardHeader(tool, context)
                }
                DisplayMode.LINE -> {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left half: ToolCardHeader centered vertically and horizontally
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            ToolCardHeader(tool, context)
                        }
                        
                        // Use Box + weight for text
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            UI.Text(
                                text = ToolTypeManager.getToolTypeName(tool.tool_type, context),
                                type = TextType.BODY,
                                fillMaxWidth = true,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                DisplayMode.CONDENSED, DisplayMode.EXTENDED, DisplayMode.SQUARE, DisplayMode.FULL -> {
                    Column {
                        Row {
                            // Icon + title on left (fixed part)
                            ToolCardHeader(tool, context)
                            // Free zone at top right defined by tool type
                            Box {
                                // TODO: Top free content defined by tool type according to mode
                            }
                        }
                        // Free zone below defined by tool type
                        Box {
                            // TODO: Bottom free content defined by tool type according to mode
                        }
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
    
    // =====================================
    // VALIDATION HELPERS
    // =====================================
    
    /**
     * Unified validation helper for all tooltypes.
     */
    object ValidationHelper {
        fun validateAndSave(
            toolTypeName: String,
            configData: Map<String, Any>,
            context: android.content.Context,
            schemaType: String = "config",
            onSuccess: (String) -> Unit,
            onError: ((String) -> Unit)? = null
        ): Boolean = com.assistant.core.tools.ui.ValidationHelper.validateAndSave(
            toolTypeName, configData, context, schemaType, onSuccess, onError
        )
    }
    
    @Composable
    fun Pagination(
        currentPage: Int,
        totalPages: Int,
        onPageChange: (Int) -> Unit,
        showPageInfo: Boolean = true
    ) = com.assistant.core.ui.components.Pagination(
        currentPage, totalPages, onPageChange, showPageInfo
    )
}