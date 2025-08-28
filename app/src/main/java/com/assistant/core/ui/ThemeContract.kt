package com.assistant.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

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
        type: TextType
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
    
    @Composable
    fun Toast(
        type: FeedbackType,
        message: String,
        duration: Duration
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
        contentDescription: String?
    )
    
    @Composable
    fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        content: @Composable () -> Unit
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
    
    // Anciens boutons supprimés - utiliser ActionButton
}