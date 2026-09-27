package com.assistant.core.themes

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ColorScheme
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Size
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.FeedbackType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.FieldModifier

/**
 * ThemeContract - Interface that all themes must implement
 * ONLY VISUAL components (themed)
 * 
 * LAYOUTS: use Compose Row/Column/Box/Spacer directly
 * VISUALS: defined by this contract for theming
 * 
 * Each theme (DefaultTheme, RetroTheme, etc.) implements this interface
 * to provide its own visual style to components
 */
interface ThemeContract {

    /** Who draws this theme's icons: Lucide, or the theme itself, all of them. */
    val iconSource: com.assistant.core.icons.IconSource
    
    // =====================================
    // LAYOUTS: USE COMPOSE DIRECTLY
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // NO interface - direct access for maximum flexibility
    
    // =====================================
    // INTERACTIVE
    // =====================================
    
    @Composable
    fun Button(
        type: ButtonType,
        size: Size,
        state: ComponentState,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun ActionButton(
        action: ButtonAction,
        display: ButtonDisplay,
        size: Size,
        type: ButtonType?,
        enabled: Boolean,
        requireConfirmation: Boolean,
        confirmMessage: String?,
        onClick: () -> Unit
    )
    
    @Composable
    fun TextField(
        fieldType: FieldType,
        state: ComponentState,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String,
        fieldModifier: FieldModifier
    )
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    fun Text(
        text: String,
        type: TextType,
        fillMaxWidth: Boolean,
        textAlign: TextAlign?
    )
    
    @Composable
    fun Card(
        type: CardType,
        size: Size,
        highlight: Boolean,
        content: @Composable () -> Unit
    )

    @Composable
    fun StatusIndicator(
        color: androidx.compose.ui.graphics.Color,
        size: Dp
    )

    /**
     * A short colored label: an option of a CHOICE field whose config gives it a color.
     * The theme draws it and decides the actual color of [color] in the current palette.
     */
    @Composable
    fun Tag(
        text: String,
        color: TagColor
    )

    /**
     * A horizontal gauge filled to [fraction] (0 to 1): where a value stands between two bounds,
     * such as a SCALE field's value on its scale.
     */
    @Composable
    fun Gauge(fraction: Float)

    /** A horizontal line parting two parts of a screen or a card. */
    @Composable
    fun Divider()

    /**
     * The grip an item of a reorderable list is dragged by. Only its look: the gesture is the
     * core's (`ReorderableColumn`), which wraps it.
     */
    @Composable
    fun DragHandle()

    /**
     * An item of a reorderable list, [lifted] while it is being dragged. Only its look: where it
     * sits, and the gap the others open, are the core's.
     */
    @Composable
    fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit)

    /**
     * The actual color a tag color name takes in [paletteId], for the swatches a color is
     * chosen from. Every name of TagColor has one in every palette.
     */
    fun getTagColor(color: TagColor, paletteId: String): androidx.compose.ui.graphics.Color

    // =====================================
    // FEEDBACK SYSTEM
    // =====================================
    
    fun Toast(
        context: Context,
        message: String,
        duration: Duration = Duration.SHORT
    )
    
    @Composable
    fun Snackbar(
        type: FeedbackType,
        message: String,
        action: String?,
        onAction: (() -> Unit)?
    )

    @Composable
    fun ConfirmDialog(
        title: String,
        message: String,
        confirmText: String?,
        cancelText: String?,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    )

    // =====================================
    // SYSTEM
    // =====================================
    
    @Composable
    fun LoadingIndicator(size: Size)
    
    @Composable
    fun Icon(
        resourceId: Int,
        size: Dp,
        contentDescription: String?,
        tint: androidx.compose.ui.graphics.Color?,
        background: androidx.compose.ui.graphics.Color?
    )
    
    @Composable
    fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        confirmEnabled: Boolean,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun DatePicker(
        selectedDate: String,
        onDateSelected: (String) -> Unit,
        onDismiss: () -> Unit
    )
    
    @Composable
    fun TimePicker(
        selectedTime: String,
        onTimeSelected: (String) -> Unit,
        onDismiss: () -> Unit
    )
    
    // =====================================
    // SPECIALIZED CONTAINERS (appearance only)
    // =====================================
    
