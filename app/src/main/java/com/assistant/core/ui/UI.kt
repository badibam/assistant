package com.assistant.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.themes.default.DefaultTheme
import org.json.JSONObject

/**
 * UI - API publique unifiée
 * UNIQUEMENT les composants VISUELS (thématisés)
 * 
 * LAYOUTS : utiliser Row/Column/Box/Spacer de Compose directement
 * VISUELS : utiliser UI.* pour thématisation
 * 
 * Principe : UI.* → délégation au thème actuel via CurrentTheme.current
 */
object UI {
    
    // =====================================
    // LAYOUTS : UTILISER COMPOSE DIRECTEMENT
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // PAS de wrappers - accès direct pour flexibilité maximale
    
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
    fun TextField(
        type: TextFieldType,
        state: ComponentState = ComponentState.NORMAL,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String
    ) = CurrentTheme.current.TextField(type, state, value, onChange, placeholder)
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    fun Text(
        text: String,
        type: TextType
    ) = CurrentTheme.current.Text(text, type)
    
    @Composable
    fun Card(
        type: CardType,
        size: Size = Size.M,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Card(type, size, content)
    
    // =====================================
    // FEEDBACK SYSTEM
    // =====================================
    
    @Composable
    fun Toast(
        type: FeedbackType,
        message: String,
        duration: Duration = Duration.SHORT
    ) = CurrentTheme.current.Toast(type, message, duration)
    
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
        resourceId: Int,
        size: Dp = 24.dp,
        contentDescription: String? = null
    ) = CurrentTheme.current.Icon(resourceId, size, contentDescription)
    
    @Composable
    fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = { },
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Dialog(type, onConfirm, onCancel, content)
    
    // =====================================
    // FORMULAIRES UNIFIÉS
    // =====================================
    
    @Composable
    fun FormField(
        label: String,
        value: String,
        onChange: (String) -> Unit,
        type: TextFieldType = TextFieldType.TEXT,
        required: Boolean = false,
        fieldType: FieldType = FieldType.TEXT,
        state: ComponentState = ComponentState.NORMAL,
        readonly: Boolean = false,
        onClick: (() -> Unit)? = null,
        contentDescription: String? = null
    ) = CurrentTheme.current.FormField(
        label = label,
        value = value, 
        onChange = onChange,
        type = type,
        state = state,
        readonly = readonly,
        onClick = onClick,
        contentDescription = contentDescription,
        required = required,
        fieldType = fieldType
    )
    
    @Composable
    fun FormSelection(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
        required: Boolean = false
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
    
    // =====================================
    // BOUTONS AVEC ICÔNES AUTOMATIQUES
    // =====================================
    
    @Composable
    fun SaveButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.SaveButton(onClick, size)
    
    @Composable
    fun EditButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.EditButton(onClick, size)
    
    @Composable
    fun DeleteButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.DeleteButton(onClick, size)
    
    @Composable
    fun AddButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.AddButton(onClick, size)
    
    @Composable
    fun CancelButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.CancelButton(onClick, size)
    
    @Composable
    fun ConfirmButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.ConfirmButton(onClick, size)
    
    @Composable
    fun BackButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.BackButton(onClick, size)
    
    @Composable
    fun PlayButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.PlayButton(onClick, size)
    
    @Composable
    fun StopButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.StopButton(onClick, size)
    
    @Composable
    fun PauseButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.PauseButton(onClick, size)
    
    @Composable
    fun ConfigButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.ConfigButton(onClick, size)
    
    @Composable
    fun InfoButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.InfoButton(onClick, size)
    
    @Composable
    fun RefreshButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.RefreshButton(onClick, size)
    
    @Composable
    fun UpButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.UpButton(onClick, size)
    
    @Composable
    fun DownButton(onClick: () -> Unit, size: Size = Size.M) = CurrentTheme.current.DownButton(onClick, size)
    
    // =====================================
    // COMPOSANTS SPÉCIALISÉS
    // =====================================
    
    @Composable
    fun ZoneCard(
        zone: Zone,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { }
    ) {
        // Container thématisé + contenu standard avec UI.*
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
    fun ToolCardHeader(
        tool: ToolInstance,
        context: android.content.Context
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône réelle
            val iconName = JSONObject(tool.config_json).optString("icon", "activity")
            val iconResourceId = ThemeIconManager.getIconResource(context, "default", iconName)
            Icon(iconResourceId, size = 24.dp)
            
            // Nom de l'instance
            val toolInstanceName = JSONObject(tool.config_json).optString("name", "Sans nom")
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
        // Contenu défini au niveau core + tool types avec UI.*
        CurrentTheme.current.ToolCardContainer(
            displayMode = displayMode,
            onClick = onClick, 
            onLongClick = onLongClick
        ) {
            when (displayMode) {
                DisplayMode.ICON -> {
                    // TODO: Icône seule via tool type
                    Text("T", TextType.BODY) // Placeholder
                }
                DisplayMode.MINIMAL -> {
                    ToolCardHeader(tool, context)
                }
                DisplayMode.LINE -> {
                    Row {
                        // Icône + titre à gauche (partie fixe)
                        ToolCardHeader(tool, context)
                        // Zone libre à droite définie par tool type
                        Box {
                            // TODO: Contenu libre LINE défini par tool type
                        }
                    }
                }
                DisplayMode.CONDENSED, DisplayMode.EXTENDED, DisplayMode.SQUARE, DisplayMode.FULL -> {
                    Column {
                        Row {
                            // Icône + titre à gauche (partie fixe)
                            ToolCardHeader(tool, context)
                            // Zone libre en haut à droite définie par tool type
                            Box {
                                // TODO: Contenu libre haut défini par tool type selon mode
                            }
                        }
                        // Zone libre en-dessous définie par tool type
                        Box {
                            // TODO: Contenu libre bas défini par tool type selon mode
                        }
                    }
                }
            }
        }
    }
}