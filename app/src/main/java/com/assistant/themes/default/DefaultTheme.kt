package com.assistant.themes.default

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.assistant.core.utils.DateUtils
import com.assistant.core.tools.BaseSchemas
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
                primary = Color(0xFF1976D2),        // Material Blue
                onPrimary = Color(0xFFFFFFFF),      // White
                secondary = Color(0xFF03DAC6),      // Teal
                onSecondary = Color(0xFF000000),    // Black
                surface = Color(0xFFFFFFFF),        // White
                onSurface = Color(0xFF1C1B1F),      // Dark Gray
                surfaceVariant = Color(0xFFF3F0F4), // Very Light Gray
                onSurfaceVariant = Color(0xFF49454F), // Medium Dark Gray
                background = Color(0xFFFFFBFE),     // Off White
                onBackground = Color(0xFF1C1B1F),   // Dark Gray
                error = Color(0xFFBA1A1A),          // Red
                onError = Color(0xFFFFFFFF),        // White
                outline = Color(0xFF79747E),        // Medium Gray
                outlineVariant = Color(0xFFCAC4D0)  // Light Gray
            )
            "default_dark" -> darkColorScheme(
                primary = Color(0xFF00FF41),        // Green Matrix/terminal
                onPrimary = Color(0xFF0D1117),      // Deep terminal black
                secondary = Color(0xFF58A6FF),      // Code blue (GitHub)
                onSecondary = Color(0xFF0D1117),    // Deep terminal black
                surface = Color(0xFF161B22),        // GitHub dark gray
                onSurface = Color(0xFFF0F6FC),      // Terminal off-white
                surfaceVariant = Color(0xFF21262D), // GitHub medium gray
                onSurfaceVariant = Color(0xFF8B949E), // Comment gray
                background = Color(0xFF0D1117),     // GitHub/terminal black
                onBackground = Color(0xFFF0F6FC),   // Terminal off-white
                error = Color(0xFFFF6B6B),          // Soft terminal red
                onError = Color(0xFF0D1117),        // Deep terminal black
                outline = Color(0xFF30363D),        // Subtle GitHub border
                outlineVariant = Color(0xFF21262D)  // More subtle border
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
        onClick: () -> Unit
    ) {
        // État du dialogue de confirmation
        var showConfirmDialog by remember { mutableStateOf(false) }
        
        // Determine default type based on action
        val buttonType = type ?: getDefaultButtonType(action)
        
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
                androidx.compose.material3.Text(
                    getButtonIcon(action),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            } else {
                androidx.compose.material3.Text(
                    getButtonText(action)
                )
            }
        }
        
        // Dialogue de confirmation automatique
        if (showConfirmDialog && requireConfirmation) {
            Dialog(
                type = DialogType.DANGER,
                onConfirm = {
                    showConfirmDialog = false
                    onClick()  // Execute action after confirmation
                },
                onCancel = {
                    showConfirmDialog = false
                }
            ) {
                androidx.compose.material3.Text(
                    confirmMessage ?: getDefaultConfirmMessage(action),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    
    // Helpers for action -> type/text/icon mapping
    private fun getDefaultButtonType(action: ButtonAction): ButtonType {
        return when (action) {
            // PRIMARY: Actions critiques/importantes
            ButtonAction.SAVE, ButtonAction.CREATE, ButtonAction.ADD, ButtonAction.CONFIGURE, ButtonAction.SELECT, ButtonAction.EDIT, ButtonAction.UPDATE, ButtonAction.CONFIRM -> ButtonType.PRIMARY
            
            // SECONDARY: Actions destructives/dangereuses avec confirmation
            ButtonAction.DELETE -> ButtonType.SECONDARY
            
            // DEFAULT: Actions neutres/navigation standard
            ButtonAction.CANCEL, ButtonAction.BACK, ButtonAction.REFRESH, ButtonAction.UP, ButtonAction.DOWN, ButtonAction.LEFT, ButtonAction.RIGHT -> ButtonType.DEFAULT
        }
    }
    
    @Composable
    private fun getButtonText(action: ButtonAction): String {
        val context = androidx.compose.ui.platform.LocalContext.current
        val s = com.assistant.core.strings.Strings.`for`(context = context)
        
        return when (action) {
            ButtonAction.SAVE -> s.shared("action_save")
            ButtonAction.CREATE -> s.shared("action_create")
            ButtonAction.UPDATE -> s.shared("action_update")
            ButtonAction.DELETE -> s.shared("action_delete")
            ButtonAction.CANCEL -> s.shared("action_cancel")
            ButtonAction.BACK -> s.shared("action_back")
            ButtonAction.CONFIGURE -> s.shared("action_configure")
            ButtonAction.ADD -> s.shared("action_add")
            ButtonAction.EDIT -> s.shared("action_edit")
            ButtonAction.REFRESH -> s.shared("action_refresh")
            ButtonAction.SELECT -> s.shared("action_select")
            ButtonAction.CONFIRM -> s.shared("action_confirm")
            ButtonAction.UP -> s.shared("action_up")
            ButtonAction.DOWN -> s.shared("action_down")
            ButtonAction.LEFT -> s.shared("action_left")
            ButtonAction.RIGHT -> s.shared("action_right")
        }
    }
    
    private fun getButtonIcon(action: ButtonAction): String {
        // Unicode symbols for readability and consistency
        return when (action) {
            ButtonAction.SAVE -> "✓"      // Check mark
            ButtonAction.CREATE -> "+"    // Plus
            ButtonAction.UPDATE -> "✎"    // Pencil
            ButtonAction.DELETE -> "✕"    // X mark
            ButtonAction.CANCEL -> "✕"    // X mark
            ButtonAction.BACK -> "◀"      // Triangle gauche
            ButtonAction.CONFIGURE -> "⚙" // Gear
            ButtonAction.ADD -> "+"       // Plus
            ButtonAction.EDIT -> "✎"      // Pencil
            ButtonAction.REFRESH -> "↻"   // Circular arrow
            ButtonAction.SELECT -> "✓"    // Check mark
            ButtonAction.CONFIRM -> "✓"   // Check mark
            ButtonAction.UP -> "▲"        // Triangle haut
            ButtonAction.DOWN -> "▼"      // Triangle bas
            ButtonAction.LEFT -> "◀"      // Triangle gauche
            ButtonAction.RIGHT -> "▶"     // Triangle droite
        }
    }
    
    private fun getDefaultConfirmMessage(action: ButtonAction): String {
        return when (action) {
            ButtonAction.DELETE -> "Are you sure you want to delete this item? This action is irreversible."
            else -> "Confirm this action?"
        }
    }
    
    @Composable
    override fun TextField(
        fieldType: FieldType,
        state: ComponentState,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String,
        fieldModifier: FieldModifier
    ) {
        val isError = state == ComponentState.ERROR
        val isReadOnly = state == ComponentState.READONLY
        
        // Configuration intelligente du clavier selon le type de champ
        val keyboardOptions = when (fieldType) {
            FieldType.TEXT -> KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
            FieldType.TEXT_MEDIUM -> KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
            FieldType.TEXT_LONG -> KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            FieldType.TEXT_UNLIMITED -> KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            )
            FieldType.NUMERIC -> KeyboardOptions(
                keyboardType = KeyboardType.Number,
                autoCorrect = false,
                imeAction = ImeAction.Next
            )
            FieldType.EMAIL -> KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            )
            FieldType.PASSWORD -> KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            )
            FieldType.SEARCH -> KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            )
        }
        
        // Character limit based on field type
        val maxLength = when (fieldType) {
            FieldType.TEXT -> BaseSchemas.FieldLimits.SHORT_LENGTH
            FieldType.TEXT_MEDIUM -> BaseSchemas.FieldLimits.MEDIUM_LENGTH
            FieldType.TEXT_LONG -> BaseSchemas.FieldLimits.LONG_LENGTH
            FieldType.TEXT_UNLIMITED -> BaseSchemas.FieldLimits.UNLIMITED_LENGTH
            FieldType.NUMERIC, FieldType.EMAIL, FieldType.PASSWORD, FieldType.SEARCH -> BaseSchemas.FieldLimits.UNLIMITED_LENGTH
        }
        
        // Filter input if there's a character limit
        val filteredOnChange: (String) -> Unit = if (maxLength < Int.MAX_VALUE) {
            { newValue -> 
                if (newValue.length <= maxLength) {
                    onChange(newValue)
                }
            }
        } else {
            onChange
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = filteredOnChange,
            placeholder = { androidx.compose.material3.Text(placeholder) },
            isError = isError,
            readOnly = isReadOnly,
            enabled = state != ComponentState.DISABLED,
            keyboardOptions = keyboardOptions,
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
        textAlign: TextAlign?
    ) {
        val style = when (type) {
            TextType.TITLE -> MaterialTheme.typography.headlineMedium
            TextType.SUBTITLE -> MaterialTheme.typography.headlineSmall
            TextType.BODY -> MaterialTheme.typography.bodyMedium
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
            textAlign = textAlign
        )
    }
    
    @Composable
    override fun Card(
        type: CardType,
        size: Size,
        content: @Composable () -> Unit
    ) {
        val elevation = when (size) {
            Size.XS, Size.S -> CardDefaults.cardElevation(defaultElevation = 2.dp)
            Size.M -> CardDefaults.cardElevation(defaultElevation = 4.dp)
            Size.L, Size.XL, Size.XXL -> CardDefaults.cardElevation(defaultElevation = 8.dp)
        }
        
        androidx.compose.material3.Card(
            elevation = elevation,
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = CurrentTheme.getCurrentColorScheme().surface,
                contentColor = CurrentTheme.getCurrentColorScheme().onSurface
            ),
            content = { content() }
        )
    }
    
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
                FeedbackType.ERROR -> CurrentTheme.getCurrentColorScheme().onErrorContainer
                FeedbackType.WARNING -> CurrentTheme.getCurrentColorScheme().secondary
                FeedbackType.INFO -> CurrentTheme.getCurrentColorScheme().surfaceVariant
            }
        ) {
            androidx.compose.material3.Text(message)
        }
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
        content: @Composable () -> Unit
    ) {
        val (confirmText, cancelText) = when (type) {
            DialogType.CONFIGURE -> "Confirm" to "Cancel"
            DialogType.CREATE -> "Create" to "Cancel"
            DialogType.EDIT -> "Save" to "Cancel"
            DialogType.CONFIRM -> "Confirmer" to "Annuler"
            DialogType.DANGER -> "Delete" to "Cancel"
            DialogType.SELECTION -> null to "Annuler"
            DialogType.INFO -> "OK" to null
        }
        
        AlertDialog(
            onDismissRequest = onCancel,
            text = { content() },
            confirmButton = if (confirmText != null) {
                {
                    androidx.compose.material3.Button(
                        onClick = onConfirm,
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
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            // Le contenu vient de UI.ZoneCard()
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
    
    @Composable
    override fun ToolCardContainer(
        displayMode: DisplayMode,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        // Theme defines appearance based on display mode
        val cardModifier = when (displayMode) {
            DisplayMode.ICON -> Modifier.size(64.dp)
            DisplayMode.MINIMAL -> Modifier.height(48.dp).fillMaxWidth()
            DisplayMode.LINE -> Modifier.height(64.dp).fillMaxWidth()
            DisplayMode.CONDENSED -> Modifier.size(128.dp)
            DisplayMode.EXTENDED -> Modifier.width(256.dp).height(128.dp)
            DisplayMode.SQUARE -> Modifier.size(256.dp)
            DisplayMode.FULL -> Modifier.fillMaxWidth().wrapContentHeight()
        }
        
        val cardPadding = when (displayMode) {
            DisplayMode.ICON -> 4.dp
            DisplayMode.MINIMAL -> 8.dp
            else -> 12.dp
        }
        
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
    
    @Composable
    override fun FormField(
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
    ) {
        val displayLabel = if (required) label else "$label (optionnel)"
        
        Column {
            Text(displayLabel, TextType.LABEL, false, null)
            
            if (readonly) {
                // Display as text when readonly - clickable if onClick provided
                val textModifier = if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
                
                Box(modifier = textModifier) {
                    Text(
                        text = if (value.isNotBlank()) value else "(vide)",
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
        val displayLabel = if (required) label else "$label (optionnel)"
        
        Column {
            Text(displayLabel, TextType.LABEL, false, null)
            
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
    override fun ToggleField(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        trueLabel: String,
        falseLabel: String,
        required: Boolean
    ) {
        val displayLabel = if (required) label else "$label (optionnel)"
        
        if (label.isNotBlank()) {
            // Avec label - structure Column
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Label du champ
                Text(displayLabel, TextType.LABEL, false, null)
                
                // Toggle avec labels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange
                    )
                    
                    androidx.compose.material3.Text(
                        text = if (checked) trueLabel else falseLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = if (checked) {
                            CurrentTheme.getCurrentColorScheme().primary
                        } else {
                            CurrentTheme.getCurrentColorScheme().onSurfaceVariant
                        }
                    )
                }
            }
        } else {
            // Sans label - juste le Row avec toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
                
                androidx.compose.material3.Text(
                    text = if (checked) trueLabel else falseLabel,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = if (checked) {
                        CurrentTheme.getCurrentColorScheme().primary
                    } else {
                        CurrentTheme.getCurrentColorScheme().onSurfaceVariant
                    }
                )
            }
        }
    }
    
    @Composable
    override fun SliderField(
        label: String,
        value: Int,
        onValueChange: (Int) -> Unit,
        range: IntRange,
        minLabel: String,
        maxLabel: String,
        required: Boolean
    ) {
        val displayLabel = if (required) label else "$label (optionnel)"
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Label du champ
            Text(displayLabel, TextType.LABEL, false, null)
            
            // Valeur actuelle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Text(
                    text = value.toString(),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = CurrentTheme.getCurrentColorScheme().primary
                )
            }
            
            // Slider
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = if (range.last - range.first > 1) range.last - range.first - 1 else 0
            )
            
            // Labels min/max
            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Text(
                        text = minLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = CurrentTheme.getCurrentColorScheme().onSurfaceVariant
                    )
                    
                    androidx.compose.material3.Text(
                        text = maxLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = CurrentTheme.getCurrentColorScheme().onSurfaceVariant
                    )
                }
            }
        }
    }
    
    @Composable
    override fun CounterField(
        label: String,
        incrementButtons: List<Pair<String, Int>>,
        decrementButtons: List<Pair<String, Int>>,
        onIncrement: (Int) -> Unit,
        required: Boolean
    ) {
        val displayLabel = if (required) label else "$label (optionnel)"
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Label du champ
            Text(displayLabel, TextType.LABEL, false, null)
            
            // Increment buttons  
            if (incrementButtons.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Ajouter :",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = CurrentTheme.getCurrentColorScheme().onSurface
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    incrementButtons.chunked(2).forEach { rowButtons ->
                        rowButtons.forEach { (displayText, incrementValue) ->
                            Button(
                                onClick = { onIncrement(incrementValue) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CurrentTheme.getCurrentColorScheme().surfaceVariant,
                                    contentColor = CurrentTheme.getCurrentColorScheme().onSurfaceVariant
                                ),
                                shape = ButtonTextShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                androidx.compose.material3.Text(displayText)
                            }
                        }
                    }
                }
            }
            
            // Decrement buttons
            if (decrementButtons.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Retirer :",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = CurrentTheme.getCurrentColorScheme().onSurface
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    decrementButtons.chunked(2).forEach { rowButtons ->
                        rowButtons.forEach { (displayText, decrementValue) ->
                            Button(
                                onClick = { onIncrement(-decrementValue) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CurrentTheme.getCurrentColorScheme().errorContainer,
                                    contentColor = CurrentTheme.getCurrentColorScheme().onErrorContainer
                                ),
                                shape = ButtonTextShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                androidx.compose.material3.Text(displayText)
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    override fun DynamicList(
        label: String,
        items: List<String>,
        onItemsChanged: (List<String>) -> Unit,
        placeholder: String,
        required: Boolean,
        minItems: Int,
        maxItems: Int
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Label with required indicator
            if (label.isNotBlank()) {
                val displayLabel = if (required) label else "$label (optionnel)"
                Text(displayLabel, TextType.LABEL, false, null)
            }
            
            // List of items with delete buttons
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = item,
                        onValueChange = { newValue ->
                            val newItems = items.toMutableList()
                            newItems[index] = newValue
                            onItemsChanged(newItems)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    // Delete button (only show if more than minItems)
                    if (items.size > minItems) {
                        ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            type = null,
                            enabled = true,
                            requireConfirmation = false,
                            confirmMessage = null,
                            onClick = {
                                val newItems = items.toMutableList()
                                newItems.removeAt(index)
                                onItemsChanged(newItems)
                            }
                        )
                    }
                }
            }
            
            // Add button (only show if less than maxItems)
            if (items.size < maxItems) {
                ActionButton(
                    action = ButtonAction.ADD,
                    display = ButtonDisplay.LABEL,
                    size = Size.M,
                    type = null,
                    enabled = true,
                    requireConfirmation = false,
                    confirmMessage = null,
                    onClick = {
                        onItemsChanged(items + "")
                    }
                )
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
        // Convert local date to UTC for DatePicker compatibility
        val selectedDateMs = DateUtils.parseDateForFilter(selectedDate)
        val utcDate = selectedDateMs + java.util.TimeZone.getDefault().getOffset(selectedDateMs)
        
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        // Use key() to recreate state on orientation change
        val datePickerState = key(isLandscape) {
            rememberDatePickerState(
                initialSelectedDateMillis = utcDate,
                initialDisplayMode = if (isLandscape) {
                    androidx.compose.material3.DisplayMode.Input // Mode saisie plus compact
                } else {
                    androidx.compose.material3.DisplayMode.Picker // Mode roue normal
                }
            )
        }
        
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
                    androidx.compose.material3.Text("OK")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text("Annuler")
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
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        val (hour, minute) = DateUtils.parseTime(selectedTime)
        
        // Use key() to recreate state on orientation change
        val timePickerState = key(isLandscape) {
            rememberTimePickerState(
                initialHour = hour,
                initialMinute = minute,
                is24Hour = true // Format 24h plus compact
            )
        }
        
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
                    androidx.compose.material3.Text("OK")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    shape = ButtonTextShape
                ) {
                    androidx.compose.material3.Text("Annuler")
                }
            },
            text = {
                if (isLandscape) {
                    // Mode paysage - TimePicker avec layout plus compact
                    TimeInput(state = timePickerState)
                } else {
                    // Mode portrait - TimePicker normal avec roues
                    TimePicker(state = timePickerState)
                }
            }
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
            // Zone gauche fixe (48.dp)
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
                        if (com.assistant.core.themes.ThemeIconManager.iconExists(context, "default", iconName)) {
                            val iconResource = com.assistant.core.themes.ThemeIconManager.getIconResource(context, "default", iconName)
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
            
            // Zone droite fixe (48.dp)
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
}