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
        validation: ValidationRule = ValidationRule.NONE,
        state: ComponentState = ComponentState.NORMAL,
        placeholder: String = ""
    ) {
        Column {
            Text(label, TextType.LABEL)
            TextField(
                type = type,
                state = state,
                value = value,
                onChange = onChange,
                placeholder = placeholder.ifEmpty { label }
            )
        }
    }
    
    @Composable
    fun FormSelection(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        Column {
            Text(label, TextType.LABEL)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    Button(
                        type = if (option == selected) ButtonType.PRIMARY else ButtonType.SECONDARY,
                        onClick = { onSelect(option) }
                    ) {
                        Text(option, TextType.LABEL)
                    }
                }
            }
        }
    }
    
    @Composable
    fun FormActions(
        content: @Composable RowScope.() -> Unit
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
    
    // =====================================
    // BOUTONS AVEC ICÔNES AUTOMATIQUES
    // =====================================
    
    @Composable
    fun SaveButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("✅", TextType.LABEL) }
    
    @Composable
    fun EditButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("✏️", TextType.LABEL) }
    
    @Composable
    fun DeleteButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("🗑️", TextType.LABEL) }
    
    @Composable
    fun AddButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("➕", TextType.LABEL) }
    
    @Composable
    fun CancelButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("❌", TextType.LABEL) }
    
    @Composable
    fun ConfirmButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("✅", TextType.LABEL) }
    
    @Composable
    fun BackButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("←", TextType.LABEL) }
    
    @Composable
    fun PlayButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("▶️", TextType.LABEL) }
    
    @Composable
    fun StopButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("⏹️", TextType.LABEL) }
    
    @Composable
    fun PauseButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("⏸️", TextType.LABEL) }
    
    @Composable
    fun ConfigButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("⚙️", TextType.LABEL) }
    
    @Composable
    fun InfoButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("ℹ️", TextType.LABEL) }
    
    @Composable
    fun RefreshButton(onClick: () -> Unit) = Button(
        type = ButtonType.DEFAULT,
        onClick = onClick
    ) { Text("🔄", TextType.LABEL) }
    
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
    fun ToolCard(
        tool: ToolInstance,
        displayMode: DisplayMode,
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
                    Row {
                        Text("T", TextType.BODY) // TODO: Icône
                        val toolInstanceName = try {
                            JSONObject(tool.config_json).optString("name")
                        } catch (e: Exception) {
                            ""
                        }
                        Text(toolInstanceName, TextType.BODY)
                    }
                }
                DisplayMode.LINE -> {
                    Row {
                        // Icône + titre à gauche (partie fixe)
                        Row {
                            Text("T", TextType.BODY) // TODO: Icône via tool type
                            val toolInstanceName = try {
                                JSONObject(tool.config_json).optString("name")
                            } catch (e: Exception) {
                                ""
                            }
                            Text(toolInstanceName, TextType.BODY)
                        }
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
                            Row {
                                Text("T", TextType.BODY) // TODO: Icône via tool type
                                val toolInstanceName = try {
                                JSONObject(tool.config_json).optString("name")
                            } catch (e: Exception) {
                                ""
                            }
                            Text(toolInstanceName, TextType.BODY)
                            }
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