package com.assistant.themes.retro

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog as WindowDialog
import com.assistant.R
import com.assistant.core.ai.data.MessageSender
import com.assistant.core.icons.IconSource
import com.assistant.core.icons.Icons
import com.assistant.core.strings.Strings
import com.assistant.core.themes.PaletteMode
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.themes.TagColor
import com.assistant.core.themes.ThemeContract
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.Duration
import com.assistant.core.ui.FeedbackType
import com.assistant.core.ui.FieldInput
import com.assistant.core.ui.FieldModifier
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.Size
import com.assistant.core.ui.SliderSteps
import com.assistant.core.ui.Spacing
import com.assistant.core.ui.StatusColor
import com.assistant.core.ui.TextType
import com.assistant.core.ui.confirmMessage
import com.assistant.core.ui.defaultType
import com.assistant.core.ui.label
import com.assistant.core.ui.sound.UISignal
import com.assistant.core.ui.sound.scrollEndSound
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateUtils
import com.assistant.themes.default.DefaultDrawing
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The retro theme (docs/design/retro-theme.md): the console, softened. Pixel art on an integer
 * grid (RetroGrid), the Cartouche font, frames written in its own pieces (RetroFrame), a palette
 * of a handful of OKLCH numbers set on a bench (RetroPalette), interface sounds.
 *
 * What it holds to (pixel-ui): one drawing pixel is a whole number of screen pixels; frames,
 * margins and spaces fall on whole cells; a frame says "object", an input stays on the page; grey
 * says "off"; a pressed element swaps its border's two tones, without a ripple; movement goes by
 * jumps, never a fade; softness is painted, never filtered.
 */
@OptIn(ExperimentalFoundationApi::class)
object RetroTheme : ThemeContract {

    override fun name(context: android.content.Context): String =
        com.assistant.core.strings.Strings.`for`(context = context, theme = "retro").theme("name")

    /**
     * Its own icons, every one: Lucide's pixelized in the register's box (icons/, written by
     * scripts/pixelize_icons.py, hand retouches in icon-retouches.txt).
     */
    override val iconSource = IconSource.OWN

    // =====================================
    // GRID, SPACES, SOUNDS
    // =====================================

    /** The most cells four cases take with a cell between two: the grid spans the screen's width. */
    @Composable
    override fun gridCellPx(availableWidthPx: Int): Int {
        val grid = retroGrid()
        return ((availableWidthPx / grid.cellPx - 3) / 4).coerceAtLeast(2) * grid.cellPx
    }

    /**
     * The even number of drawing pixels nearest the case's width, one more when the width is
     * odd: with the row gap (gridRowGapPx), every tile's lines are then whole pixels.
     */
    @Composable
    override fun gridRowPx(cellPx: Int): Int {
        val grid = retroGrid()
        val width = cellPx / grid.scale
        return grid.px(if (width % 2 == 0) width else width + 1)
    }

    /** A cell between two tiles, outside them: the grid spans every cell the screen has. */
    @Composable
    override fun gridColumnGapPx(): Int = retroGrid().cellPx

    /**
     * Ten drawing pixels between two rows, one short of a cell, so that a tile's inside, its frame's
     * cell taken off each side, splits into whole pixels for every height a tile has: in two lines
     * on one row (2h - 22), four on two (4h + 10 - 22), eight on four (8h + 30 - 22), h being half
     * a row. The vertical is free of the cells; only the width keeps to them.
     */
    @Composable
    override fun gridRowGapPx(): Int = retroGrid().px(RetroGrid.ROW_GAP)

    override fun sound(signal: UISignal): Int? = when (signal) {
        UISignal.CONFIRM -> R.raw.retro_confirm
        UISignal.ENTER -> R.raw.retro_enter
        UISignal.BACK -> R.raw.retro_back
        UISignal.OPEN -> R.raw.retro_open
        UISignal.CLOSE -> R.raw.retro_close
        UISignal.TOGGLE -> R.raw.retro_toggle
        UISignal.STEP -> R.raw.retro_step
        UISignal.SCROLL_END -> R.raw.retro_scroll_end
        UISignal.REFUSE -> R.raw.retro_refuse
    }

    /** The frame's cell: the content of a tile starts one cell in. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp = retroGrid().cells(1)

    /** Every space a whole number of cells: the three small ones one cell, then two, then three. */
    @Composable
    override fun spacing(spacing: Spacing): Dp = retroGrid().cells(
        when (spacing) {
            Spacing.XS, Spacing.S, Spacing.M -> 1
            Spacing.L -> 2
            Spacing.XL -> 3
        }
    )

    // =====================================
    // INTERACTIVE
    // =====================================

    @Composable
    override fun Button(type: ButtonType, size: Size, state: ComponentState, onClick: () -> Unit, content: @Composable () -> Unit) {
        val enabled = state == ComponentState.NORMAL || state == ComponentState.SUCCESS
        Pressable(onClick = onClick, enabled = enabled) { pressed ->
            TouchRoom {
                Framed(compact = true, minRows = touchRows, pressed = pressed, off = !enabled) {
                    CompositionLocalProvider(LocalRetroInk provides buttonInk(type, enabled)) { content() }
                }
            }
        }
    }

