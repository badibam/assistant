package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.ui.*


/**
 * Sélecteur d'icône réutilisable
 * 
 * @param current Icône actuellement sélectionnée
 * @param suggested Liste d'icônes suggérées (affichées en premier)
 * @param onChange Callback appelé quand une icône est sélectionnée
 */
@Composable
fun IconSelector(
    current: String,
    suggested: List<String> = emptyList(),
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    
    // Chargement des icônes disponibles
    val allAvailableIcons by remember { 
        mutableStateOf(ThemeIconManager.getAvailableIcons(context, "default"))
    }
    
    // Tri des icônes : suggestions en premier (dans l'ordre), puis le reste
    val sortedIcons by remember(suggested, allAvailableIcons) {
        mutableStateOf(
            buildList {
                // Ajouter les suggestions qui existent, dans l'ordre de la liste
                suggested.forEach { suggestedId ->
                    allAvailableIcons.find { it.id == suggestedId }?.let { add(it) }
                }
                
                // Ajouter le reste des icônes (pas dans les suggestions)
                addAll(allAvailableIcons.filter { icon -> 
                    !suggested.contains(icon.id) 
                })
            }
        )
    }
    
    // Interface : icône actuelle + bouton SELECT
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UI.Text("Icône:", TextType.LABEL)
        
        // Icône actuelle
        UI.Icon(iconName = current, size = 32.dp)
        
        UI.ActionButton(
            action = ButtonAction.SELECT,
            onClick = { showDialog = true }
        )
    }
    
    // Dialog de sélection
    if (showDialog) {
        UI.Dialog(
            type = DialogType.SELECTION,
            onConfirm = {},
            onCancel = { showDialog = false }
        ) {
            Column {
                UI.Text("Choisir une icône", TextType.SUBTITLE)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Grille d'icônes 3 par ligne
                sortedIcons.chunked(3).forEach { iconRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        iconRow.forEach { icon ->
                            UI.Button(
                                type = if (current == icon.id) ButtonType.PRIMARY else ButtonType.DEFAULT,
                                onClick = {
                                    onChange(icon.id)
                                    showDialog = false
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    UI.Icon(iconName = icon.id, size = 32.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    UI.Text(
                                        text = icon.id,
                                        type = TextType.CAPTION,
                                        fillMaxWidth = true,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        // Remplir la ligne avec des espaces vides si nécessaire
                        repeat(3 - iconRow.size) {
                            Spacer(modifier = Modifier.size(80.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}