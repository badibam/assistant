package app.treelune.themes.cosy

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as WindowDialog
import app.treelune.R
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.icons.IconSource
import app.treelune.core.icons.Icons
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import app.treelune.core.themes.ThemeContract
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.Duration
import app.treelune.core.ui.FeedbackType
import app.treelune.core.ui.FieldInput
import app.treelune.core.ui.FieldModifier
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.Size
import app.treelune.core.ui.SliderSteps
import app.treelune.core.ui.Spacing
import app.treelune.core.ui.StatusColor
import app.treelune.core.ui.TextType
import app.treelune.core.ui.confirmMessage
import app.treelune.core.ui.defaultType
import app.treelune.core.ui.label
import app.treelune.core.ui.sound.UISignal
import app.treelune.core.ui.sound.scrollEndSound
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateUtils
import app.treelune.themes.default.DefaultDrawing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The cosy theme (docs/design/cosy-theme.md): the app as a phone in a gentle game. Everything round
 * and plump, cream tiles on a sand ground sown with dots, each piece raised on a full shadow it
 * sinks into under the finger before springing back (CosyPieces); the Baloo 2 font; two palettes of
 * a handful of OKLCH numbers (CosyPalette); Kenney's round sounds.
 */
@OptIn(ExperimentalFoundationApi::class)
object CosyTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "cosy").theme("name")

    /** Lucide's icons, round ends and all, which suit the register as they are. */
    override val iconSource = IconSource.LUCIDE

    // =====================================
    // GRID, SPACES, SOUNDS
    // =====================================

    /** A quarter of what the three gaps leave, the grid growing no wider than 480 dp. */
    @Composable
    override fun gridCellPx(availableWidthPx: Int): Int {
        val density = LocalDensity.current
        val width = minOf(availableWidthPx, with(density) { MAX_GRID_DP.dp.roundToPx() })
        return (width - 3 * gridColumnGapPx()) / 4
    }

    /** Square cells. */
    @Composable
    override fun gridRowPx(cellPx: Int): Int = cellPx

    /** The mockup's ten between two tiles, left to the grid: each tile fills its cells. */
    @Composable
    override fun gridColumnGapPx(): Int = with(LocalDensity.current) { cosySize().dp(10f).roundToPx() }

    /** Twelve between two rows: a little more than across, the shadows taking some. */
    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { cosySize().dp(12f).roundToPx() }

    override fun sound(signal: UISignal): Int? = when (signal) {
        UISignal.CONFIRM -> R.raw.cosy_confirm
        UISignal.ENTER -> R.raw.cosy_enter
        UISignal.BACK -> R.raw.cosy_back
        UISignal.OPEN -> R.raw.cosy_open
        UISignal.CLOSE -> R.raw.cosy_close
        UISignal.TOGGLE -> R.raw.cosy_toggle
        UISignal.STEP -> R.raw.cosy_step
        UISignal.SCROLL_END -> R.raw.cosy_scroll_end
        UISignal.REFUSE -> R.raw.cosy_refuse
    }

    /** The tile's padding round its content: less on a small tile, which has little room. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp = cosySize().dp(
        when (displayMode) {
            DisplayMode.ICON -> 6f
            DisplayMode.MINIMAL -> 10f
            else -> 14f
        }
    )

    @Composable
    override fun spacing(spacing: Spacing): Dp = cosySize().dp(
        when (spacing) {
            Spacing.XS -> 4f
            Spacing.S -> 8f
            Spacing.M -> 12f
            Spacing.L -> 16f
            Spacing.XL -> 24f
        }
    )

    // =====================================
    // INTERACTIVE
    // =====================================

    @Composable
    override fun Button(type: ButtonType, size: Size, state: ComponentState, onClick: () -> Unit, content: @Composable () -> Unit) {
        val enabled = state == ComponentState.NORMAL || state == ComponentState.SUCCESS
        Pressable(onClick = onClick, enabled = enabled) { pressed ->
            Pill(type, enabled, pressed) { content() }
        }
    }

    /** A round bubble of the main colour on its shadow, its icon a size up. */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = cosySize()
        val c = cosyColors
        Pressable(onClick = onClick) { pressed ->
            Raised(
                modifier = Modifier.size(size.floating + size.buttonDepth),
                shape = CircleShape, fill = c.accent.srgb, shadow = c.accentShadow.srgb, depth = size.buttonDepth,
                pressed = pressed, contentModifier = Modifier.size(size.floating)
            ) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    NamedIcon(action.iconName, action.label(), size.dp(28f), c.onAccent.srgb)
                }
            }
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
        val label = action.label()
        val buttonType = if (active) ButtonType.PRIMARY else type ?: action.defaultType()
        Pressable(onClick = { if (requireConfirmation) confirming = true else onClick() }, enabled = enabled) { pressed ->
            if (display == ButtonDisplay.ICON) {
                RoundButton(action.iconName, label, buttonType, enabled, pressed)
            } else {
                Pill(buttonType, enabled, pressed) { Line(label, cosySize().button, LocalCosyInk.current ?: cosyColors.ink.srgb, maxLines = 1) }
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

    // =====================================
    // DISPLAY
    // =====================================

    @Composable
    override fun Text(text: String, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = cosySize()
        val c = cosyColors
        val override = LocalCosyInk.current
        val (style, ink) = when (type) {
            TextType.TITLE -> size.title to (override ?: c.strong.srgb)
            TextType.SUBTITLE -> size.subtitle to (override ?: c.strong.srgb)
            TextType.HEADING -> size.heading to (override ?: c.ink.srgb)
            TextType.BODY -> size.body to (override ?: c.ink.srgb)
            TextType.STRONG -> size.strong to (override ?: c.strong.srgb)
            TextType.CAPTION -> size.caption to c.dim.srgb
            TextType.LABEL -> size.label to c.dim.srgb
            TextType.ERROR -> size.body to c.error.srgb
            TextType.WARNING -> size.body to c.warning.srgb
        }
        Line(text, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines)
    }

    /**
     * A card is a tile on its shadow, a highlighted one ringed with the main colour. A section's
     * header is no tile: its content on the ground over a dotted line, the tiles being the only pieces.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        when (type) {
            CardType.SECTION_HEADER -> Column(modifier = Modifier.fillMaxWidth()) {
                content()
                Divider()
            }
            CardType.DEFAULT -> TileBox(modifier = Modifier.fillMaxWidth(), highlight = highlight) { content() }
        }
    }

    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }

    @Composable
    override fun statusColor(status: StatusColor): Color {
        val c = cosyColors
        return when (status) {
            StatusColor.SUCCESS -> c.success
            StatusColor.WARNING -> c.warning
            StatusColor.ERROR -> c.error
            StatusColor.INFO -> c.info
            StatusColor.MUTED -> c.muted
        }.srgb
    }

    /** A pastel pill, its name in its own hue. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = cosySize()
        val c = cosyColors
        Box(
            modifier = Modifier
                .background(c.tag(color).srgb, CircleShape)
                .padding(horizontal = size.dp(10f), vertical = size.dp(1f))
        ) {
            Line(text, size.label, c.tagInk(color).srgb, maxLines = 1)
        }
    }

    /** A pill sunk in what holds it, filled with the main colour, stripes across the fill. */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = cosySize()
        val c = cosyColors
        Box(modifier = Modifier.fillMaxWidth().height(size.dp(14f)).clip(CircleShape).background(well())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(size.dp(14f))
                    .clip(CircleShape)
                    .background(c.accent.srgb)
                    .drawBehind { stripes(c.accentShadow.srgb.copy(alpha = STRIPE_ALPHA), size.dp(8f).toPx()) }
            )
        }
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = cosySize().caption.copy(color = cosyColors.dim.srgb)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    /** The default theme's drawing for now, its colours included, in this theme's text. */
    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        DefaultDrawing.Draw(drawing, drawingTextStyle(), CurrentTheme.isDark, modifier)
    }

    /** Rounded dashes across the width, in the shadows' colour. */
    @Composable
    override fun Divider() {
        val size = cosySize()
        val color = cosyColors.shadow.srgb
        Canvas(modifier = Modifier.fillMaxWidth().padding(vertical = size.dp(6f)).height(size.dp(4f))) {
            val stroke = this.size.height
            drawLine(
                color = color,
                start = Offset(stroke / 2, stroke / 2),
                end = Offset(this.size.width - stroke / 2, stroke / 2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.dp(6f).toPx(), size.dp(8f).toPx()))
            )
        }
    }

    @Composable
    override fun DragHandle() {
        Box(modifier = Modifier.padding(cosySize().dp(8f))) {
            NamedIcon("grip-vertical", null, cosySize().icon, cosyColors.dim.srgb)
        }
    }

    /** Lifted, an item grows a little and tilts; put down, it springs back to its place. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted, content)

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = CosyPalettes.colors(mode, 0).tag(color).srgb

    // =====================================
    // FEEDBACK
    // =====================================

    /** A tile at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { CosyToasts.show(message, shown) }
    }

    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val c = cosyColors
        val ink = when (type) {
            FeedbackType.SUCCESS -> c.success
            FeedbackType.ERROR -> c.error
            FeedbackType.WARNING -> c.warning
            FeedbackType.INFO -> c.ink
        }.srgb
        TileBox(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(cosySize().dp(10f))) {
                Box(modifier = Modifier.weight(1f)) { Line(message, cosySize().body, ink) }
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

    /** Lucide's drawing; a background, when asked, is a round of it behind the icon. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint ?: LocalCosyInk.current ?: cosyColors.ink.srgb),
            modifier = Modifier.size(size).let { m -> background?.let { m.background(it, CircleShape) } ?: m }
        )
    }

    /**
     * A zone's or a tool's icon in a round pastel of its colour, drawn in the colour's own ink, as
     * the mockup's icon tiles; a neutral one in a round of the well's colour, in the ink. The round
     * is half again the icon's size, whatever the colour, so names beside icons line up.
     */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val c = cosyColors
        val (round, ink) = if (color == null) well() to c.ink.srgb else c.tag(color).srgb to c.tagInk(color).srgb
        Box(modifier = Modifier.size(size * ITEM_ROUND).background(round, CircleShape), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(resourceId), contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(size))
        }
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

    /** A month of round days, round arrows between months; the day chosen is a bubble of the main colour, today a ring. */
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
        val size = cosySize()
        val c = cosyColors
        val s = Strings.`for`(context = LocalContext.current)

        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onDateSelected(DateUtils.formatDateForDisplay(chosen.atStartOfDay(zone).toInstant().toEpochMilli()))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallRoundButton("chevron-left") { month = month.minusMonths(1) }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Line(month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year, size.heading, c.ink.srgb)
                }
                SmallRoundButton("chevron-right") { month = month.plusMonths(1) }
            }
            val first = WeekFields.of(locale).firstDayOfWeek
            val days = (0 until 7).map { first.plus(it.toLong()) }
            Column(verticalArrangement = Arrangement.spacedBy(size.dp(2f))) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Line(day.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale), size.label, c.dim.srgb)
                        }
                    }
                }
                val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
                val cells = lead + month.lengthOfMonth()
                (0 until (cells + 6) / 7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { column ->
                            val n = week * 7 + column - lead + 1
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(size.dp(2f)), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(n)
                                    val lit = date == chosen
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .let { if (lit) it.background(c.accent.srgb) else it }
                                            .let { if (!lit && date == today) it.border(size.dp(2f), c.accent.srgb, CircleShape) else it }
                                            .combinedClickable(onClick = { chosen = date }),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Line(n.toString(), if (lit) size.strong else size.body, if (lit) c.onAccent.srgb else c.ink.srgb)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Two counters, hours and minutes, each a large number between round − and + buttons. */
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
                Line(":", cosySize().number, cosyColors.strong.srgb)
                Counter(minute, 60) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /** The sand ground sown with dots; the toasts show over its bottom. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        val c = cosyColors
        val size = cosySize()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.ground.srgb)
                .drawBehind {
                    val pitch = size.dp(DOT_PITCH).toPx()
                    val radius = size.dp(DOT_RADIUS).toPx()
                    val dots = c.dots.srgb
                    var y = pitch / 2
                    var row = 0
                    while (y < this.size.height) {
                        // Every other row moved by half a pitch: the dots sit in quincunx
                        var x = if (row % 2 == 0) pitch / 2 else pitch
                        while (x < this.size.width) {
                            drawCircle(dots, radius, Offset(x, y))
                            x += pitch
                        }
                        y += pitch / 2
                        row++
                    }
                }
        ) {
            CompositionLocalProvider(LocalCosyLayer provides CosyLayer.GROUND, LocalCosyInk provides null) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /** A row across the width, its pieces spaced. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = cosySize()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(12f), vertical = size.dp(8f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    @Composable
    override fun ZoneCardContainer(onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(DisplayMode.CONDENSED, onClick, onLongClick, content)

    /** An orange bubble with an exclamation mark, on its own small shadow. */
    @Composable
    override fun WaitingMark() {
        val size = cosySize()
        val c = cosyColors
        Raised(
            modifier = Modifier.size(size.dp(18f) + size.dp(2f)),
            shape = CircleShape, fill = c.warning.srgb, shadow = c.shadow.srgb, depth = size.dp(2f),
            contentModifier = Modifier.size(size.dp(18f))
        ) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Line("!", size.label, c.tile.srgb)
            }
        }
    }

    /** A cell of the grid in edit mode: a dashed rounded outline in the shadows' colour. */
    @Composable
    override fun GridCell() {
        val size = cosySize()
        val color = cosyColors.shadow.srgb
        Canvas(modifier = Modifier.fillMaxSize().padding(size.dp(3f))) {
            val stroke = size.dp(2.5f).toPx()
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(size.tileRadius.toPx()),
                style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 2.5f)))
            )
        }
    }

    /**
     * The tile being moved is lifted: a little larger, tilted, its shadow twice as deep; put down,
     * it springs back. The others step back, faded, the cells showing through them.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        Box(modifier = Modifier.alpha(if (state == app.treelune.core.ui.GridTileState.ASIDE) ASIDE_ALPHA else 1f)) {
            Lifted(chosen) { CompositionLocalProvider(LocalCosyLifted provides chosen) { content() } }
        }
    }

    /** A small round of the main colour with Lucide's timer: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = cosySize()
        val c = cosyColors
        Box(modifier = Modifier.size(size.dp(18f)).background(c.accent.srgb, CircleShape), contentAlignment = Alignment.Center) {
            NamedIcon("timer", null, size.dp(12f), c.onAccent.srgb)
        }
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(displayMode, onClick, onLongClick, content)

    /** A tile filling the cells the grid gives it, on its shadow; its content padded in. */
    @Composable
    private fun Tile(displayMode: DisplayMode, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val size = cosySize()
        val c = cosyColors
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            Raised(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(size.tileRadius), fill = c.tile.srgb, shadow = c.shadow.srgb,
                depth = if (LocalCosyLifted.current) size.tileDepth * 2 else size.tileDepth,
                pressed = pressed, padding = tileFrame(displayMode), contentModifier = Modifier.fillMaxSize()
            ) {
                CompositionLocalProvider(LocalCosyLayer provides CosyLayer.TILE) { content() }
            }
        }
    }

    /** The page's title in a pill of the main colour on its shadow, across the width. */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?
    ) {
        val size = cosySize()
        val c = cosyColors
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(8f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Raised(shape = CircleShape, fill = c.accent.srgb, shadow = c.accentShadow.srgb, depth = size.buttonDepth, padding = size.dp(2f)) {
                Row(
                    modifier = Modifier.padding(horizontal = size.dp(20f)),
                    horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A coloured icon in its round, as on its tile; a neutral one in the pill's ink
                    icon?.let { name ->
                        if (iconColor == null) NamedIcon(name, null, size.icon, c.onAccent.srgb)
                        else app.treelune.core.ui.UI.ItemIcon(name, iconColor, size.dp(18f))
                    }
                    Line(title, size.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), c.onAccent.srgb, align = TextAlign.Center, maxLines = 1)
                }
            }
            subtitle?.let { Box(modifier = Modifier.padding(top = size.dp(4f))) { Text(it, TextType.CAPTION, false, TextAlign.Center) } }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** In the dim ink, bold; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, cosySize().label, cosyColors.dim.srgb)
    }

    /** A rounded well sunk in what holds it, the label above, the label again inside while it is empty. */
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
        val size = cosySize()
        val c = cosyColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (labelAbove) FieldLabel(label, required)
            if (readonly) {
                val shown = value.text.ifBlank { Strings.`for`(context = LocalContext.current).shared("label_no_value") }
                Box(modifier = if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier) {
                    Text(shown, TextType.BODY, false, null)
                }
            } else {
                val isPassword = fieldType == FieldType.PASSWORD
                var revealed by remember { mutableStateOf(false) }
                Well(error = state == ComponentState.ERROR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = FieldInput.limited(fieldType, onChange),
                            readOnly = state == ComponentState.READONLY,
                            enabled = state != ComponentState.DISABLED,
                            textStyle = size.body.copy(color = (if (state == ComponentState.ERROR) c.error else c.ink).srgb),
                            cursorBrush = SolidColor(c.accentShadow.srgb),
                            keyboardOptions = FieldInput.keyboardOptions(fieldType),
                            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                            modifier = Modifier
                                .weight(1f)
                                .let { m -> fieldModifier.focusRequester?.let { m.focusRequester(it) } ?: m }
                                .let { m -> fieldModifier.onFocusChanged?.let { m.onFocusChanged(it) } ?: m },
                            decorationBox = { inner ->
                                Box {
                                    if (value.text.isEmpty()) Line(label, size.body, c.dim.srgb, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                        if (isPassword) {
                            Box(modifier = Modifier.combinedClickable(onClick = { revealed = !revealed })) {
                                NamedIcon(if (revealed) "eye-off" else "eye", null, size.icon, c.dim.srgb)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The choice shown in a well, its icon before it, a chevron at its end; the options in a window,
     * under their sections' titles, the chosen one in a well and checked.
     */
    @Composable
    override fun FormSelection(
        label: String,
        options: List<String>,
        sections: List<String?>,
        icons: List<app.treelune.core.themes.OptionIcon?>,
        selected: Int?,
        shown: String,
        onSelect: (Int) -> Unit,
        required: Boolean
    ) {
        var open by remember { mutableStateOf(false) }
        val size = cosySize()
        val c = cosyColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(10f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(18f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) {
                Well {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OptionIconOf(selected)
                        Box(modifier = Modifier.weight(1f)) { Line(shown, size.body, c.ink.srgb, maxLines = 1) }
                        NamedIcon("chevron-down", null, size.icon, c.dim.srgb)
                    }
                }
            }
        }
        if (open) {
            DialogFrame(onDismiss = { open = false }, actions = {}) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { index, option ->
                        // A section's title above its first option, in the dim ink, not chosen
                        val section = sections.getOrNull(index)
                        if (section != null && section != sections.getOrNull(index - 1)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(10f), bottom = size.dp(2f), start = size.dp(12f))) {
                                Line(section, size.label, c.dim.srgb)
                            }
                        }
                        val chosen = index == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = size.touch)
                                .clip(RoundedCornerShape(size.fieldRadius))
                                .let { if (chosen) it.background(well()) else it }
                                .combinedClickable(onClick = { onSelect(index); open = false })
                                .padding(horizontal = size.dp(12f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OptionIconOf(index)
                            Box(modifier = Modifier.weight(1f)) { Line(option, if (chosen) size.strong else size.body, c.ink.srgb) }
                            if (chosen) NamedIcon("check", null, size.icon, c.accentShadow.srgb)
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
            horizontalArrangement = Arrangement.spacedBy(cosySize().dp(10f), Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    /** A round: hollow when empty, full of the main colour with a check when checked. */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = cosySize()
        val c = cosyColors
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(10f))
            ) {
                val squash by animateFloatAsState(if (pressed) 0.85f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "check")
                Box(
                    modifier = Modifier
                        .size(size.dp(24f))
                        .graphicsLayer { scaleX = squash; scaleY = squash }
                        .clip(CircleShape)
                        .let { if (checked) it.background(c.accent.srgb) else it.border(size.dp(3f), c.shadow.srgb, CircleShape) },
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) NamedIcon("check", null, size.dp(15f), c.onAccent.srgb)
                }
                label?.let { Line(it, size.body, c.ink.srgb) }
            }
        }
    }

    /** The label across, a pill at the end whose knob slides on a spring: the main colour when on. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = cosySize()
        val c = cosyColors
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink.srgb) }
                val at by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "switch")
                val width = size.dp(52f)
                val height = size.dp(30f)
                val knob = size.dp(22f)
                val inset = (height - knob) / 2
                Box(
                    modifier = Modifier
                        .size(width, height)
                        .clip(CircleShape)
                        .background(if (checked) c.accent.srgb else c.shadow.srgb)
                ) {
                    val travel = with(LocalDensity.current) { (width - knob - inset * 2).toPx() }
                    Box(
                        modifier = Modifier
                            .padding(inset)
                            .offset { IntOffset((travel * at).roundToInt(), 0) }
                            .size(knob)
                            .background(c.tile.srgb, CircleShape)
                    )
                }
            }
        }
    }

    /** The tab shown a pill of the main colour on its shadow, the others plain in the dim ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = cosySize()
        val c = cosyColors
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(size.dp(6f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    if (index == selected) {
                        Pill(ButtonType.PRIMARY, true, pressed) { Line(label, size.button, c.onAccent.srgb, maxLines = 1) }
                    } else {
                        Box(modifier = Modifier.heightIn(min = size.touch).padding(horizontal = size.dp(14f)), contentAlignment = Alignment.Center) {
                            Line(label, size.button, c.dim.srgb, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /** Two pills: the chosen one of the main colour on its shadow, the other a well. */
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
        val size = cosySize()
        val c = cosyColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(
                modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
            ) {
                listOf(true to trueLabel, false to falseLabel).forEach { (answer, text) ->
                    val chosen = value == answer
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        modifier = if (compact) Modifier else Modifier.weight(1f)
                    ) { pressed ->
                        val fill = if (compact) Modifier else Modifier.fillMaxWidth()
                        Raised(
                            modifier = fill,
                            shape = CircleShape,
                            fill = if (chosen) c.accent.srgb else well(),
                            shadow = c.accentShadow.srgb,
                            depth = if (chosen) size.buttonDepth else 0.dp,
                            pressed = pressed,
                            contentModifier = fill
                        ) {
                            Box(
                                modifier = fill.heightIn(min = size.touch - size.buttonDepth).padding(horizontal = size.dp(16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Line(text, size.button, (if (chosen) c.onAccent else c.dim).srgb, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A well across the width filled with the main colour to the value, a round knob at its end;
     * a touch or a drag anywhere on it answers the stop under the finger. No answer yet shows the
     * well alone and "—".
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
        val size = cosySize()
        val c = cosyColors
        val strings = Strings.`for`(context = LocalContext.current)
        val lastStop = SliderSteps.lastStop(min, max, step)
        val decimals = SliderSteps.decimals(min, step)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(size.dp(10f), Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Line(value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—", size.number, (if (value != null) c.strong else c.dim).srgb)
                if (value != null && !required) WordButton(strings.shared("action_clear"), { onValueChange(null) })
            }
            // The gestures below are installed once: they call the latest onValueChange, which
            // writes into the settings as they are now, not as they were when the slider was drawn
            val latest by rememberUpdatedState(onValueChange)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val width = constraints.maxWidth.toFloat()
                fun answer(x: Float) {
                    val raw = min + (x / width).coerceIn(0f, 1f) * (lastStop - min)
                    latest(SliderSteps.snap(raw, min, step))
                }
                val fraction = value?.let { ((it - min) / (lastStop - min)).toFloat().coerceIn(0f, 1f) }
                val knob = size.dp(26f)
                val trackWidth = maxWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(size.dp(14f)).clip(CircleShape).background(well())) {
                        if (fraction != null) {
                            Box(modifier = Modifier.fillMaxWidth(fraction).height(size.dp(14f)).clip(CircleShape).background(c.accent.srgb))
                        }
                    }
                    if (fraction != null) {
                        val travel = with(LocalDensity.current) { (trackWidth - knob).toPx() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                                .size(knob)
                                .background(c.tile.srgb, CircleShape)
                                .border(size.dp(4f), c.accentShadow.srgb, CircleShape)
                        )
                    }
                }
            }
            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Line(minLabel, size.caption, c.dim.srgb)
                    Line(maxLabel, size.caption, c.dim.srgb)
                }
            }
        }
    }

    // =====================================
    // PALETTES
    // =====================================

    /** For what Material still draws at the app's root: the palette's colours under its names. */
    override fun getColorScheme(mode: PaletteMode, hueShift: Int): ColorScheme {
        val c = CosyPalettes.colors(mode, hueShift)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.accent.srgb, onPrimary = c.onAccent.srgb,
            secondary = c.ink.srgb, onSecondary = c.ground.srgb,
            tertiary = c.warning.srgb, onTertiary = c.ground.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.tile.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.ground.srgb,
            outline = c.shadow.srgb, outlineVariant = c.shadow.srgb
        ) else lightColorScheme(
            primary = c.accent.srgb, onPrimary = c.onAccent.srgb,
            secondary = c.ink.srgb, onSecondary = c.ground.srgb,
            tertiary = c.warning.srgb, onTertiary = c.ground.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.tile.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.ground.srgb,
            outline = c.shadow.srgb, outlineVariant = c.shadow.srgb
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    @Composable
    override fun AIThinkingIndicator() {
        Box(modifier = Modifier.fillMaxWidth().padding(cosySize().dp(12f)), contentAlignment = Alignment.Center) { Dots() }
    }

    /**
     * A message is a tile on its shadow, the user's tinted toward the main colour; the system's lies
     * flat in a well, without a shadow.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = cosySize()
        val c = cosyColors
        when (sender) {
            MessageSender.SYSTEM -> Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(size.fieldRadius)).background(well()).padding(size.dp(12f))
            ) { content() }
            else -> TileBox(modifier = Modifier.fillMaxWidth(), highlight = sender == MessageSender.USER) { content() }
        }
    }

    /** A tile ringed with the main colour, to draw the eye: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: @Composable RowScope.() -> Unit) {
        val size = cosySize()
        TileBox(modifier = Modifier.fillMaxWidth(), highlight = true) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(size.dp(10f))) {
                Text(title, TextType.TITLE, true, TextAlign.Center)
                content()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.dp(10f)), content = actions)
            }
        }
    }

    // =====================================
    // PIECES
    // =====================================

    /** A text in one style and colour, cut with an ellipsis past [maxLines]. */
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

    /** The colour of a well: an input sunk in what holds it takes the colour of the other layer. */
    @Composable
    private fun well(): Color = if (LocalCosyLayer.current == CosyLayer.TILE) cosyColors.ground.srgb else cosyColors.tile.srgb

    /** A tile on its shadow, not touched: a card, a message, a snackbar. */
    @Composable
    private fun TileBox(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable () -> Unit) {
        val size = cosySize()
        val c = cosyColors
        Raised(
            modifier = modifier,
            shape = RoundedCornerShape(size.tileRadius),
            fill = c.tile.srgb,
            shadow = if (highlight) c.accentShadow.srgb else c.shadow.srgb,
            depth = size.tileDepth,
            border = if (highlight) c.accent.srgb else null,
            borderWidth = size.dp(3f),
            padding = size.dp(16f),
            contentModifier = modifier
        ) {
            CompositionLocalProvider(LocalCosyLayer provides CosyLayer.TILE) { content() }
        }
    }

    /** A rounded well a finger's target tall, its content padded in. */
    @Composable
    private fun Well(error: Boolean = false, content: @Composable () -> Unit) {
        val size = cosySize()
        val c = cosyColors
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.touch)
                .clip(RoundedCornerShape(size.fieldRadius))
                .background(well())
                .let { if (error) it.border(size.dp(2f), c.error.srgb, RoundedCornerShape(size.fieldRadius)) else it }
                .padding(horizontal = size.dp(14f), vertical = size.dp(10f)),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }

    /**
     * A pill on its shadow, a finger's target tall: the main colour for a main action, a tile
     * otherwise, the error's ink for a destructive one; off, it lies flat in the dim ink.
     */
    @Composable
    private fun Pill(type: ButtonType, enabled: Boolean, pressed: Boolean, content: @Composable () -> Unit) {
        val size = cosySize()
        val c = cosyColors
        val main = enabled && type == ButtonType.PRIMARY
        val ink = when {
            !enabled -> c.dim
            main -> c.onAccent
            type == ButtonType.DANGER -> c.error
            else -> c.ink
        }.srgb
        Raised(
            shape = CircleShape,
            fill = if (main) c.accent.srgb else if (enabled) c.tile.srgb else well(),
            shadow = if (main) c.accentShadow.srgb else c.shadow.srgb,
            depth = if (enabled) size.buttonDepth else 0.dp,
            pressed = pressed
        ) {
            Box(
                modifier = Modifier.heightIn(min = size.touch - size.buttonDepth).widthIn(min = size.touch).padding(horizontal = size.dp(18f)),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalCosyInk provides ink) { content() }
            }
        }
    }

    /** An icon in a round button on its shadow, a finger's target wide. */
    @Composable
    private fun RoundButton(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean) {
        val size = cosySize()
        val c = cosyColors
        val main = enabled && type == ButtonType.PRIMARY
        val ink = when {
            !enabled -> c.dim
            main -> c.onAccent
            type == ButtonType.DANGER -> c.error
            else -> c.ink
        }.srgb
        val side = size.touch - size.buttonDepth
        Raised(
            modifier = Modifier.size(side + if (enabled) size.buttonDepth else 0.dp),
            shape = CircleShape,
            fill = if (main) c.accent.srgb else if (enabled) c.tile.srgb else well(),
            shadow = if (main) c.accentShadow.srgb else c.shadow.srgb,
            depth = if (enabled) size.buttonDepth else 0.dp,
            pressed = pressed,
            contentModifier = Modifier.size(side)
        ) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) { NamedIcon(icon, description, size.icon, ink) }
        }
    }

    /** A pill with a word, that acts. */
    @Composable
    private fun WordButton(text: String, onClick: () -> Unit, strong: Boolean = false, danger: Boolean = false, enabled: Boolean = true) {
        val type = when {
            danger -> ButtonType.DANGER
            strong -> ButtonType.PRIMARY
            else -> ButtonType.DEFAULT
        }
        Pressable(onClick = onClick, enabled = enabled) { pressed ->
            Pill(type, enabled, pressed) { Line(text, cosySize().button, LocalCosyInk.current ?: cosyColors.ink.srgb, maxLines = 1) }
        }
    }

    /** A small round button with a Lucide icon: a month's arrow, a counter's − and +. */
    @Composable
    private fun SmallRoundButton(icon: String, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed -> RoundButton(icon, null, ButtonType.DEFAULT, true, pressed) }
    }

    /** A Lucide icon by name, at [size], in [tint]. */
    @Composable
    private fun NamedIcon(name: String, description: String?, size: Dp, tint: Color) {
        val resource = requireNotNull(Icons.drawable(LocalContext.current, name)) { "No drawable for the icon $name" }
        Image(
            painter = painterResource(resource),
            contentDescription = description,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(size)
        )
    }

    /** Three dots hopping one after the other, each on a spring of its own. */
    @Composable
    private fun Dots() {
        val size = cosySize()
        val c = cosyColors
        val transition = rememberInfiniteTransition(label = "dots")
        Row(horizontalArrangement = Arrangement.spacedBy(size.dp(6f)), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                val hop by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        androidx.compose.animation.core.keyframes {
                            durationMillis = DOTS_CYCLE_MS
                            0f at i * DOTS_STEP_MS
                            1f at i * DOTS_STEP_MS + DOTS_HOP_MS / 2
                            0f at i * DOTS_STEP_MS + DOTS_HOP_MS
                        },
                        RepeatMode.Restart
                    ),
                    label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = -hop * size.dp(6f).toPx() }
                        .size(size.dp(10f))
                        .background((if (hop > 0.5f) c.accent else c.accentShadow).srgb, CircleShape)
                )
            }
        }
    }

    /** A window over the screen, swelling in: a tile on its shadow, its content, its actions under it. */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
        WindowDialog(onDismissRequest = onDismiss) {
            SwellIn {
                val size = cosySize()
                val c = cosyColors
                Raised(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(size.dialogRadius),
                    fill = c.tile.srgb,
                    shadow = c.shadow.srgb,
                    depth = size.tileDepth,
                    padding = size.dp(20f),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    CompositionLocalProvider(LocalCosyLayer provides CosyLayer.TILE) {
                        Column(verticalArrangement = Arrangement.spacedBy(size.dp(14f))) {
                            Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(size.dp(10f)), content = content)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(size.dp(8f), Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                content = actions
                            )
                        }
                    }
                }
            }
        }
    }

    /** A number between round − and +, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val size = cosySize()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(6f))) {
            SmallRoundButton("plus") { onChange((value + 1) % modulo) }
            Box(modifier = Modifier.width(size.dp(64f)), contentAlignment = Alignment.Center) {
                Line(String.format(Locale.ROOT, "%02d", value), size.number, cosyColors.strong.srgb)
            }
            SmallRoundButton("minus") { onChange((value + modulo - 1) % modulo) }
        }
    }

    /** The toast shown, a tile across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = CosyToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            CosyToasts.dismiss(shown.id)
        }
        Box(modifier = modifier.fillMaxWidth().padding(cosySize().dp(16f))) {
            SwellIn { TileBox(modifier = Modifier.fillMaxWidth()) { Line(shown.message, cosySize().body, cosyColors.ink.srgb) } }
        }
    }

    /** Diagonal stripes across what is drawn, [pitch] apart. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.stripes(color: Color, pitch: Float) {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = pitch / 2)
            x += pitch
        }
    }

    private const val MAX_GRID_DP = 480
    private const val ITEM_ROUND = 1.5f
    private const val ASIDE_ALPHA = 0.4f
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val DOT_PITCH = 22f
    private const val DOT_RADIUS = 2f
    private const val STRIPE_ALPHA = 0.35f
    private const val DOTS_STEP_MS = 160
    private const val DOTS_HOP_MS = 360
    private const val DOTS_CYCLE_MS = 1100
}

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object CosyToasts {
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