    @Composable
    fun ZoneCardContainer(
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun ToolCardContainer(
        displayMode: DisplayMode,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        leftButton: ButtonAction?,
        rightButton: ButtonAction?,
        onLeftClick: (() -> Unit)?,
        onRightClick: (() -> Unit)?
    )
    
    // =====================================
    // FORMULAIRES
    // =====================================
    
    /**
     * The label of a field, marking whether it must be answered before the form is confirmed: each
     * input draws its own with it, and an input made of several parts (a range, a duration) above
     * them. How [required] shows is the theme's choice.
     */
    @Composable
    fun FieldLabel(label: String, required: Boolean)

    @Composable
    fun FormField(
        label: String,
        value: String,
        onChange: (String) -> Unit,
        fieldType: FieldType,
        state: ComponentState,
        readonly: Boolean,
        onClick: (() -> Unit)?,
        contentDescription: String?,
        required: Boolean,
        fieldModifier: FieldModifier
    )
    
    @Composable
    fun FormSelection(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
        required: Boolean
    )
    
    @Composable
    fun FormActions(
        content: @Composable RowScope.() -> Unit
    )
    
    @Composable
    fun Checkbox(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String?
    )
    
    /**
     * Yes or no: two buttons, [trueLabel] and [falseLabel]. [value] null is no answer yet, shown
     * with neither chosen. Touching the chosen button again empties it when [emptiable], and
     * changes nothing otherwise. A blank [label] shows the buttons alone. [compact] buttons take
     * only the width of their labels, to sit beside other content (an on/off state on a card).
     */
    @Composable
    fun BooleanField(
        label: String,
        value: Boolean?,
        onValueChange: (Boolean?) -> Unit,
        trueLabel: String,
        falseLabel: String,
        required: Boolean,
        emptiable: Boolean,
        compact: Boolean
    )

    /**
     * A slider from [min] to [max] that stops every [step] from [min]; hands back the stop reached.
     * [value] null is no answer yet, shown as such and not as a position. Any touch on the track
     * answers, the minimum included; an answer that is not [required] can be emptied (null).
     */
    @Composable
    fun SliderField(
        label: String,
        value: Double?,
        onValueChange: (Double?) -> Unit,
        min: Double,
        max: Double,
        step: Double,
        minLabel: String,
        maxLabel: String,
        required: Boolean
    )
    
    // =====================================
    // PALETTE SYSTEM
    // =====================================
    
    /**
     * Gets all base palettes supported by this theme
     * Every theme must provide LIGHT and DARK as minimum
     * 
     * @return List of base palettes (LIGHT, DARK) adapted to theme style
     */
    fun getBasePalettes(): List<ThemePalette>
    
    /**
     * Gets custom palettes specific to this theme
     * Optional - themes can return empty list if no custom palettes
     * 
     * @return List of theme-specific custom palettes
     */
    fun getCustomPalettes(): List<ThemePalette>
    
    /**
     * Gets all available palettes (base + custom)
     * Convenience method that combines base and custom palettes
     * 
     * @return List of all palettes available for this theme
     */
    fun getAllPalettes(): List<ThemePalette> {
        return getBasePalettes() + getCustomPalettes()
    }
    
    /**
     * Gets ColorScheme for specific palette
     * 
     * @param paletteId The palette identifier (e.g., "default_light", "glass_frosted")
     * @return ColorScheme for the palette, or default if not found
     */
    fun getColorScheme(paletteId: String): ColorScheme

    /**
     * AI Thinking Indicator
     * Visual indicator shown while waiting for AI response
     */
    @Composable
    fun AIThinkingIndicator()

    // =====================================
    // AI MESSAGING COMPONENTS
    // =====================================

    /**
     * MessageBubble - Container for AI/User/System messages in chat
     *
     * Themed container with alignment and appearance based on message sender.
     * The theme controls borders, colors, shadows, and positioning.
     *
     * @param sender Who sent the message (USER, AI, SYSTEM)
     * @param content Message content to display
     */
    @Composable
    fun MessageBubble(
        sender: com.assistant.core.ai.data.MessageSender,
        content: @Composable () -> Unit
    )

    /**
     * InteractionCard - Themed card for user interactions (validation, communication modules)
     *
     * Highlighted card to draw attention to actions requiring user response.
     * Theme controls border style, highlight color, and overall appearance.
     *
     * @param title Title of the interaction
     * @param content Main content area
     * @param actions Action buttons area (typically CONFIRM/CANCEL)
     */
    @Composable
    fun InteractionCard(
        title: String,
        content: @Composable ColumnScope.() -> Unit,
        actions: @Composable RowScope.() -> Unit
    )

    // Old buttons removed - use ActionButton
}