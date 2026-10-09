package app.treelune.core.themes

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ColorScheme
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.Size
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.FeedbackType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.FieldModifier

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

    /** The theme's name, in the app's language, from its own strings (themes/<id>/strings.xml). */
    fun name(context: Context): String

    /** Who draws this theme's icons: Lucide, or the theme itself, all of them. */
    val iconSource: app.treelune.core.icons.IconSource

    /**
     * The width of a cell of the tile grid (GridLayout), in pixels, given the width there is for
     * its four columns and the three gaps between them (gridColumnGapPx), in pixels; the grid is
     * centered in what it leaves. Composable, as a size
     * in whole cells of the font depends on the screen's density.
     */
    @Composable
    fun gridCellPx(availableWidthPx: Int): Int

    /**
     * The height of a row of the tile grid, in pixels, for cells [cellPx] wide: their width for
     * square cells, or near it where a theme needs the lines of a tile to fall on whole pixels.
     */
    @Composable
    fun gridRowPx(cellPx: Int): Int

    /**
     * The space between two columns of the tile grid, in pixels, left empty by the grid: a theme
     * whose tiles keep their gap inside their cells gives none.
     */
    @Composable
    fun gridColumnGapPx(): Int

    /** The space between two rows of the tile grid, in pixels, as gridColumnGapPx is between columns. */
    @Composable
    fun gridRowGapPx(): Int

    /**
     * The raw resource of the sound that answers [signal] in this theme, or null for silence
     * (UISounds plays it).
     */
    fun sound(signal: app.treelune.core.ui.sound.UISignal): Int?

    /**
     * How far a tool's tile frame (ToolCardContainer) keeps its content from the edge of its
     * cells, on each side, in [displayMode]: the space between tiles and the frame's own margin.
     */
    @Composable
    fun tileFrame(displayMode: DisplayMode): Dp

    /**
     * The size of a named space (UI.Space). Composable, as a size in whole cells depends on the
     * screen's density.
     */
    @Composable
    fun spacing(spacing: app.treelune.core.ui.Spacing): Dp

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
    
    /**
     * A button that floats over the screen's content (the chat's, on the home screen): its icon on
     * a fill of its own, for what scrolls under it never to show through.
     */
    @Composable
    fun FloatingButton(action: ButtonAction, onClick: () -> Unit)

    @Composable
    fun ActionButton(
        action: ButtonAction,
        display: ButtonDisplay,
        size: Size,
        type: ButtonType?,
        enabled: Boolean,
        requireConfirmation: Boolean,
        confirmMessage: String?,
        /** Shown as switched on while what it opens lasts (a group's edit mode), switched off by a press again */
        active: Boolean = false,
        onClick: () -> Unit
    )
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    /** @param maxLines Lines shown at most, a cut text ending with an ellipsis (a tile's lines) */
    fun Text(
        text: String,
        type: TextType,
        fillMaxWidth: Boolean,
        textAlign: TextAlign?,
        maxLines: Int = Int.MAX_VALUE
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

    /** The color of a state (UI.StatusIndicator, a warning's icon), in the current palette. */
    @Composable
    fun statusColor(status: app.treelune.core.ui.StatusColor): androidx.compose.ui.graphics.Color

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

    /**
     * The style of a drawing's texts (DrawShape.Label): whoever lays a drawing out measures its
     * texts in it, so the theme draws them in it too.
     */
    @Composable
    fun drawingTextStyle(): androidx.compose.ui.text.TextStyle

    /**
     * The screen pixels one pixel of the theme's drawings takes: whoever lays a drawing out gives
     * a bar a width that is a whole number of them, so bars of one width are drawn equal. One for
     * a theme drawn smooth; a retro theme's whole factor.
     */
    @Composable
    fun drawingUnit(): Float

    /**
     * A drawing laid out by whoever made it (Drawing: a chart, a preview), drawn [modifier] sized
     * to it: every shape at its place, the palette's names and their mixes in the theme's colors
     * for them, its inks at their level, what is missing marked as such (hatched here, a screen
     * of pixels for a retro theme).
     */
    @Composable
    fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier)

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
     * The actual color a tag color name takes in [mode], for the swatches a color is chosen from.
     * Every name of TagColor has one in both modes; a hue shift leaves it as it is, its hue being
     * its meaning.
     */
    fun getTagColor(color: TagColor, mode: PaletteMode): androidx.compose.ui.graphics.Color

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
    
    /**
     * The icon a zone or a tool is shown by, drawn [size] across, in its [color]: null for a
     * neutral icon (docs/design/icon-colors.md). How a colour shows is the theme's, and it may
     * set the icon on a badge larger than [size]: a neutral icon then takes the same room, so
     * the names beside icons line up whatever their colours.
     */
    @Composable
    fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?)

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
    
    /** A view over the whole screen (the app's root, FullScreenDialog), on the theme's background. */
    @Composable
    fun FullScreen(content: @Composable () -> Unit)

    /**
     * The band across the top of a full-screen view, holding what its caller puts in a row: a
     * title, a state, buttons.
     */
    @Composable
    fun HeaderBar(content: @Composable RowScope.() -> Unit)

    /** A zone's tile, filling the cells the home screen's grid gives it, as a tool's does. */
    @Composable
    fun ZoneCardContainer(
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    )
    
    /** The mark on a tool's or a zone's icon when something waits for the user there. */
    @Composable
    fun WaitingMark()

    /** One cell of a grid in edit mode, filling it: the light lines that show the cells, and so the holes. */
    @Composable
    fun GridCell()

    /**
     * A tile of a grid as it stands in edit mode ([state]): the one chosen stands out from the
     * others, and the cells under the others (GridCell) show through them, so the holes are seen.
     * The rest of the screen, while a grid is edited, is set aside the same way (Faded).
     */
    @Composable
    fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit)

    /** The mark on a tool's or a zone's icon when a stopwatch runs on one of its entries. */
    @Composable
    fun RunningMark()

    /**
     * A tool's tile, filling the cells the grid gives it (ToolGrid): the theme draws its frame
     * and the space between tiles inside them, never a size of its own.
     */
    @Composable
    fun ToolCardContainer(
        displayMode: DisplayMode,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    )
    
    /**
     * A page's title, its icon and its subtitle, across the whole width: the page's buttons are in
     * the bar over it, with the breadcrumb (UI.PageHeader).
     */
    @Composable
    fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        /** The icon's colour, for a zone's or a tool's page; null for a neutral icon. */
        iconColor: TagColor?,
        /**
         * The app's mark in place of [icon], the home screen's: its launcher icon drawn the
         * theme's way, in the launcher's colours whatever the palette (LauncherMark, or the
         * theme's own drawing of it).
         */
        appMark: Boolean
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

    /**
     * The one text input of the theme. Its value is the text with its selection and its
     * composition (TextFieldValue), which a Compose text field takes as it is: the cursor survives
     * the round trip, so a button can insert where it stands (the formula editor).
     *
     * [label] names the field above it and stands inside it while it is empty; without
     * [labelAbove], it stands only inside: a field whose place says what it is for (a message).
     */
    @Composable
    fun FormField(
        label: String,
        value: TextFieldValue,
        onChange: (TextFieldValue) -> Unit,
        fieldType: FieldType,
        state: ComponentState,
        readonly: Boolean,
        onClick: (() -> Unit)?,
        contentDescription: String?,
        required: Boolean,
        fieldModifier: FieldModifier,
        labelAbove: Boolean
    )
    
    /**
     * A choice among [options], [shown] written in its field. [selected] is the index of the
     * option chosen, null for none. [sections] is empty, or gives each option the title of its
     * section (null for none): a title is drawn, not chosen, above the first option of each run
     * of options sharing it. [icons] is empty, or gives each option the icon it is shown by (null
     * for none), drawn as an item's (UI.ItemIcon) before it, and before [shown] for the one chosen.
     */
    @Composable
    fun FormSelection(
        label: String,
        options: List<String>,
        sections: List<String?>,
        icons: List<OptionIcon?>,
        selected: Int?,
        shown: String,
        onSelect: (Int) -> Unit,
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
     * An on/off setting that takes effect as it is switched (a session's validation), [label]
     * across the width at the start and the switch at the end.
     */
    @Composable
    fun Switch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String
    )

    /** A row of tabs across the width, one per label, [selected] the index of the one shown. */
    @Composable
    fun Tabs(
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
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

    /**
     * Under the hue shift's slider (Appearance.hueShift), the colour each of [shifts] gives the
     * theme in the mode shown, across the slider's width: a shift lies under the stop that
     * chooses it, so that one sees where to go instead of trying.
     */
    @Composable
    fun HueStrip(shifts: IntRange)

    // =====================================
    // PALETTE SYSTEM
    // =====================================
    
    /**
     * The Material colours of the theme in [mode], turned [hueShift] degrees round the hue circle
     * (Appearance.hueShift), the colours of the states kept: for what Material still draws.
     */
    fun getColorScheme(mode: PaletteMode, hueShift: Int): ColorScheme

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
        sender: app.treelune.core.ai.data.MessageSender,
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