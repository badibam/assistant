package com.assistant.themes.default

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.ui.ThemeContract
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.Size
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.TextType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.FeedbackType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.DisplayMode
import com.assistant.core.utils.DateUtils
import java.util.Calendar
import com.assistant.core.ui.FieldType

/**
 * DefaultTheme - Implémentation par défaut du ThemeContract
 * 
 * Thème moderne basé sur Material 3 avec nos types sémantiques
 * UNIQUEMENT les composants VISUELS (thématisés)
 * 
 * LAYOUTS : utiliser Row/Column/Box/Spacer de Compose directement
 */
@OptIn(ExperimentalFoundationApi::class)
object DefaultTheme : ThemeContract {
    
    // =====================================
    // LAYOUTS : UTILISER COMPOSE DIRECTEMENT
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // PAS d'implémentation - accès direct pour flexibilité maximale
    
    // =====================================
    // INTERACTIVE
    // =====================================
    
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
        
        // Pour Size.XS, utiliser Surface + clickable pour 0 padding absolu
        if (size == Size.XS) {
            val surfaceColors = when (type) {
                ButtonType.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                ButtonType.SECONDARY -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
                ButtonType.DEFAULT -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
            }
            
            Surface(
                color = surfaceColors.first,
                contentColor = surfaceColors.second,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                border = if (type == ButtonType.SECONDARY) {
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                } else null,
                modifier = Modifier
                    .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp) // Dimensions minimales
                    .wrapContentSize()
                    .clickable(enabled = isEnabled) { 
                        if (isEnabled) onClick() 
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                ) {
                    content()
                }
            }
        } else if (size == Size.S) {
            // Pour Size.S, utiliser Surface + clickable similaire à XS mais plus grand
            val surfaceColors = when (type) {
                ButtonType.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                ButtonType.SECONDARY -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
                ButtonType.DEFAULT -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
            }
            
            Surface(
                color = surfaceColors.first,
                contentColor = surfaceColors.second,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                border = if (type == ButtonType.SECONDARY) {
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                } else null,
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp) // Dimensions plus grandes que XS
                    .wrapContentSize()
                    .clickable(enabled = isEnabled) { 
                        if (isEnabled) onClick() 
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                ) {
                    content()
                }
            }
        } else {
            // Pour les autres tailles, utiliser les boutons Material normaux
            val buttonColors = when (type) {
                ButtonType.PRIMARY -> ButtonDefaults.buttonColors()
                ButtonType.SECONDARY -> ButtonDefaults.outlinedButtonColors()
                ButtonType.DEFAULT -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
            }
            
            val contentPadding = when (size) {
                Size.XS -> PaddingValues(0.dp) // Ne sera pas utilisé vu le if au-dessus
                Size.S -> PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                Size.M -> PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                Size.L -> PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                Size.XL -> PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                Size.XXL -> PaddingValues(horizontal = 40.dp, vertical = 20.dp)
            }
            
            when (type) {
                ButtonType.SECONDARY -> {
                    OutlinedButton(
                        onClick = onClick,
                        enabled = isEnabled,
                        colors = buttonColors as ButtonColors,
                        contentPadding = contentPadding,
                        content = { content() }
                    )
                }
                ButtonType.PRIMARY, ButtonType.DEFAULT -> {
                    androidx.compose.material3.Button(
                        onClick = onClick,
                        enabled = isEnabled,
                        colors = buttonColors,
                        contentPadding = contentPadding,
                        content = { content() }
                    )
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
        onClick: () -> Unit
    ) {
        // État du dialogue de confirmation
        var showConfirmDialog by remember { mutableStateOf(false) }
        
        // Déterminer le type par défaut selon l'action
        val buttonType = type ?: getDefaultButtonType(action)
        
        // Déterminer l'état selon enabled
        val state = if (enabled) ComponentState.NORMAL else ComponentState.DISABLED
        
        if (display == ButtonDisplay.ICON) {
            // Mode ICON : Bouton compact/circulaire comme XS/S
            val iconSize = when (size) {
                Size.XXL -> 48.dp
                Size.XL -> 40.dp  
                Size.L -> 32.dp
                Size.M -> 28.dp
                Size.S -> 24.dp
                Size.XS -> 20.dp
            }
            
            val isEnabled = state != ComponentState.DISABLED
            val buttonColors = when (buttonType) {
                ButtonType.PRIMARY -> ButtonDefaults.buttonColors()
                ButtonType.SECONDARY -> ButtonDefaults.outlinedButtonColors()
                ButtonType.DEFAULT -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
            }
            
            when (buttonType) {
                ButtonType.SECONDARY -> {
                    OutlinedButton(
                        onClick = {
                            if (requireConfirmation) {
                                showConfirmDialog = true
                            } else {
                                onClick()
                            }
                        },
                        enabled = isEnabled,
                        colors = buttonColors as ButtonColors,
                        modifier = Modifier.minimumInteractiveComponentSize().size(iconSize),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        androidx.compose.material3.Text(
                            getButtonIcon(action),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                ButtonType.PRIMARY, ButtonType.DEFAULT -> {
                    androidx.compose.material3.Button(
                        onClick = {
                            if (requireConfirmation) {
                                showConfirmDialog = true
                            } else {
                                onClick()
                            }
                        },
                        enabled = isEnabled,
                        colors = buttonColors,
                        modifier = Modifier.minimumInteractiveComponentSize().size(iconSize),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        androidx.compose.material3.Text(
                            getButtonIcon(action),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            // Mode LABEL : Bouton standard
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
                    onClick()  // Exécuter l'action après confirmation
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
    
    // Helpers pour le mapping action -> type/texte/icône
    private fun getDefaultButtonType(action: ButtonAction): ButtonType {
        return when (action) {
            ButtonAction.SAVE, ButtonAction.CREATE, ButtonAction.CONFIRM -> ButtonType.PRIMARY
            ButtonAction.DELETE, ButtonAction.CANCEL -> ButtonType.SECONDARY  
            ButtonAction.BACK, ButtonAction.CONFIGURE, ButtonAction.ADD,
            ButtonAction.EDIT, ButtonAction.REFRESH, ButtonAction.SELECT,
            ButtonAction.UPDATE, ButtonAction.UP, ButtonAction.DOWN -> ButtonType.PRIMARY
        }
    }
    
    private fun getButtonText(action: ButtonAction): String {
        // TODO: Remplacer par strings.xml pour i18n
        return when (action) {
            ButtonAction.SAVE -> "Sauvegarder"
            ButtonAction.CREATE -> "Créer"
            ButtonAction.UPDATE -> "Modifier"
            ButtonAction.DELETE -> "Supprimer"
            ButtonAction.CANCEL -> "Annuler"
            ButtonAction.BACK -> "Retour"
            ButtonAction.CONFIGURE -> "Configurer"
            ButtonAction.ADD -> "Ajouter"
            ButtonAction.EDIT -> "Modifier"
            ButtonAction.REFRESH -> "Actualiser"
            ButtonAction.SELECT -> "Sélectionner"
            ButtonAction.CONFIRM -> "Confirmer"
            ButtonAction.UP -> "Monter"
            ButtonAction.DOWN -> "Descendre"
        }
    }
    
    private fun getButtonIcon(action: ButtonAction): String {
        // Symboles Unicode pour lisibilité et uniformité
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
        }
    }
    
    private fun getDefaultConfirmMessage(action: ButtonAction): String {
        return when (action) {
            ButtonAction.DELETE -> "Êtes-vous sûr de vouloir supprimer cet élément ? Cette action est irréversible."
            else -> "Confirmer cette action ?"
        }
    }
    
    @Composable
    override fun TextField(
        fieldType: FieldType,
        state: ComponentState,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String
    ) {
        val isError = state == ComponentState.ERROR
        val isReadOnly = state == ComponentState.READONLY
        
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { androidx.compose.material3.Text(placeholder) },
            isError = isError,
            readOnly = isReadOnly,
            enabled = state != ComponentState.DISABLED
        )
    }
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    override fun Text(
        text: String,
        type: TextType
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
            TextType.ERROR -> MaterialTheme.colorScheme.error
            TextType.WARNING -> MaterialTheme.colorScheme.primary // Pas de warning dans M3, utilise primary
            else -> MaterialTheme.colorScheme.onSurface
        }
        
        androidx.compose.material3.Text(
            text = text,
            style = style,
            color = color
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
            content = { content() }
        )
    }
    
    // =====================================
    // FEEDBACK SYSTEM
    // =====================================
    
    @Composable
    override fun Toast(
        type: FeedbackType,
        message: String,
        duration: Duration
    ) {
        // TODO: Implémenter Toast (pas natif dans Compose, nécessite une lib externe)
        // Pour l'instant, on simule avec une Card temporaire
        androidx.compose.material3.Card(
            colors = CardDefaults.cardColors(
                containerColor = when (type) {
                    FeedbackType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                    FeedbackType.ERROR -> MaterialTheme.colorScheme.errorContainer
                    FeedbackType.WARNING -> MaterialTheme.colorScheme.secondaryContainer
                    FeedbackType.INFO -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            androidx.compose.material3.Text(
                text = message,
                modifier = Modifier.padding(16.dp),
                color = when (type) {
                    FeedbackType.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                    FeedbackType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                    FeedbackType.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
                    FeedbackType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
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
                    TextButton(onClick = onAction) {
                        androidx.compose.material3.Text(action)
                    }
                }
            } else null,
            containerColor = when (type) {
                FeedbackType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                FeedbackType.ERROR -> MaterialTheme.colorScheme.errorContainer
                FeedbackType.WARNING -> MaterialTheme.colorScheme.secondaryContainer
                FeedbackType.INFO -> MaterialTheme.colorScheme.surfaceVariant
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
        contentDescription: String?
    ) {
        Icon(
            painter = painterResource(id = resourceId),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
            tint = Color(0xFF333333)
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
            DialogType.CONFIGURE -> "Valider" to "Annuler"
            DialogType.CREATE -> "Créer" to "Annuler"
            DialogType.EDIT -> "Sauvegarder" to "Annuler"
            DialogType.CONFIRM -> "Confirmer" to "Annuler"
            DialogType.DANGER -> "Supprimer" to "Annuler"
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
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        androidx.compose.material3.Text(confirmText)
                    }
                }
            } else {
                {}
            },
            dismissButton = if (cancelText != null) {
                {
                    TextButton(onClick = onCancel) {
                        androidx.compose.material3.Text(cancelText)
                    }
                }
            } else null
        )
    }
    
    // =====================================
    // CONTAINERS SPÉCIALISÉS (apparence uniquement)
    // =====================================
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun ZoneCardContainer(
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        // Thème défini UNIQUEMENT l'apparence : bordures, couleurs, shadows, etc.
        androidx.compose.material3.Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
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
        // Thème défini l'apparence selon le mode d'affichage
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
        required: Boolean
    ) {
        val displayLabel = if (required) label else "$label (optionnel)"
        
        Column {
            Text(displayLabel, TextType.LABEL)
            
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
                        type = TextType.BODY
                    )
                }
            } else {
                TextField(
                    fieldType = fieldType,
                    state = state,
                    value = value,
                    onChange = onChange,
                    placeholder = label
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
            Text(displayLabel, TextType.LABEL)
            
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
                    modifier = Modifier.menuAnchor()
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                Text(label, TextType.BODY)
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
        
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcDate
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
                    }
                ) {
                    androidx.compose.material3.Text("OK")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss
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
    
    // Anciens boutons supprimés - utiliser UI.ActionButton à la place
}