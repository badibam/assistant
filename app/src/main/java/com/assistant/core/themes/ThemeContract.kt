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

/**
 * ThemeContract - Interface que tous les thèmes doivent implémenter
 * UNIQUEMENT les composants VISUELS (thématisés)
 * 
 * LAYOUTS : utiliser Row/Column/Box/Spacer de Compose directement
 * VISUELS : définis par ce contrat pour thématisation
 * 
 * Chaque thème (DefaultTheme, RetroTheme, etc.) implémente cette interface
 * pour fournir son propre style visuel aux composants
 */
interface ThemeContract {
    
    // =====================================
    // LAYOUTS : UTILISER COMPOSE DIRECTEMENT
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // PAS d'interface - accès direct pour flexibilité maximale
    
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
        placeholder: String
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
        content: @Composable () -> Unit
    )
    
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
    // CONTAINERS SPÉCIALISÉS (apparence uniquement)
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
        required: Boolean
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
    
    @Composable
    fun ToggleField(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        trueLabel: String,
        falseLabel: String,
        required: Boolean
    )
    
    @Composable
    fun SliderField(
        label: String,
        value: Int,
        onValueChange: (Int) -> Unit,
        range: IntRange,
        minLabel: String,
        maxLabel: String,
        required: Boolean
    )
    
    @Composable
    fun CounterField(
        label: String,
        incrementButtons: List<Pair<String, Int>>,
        decrementButtons: List<Pair<String, Int>>,
        onIncrement: (Int) -> Unit,
        required: Boolean
    )
    
    @Composable
    fun DynamicList(
        label: String,
        items: List<String>,
        onItemsChanged: (List<String>) -> Unit,
        placeholder: String,
        required: Boolean,
        minItems: Int,
        maxItems: Int
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
    
    
    // Anciens boutons supprimés - utiliser ActionButton
}