    /**
     * An icon's frame, a frame having its own fill: the size of a floating button
     * (RetroGrid.floating), its icon a size up.
     */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed ->
            IconFrame(action.iconName, action.label(), buttonInk(action.defaultType(), true), retroGrid().floating, pressed, retroGridUp())
        }
    }

    @Composable
    override fun ActionButton(
        action: ButtonAction,
        display: ButtonDisplay,
        size: Size,
        type: ButtonType?,
        enabled: Boolean,
        requireConfirmation: Boolean,
        confirmMessage: String?,
        active: Boolean,
        onClick: () -> Unit
    ) {
        var confirming by rememberSaveable { mutableStateOf(false) }
        val ink = buttonInk(if (active) ButtonType.PRIMARY else type ?: action.defaultType(), enabled)
        val label = action.label()
        Pressable(onClick = { if (requireConfirmation) confirming = true else onClick() }, enabled = enabled) { pressed ->
            // Switched on, it stays pressed: the two tones swapped while what it opens lasts
            val down = pressed || active
            if (display == ButtonDisplay.ICON) {
                IconFrame(action.iconName, label, ink, retroGrid().touch, down, off = !enabled)
            } else {
                TouchRoom {
                    Framed(compact = true, minRows = touchRows, pressed = down, off = !enabled) { Line(label, retroGrid().text, ink) }
                }
            }
        }
        if (confirming && requireConfirmation) {
            Dialog(
                type = if (action == ButtonAction.DELETE) DialogType.DANGER else DialogType.CONFIRM,
                onConfirm = { confirming = false; onClick() },
                onCancel = { confirming = false },
                confirmEnabled = true
            ) {
                Text(confirmMessage ?: action.confirmMessage(), TextType.BODY, false, null)
            }
        }
    }

    /** A button's ink: off is dim, a destructive action the error's colour, a main one strong. */
    @Composable
    private fun buttonInk(type: ButtonType, enabled: Boolean): Color {
        val s = retroColors.panel
        return when {
            !enabled -> s.dim
            type == ButtonType.DANGER -> s.error
            type == ButtonType.PRIMARY -> s.strong
            else -> s.ink
        }.srgb
    }

    // =====================================
    // DISPLAY
    // =====================================

    /**
     * The register's size: a title stands out by the strong ink, a caption steps back by the thin
     * weight in the dim ink. Cartouche has no bold. A heading (a tile's or a section's name) and a
     * page's title (PageHeader) are a size up, one more screen pixel to each of their drawing pixels: still whole pixels,
     * though larger than the screen's others and off the cells.
     */
    @Composable
    override fun Text(text: String, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val grid = retroGrid()
        val s = retroSurface
        val override = LocalRetroInk.current
        val (style, ink) = when (type) {
            TextType.HEADING -> retroGridUp().text to (override ?: s.strong.srgb)
            TextType.TITLE, TextType.SUBTITLE, TextType.STRONG -> grid.text to (override ?: s.strong.srgb)
            TextType.BODY -> grid.text to (override ?: s.ink.srgb)
            TextType.CAPTION, TextType.LABEL -> grid.thin to s.dim.srgb
            TextType.ERROR -> grid.text to s.error.srgb
            TextType.WARNING -> grid.text to s.warning.srgb
        }
        Line(text, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines)
    }

    /**
     * A card is a frame, a highlighted one keeping its tones swapped. A section's header is no
     * frame: its title on the screen's ground over a divider, so that the tiles are the only panels
     * and a heading never reads as a thing to touch; its buttons keep their own frames.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        when (type) {
            CardType.SECTION_HEADER -> Column(modifier = Modifier.fillMaxWidth()) {
                content()
                Divider()
            }
            CardType.DEFAULT -> Framed(pressed = highlight) { content() }
        }
    }

    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Line(DOT_FILLED.toString(), retroGrid().text, color)
    }

    @Composable
    override fun statusColor(status: StatusColor): Color {
        val s = retroSurface
        return when (status) {
            StatusColor.SUCCESS -> s.success
            StatusColor.WARNING -> s.warning
            StatusColor.ERROR -> s.error
            StatusColor.INFO -> s.info
            StatusColor.MUTED -> s.muted
        }.srgb
    }

    /** A name on its colour, no border: three pixels each side, one above and under the box. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val grid = retroGrid()
        val colors = retroColors
        Box(
            modifier = Modifier
                .background(colors.tag(color).srgb)
                .padding(start = grid.dp(3), end = grid.dp(2), top = grid.dp(1), bottom = grid.dp(1))
        ) {
            Line(text, grid.text, colors.tagText.srgb, maxLines = 1)
        }
    }

    /** Gauge blocks on the grid: full cells filled, the last one by quarters, the rest the track. */
    @Composable
    override fun Gauge(fraction: Float) {
        val grid = retroGrid()
        val s = retroSurface
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cells = (constraints.maxWidth / grid.cellPx).coerceAtLeast(1)
            GaugeBlocks(cells, fraction, s.ink.srgb, s.borderInner.srgb)
        }
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = retroGrid().thin.copy(color = retroSurface.dim.srgb)

    /**
     * The default theme's strokes for now, in this theme's text and colours (RetroColors.drawing,
     * a series keeping its tag's hue whatever the hue shift): the chart's scene in pixels and
     * screens of dots is still to be drawn (retro-theme.md).
     */
    @Composable
    override fun Drawing(drawing: com.assistant.core.drawing.Drawing, modifier: Modifier) {
        val colors = retroColors
        DefaultDrawing.Draw(drawing, drawingTextStyle(), { colors.drawing(it).srgb }, modifier)
    }

    /** The top edge of a frame across the width, both tones. */
    @Composable
    override fun Divider() {
        val grid = retroGrid()
        val s = retroSurface
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(grid.cells(1))) {
            val columns = constraints.maxWidth / grid.cellPx
            Line(dividerRow(columns, dark = false), grid.text, s.borderOuter.srgb, maxLines = 1)
            Line(dividerRow(columns, dark = true), grid.text, s.borderInner.srgb, maxLines = 1)
        }
    }

    @Composable
    override fun DragHandle() {
        Box(modifier = Modifier.padding(retroGrid().cells(1))) {
            CompositionLocalProvider(LocalRetroInk provides retroSurface.dim.srgb) { NamedIcon("grip-vertical", null) }
        }
    }

    /** Lifted, an item's frames swap their tones: no shadow, no scale, nothing between two pixels. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalRetroLifted provides lifted) { content() }
    }

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = RetroPalettes.colors(mode, 0).tag(color).srgb

    // =====================================
    // FEEDBACK
    // =====================================

    /** A frame at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { RetroToasts.show(message, shown) }
    }

    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val s = retroColors.panel
        val ink = when (type) {
            FeedbackType.SUCCESS -> s.success
            FeedbackType.ERROR -> s.error
            FeedbackType.WARNING -> s.warning
            FeedbackType.INFO -> s.ink
        }.srgb
        Framed(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(retroGrid().cells(1))) {
                Box(modifier = Modifier.weight(1f)) { Line(message, retroGrid().text, ink) }
                if (action != null && onAction != null) WordButton(action, onAction)
            }
        }
    }

    @Composable
    override fun ConfirmDialog(title: String, message: String, confirmText: String?, cancelText: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        val s = Strings.`for`(context = LocalContext.current)
        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(cancelText ?: s.shared("action_cancel"), onDismiss)
            WordButton(confirmText ?: s.shared("action_confirm"), onConfirm, strong = true)
        }) {
            Text(title, TextType.TITLE, false, null)
            Text(message, TextType.BODY, false, null)
        }
    }

    // =====================================
    // SYSTEM
    // =====================================

    @Composable
    override fun LoadingIndicator(size: Size) = Dots()

    /**
     * The icon's box at a whole multiple, the nearest to the size asked for and at least one:
     * every pixel of the icon then covers whole pixels of the screen.
     */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        val grid = retroGrid()
        val times = with(LocalDensity.current) { (size.toPx() / grid.px(RetroGrid.ICON)).roundToInt().coerceAtLeast(1) }
        val pixels = times * RetroGrid.ICON
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint ?: LocalRetroInk.current ?: retroSurface.ink.srgb),
            modifier = Modifier.size(grid.dp(pixels)).let { m -> background?.let { m.background(it) } ?: m }
        )
    }

    @Composable
    override fun Dialog(type: DialogType, onConfirm: () -> Unit, onCancel: () -> Unit, confirmEnabled: Boolean, content: @Composable () -> Unit) {
        val s = Strings.`for`(context = LocalContext.current)
        val cancel = s.shared("action_cancel")
        val (confirmText, cancelText) = when (type) {
            DialogType.CONFIGURE, DialogType.CONFIRM -> s.shared("action_confirm") to cancel
            DialogType.CREATE -> s.shared("action_create") to cancel
            DialogType.EDIT -> s.shared("action_save") to cancel
            DialogType.DANGER -> s.shared("action_delete") to cancel
            DialogType.SELECTION -> null to cancel
            DialogType.INFO -> s.shared("action_ok") to null
        }
        DialogFrame(onDismiss = onCancel, actions = {
            if (cancelText != null) WordButton(cancelText, onCancel)
            if (confirmText != null) WordButton(confirmText, onConfirm, strong = true, danger = type == DialogType.DANGER, enabled = confirmEnabled)
        }) {
            Box(modifier = Modifier.scrollEndSound()) { content() }
        }
    }

    /** A month of days on the grid, arrows between months; the day chosen is lit. */
    @Composable
    override fun DatePicker(selectedDate: String, onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        // "" means nothing chosen yet, and the picker opens on today by contract
        val initial = (DateUtils.parseDateForFilter(selectedDate) ?: System.currentTimeMillis())
            .let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        var chosen by remember { mutableStateOf(initial) }
        var month by remember { mutableStateOf(YearMonth.from(initial)) }
        val today = LocalDate.now(zone)
        val locale = Locale.getDefault()
        val grid = retroGrid()
        val s = Strings.`for`(context = LocalContext.current)
        val panel = retroColors.panel

        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onDateSelected(DateUtils.formatDateForDisplay(chosen.atStartOfDay(zone).toInstant().toEpochMilli()))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlyphButton(ARROW_LEFT) { month = month.minusMonths(1) }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Line(month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale) + " " + month.year, grid.text, panel.strong.srgb)
                }
                GlyphButton(ARROW_RIGHT) { month = month.plusMonths(1) }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Seven columns of whole cells, as wide as the frame allows
                val per = (constraints.maxWidth / grid.cellPx / 7).coerceAtLeast(2)
                val first = WeekFields.of(locale).firstDayOfWeek
                val days = (0 until 7).map { first.plus(it.toLong()) }
                Column {
                    Row { days.forEach { day -> DayCell(per) { Line(day.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale), grid.thin, panel.dim.srgb) } } }
                    val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
                    val cells = lead + month.lengthOfMonth()
                    (0 until (cells + 6) / 7).forEach { week ->
                        Row {
                            (0 until 7).forEach { column ->
                                val n = week * 7 + column - lead + 1
                                if (n < 1 || n > month.lengthOfMonth()) DayCell(per) {}
                                else {
                                    val date = month.atDay(n)
                                    val lit = date == chosen
                                    DayCell(per, onClick = { chosen = date }, lit = lit) {
                                        Line(n.toString(), grid.text, if (lit || date == today) panel.strong.srgb else panel.ink.srgb)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Two counters, hours and minutes, between − and + buttons of a fixed width. */
    @Composable
    override fun TimePicker(selectedTime: String, onTimeSelected: (String) -> Unit, onDismiss: () -> Unit) {
        // "" means nothing chosen yet, and the picker opens on now by contract
        val (h, m) = DateUtils.parseTime(selectedTime)
            ?: ZonedDateTime.now(AppConfigManager.getDateTimeConfig().getZoneId()).let { it.hour to it.minute }
        var hour by remember { mutableIntStateOf(h) }
        var minute by remember { mutableIntStateOf(m) }
        val s = Strings.`for`(context = LocalContext.current)
        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24) { hour = it }
                Line(":", retroGrid().text, retroColors.panel.strong.srgb)
                Counter(minute, 60) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /**
     * The screen's ground, its content a whole number of cells wide and centred: what the width
     * leaves past the last whole cell is shared in two margins, the only ones the screen has, and
     * whatever fills the width lines up on them. The toasts show over its bottom.
     */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        val grid = retroGrid()
        // The width past whole cells, as the two side margins, in whole drawing pixels. Laid out
        // rather than read from BoxWithConstraints, whose content is composed apart: the app's
        // screens, moved in when the theme changes (MainActivity), would not follow the theme there
        Box(modifier = Modifier.fillMaxSize().background(retroColors.screen.ground.srgb).layout { measurable, constraints ->
            val margin = (constraints.maxWidth % grid.cellPx) / 2 / grid.scale * grid.scale
            val placeable = measurable.measure(constraints.copy(
                minWidth = (constraints.minWidth - 2 * margin).coerceAtLeast(0),
                maxWidth = constraints.maxWidth - 2 * margin
            ))
            layout(constraints.maxWidth, placeable.height) { placeable.place(margin, 0) }
        }) {
            CompositionLocalProvider(LocalRetroSurface provides RetroSurface.SCREEN, LocalRetroInk provides null) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /** A row on the screen's ground across the width, closed by a divider. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val grid = retroGrid()
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = grid.dp(RetroGrid.BORDER)),
                horizontalArrangement = Arrangement.spacedBy(grid.cells(1)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
            Divider()
        }
    }

    @Composable
    override fun ZoneCardContainer(onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(onClick, onLongClick, content)

    /** A dot of four pixels in the info colour. */
    @Composable
    override fun WaitingMark() {
        Box(modifier = Modifier.size(retroGrid().dp(4)).background(retroSurface.info.srgb))
    }

    /**
     * A cell of the grid in edit mode: a dotted outline, one pixel lit in two, around the cell.
     */
    @Composable
    override fun GridCell() {
        val grid = retroGrid()
        val dots = retroColors.screen.borderInner.srgb
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                val p = grid.scale.toFloat()
                val w = (size.width / p).toInt()
                val h = (size.height / p).toInt()
                val dot = GeometrySize(p, p)
                for (x in 0 until w step 2) {
                    drawRect(dots, Offset(x * p, 0f), dot)
                    drawRect(dots, Offset(x * p, (h - 1) * p), dot)
                }
                for (y in 0 until h step 2) {
                    drawRect(dots, Offset(0f, y * p), dot)
                    drawRect(dots, Offset((w - 1) * p, y * p), dot)
                }
            }
        )
    }

    /** The font's triangle in the warning colour: something is running. */
    @Composable
    override fun RunningMark() {
        Line(PLAY.toString(), retroGrid().text, retroSurface.warning.srgb)
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(onClick, onLongClick, content)

    /** A tile: a frame filling the cells the grid gives it; its content a cell in from the frame's edge. */
    @Composable
    private fun Tile(onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            Framed(modifier = Modifier.fillMaxSize(), pressed = pressed, fillContent = true) { content() }
        }
    }

    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        leftButton: ButtonAction?,
        rightButton: ButtonAction?,
        onLeftClick: (() -> Unit)?,
        onRightClick: (() -> Unit)?
    ) {
        val grid = retroGrid()
        // Across the width, its buttons on the screen's margins
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(grid.cells(grid.touch)), contentAlignment = Alignment.CenterStart) {
                leftButton?.let { com.assistant.core.ui.UI.ActionButton(action = it, display = ButtonDisplay.ICON, onClick = onLeftClick ?: {}) }
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // The page's icon and title a size up, as a heading is (HEADING)
                val up = retroGridUp()
                Row(horizontalArrangement = Arrangement.spacedBy(grid.cells(1)), verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { NamedIcon(it, null, up) }
                    Line(title, up.text, LocalRetroInk.current ?: retroSurface.strong.srgb, align = TextAlign.Center)
                }
                subtitle?.let { Text(it, TextType.CAPTION, false, TextAlign.Center) }
            }
            Box(modifier = Modifier.width(grid.cells(grid.touch)), contentAlignment = Alignment.CenterEnd) {
                rightButton?.let { com.assistant.core.ui.UI.ActionButton(action = it, display = ButtonDisplay.ICON, onClick = onRightClick ?: {}) }
            }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** The thin weight in the dim ink; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, retroGrid().thin, retroSurface.dim.srgb)
    }

    /** A frame on the page's ground, the label above, the label again inside while it is empty. */
    @Composable
    override fun FormField(
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
    ) {
        val grid = retroGrid()
        val s = retroSurface
        Column(verticalArrangement = Arrangement.spacedBy(grid.dp(3))) {
            if (labelAbove) FieldLabel(label, required)
            if (readonly) {
                val shown = value.text.ifBlank { Strings.`for`(context = LocalContext.current).shared("label_no_value") }
                Box(modifier = if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier) {
                    Text(shown, TextType.BODY, false, null)
                }
            } else {
                val isPassword = fieldType == FieldType.PASSWORD
                var revealed by remember { mutableStateOf(false) }
                Framed(modifier = Modifier.fillMaxWidth(), input = true, compact = true, minRows = touchRows) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = FieldInput.limited(fieldType, onChange),
                            readOnly = state == ComponentState.READONLY,
                            enabled = state != ComponentState.DISABLED,
                            textStyle = grid.text.copy(color = (if (state == ComponentState.ERROR) s.error else s.ink).srgb),
                            cursorBrush = SolidColor(s.strong.srgb),
                            keyboardOptions = FieldInput.keyboardOptions(fieldType),
                            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier
                                .weight(1f)
                                .let { m -> fieldModifier.focusRequester?.let { m.focusRequester(it) } ?: m }
                                .let { m -> fieldModifier.onFocusChanged?.let { m.onFocusChanged(it) } ?: m },
                            decorationBox = { inner ->
                                Box {
                                    if (value.text.isEmpty()) Line(label, grid.thin, s.dim.srgb, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                        if (isPassword) {
                            Box(modifier = Modifier.combinedClickable(onClick = { revealed = !revealed })) {
                                CompositionLocalProvider(LocalRetroInk provides s.dim.srgb) { NamedIcon(if (revealed) "eye-off" else "eye", null) }
                            }
                        }
                    }
                }
            }
        }
    }

    /** The choice shown in an input's frame, an arrow at its end; the options in a window. */
    @Composable
    override fun FormSelection(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, required: Boolean) {
        var open by remember { mutableStateOf(false) }
        val grid = retroGrid()
        val s = retroSurface
        Column(verticalArrangement = Arrangement.spacedBy(grid.dp(3))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Framed(modifier = Modifier.fillMaxWidth(), input = true, compact = true, minRows = touchRows, pressed = pressed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) { Line(selected, grid.text, s.ink.srgb, maxLines = 1) }
                        Line(ARROW_DOWN.toString(), grid.text, s.dim.srgb)
                    }
                }
            }
        }
        if (open) {
            DialogFrame(onDismiss = { open = false }, actions = {}) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val panel = retroColors.panel
                    options.forEach { option ->
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = grid.cells(grid.touch))
                                .combinedClickable(onClick = { onSelect(option); open = false }),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Line(option, grid.text, (if (option == selected) panel.strong else panel.ink).srgb)
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun FormActions(content: @Composable RowScope.() -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(retroGrid().cells(1), Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    /** Symbols of the font, no frame: the check between brackets, or the brackets empty. */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val grid = retroGrid()
        val s = retroSurface
        Pressable(onClick = { onCheckedChange(!checked) }) {
            Row(
                modifier = Modifier.heightIn(min = grid.cells(grid.touch)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(grid.cells(1))
            ) {
                Line(if (checked) "[$CHECK]" else "[ ]", grid.text, (if (checked) s.strong else s.dim).srgb)
                label?.let { Line(it, grid.text, s.ink.srgb) }
            }
        }
    }

    /**
     * The label across, a square frame a touch wide at the end: empty when off, Lucide's check in
     * the strong ink when on. The icon buttons' frame (IconFrame).
     */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val grid = retroGrid()
        val s = retroSurface
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) { pressed ->
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = grid.cells(grid.touch)), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, grid.text, s.ink.srgb) }
                if (checked) IconFrame("check", null, s.strong.srgb, touchRows, pressed)
                else Framed(modifier = Modifier.size(grid.cells(touchRows)), pressed = pressed) { }
            }
        }
    }

    /** The tab shown in a frame, the others plain in the dim ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val grid = retroGrid()
        val s = retroSurface
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    TouchRoom {
                        if (index == selected) {
                            Framed(compact = true, minRows = touchRows, pressed = pressed) { Line(label, grid.text, retroColors.panel.strong.srgb) }
                        } else {
                            Box(modifier = Modifier.height(grid.cells(touchRows)).padding(horizontal = grid.cells(1)), contentAlignment = Alignment.Center) {
                                Line(label, grid.thin, s.dim.srgb)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Two buttons: the chosen one a frame filled with the dim ink, its word in the ground's colour; the other on the page in the dim. */
    @Composable
    override fun BooleanField(
        label: String,
        value: Boolean?,
        onValueChange: (Boolean?) -> Unit,
        trueLabel: String,
        falseLabel: String,
        required: Boolean,
        emptiable: Boolean,
        compact: Boolean
    ) {
        val grid = retroGrid()
        Column(verticalArrangement = Arrangement.spacedBy(grid.dp(3))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(
                modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(grid.cells(1))
            ) {
                listOf(true to trueLabel, false to falseLabel).forEach { (answer, text) ->
                    val chosen = value == answer
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        modifier = if (compact) Modifier else Modifier.weight(1f)
                    ) { pressed ->
                        val fill = if (compact) Modifier else Modifier.fillMaxWidth()
                        Framed(modifier = fill, input = !chosen, compact = true, minRows = touchRows, pressed = pressed, filled = chosen) {
                            Box(modifier = fill, contentAlignment = Alignment.Center) {
                                Line(text, if (chosen) grid.text else grid.thin, (if (chosen) retroColors.panel.ground else retroSurface.dim).srgb)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Gauge blocks across the width, a touch or a drag anywhere on them answering the stop under
     * the finger; no answer yet shows the track alone and "—".
     */
    @Composable
    override fun SliderField(
        label: String,
        value: Double?,
        onValueChange: (Double?) -> Unit,
        min: Double,
        max: Double,
        step: Double,
        minLabel: String,
        maxLabel: String,
        required: Boolean
    ) {
        val grid = retroGrid()
        val s = retroSurface
        val strings = Strings.`for`(context = LocalContext.current)
        val lastStop = SliderSteps.lastStop(min, max, step)
        val decimals = SliderSteps.decimals(min, step)
        Column(verticalArrangement = Arrangement.spacedBy(grid.dp(3))) {
            FieldLabel(label, required)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(grid.cells(1), Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Line(value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—", grid.text, (if (value != null) s.strong else s.dim).srgb)
                if (value != null && !required) WordButton(strings.shared("action_clear"), { onValueChange(null) })
            }
            // The gestures below are installed once: they call the latest onValueChange, which
            // writes into the settings as they are now, not as they were when the slider was drawn
            val latest by androidx.compose.runtime.rememberUpdatedState(onValueChange)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val width = constraints.maxWidth.toFloat()
                val cells = (constraints.maxWidth / grid.cellPx).coerceAtLeast(1)
                fun answer(x: Float) {
                    val raw = min + (x / width).coerceIn(0f, 1f) * (lastStop - min)
                    latest(SliderSteps.snap(raw, min, step))
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = grid.cells(grid.touch))
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val fraction = value?.let { ((it - min) / (lastStop - min)).toFloat() } ?: 0f
                    GaugeBlocks(cells, fraction, s.ink.srgb, s.borderInner.srgb)
                }
            }
            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Line(minLabel, grid.thin, s.dim.srgb)
                    Line(maxLabel, grid.thin, s.dim.srgb)
                }
            }
        }
    }

    // =====================================
    // PALETTES
    // =====================================

    /** For what Material still draws at the app's root: the palette's colours under its names. */
    override fun getColorScheme(mode: PaletteMode, hueShift: Int): ColorScheme {
        val c = RetroPalettes.colors(mode, hueShift)
        val dark = mode == PaletteMode.DARK
        val screen = c.screen
        val panel = c.panel
        return if (dark) darkColorScheme(
            primary = screen.strong.srgb, onPrimary = screen.ground.srgb,
            secondary = screen.ink.srgb, onSecondary = screen.ground.srgb,
            tertiary = screen.warning.srgb, onTertiary = screen.ground.srgb,
            background = screen.ground.srgb, onBackground = screen.ink.srgb,
            surface = screen.ground.srgb, onSurface = screen.ink.srgb,
            surfaceVariant = panel.ground.srgb, onSurfaceVariant = panel.ink.srgb,
            error = screen.error.srgb, onError = screen.ground.srgb,
            outline = screen.borderInner.srgb, outlineVariant = screen.borderInner.srgb
        ) else lightColorScheme(
            primary = screen.strong.srgb, onPrimary = screen.ground.srgb,
            secondary = screen.ink.srgb, onSecondary = screen.ground.srgb,
            tertiary = screen.warning.srgb, onTertiary = screen.ground.srgb,
            background = screen.ground.srgb, onBackground = screen.ink.srgb,
            surface = screen.ground.srgb, onSurface = screen.ink.srgb,
            surfaceVariant = panel.ground.srgb, onSurfaceVariant = panel.ink.srgb,
            error = screen.error.srgb, onError = screen.ground.srgb,
            outline = screen.borderInner.srgb, outlineVariant = screen.borderInner.srgb
        )
    }

    // =====================================
    // AI
    // =====================================

    @Composable
    override fun AIThinkingIndicator() {
        Box(modifier = Modifier.fillMaxWidth().padding(retroGrid().cells(1)), contentAlignment = Alignment.Center) { Dots() }
    }

    /** A message is a frame; the system's stays on the page, an input's frame rather than a panel. */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        Framed(input = sender == MessageSender.SYSTEM) { content() }
    }

    /** A frame whose tones stay swapped, to draw the eye: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: @Composable RowScope.() -> Unit) {
        val grid = retroGrid()
        Framed(modifier = Modifier.fillMaxWidth(), pressed = true) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(grid.cells(1))) {
                Text(title, TextType.TITLE, true, TextAlign.Center)
                content()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(grid.cells(1)), content = actions)
            }
        }
    }

    // =====================================
    // PIECES
    // =====================================

    /**
     * A text in one style and colour, cut with an ellipsis past [maxLines]. It goes to the line at
     * spaces; a word wider than its line, which the font's wide letters make frequent in a narrow
     * column, is split between syllables with a hyphen, by the system's rules for the language.
     */
    @Composable
    private fun Line(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
        BasicText(
            text = text,
            modifier = modifier,
            style = style.copy(
                color = color,
                textAlign = align ?: TextAlign.Unspecified,
                hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
            ),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines
        )
    }

    /** Something touched, told whether a finger is down on it. No ripple: the frame shows it. */
    @Composable
    private fun Pressable(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        onLongClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        content: @Composable (pressed: Boolean) -> Unit
    ) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        Box(modifier = modifier.combinedClickable(interactionSource = source, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)) {
            content(pressed)
        }
    }

    /**
     * A button's word, a field's line, a yes or no: a frame as tall as a finger's target
     * (RetroGrid.touch), its line centred in it.
     */
    private val touchRows: Int
        @Composable get() = retroGrid().touch

    /** A target a finger can take: at least a finger's target tall, the frame centred in the empty rows. */
    @Composable
    private fun TouchRoom(content: @Composable () -> Unit) {
        Box(modifier = Modifier.heightIn(min = retroGrid().cells(touchRows)), contentAlignment = Alignment.Center) { content() }
    }

    /** An icon in a square frame [side] cells wide, centred; the icon drawn at [grid]'s scale. */
    @Composable
    private fun IconFrame(name: String, description: String?, ink: Color, side: Int, pressed: Boolean, grid: RetroGrid = retroGrid(), off: Boolean = false) {
        Framed(modifier = Modifier.size(retroGrid().cells(side)), pressed = pressed, off = off) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalRetroInk provides ink) { NamedIcon(name, description, grid) }
            }
        }
    }

    /** A framed word that acts. */
    @Composable
    private fun WordButton(text: String, onClick: () -> Unit, strong: Boolean = false, danger: Boolean = false, enabled: Boolean = true) {
        val panel = retroColors.panel
        val ink = when {
            !enabled -> panel.dim
            danger -> panel.error
            strong -> panel.strong
            else -> panel.ink
        }.srgb
        Pressable(onClick = onClick, enabled = enabled) { pressed ->
            TouchRoom { Framed(compact = true, minRows = touchRows, pressed = pressed, off = !enabled) { Line(text, retroGrid().text, ink) } }
        }
    }

    /** A framed glyph of the font that acts: an arrow, a minus, a plus. A square, a finger's target wide. */
    @Composable
    private fun GlyphButton(glyph: Char, onClick: () -> Unit) {
        val grid = retroGrid()
        Pressable(onClick = onClick) { pressed ->
            Framed(modifier = Modifier.size(grid.cells(grid.touch)), pressed = pressed) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Line(glyph.toString(), grid.text, retroColors.panel.ink.srgb)
                }
            }
        }
    }

    /** A Lucide icon by name, in the register's icon box. */
    @Composable
    private fun NamedIcon(name: String, description: String?, grid: RetroGrid = retroGrid()) {
        val resource = requireNotNull(Icons.drawable(LocalContext.current, name)) { "No drawable for the icon $name" }
        // Its box once, at [grid]'s scale: drawn here rather than by Icon, which counts in the screen's grid
        Image(
            painter = painterResource(resource),
            contentDescription = description,
            colorFilter = ColorFilter.tint(LocalRetroInk.current ?: retroSurface.ink.srgb),
            modifier = Modifier.size(grid.dp(RetroGrid.ICON))
        )
    }

    /** [cells] gauge blocks, filled to [fraction] by quarters of a cell. */
    @Composable
    private fun GaugeBlocks(cells: Int, fraction: Float, fill: Color, track: Color) {
        val grid = retroGrid()
        val quarters = (fraction.coerceIn(0f, 1f) * cells * 4).roundToInt()
        val filled = buildString {
            repeat(quarters / 4) { append(GAUGE[3]) }
            if (quarters % 4 != 0) append(GAUGE[quarters % 4 - 1])
        }
        Box {
            Line(GAUGE[3].toString().repeat(cells), grid.text, track, maxLines = 1)
            Line(filled, grid.text, fill, maxLines = 1)
        }
    }

    /** Three dots lit one after the other, by jumps. */
    @Composable
    private fun Dots() {
        var lit by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(DOT_STEP_MS)
                lit = (lit + 1) % 3
            }
        }
        val s = retroSurface
        val text: AnnotatedString = buildAnnotatedString {
            repeat(3) { i ->
                withStyle(SpanStyle(color = (if (i == lit) s.strong else s.dim).srgb)) { append(DOT_FILLED) }
                if (i < 2) append(' ')
            }
        }
        BasicText(text = text, style = retroGrid().text)
    }

    /** A window over the screen: a frame across, its content, its actions under it. */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
        WindowDialog(onDismissRequest = onDismiss) {
            val grid = retroGrid()
            Framed(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(grid.cells(1))) {
                    Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(grid.cells(1)), content = content)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(grid.cells(1), Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }

    /** A day's cell in the month: [per] cells wide, lit by a fill when chosen. */
    @Composable
    private fun DayCell(per: Int, onClick: (() -> Unit)? = null, lit: Boolean = false, content: @Composable () -> Unit) {
        val grid = retroGrid()
        Box(
            modifier = Modifier
                .width(grid.cells(per)).height(grid.cells(2))
                .let { if (lit) it.background(retroColors.panel.borderInner.srgb) else it }
                .let { m -> onClick?.let { m.combinedClickable(onClick = it) } ?: m },
            contentAlignment = Alignment.Center
        ) { content() }
    }

    /** A number between − and +, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val grid = retroGrid()
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(MINUS) { onChange((value + modulo - 1) % modulo) }
            Box(modifier = Modifier.width(grid.cells(4)), contentAlignment = Alignment.Center) {
                Line(String.format(Locale.ROOT, "%02d", value), grid.text, retroColors.panel.strong.srgb)
            }
            GlyphButton('+') { onChange((value + 1) % modulo) }
        }
    }

    /** The toast shown, a frame across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = RetroToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            RetroToasts.dismiss(shown.id)
        }
        Box(modifier = modifier.fillMaxWidth().padding(retroGrid().cells(1))) {
            Framed(modifier = Modifier.fillMaxWidth()) { Line(shown.message, retroGrid().text, retroColors.panel.ink.srgb) }
        }
    }

    private const val THEME_ID = "retro"
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val DOT_STEP_MS = 300L

    // Cartouche's interface symbols (cartouche-font, private area U+E010–U+E028)
    private const val PLAY = ''
    private val GAUGE = charArrayOf('', '', '', '')
    private const val ARROW_LEFT = ''
    private const val ARROW_RIGHT = ''
    private const val ARROW_DOWN = ''
    private const val CHECK = ''
    private const val DOT_FILLED = ''
    private const val MINUS = '−'
}

/** An ink a container imposes on what it holds (a disabled button's dim, a destructive one's error). */
internal val LocalRetroInk = compositionLocalOf<Color?> { null }

/** Whether the item holding these frames is lifted (a reorder in progress). */
internal val LocalRetroLifted = compositionLocalOf { false }

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object RetroToasts {
    data class Shown(val id: Long, val message: String, val millis: Long)

    private val queue = mutableStateListOf<Shown>()
    private var next = 0L

    val current: Shown? get() = queue.firstOrNull()

    fun show(message: String, millis: Long) {
        queue.add(Shown(next++, message, millis))
    }

    fun dismiss(id: Long) {
        queue.removeAll { it.id == id }
    }
}
