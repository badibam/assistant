package com.assistant.themes.default

import com.assistant.core.ui.sound.scrollEndSound

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.themes.ThemeContract
import com.assistant.core.themes.ThemePalette
import com.assistant.core.themes.BasePalette
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.Size
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.FieldModifier
import com.assistant.core.ui.TextType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.FeedbackType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.confirmMessage
import com.assistant.core.ui.defaultType
import com.assistant.core.ui.label
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateUtils
import com.assistant.core.tools.BaseSchemas
import java.time.Instant
import java.util.Calendar

/**
 * DefaultTheme - Default ThemeContract implementation
 *
 * Modern theme based on Material 3 with our semantic types
 * ONLY VISUAL components (themed)
 *
 * LAYOUTS: use Compose Row/Column/Box/Spacer directly
 */
@OptIn(ExperimentalFoundationApi::class)
object DefaultTheme : ThemeContract {

    override fun name(context: android.content.Context): String =
        com.assistant.core.strings.Strings.`for`(context = context, theme = "default").theme("name")

    /** A palette's name is its id past the theme's: "default_dark" is named by palette_dark. */
    override fun paletteName(paletteId: String, context: android.content.Context): String =
        com.assistant.core.strings.Strings.`for`(context = context, theme = "default").theme("palette_${paletteId.removePrefix("default_")}")

    override val iconSource = com.assistant.core.icons.IconSource.LUCIDE

    /** A quarter of the width, the grid growing no wider than 480 dp. */
    @Composable
    override fun gridCellPx(availableWidthPx: Int): Int =
        minOf(availableWidthPx, with(androidx.compose.ui.platform.LocalDensity.current) { 480.dp.roundToPx() }) / 4

    /** None: a tile keeps its gap inside its cells (tileFrame). */
    @Composable
    override fun gridGapPx(): Int = 0

    /** Silent: the default theme has no sounds. */
    override fun sound(signal: com.assistant.core.ui.sound.UISignal): Int? = null

    /** 4 outside the card, between tiles, and the card's padding inside (ToolCardContainer). */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp = 4.dp + tilePadding(displayMode)

    private fun tilePadding(displayMode: DisplayMode): Dp = when (displayMode) {
        DisplayMode.ICON -> 4.dp
        DisplayMode.MINIMAL -> 8.dp
        else -> 12.dp
    }

    @Composable
    override fun spacing(spacing: com.assistant.core.ui.Spacing): Dp = when (spacing) {
        com.assistant.core.ui.Spacing.XS -> 4.dp
        com.assistant.core.ui.Spacing.S -> 8.dp
        com.assistant.core.ui.Spacing.M -> 12.dp
        com.assistant.core.ui.Spacing.L -> 16.dp
        com.assistant.core.ui.Spacing.XL -> 24.dp
    }


    // =====================================
    // PALETTE SYSTEM IMPLEMENTATION
    // =====================================
    
    /**
     * Base palettes - LIGHT and DARK versions of default theme
     */
    override fun getBasePalettes(): List<ThemePalette> {
        return listOf(
            ThemePalette.createBase("default", BasePalette.LIGHT),
            ThemePalette.createBase("default", BasePalette.DARK)
        )
    }
    
    /**
     * Custom palettes - none for default theme (minimalist approach)
     */
    override fun getCustomPalettes(): List<ThemePalette> {
        return emptyList()
    }
    
