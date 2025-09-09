package com.assistant.core.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.ui.*
import com.assistant.core.ui.components.IconSelector

/**
 * Section réutilisable des paramètres généraux pour tous les tool types
 * 
 * @param name Nom de l'outil
 * @param description Description de l'outil
 * @param iconName Nom de l'icône sélectionnée
 * @param displayMode Mode d'affichage (ICON, MINIMAL, LINE, etc.)
 * @param management Type de gestion ("manual" ou "ai")
 * @param configValidation Validation de configuration par IA activée
 * @param dataValidation Validation de données par IA activée
 * @param suggestedIcons Liste d'icônes suggérées par le ToolType
 */
@Composable
fun ToolGeneralParams(
    name: String,
    description: String,
    iconName: String,
    displayMode: String,
    management: String,
    configValidation: Boolean,
    dataValidation: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onDisplayModeChange: (String) -> Unit,
    onManagementChange: (String) -> Unit,
    onConfigValidationChange: (Boolean) -> Unit,
    onDataValidationChange: (Boolean) -> Unit,
    suggestedIcons: List<String> = emptyList()
) {
    // Options fixes communes à tous les tooltypes
    val displayModeOptions = listOf("ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL")
    val managementOptions = listOf("Manuel", "IA")
    val booleanOptions = listOf("Oui", "Non")
    
    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UI.Text("Paramètres généraux", TextType.SUBTITLE)
            
            // 1. Nom (requis)
            UI.FormField(
                label = "Nom",
                value = name,
                onChange = onNameChange,
                fieldType = FieldType.TEXT,
                required = true
            )
            
            // 2. Description (optionnel)
            UI.FormField(
                label = "Description",
                value = description,
                onChange = onDescriptionChange,
                fieldType = FieldType.TEXT_MEDIUM
            )
            
            // 3. Sélecteur d'icône
            IconSelector(
                current = iconName,
                suggested = suggestedIcons,
                onChange = onIconChange
            )
            
            // 4. Mode d'affichage (requis)
            UI.FormSelection(
                label = "Mode d'affichage",
                options = displayModeOptions,
                selected = displayMode,
                onSelect = onDisplayModeChange,
                required = true
            )
            
            // 5. Gestion (requis)
            UI.FormSelection(
                label = "Gestion",
                options = managementOptions,
                selected = when(management) {
                    "manual" -> "Manuel"
                    "ai" -> "IA"
                    else -> management
                },
                onSelect = { selectedLabel ->
                    val value = when(selectedLabel) {
                        "Manuel" -> "manual"
                        "IA" -> "ai"
                        else -> selectedLabel
                    }
                    onManagementChange(value)
                },
                required = true
            )
            
            // 6. Validation config par IA (requis)
            UI.FormSelection(
                label = "Validation config par IA",
                options = booleanOptions,
                selected = if (configValidation) "Oui" else "Non",
                onSelect = { selectedLabel ->
                    onConfigValidationChange(selectedLabel == "Oui")
                },
                required = true
            )
            
            // 7. Validation données par IA (requis)
            UI.FormSelection(
                label = "Validation données par IA",
                options = booleanOptions,
                selected = if (dataValidation) "Oui" else "Non",
                onSelect = { selectedLabel ->
                    onDataValidationChange(selectedLabel == "Oui")
                },
                required = true
            )
        }
    }
}