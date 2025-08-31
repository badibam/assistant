package com.assistant.themes.default

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.RectangleShape
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
    // PALETTE DE COULEURS CENTRALISÉE
    // =====================================
    object Colors {
        // THÈME CLAIR - Material Design (commenté)
        /*
        // Primary colors
        val Primary = Color(0xFF1976D2)        // Bleu Material
        val OnPrimary = Color(0xFFFFFFFF)      // Blanc
        val Secondary = Color(0xFF03DAC6)      // Teal
        val OnSecondary = Color(0xFF000000)    // Noir
        
        // Surface colors
        val Surface = Color(0xFFFFFFFF)        // Blanc
        val OnSurface = Color(0xFF1C1B1F)      // Gris foncé
        val SurfaceVariant = Color(0xFFF3F0F4) // Gris très clair
        val OnSurfaceVariant = Color(0xFF49454F) // Gris moyen foncé
        
        // Background colors
        val Background = Color(0xFFFFFBFE)     // Blanc légèrement teinté
        val OnBackground = Color(0xFF1C1B1F)   // Gris foncé
        
        // Feedback colors
        val Error = Color(0xFFB3261E)          // Rouge
        val OnError = Color(0xFFFFFFFF)        // Blanc
        val Success = Color(0xFF4CAF50)        // Vert
        val OnSuccess = Color(0xFFFFFFFF)      // Blanc
        val Warning = Color(0xFFFF9800)        // Orange
        val OnWarning = Color(0xFF000000)      // Noir
        
        // Outline & borders
        val Outline = Color(0xFF79747E)        // Gris moyen
        val OutlineVariant = Color(0xFFCAC4D0) // Gris clair
        
        // Container colors for feedback
        val SuccessContainer = Color(0xFFD1F2EB) // Vert clair
        val OnSuccessContainer = Color(0xFF00695C) // Vert foncé
        val ErrorContainer = Color(0xFFFDEAEA)   // Rouge clair
        val OnErrorContainer = Color(0xFFD32F2F) // Rouge foncé
        val WarningContainer = Color(0xFFFFF3CD) // Jaune clair
        val OnWarningContainer = Color(0xFFE65100) // Orange foncé
        val InfoContainer = Color(0xFFF5F5F5)    // Gris très clair
        val OnInfoContainer = Color(0xFF616161)  // Gris moyen
        */
        
        // THÈME SOMBRE NERDY - Terminal/Hacker vibes
        // Primary colors - Vert terminal classique
        val Primary = Color(0xFF00FF41)        // Vert Matrix/terminal
        val OnPrimary = Color(0xFF0D1117)      // Noir terminal profond
        val Secondary = Color(0xFF58A6FF)      // Bleu code (GitHub)
        val OnSecondary = Color(0xFF0D1117)    // Noir terminal profond
        
        // Surface colors - Tons sombres GitHub/VS Code
        val Surface = Color(0xFF161B22)        // Gris sombre GitHub
        val OnSurface = Color(0xFFF0F6FC)      // Blanc cassé terminal
        val SurfaceVariant = Color(0xFF21262D) // Gris moyen GitHub
        val OnSurfaceVariant = Color(0xFF8B949E) // Gris commentaire
        
        // Background colors - Noir terminal profond
        val Background = Color(0xFF0D1117)     // Noir GitHub/terminal
        val OnBackground = Color(0xFFF0F6FC)   // Blanc cassé terminal
        
        // Feedback colors - Couleurs terminal/code
        val Error = Color(0xFFFF6B6B)          // Rouge terminal doux
        val OnError = Color(0xFF0D1117)        // Noir terminal profond
        val Success = Color(0xFF7CE38B)        // Vert terminal doux
        val OnSuccess = Color(0xFF0D1117)      // Noir terminal profond
        val Warning = Color(0xFFFFD93D)        // Jaune terminal
        val OnWarning = Color(0xFF0D1117)      // Noir terminal profond
        
        // Outline & borders - Subtils mais visibles
        val Outline = Color(0xFF30363D)        // Bordure GitHub subtile
        val OutlineVariant = Color(0xFF21262D) // Bordure plus subtile
        
        // Container colors for feedback - Tons sombres
        val SuccessContainer = Color(0xFF1A3A2E) // Vert très sombre
        val OnSuccessContainer = Color(0xFF7CE38B) // Vert terminal doux
        val ErrorContainer = Color(0xFF3D1A1A)   // Rouge très sombre
        val OnErrorContainer = Color(0xFFFF6B6B) // Rouge terminal doux
        val WarningContainer = Color(0xFF3D3A1A) // Jaune très sombre
        val OnWarningContainer = Color(0xFFFFD93D) // Jaune terminal
        val InfoContainer = Color(0xFF1A2332)    // Bleu très sombre
        val OnInfoContainer = Color(0xFF58A6FF)  // Bleu code
    }

    // =====================================
    // COLORSCHEME MATERIAL3 PERSONNALISÉ
    // =====================================
    override val colorScheme = darkColorScheme(
        primary = Colors.Primary,
        onPrimary = Colors.OnPrimary,
        secondary = Colors.Secondary,
        onSecondary = Colors.OnSecondary,
        surface = Colors.Surface,
        onSurface = Colors.OnSurface,
        surfaceVariant = Colors.SurfaceVariant,
        onSurfaceVariant = Colors.OnSurfaceVariant,
        background = Colors.Background,
        onBackground = Colors.OnBackground,
        error = Colors.Error,
        onError = Colors.OnError,
        outline = Colors.Outline,
        outlineVariant = Colors.OutlineVariant
    )

    // =====================================
    // CONSTANTES DE FORME DU THÈME
    // =====================================
    private val ButtonTextShape = RoundedCornerShape(2.dp)
    private val ButtonIconShape = RoundedCornerShape(2.dp)
    private val CardShape = RectangleShape
    
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
                ButtonType.PRIMARY -> Colors.Primary to Colors.OnPrimary
                ButtonType.SECONDARY -> Colors.Surface to Colors.OnSurface
                ButtonType.DEFAULT -> Colors.Surface to Colors.OnSurface
            }
            
            Surface(
                color = surfaceColors.first,
                contentColor = surfaceColors.second,
                shape = ButtonIconShape,
                border = if (type == ButtonType.SECONDARY) {
                    androidx.compose.foundation.BorderStroke(1.dp, Colors.Outline)
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
                ButtonType.PRIMARY -> Colors.Primary to Colors.OnPrimary
                ButtonType.SECONDARY -> Colors.Surface to Colors.OnSurface
                ButtonType.DEFAULT -> Colors.Surface to Colors.OnSurface
            }
            
            Surface(
                color = surfaceColors.first,
                contentColor = surfaceColors.second,
                shape = ButtonTextShape,
                border = if (type == ButtonType.SECONDARY) {
                    androidx.compose.foundation.BorderStroke(1.dp, Colors.Outline)
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
                ButtonType.PRIMARY -> ButtonDefaults.buttonColors(
                    containerColor = Colors.Primary,
                    contentColor = Colors.OnPrimary
                )
                ButtonType.SECONDARY -> ButtonDefaults.outlinedButtonColors(
                    containerColor = Colors.Surface,
                    contentColor = Colors.OnSurface
                )
                ButtonType.DEFAULT -> ButtonDefaults.buttonColors(
                    containerColor = Colors.Surface,
                    contentColor = Colors.OnSurface
                )
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
                        shape = ButtonTextShape,
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
                        shape = ButtonTextShape,
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
                ButtonType.PRIMARY -> ButtonDefaults.buttonColors(
                    containerColor = Colors.Primary,
                    contentColor = Colors.OnPrimary
                )
                ButtonType.SECONDARY -> ButtonDefaults.outlinedButtonColors(
                    containerColor = Colors.Surface,
                    contentColor = Colors.OnSurface
                )
                ButtonType.DEFAULT -> ButtonDefaults.buttonColors(
                    containerColor = Colors.Surface,
                    contentColor = Colors.OnSurface
                )
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
                        shape = ButtonIconShape,
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
                        shape = ButtonIconShape,
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
            enabled = state != ComponentState.DISABLED,
            modifier = Modifier.fillMaxWidth()
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
            TextType.ERROR -> Colors.Error
            TextType.WARNING -> Colors.Primary // Pas de warning dans M3, utilise primary
            else -> Colors.OnSurface
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
                containerColor = Colors.Surface,
                contentColor = Colors.OnSurface
            ),
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
                    FeedbackType.SUCCESS -> Colors.SuccessContainer
                    FeedbackType.ERROR -> Colors.ErrorContainer
                    FeedbackType.WARNING -> Colors.WarningContainer
                    FeedbackType.INFO -> Colors.InfoContainer
                }
            ),
            shape = CardShape
        ) {
            androidx.compose.material3.Text(
                text = message,
                modifier = Modifier.padding(16.dp),
                color = when (type) {
                    FeedbackType.SUCCESS -> Colors.OnSuccessContainer
                    FeedbackType.ERROR -> Colors.OnErrorContainer
                    FeedbackType.WARNING -> Colors.OnWarningContainer
                    FeedbackType.INFO -> Colors.OnInfoContainer
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
                    TextButton(
                        onClick = onAction,
                        shape = ButtonTextShape
                    ) {
                        androidx.compose.material3.Text(action)
                    }
                }
            } else null,
            containerColor = when (type) {
                FeedbackType.SUCCESS -> Colors.OnSuccessContainer
                FeedbackType.ERROR -> Colors.OnErrorContainer
                FeedbackType.WARNING -> Colors.OnWarningContainer
                FeedbackType.INFO -> Colors.OnInfoContainer
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
            tint = Colors.OnSurface
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
                            ButtonDefaults.buttonColors(
                                containerColor = Colors.Error,
                                contentColor = Colors.OnError
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = Colors.Primary,
                                contentColor = Colors.OnPrimary
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
                containerColor = Colors.Surface,
                contentColor = Colors.OnSurface
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
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = Colors.Surface,
                contentColor = Colors.OnSurface
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
        required: Boolean
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
                        Colors.Primary
                    } else {
                        Colors.OnSurfaceVariant
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
                    color = Colors.Primary
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
                        color = Colors.OnSurfaceVariant
                    )
                    
                    androidx.compose.material3.Text(
                        text = maxLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Colors.OnSurfaceVariant
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
            
            // Boutons d'incrément  
            if (incrementButtons.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Ajouter :",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Colors.OnSurface
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
                                    containerColor = Colors.SurfaceVariant,
                                    contentColor = Colors.OnSurfaceVariant
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
            
            // Boutons de décrément
            if (decrementButtons.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Retirer :",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Colors.OnSurface
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
                                    containerColor = Colors.ErrorContainer,
                                    contentColor = Colors.OnErrorContainer
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
    override fun TimerField(
        label: String,
        activities: List<String>,
        currentActivity: String?,
        currentDuration: String,
        onStartActivity: (String) -> Unit,
        onStopActivity: () -> Unit,
        onSaveSession: (String, Int) -> Unit,
        required: Boolean
    ) {
        val displayLabel = if (required) label else "$label (optionnel)"
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Label du champ
            Text(displayLabel, TextType.LABEL, false, null)
            
            // État actuel du timer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (currentActivity != null) {
                        Colors.SurfaceVariant
                    } else {
                        Colors.InfoContainer
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentActivity != null) {
                        androidx.compose.material3.Text(
                            text = "En cours : $currentActivity",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = Colors.OnSurfaceVariant
                        )
                        androidx.compose.material3.Text(
                            text = currentDuration,
                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                            color = Colors.Primary
                        )
                    } else {
                        androidx.compose.material3.Text(
                            text = "Arrêté",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = Colors.OnSurfaceVariant
                        )
                    }
                }
            }
            
            // Boutons d'activités
            if (activities.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Activités :",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                
                activities.chunked(2).forEach { rowActivities ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowActivities.forEach { activity ->
                            val isActive = currentActivity == activity
                            Button(
                                onClick = {
                                    if (isActive) {
                                        onStopActivity()
                                    } else {
                                        onStartActivity(activity)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActive) {
                                        Colors.Primary
                                    } else {
                                        Colors.Outline
                                    },
                                    contentColor = if (isActive) {
                                        Colors.OnPrimary
                                    } else {
                                        Colors.OnSurface
                                    }
                                ),
                                shape = ButtonTextShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                androidx.compose.material3.Text(activity)
                            }
                        }
                        
                        // Fill remaining space if odd number of activities
                        if (rowActivities.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
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
        val (hour, minute) = DateUtils.parseTime(selectedTime)
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute
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
                TimePicker(state = timePickerState)
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
            
            // Zone centrale (titre centré, flexible)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ligne 1: Icône + Titre
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    icon?.let { iconName ->
                        val context = LocalContext.current
                        if (com.assistant.core.ui.ThemeIconManager.iconExists(context, "default", iconName)) {
                            val iconResource = com.assistant.core.ui.ThemeIconManager.getIconResource(context, "default", iconName)
                            Box(modifier = Modifier.size(24.dp)) {
                                Image(
                                    painter = painterResource(iconResource),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Text(title, TextType.TITLE, false, TextAlign.Center)
                }
                
                // Ligne 2: Sous-titre (forcé centré)
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
    
    // Anciens boutons supprimés - utiliser UI.ActionButton à la place
}