    /**
     * Gets ColorScheme for specific palette
     */
    override fun getColorScheme(paletteId: String): ColorScheme {
        return when (paletteId) {
            "default_light" -> lightColorScheme(
                primary = Color(0xFF7C9DD6),        // Bleu pervenche vif mais doux
                onPrimary = Color(0xFFFFFFFF),      // Blanc
                secondary = Color(0xFFB08BBE),      // Mauve moyen (accent décoratif)
                onSecondary = Color(0xFF2E2438),    // Violet très foncé
                tertiary = Color(0xFFF5A47A),       // Orange doux (warning)
                onTertiary = Color(0xFF4A2E1A),     // Brun foncé
                surface = Color(0xFFFAF8F5),        // Crème très léger
                onSurface = Color(0xFF2E2C3A),      // Gris foncé tirant vers violet
                surfaceVariant = Color(0xFFEBE7F2), // Lavande très pâle
                onSurfaceVariant = Color(0xFF524E5F), // Gris violet moyen
                background = Color(0xFFFFFCF9),     // Blanc cassé chaud
                onBackground = Color(0xFF2E2C3A),   // Gris foncé
                error = Color(0xFFDB6B6B),          // Rouge corail vif
                onError = Color(0xFFFFFFFF),        // Blanc
                outline = Color(0xFFB5AEC4),        // Gris mauve
                outlineVariant = Color(0xFFDAD5E4)  // Lavande claire
            )
            "default_dark" -> darkColorScheme(
                primary = Color(0xFF9BB8E8),        // Bleu ciel doux mais vif
                onPrimary = Color(0xFF1B2A3F),      // Bleu marine profond
                secondary = Color(0xFFC9A8D8),      // Mauve lumineux (accent décoratif)
                onSecondary = Color(0xFF2E1F3A),    // Violet très foncé
                tertiary = Color(0xFFFFB88C),       // Orange lumineux (warning)
                onTertiary = Color(0xFF3F2A1A),     // Brun très foncé
                surface = Color(0xFF282433),        // Violet grisé foncé
                onSurface = Color(0xFFE8E2EE),      // Lavande très pâle
                surfaceVariant = Color(0xFF3D3848), // Violet moyen
                onSurfaceVariant = Color(0xFFD5CBDF), // Lavande claire
                background = Color(0xFF1E1B26),     // Violet foncé profond
                onBackground = Color(0xFFE8E2EE),   // Lavande très pâle
                error = Color(0xFFE88888),          // Rouge saumon vif
                onError = Color(0xFF2D1A1A),        // Rouge très foncé
                outline = Color(0xFF6B6178),        // Mauve grisé
                outlineVariant = Color(0xFF433E4D)  // Violet foncé
            )
            else -> getColorScheme("default_dark") // Default fallback
        }
    }

    // =====================================
    // THEME SHAPE CONSTANTS
    // =====================================
    private val ButtonTextShape = RoundedCornerShape(2.dp)
    private val ButtonIconShape = RoundedCornerShape(2.dp)
    private val CardShape = RectangleShape
    
    // =====================================
    // LAYOUTS: USE COMPOSE DIRECTLY
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // NO implementation - direct access for maximum flexibility
    
    // =====================================
    // INTERACTIVE
    // =====================================
    
    // Button configuration by size
    private data class ButtonConfig(
        val minWidth: Dp,
        val minHeight: Dp,
        val padding: PaddingValues,
        val shape: Shape,
        val containerColor: Color,
        val contentColor: Color,
        val border: BorderStroke?
    )
    
    private fun getButtonConfig(size: Size, type: ButtonType): ButtonConfig {
        val (containerColor, contentColor, border) = when (type) {
            ButtonType.PRIMARY -> Triple(
                CurrentTheme.getCurrentColorScheme().primary,
                CurrentTheme.getCurrentColorScheme().onPrimary,
                null
            )
            ButtonType.SECONDARY -> Triple(
                CurrentTheme.getCurrentColorScheme().secondary,
                CurrentTheme.getCurrentColorScheme().onSecondary,
                null
            )
            ButtonType.TERTIARY -> Triple(
                CurrentTheme.getCurrentColorScheme().tertiary,
                CurrentTheme.getCurrentColorScheme().onTertiary,
                null
            )
            ButtonType.DANGER -> Triple(
                CurrentTheme.getCurrentColorScheme().errorContainer,
                CurrentTheme.getCurrentColorScheme().onErrorContainer,
                null
            )
            ButtonType.DEFAULT -> Triple(
                CurrentTheme.getCurrentColorScheme().surfaceVariant,
                CurrentTheme.getCurrentColorScheme().onSurfaceVariant,
                null
            )
        }
        
        return when (size) {
            Size.XS -> ButtonConfig(
                minWidth = 30.dp,
                minHeight = 30.dp,
                padding = PaddingValues(4.dp),
                shape = ButtonIconShape,
                containerColor = containerColor,
                contentColor = contentColor,
                border = border
            )
            Size.S -> ButtonConfig(
                minWidth = 36.dp,
                minHeight = 36.dp,
                padding = PaddingValues(6.dp),
                shape = ButtonIconShape,
                containerColor = containerColor,
                contentColor = contentColor,
                border = border
            )
            Size.M -> ButtonConfig(
                minWidth = 48.dp,
                minHeight = 40.dp,
                padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = ButtonTextShape,
                containerColor = containerColor,
                contentColor = contentColor,
                border = border
            )
            Size.L -> ButtonConfig(
                minWidth = 64.dp,
                minHeight = 48.dp,
                padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                shape = ButtonTextShape,
                containerColor = containerColor,
                contentColor = contentColor,
                border = border
            )
            Size.XL -> ButtonConfig(
                minWidth = 80.dp,
                minHeight = 56.dp,
                padding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                shape = ButtonTextShape,
                containerColor = containerColor,
                contentColor = contentColor,
                border = border
            )
            Size.XXL -> ButtonConfig(
                minWidth = 96.dp,
                minHeight = 64.dp,
                padding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
                shape = ButtonTextShape,
                containerColor = containerColor,
                contentColor = contentColor,
                border = border
            )
        }
    }

