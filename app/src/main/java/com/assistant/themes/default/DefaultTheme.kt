package com.assistant.themes.default

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.ui.core.*

/**
 * DefaultTheme - Implémentation par défaut du ThemeContract
 * 
 * Thème moderne basé sur Material 3 avec nos types sémantiques
 * UNIQUEMENT les éléments convenus dans UI_DECISIONS.md
 */
object DefaultTheme : ThemeContract {
    
    // =====================================
    // LAYOUT
    // =====================================
    
    @Composable
    override fun Column(content: @Composable ColumnScope.() -> Unit) {
        androidx.compose.foundation.layout.Column(content = content)
    }
    
    @Composable
    override fun Row(content: @Composable RowScope.() -> Unit) {
        androidx.compose.foundation.layout.Row(content = content)
    }
    
    @Composable
    override fun Box(content: @Composable BoxScope.() -> Unit) {
        androidx.compose.foundation.layout.Box(content = content)
    }
    
    @Composable
    override fun Spacer(modifier: Modifier) {
        androidx.compose.foundation.layout.Spacer(modifier = modifier)
    }
    
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
        val buttonColors = when (type) {
            ButtonType.SAVE -> ButtonDefaults.buttonColors()
            ButtonType.DELETE -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ButtonType.CONFIRM_DELETE -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ButtonType.CANCEL -> ButtonDefaults.outlinedButtonColors()
            ButtonType.ADD -> ButtonDefaults.buttonColors()
            ButtonType.BACK -> ButtonDefaults.textButtonColors()
        }
        
        val isEnabled = when (state) {
            ComponentState.NORMAL, ComponentState.SUCCESS -> true
            ComponentState.LOADING, ComponentState.DISABLED, ComponentState.ERROR, ComponentState.READONLY -> false
        }
        
        when (type) {
            ButtonType.CANCEL, ButtonType.BACK -> {
                if (type == ButtonType.BACK) {
                    TextButton(
                        onClick = onClick,
                        enabled = isEnabled,
                        colors = buttonColors as ButtonColors,
                        content = content
                    )
                } else {
                    OutlinedButton(
                        onClick = onClick,
                        enabled = isEnabled,
                        colors = buttonColors as ButtonColors,
                        content = content
                    )
                }
            }
            else -> {
                androidx.compose.material3.Button(
                    onClick = onClick,
                    enabled = isEnabled,
                    colors = buttonColors,
                    content = content
                )
            }
        }
    }
    
    @Composable
    override fun TextField(
        type: TextFieldType,
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
    
    @Composable
    override fun ZoneCardContainer(
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        // Thème défini UNIQUEMENT l'apparence : bordures, couleurs, shadows, etc.
        androidx.compose.material3.Card(
            onClick = onClick,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
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
            onClick = onClick,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = cardModifier
        ) {
            // Le contenu vient de UI.ToolCard()
            Box(modifier = Modifier.padding(cardPadding)) {
                content()
            }
        }
    }
}