    @Composable
    override fun Button(
        type: ButtonType,
        size: Size,
        state: ComponentState,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        val isEnabled = when (state) {
            ComponentState.NORMAL, ComponentState.SUCCESS -> true
            ComponentState.LOADING, ComponentState.DISABLED, ComponentState.ERROR, ComponentState.READONLY -> false
        }
        
        val config = getButtonConfig(size, type)
        
        Surface(
            color = if (isEnabled) config.containerColor else CurrentTheme.getCurrentColorScheme().surfaceVariant,
            contentColor = if (isEnabled) config.contentColor else CurrentTheme.getCurrentColorScheme().onSurfaceVariant,
            shape = config.shape,
            border = if (isEnabled) config.border else null,
            modifier = Modifier
                .defaultMinSize(minWidth = config.minWidth, minHeight = config.minHeight)
                .clickable(enabled = isEnabled) {
                    if (isEnabled) onClick()
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(config.padding)
            ) {
                content()
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
        // État du dialogue de confirmation
        var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
        
        // Determine default type based on action; switched on, it is filled with the main color
        val buttonType = if (active) ButtonType.PRIMARY else type ?: action.defaultType()
        
        // Determine state based on enabled
        val state = if (enabled) ComponentState.NORMAL else ComponentState.DISABLED
        
        // Use unified Button for all cases
        Button(
            type = buttonType,
            size = size,
            state = state,
            onClick = {
                if (requireConfirmation) {
                    showConfirmDialog = true
                } else {
                    onClick()
                }
            }
        ) {
            if (display == ButtonDisplay.ICON) {
                // Every action's icon is in the index (ButtonAction's test), so a missing
                // drawable is a broken build, not a case to draw around.
                val context = LocalContext.current
                val iconResource = requireNotNull(com.assistant.core.icons.Icons.drawable(context, action.iconName)) {
                    "No drawable for the icon ${action.iconName} of $action"
                }
                // No tint given: the icon takes the button's content colour, as its text would
                Icon(
                    painter = painterResource(iconResource),
                    contentDescription = action.label(),
                    modifier = Modifier.size(getButtonIconSize(size))
                )
            } else {
                androidx.compose.material3.Text(
                    action.label()
                )
            }
        }
        
        // Dialogue de confirmation automatique
        if (showConfirmDialog && requireConfirmation) {
            // Use DANGER dialog only for destructive actions (DELETE)
            val dialogType = when (action) {
                ButtonAction.DELETE -> DialogType.DANGER
                else -> DialogType.CONFIRM
            }

            Dialog(
                type = dialogType,
                onConfirm = {
                    showConfirmDialog = false
                    onClick()  // Execute action after confirmation
                },
                onCancel = {
                    showConfirmDialog = false
                },
                confirmEnabled = true
            ) {
                androidx.compose.material3.Text(
                    confirmMessage ?: action.confirmMessage(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    
    /** The size of an action's icon in a button of [size]. */
    private fun getButtonIconSize(size: Size): Dp = when (size) {
        Size.XS -> 16.dp
        Size.S, Size.M -> 20.dp
        Size.L -> 24.dp
        Size.XL -> 28.dp
        Size.XXL -> 32.dp
    }

    @Composable
    private fun TextField(
        fieldType: FieldType,
        state: ComponentState,
        value: TextFieldValue,
        onChange: (TextFieldValue) -> Unit,
        placeholder: String,
        fieldModifier: FieldModifier
    ) {
        val isError = state == ComponentState.ERROR
        val isReadOnly = state == ComponentState.READONLY
        
        // The keyboard and the length come from the field type (FieldInput)
        val keyboardOptions = com.assistant.core.ui.FieldInput.keyboardOptions(fieldType)
        val filteredOnChange = com.assistant.core.ui.FieldInput.limited(fieldType, onChange)

        // A password is masked, and shown while the eye is on
        val isPassword = fieldType == FieldType.PASSWORD
        var revealed by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = value,
            onValueChange = filteredOnChange,
            placeholder = { androidx.compose.material3.Text(placeholder) },
            isError = isError,
            readOnly = isReadOnly,
            enabled = state != ComponentState.DISABLED,
            keyboardOptions = keyboardOptions,
            visualTransformation = if (isPassword && !revealed) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    androidx.compose.material3.IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            painter = painterResource(if (revealed) com.assistant.R.drawable.lucide_eye_off else com.assistant.R.drawable.lucide_eye),
                            contentDescription = null,
                            tint = CurrentTheme.getCurrentColorScheme().onSurface
                        )
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .let { mod ->
                    fieldModifier.focusRequester?.let { focusReq -> mod.focusRequester(focusReq) } ?: mod
                }
                .let { mod ->
                    fieldModifier.onFocusChanged?.let { callback -> mod.onFocusChanged(callback) } ?: mod
                }
        )
    }
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    override fun Text(
        text: String,
        type: TextType,
        fillMaxWidth: Boolean,
        textAlign: TextAlign?,
        maxLines: Int
    ) {
        val style = when (type) {
            TextType.TITLE -> MaterialTheme.typography.headlineMedium
            TextType.SUBTITLE -> MaterialTheme.typography.headlineSmall
            TextType.TILE_TITLE -> MaterialTheme.typography.titleMedium
            TextType.BODY -> MaterialTheme.typography.bodyMedium
            TextType.STRONG -> MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            TextType.CAPTION -> MaterialTheme.typography.bodySmall
            TextType.LABEL -> MaterialTheme.typography.labelMedium
            TextType.ERROR -> MaterialTheme.typography.bodyMedium
            TextType.WARNING -> MaterialTheme.typography.bodyMedium
        }
        
        val color = when (type) {
            TextType.ERROR -> CurrentTheme.getCurrentColorScheme().error
            TextType.WARNING -> CurrentTheme.getCurrentColorScheme().primary // Pas de warning dans M3, utilise primary
            else -> CurrentTheme.getCurrentColorScheme().onSurface
        }
        
        // Construction du modifier
        val textModifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
        
        androidx.compose.material3.Text(
            text = text,
            style = style,
            color = color,
            modifier = textModifier,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
    
    @Composable
    override fun Card(
        type: CardType,
        size: Size,
        highlight: Boolean,
        content: @Composable () -> Unit
    ) {
        val elevation = when (size) {
            Size.XS, Size.S -> CardDefaults.cardElevation(defaultElevation = 2.dp)
            Size.M -> CardDefaults.cardElevation(defaultElevation = 4.dp)
            Size.L, Size.XL, Size.XXL -> CardDefaults.cardElevation(defaultElevation = 8.dp)
        }

        val border = if (highlight) {
            BorderStroke(2.dp, CurrentTheme.getCurrentColorScheme().primary)
        } else {
            null
        }

        val colors = when (type) {
            CardType.SECTION_HEADER -> CardDefaults.cardColors(
                containerColor = CurrentTheme.getCurrentColorScheme().surfaceVariant,
                contentColor = CurrentTheme.getCurrentColorScheme().onSurfaceVariant
            )
            CardType.DEFAULT -> CardDefaults.cardColors(
                containerColor = CurrentTheme.getCurrentColorScheme().surface,
                contentColor = CurrentTheme.getCurrentColorScheme().onSurface
            )
        }

        androidx.compose.material3.Card(
            elevation = elevation,
            shape = CardShape,
            border = border,
            colors = colors,
            content = { content() }
        )
    }

    @Composable
    override fun StatusIndicator(
        color: Color,
        size: Dp
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color = color, shape = CircleShape)
        )
    }

    @Composable
    override fun statusColor(status: com.assistant.core.ui.StatusColor): Color {
        val scheme = CurrentTheme.getCurrentColorScheme()
        return when (status) {
            com.assistant.core.ui.StatusColor.SUCCESS -> scheme.primary
            com.assistant.core.ui.StatusColor.WARNING -> scheme.tertiary
            com.assistant.core.ui.StatusColor.ERROR -> scheme.error
            com.assistant.core.ui.StatusColor.INFO -> scheme.secondary
            com.assistant.core.ui.StatusColor.MUTED -> scheme.outline
        }
    }

    /** A Material surface: the background, and the content color Material's components inside read. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = CurrentTheme.getCurrentColorScheme().surface) {
            content()
        }
    }

    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CurrentTheme.getCurrentColorScheme().surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    @Composable
    override fun Tag(
        text: String,
        color: com.assistant.core.themes.TagColor
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = getTagColor(color, com.assistant.core.themes.CurrentTheme.currentPaletteId),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            // Dark text on every tag: the tag colors are soft enough in both palettes to carry it
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = TagTextColor
            )
        }
    }

    @Composable
    override fun Gauge(fraction: Float) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }

    @Composable
    override fun drawingTextStyle(): androidx.compose.ui.text.TextStyle =
        MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    @Composable
    override fun Drawing(drawing: com.assistant.core.drawing.Drawing, modifier: Modifier) {
        DefaultDrawing.Draw(drawing, drawingTextStyle(), com.assistant.core.themes.CurrentTheme.currentPaletteId == "default_dark", modifier)
    }

    @Composable
    override fun Divider() {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    @Composable
    override fun DragHandle() {
        val context = LocalContext.current
        val iconResource = requireNotNull(com.assistant.core.icons.Icons.drawable(context, "grip-vertical")) {
            "No drawable for the icon grip-vertical"
        }
        // Padded to a finger's width: the grip is small, the place it is taken by is not
        Icon(
            painter = painterResource(iconResource),
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) {
        val elevation by animateDpAsState(if (lifted) 8.dp else 0.dp, label = "reorder_elevation")
        Box(
            modifier = Modifier.graphicsLayer {
                shadowElevation = elevation.toPx()
                shape = RoundedCornerShape(12.dp)
                clip = false
                val scale = if (lifted) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
        ) {
            content()
        }
    }

    override fun getTagColor(color: com.assistant.core.themes.TagColor, paletteId: String): Color {
        val dark = paletteId == "default_dark"
        return when (color) {
            com.assistant.core.themes.TagColor.RED -> if (dark) Color(0xFFE39A9A) else Color(0xFFF4B9B9)
            com.assistant.core.themes.TagColor.ORANGE -> if (dark) Color(0xFFEFB48C) else Color(0xFFF9CDAE)
            com.assistant.core.themes.TagColor.YELLOW -> if (dark) Color(0xFFE6D48A) else Color(0xFFF5E8AE)
            com.assistant.core.themes.TagColor.GREEN -> if (dark) Color(0xFFA6CF9C) else Color(0xFFC6E3BE)
            com.assistant.core.themes.TagColor.TEAL -> if (dark) Color(0xFF8FCBC4) else Color(0xFFB5E0DA)
            com.assistant.core.themes.TagColor.BLUE -> if (dark) Color(0xFF9BB8E8) else Color(0xFFBCD0F2)
            com.assistant.core.themes.TagColor.PURPLE -> if (dark) Color(0xFFC0A6DA) else Color(0xFFD8C6EA)
            com.assistant.core.themes.TagColor.PINK -> if (dark) Color(0xFFE3A3C6) else Color(0xFFF3C3DC)
            com.assistant.core.themes.TagColor.GREY -> if (dark) Color(0xFFB8B2C2) else Color(0xFFD9D5E0)
        }
    }

    private val TagTextColor = Color(0xFF2E2C3A)

    // =====================================
    // FEEDBACK SYSTEM
    // =====================================
    
    override fun Toast(
        context: android.content.Context,
        message: String,
        duration: Duration
    ) {
        val androidDuration = when (duration) {
            Duration.SHORT -> android.widget.Toast.LENGTH_SHORT
            Duration.LONG -> android.widget.Toast.LENGTH_LONG
            Duration.INDEFINITE -> android.widget.Toast.LENGTH_LONG // No equivalent, use LONG
        }
        android.widget.Toast.makeText(context, message, androidDuration).show()
    }
    
    @Composable
    override fun Snackbar(
        type: FeedbackType,
        message: String,
        action: String?,
        onAction: (() -> Unit)?
    ) {
        androidx.compose.material3.Snackbar(
            action = if (action != null && onAction != null) {
                {
                    TextButton(
                        onClick = onAction,
                        shape = ButtonTextShape
                    ) {
                        androidx.compose.material3.Text(action)
                    }
                }
            } else null,
            containerColor = when (type) {
                FeedbackType.SUCCESS -> CurrentTheme.getCurrentColorScheme().primary
                FeedbackType.ERROR -> CurrentTheme.getCurrentColorScheme().error
                FeedbackType.WARNING -> CurrentTheme.getCurrentColorScheme().tertiary
                FeedbackType.INFO -> CurrentTheme.getCurrentColorScheme().surfaceVariant
            }
        ) {
            androidx.compose.material3.Text(message)
        }
    }

    @Composable
    override fun ConfirmDialog(
        title: String,
        message: String,
        confirmText: String?,
        cancelText: String?,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val s = com.assistant.core.strings.Strings.`for`(context = context)

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                androidx.compose.material3.Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
            },
            text = {
                androidx.compose.material3.Text(
                    text = message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text(
                        text = confirmText ?: s.shared("action_confirm"),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text(
                        text = cancelText ?: s.shared("action_cancel"),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                    )
                }
            },
            containerColor = CurrentTheme.getCurrentColorScheme().surface,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // =====================================
    // SYSTEM
    // =====================================
    
    @Composable
    override fun LoadingIndicator(size: Size) {
        val indicatorSize = when (size) {
            Size.XS -> 16.dp
            Size.S -> 24.dp
            Size.M -> 32.dp
            Size.L -> 48.dp
            Size.XL -> 64.dp
            Size.XXL -> 80.dp
        }
        
        CircularProgressIndicator(modifier = Modifier.size(indicatorSize))
    }
    
    @Composable
    override fun Icon(
        resourceId: Int,
        size: Dp,
        contentDescription: String?,
        tint: androidx.compose.ui.graphics.Color?,
        background: androidx.compose.ui.graphics.Color?
    ) {
        val iconModifier = if (background != null) {
            Modifier
                .size(size)
                .background(background, ButtonIconShape)
        } else {
            Modifier.size(size)
        }
        
        Icon(
            painter = painterResource(id = resourceId),
            contentDescription = contentDescription,
            modifier = iconModifier,
            tint = tint ?: CurrentTheme.getCurrentColorScheme().onSurface
        )
    }
    
    @Composable
    override fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        confirmEnabled: Boolean,
        content: @Composable () -> Unit
    ) {
        val s = com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current)
        val cancel = s.shared("action_cancel")
        val (confirmText, cancelText) = when (type) {
            DialogType.CONFIGURE, DialogType.CONFIRM -> s.shared("action_confirm") to cancel
            DialogType.CREATE -> s.shared("action_create") to cancel
            DialogType.EDIT -> s.shared("action_save") to cancel
            DialogType.DANGER -> s.shared("action_delete") to cancel
            DialogType.SELECTION -> null to cancel
            DialogType.INFO -> s.shared("action_ok") to null
        }
        
        AlertDialog(
            onDismissRequest = onCancel,
            text = { Box(modifier = Modifier.scrollEndSound()) { content() } },
            confirmButton = if (confirmText != null) {
                {
                    androidx.compose.material3.Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        colors = if (type == DialogType.DANGER) {
                            ButtonDefaults.buttonColors(
                                containerColor = CurrentTheme.getCurrentColorScheme().error,
                                contentColor = CurrentTheme.getCurrentColorScheme().onError
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = CurrentTheme.getCurrentColorScheme().primary,
                                contentColor = CurrentTheme.getCurrentColorScheme().onPrimary
                            )
                        },
                        shape = ButtonTextShape
                    ) {
                        androidx.compose.material3.Text(confirmText)
                    }
                }
            } else {
                {}
            },
            dismissButton = if (cancelText != null) {
                {
                    TextButton(
                        onClick = onCancel,
                        shape = ButtonTextShape
                    ) {
                        androidx.compose.material3.Text(cancelText)
                    }
                }
            } else null
        )
    }
    
    // =====================================
    // SPECIALIZED CONTAINERS (appearance only)
    // =====================================
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun ZoneCardContainer(
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        // Theme defines ONLY appearance: borders, colors, shadows, etc.
        androidx.compose.material3.Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = CurrentTheme.getCurrentColorScheme().surface,
                contentColor = CurrentTheme.getCurrentColorScheme().onSurface
            ),
            shape = CardShape,
            // The grid gives the tile its cells; the space between tiles is taken inside them
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            // Le contenu vient de UI.ZoneCard()
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
    
    @Composable
    override fun WaitingMark() {
        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }

    @Composable
    override fun GridCell() {
        Box(modifier = Modifier.fillMaxSize().padding(2.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)))
    }

    @Composable
    override fun RunningMark() {
        Box(modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
            com.assistant.core.ui.UI.Icon(iconName = "timer", size = 10.dp, tint = MaterialTheme.colorScheme.tertiary)
        }
    }

    @Composable
    override fun ToolCardContainer(
        displayMode: DisplayMode,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        // The grid gives the tile its cells; the space between tiles is taken inside them
        val cardModifier = Modifier.fillMaxSize().padding(4.dp)

        val cardPadding = tilePadding(displayMode)
        
        androidx.compose.material3.Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = CurrentTheme.getCurrentColorScheme().surface,
                contentColor = CurrentTheme.getCurrentColorScheme().onSurface
            ),
            modifier = cardModifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
        ) {
            // Le contenu vient de UI.ToolCard()
            Box(modifier = Modifier.padding(cardPadding)) {
                content()
            }
        }
    }
    
    // =====================================
    // FORMULAIRES
    // =====================================
    
    /** A required field is marked by an asterisk after its label; an optional one bears no mark. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Text(if (required) "$label *" else label, TextType.LABEL, false, null)
    }

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
        fieldModifier: FieldModifier
    ) {
        
        Column {
            FieldLabel(label, required)
            
            if (readonly) {
                // Display as text when readonly - clickable if onClick provided
                val textModifier = if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
                
                Box(modifier = textModifier) {
                    Text(
                        text = value.text.ifBlank { com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current).shared("label_no_value") },
                        type = TextType.BODY,
                        fillMaxWidth = false,
                        textAlign = null
                    )
                }
            } else {
                TextField(
                    fieldType = fieldType,
                    state = state,
                    value = value,
                    onChange = onChange,
                    placeholder = label,
                    fieldModifier = fieldModifier
                )
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun FormSelection(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
        required: Boolean
    ) {
        var expanded by remember { mutableStateOf(false) }
        
        Column {
            FieldLabel(label, required)
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selected,
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { androidx.compose.material3.Text(option) },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    override fun FormActions(
        content: @Composable RowScope.() -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
    
    @Composable
    override fun Checkbox(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String?
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            
            if (label != null) {
                Text(label, TextType.BODY, false, null)
            }
        }
    }
    
    @Composable
    override fun Switch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, TextType.BODY, false, null)
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    override fun Tabs(
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ) {
        androidx.compose.material3.TabRow(selectedTabIndex = selected) {
            labels.forEachIndexed { index, label ->
                androidx.compose.material3.Tab(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    text = { Text(label, TextType.BODY, false, null) }
                )
            }
        }
    }

    /** Two segmented buttons side by side; neither is chosen while there is no answer. */
    @OptIn(ExperimentalMaterial3Api::class)
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label.isNotBlank()) FieldLabel(label, required)
            SingleChoiceSegmentedButtonRow(modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
                listOf(true to trueLabel, false to falseLabel).forEachIndexed { index, (answer, text) ->
                    SegmentedButton(
                        selected = value == answer,
                        onClick = {
                            if (value != answer) onValueChange(answer)
                            else if (emptiable) onValueChange(null)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                    ) {
                        androidx.compose.material3.Text(text)
                    }
                }
            }
        }
    }
    
    /**
     * Without an answer the slider has no thumb, its track all inactive, and its value reads "—".
     * Compose reports no change when a touch lands where its thumb already is, and an empty
     * slider rests on its minimum: a touch that changed nothing on an empty slider answers that
     * minimum when it ends.
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
        val context = androidx.compose.ui.platform.LocalContext.current
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        // The track ends on the last stop, so every notch Compose draws falls on a step from min
        val lastStop = com.assistant.core.ui.SliderSteps.lastStop(min, max, step)
        val decimals = com.assistant.core.ui.SliderSteps.decimals(min, step)
        val currentValue by rememberUpdatedState(value)
        // Whether the touch under way has changed the value: read at once, not after a recomposition
        val changedByTouch = remember { mutableStateOf(false) }
        val colors = CurrentTheme.getCurrentColorScheme()

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FieldLabel(label, required)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    text = value?.let { String.format(java.util.Locale.getDefault(), "%.${decimals}f", it) } ?: "—",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = if (value != null) colors.primary else colors.onSurfaceVariant
                )
                if (value != null && !required) {
                    TextButton(onClick = { onValueChange(null) }) {
                        androidx.compose.material3.Text(s.shared("action_clear"))
                    }
                }
            }

            Slider(
                value = (value ?: min).toFloat(),
                onValueChange = {
                    changedByTouch.value = true
                    onValueChange(com.assistant.core.ui.SliderSteps.snap(it.toDouble(), min, step))
                },
                onValueChangeFinished = {
                    if (!changedByTouch.value && currentValue == null) onValueChange(min)
                    changedByTouch.value = false
                },
                valueRange = min.toFloat()..lastStop.toFloat(),
                steps = com.assistant.core.ui.SliderSteps.innerStops(min, lastStop, step),
                colors = if (value != null) SliderDefaults.colors() else SliderDefaults.colors(
                    thumbColor = androidx.compose.ui.graphics.Color.Transparent,
                    activeTrackColor = colors.surfaceVariant,
                    activeTickColor = colors.onSurfaceVariant
                )
            )

            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Text(
                        text = minLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        text = maxLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DatePicker(
        selectedDate: String,
        onDateSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        // Which day the picker opens on. Callers pass "" to mean nothing is chosen yet, so
        // that case opens on today by contract, not as a fallback hiding a failure.
        val selectedDateMs = DateUtils.parseDateForFilter(selectedDate) ?: System.currentTimeMillis()
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        val offsetMs = timezone.rules.getOffset(java.time.Instant.ofEpochMilli(selectedDateMs)).totalSeconds * 1000L
        val utcDate = selectedDateMs + offsetMs
        
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcDate,
            initialDisplayMode = androidx.compose.material3.DisplayMode.Picker
        )
        
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(DateUtils.formatDateForDisplay(millis))
                        }
                        onDismiss()
                    },
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text(com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current).shared("action_ok"))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text(com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current).shared("action_cancel"))
                }
            }
        ) {
            DatePicker(
                state = datePickerState
            )
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun TimePicker(
        selectedTime: String,
        onTimeSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        // Same contract for the time: "" means nothing chosen, and the picker opens on now.
        val (hour, minute) = DateUtils.parseTime(selectedTime)
            ?: java.time.ZonedDateTime.now(AppConfigManager.getDateTimeConfig().getZoneId())
                .let { Pair(it.hour, it.minute) }
        
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val formattedTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        onTimeSelected(formattedTime)
                        onDismiss()
                    },
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text(com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current).shared("action_ok"))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text(com.assistant.core.strings.Strings.`for`(context = androidx.compose.ui.platform.LocalContext.current).shared("action_cancel"))
                }
            },
            text = { TimePicker(state = timePickerState) }
        )
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed left area (48.dp)
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                leftButton?.let { action ->
                    com.assistant.core.ui.UI.ActionButton(
                        action = action,
                        display = ButtonDisplay.ICON,
                        size = Size.M,
                        onClick = onLeftClick ?: { }
                    )
                }
            }
            
            // Central zone (centered title, flexible)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Line 1: Icon + Title
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let { iconName ->
                        val context = LocalContext.current
                        com.assistant.core.icons.Icons.drawable(context, iconName)?.let { iconResource ->
                            Icon(
                                painter = painterResource(iconResource),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = CurrentTheme.getCurrentColorScheme().onSurface
                            )
                        }
                    }
                    Text(title, TextType.TITLE, false, TextAlign.Center)
                }
                
                // Line 2: Subtitle (forced centered)
                subtitle?.let { 
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(it, TextType.CAPTION, false, TextAlign.Center)
                    }
                }
            }
            
            // Fixed right area (48.dp)
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                rightButton?.let { action ->
                    com.assistant.core.ui.UI.ActionButton(
                        action = action,
                        display = ButtonDisplay.ICON,
                        size = Size.M,
                        onClick = onRightClick ?: { }
                    )
                }
            }
        }
    }

    @Composable
    override fun AIThinkingIndicator() {
        val colors = CurrentTheme.getCurrentColorScheme()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Three animated dots
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "thinkingDot$index")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 200)
                    ),
                    label = "dotAlpha$index"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = colors.primary.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )

                if (index < 2) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }

    // =====================================
    // AI MESSAGING COMPONENTS IMPLEMENTATION
    // =====================================

    @Composable
    override fun MessageBubble(
        sender: com.assistant.core.ai.data.MessageSender,
        content: @Composable () -> Unit
    ) {
        // Just wrap in a Card like the original code did - no custom styling
        // The alignment logic stays in AIFloatingChat.kt where it was before
        Card(
            type = CardType.DEFAULT,
            size = Size.M,
            highlight = false,
            content = { Box(modifier = Modifier.padding(12.dp)) { content() } }
        )
    }

    @Composable
    override fun InteractionCard(
        title: String,
        content: @Composable ColumnScope.() -> Unit,
        actions: @Composable RowScope.() -> Unit
    ) {
        val colors = CurrentTheme.getCurrentColorScheme()

        // Card with primary border highlight for attention
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = colors.primary,
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            androidx.compose.material3.Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface
                ),
                shape = CardShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    androidx.compose.material3.Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    // Content
                    content()

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}