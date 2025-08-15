package com.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*

/**
 * Main screen - entry point of the application
 * Uses purely semantic UI components
 */
@Composable
fun MainScreen() {
    UI.Screen(type = ScreenType.MAIN) {
        // Top bar
        UI.TopBar(
            type = TopBarType.DEFAULT,
            title = "Assistant Personnel"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Zones section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Text(
                text = "Zones",
                type = TextType.TITLE,
                semantic = "section-title"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // TODO: Replace with actual zones from database
            // For now, show placeholder message
            UI.Card(
                type = CardType.SYSTEM,
                semantic = "placeholder",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    UI.Text(
                        text = "Aucune zone configurée",
                        type = TextType.BODY,
                        semantic = "empty-state"
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        semantic = "create-zone",
                        onClick = { 
                            // TODO: Navigate to zone creation
                        }
                    ) {
                        UI.Text(
                            text = "Créer une zone",
                            type = TextType.LABEL,
                            semantic = "button-label"
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Terminal section
        UI.Container(type = ContainerType.SECONDARY) {
            UI.Text(
                text = "Terminal",
                type = TextType.SUBTITLE,
                semantic = "section-title"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            UI.Terminal(
                content = "Assistant démarré. En attente de commandes...",
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // AI Call Zone (permanent toggle)
        UI.Container(type = ContainerType.FLOATING) {
            UI.Button(
                type = ButtonType.SECONDARY,
                semantic = "ai-toggle",
                onClick = {
                    // TODO: Toggle AI dialogue interface
                }
            ) {
                UI.Text(
                    text = "Appel IA",
                    type = TextType.LABEL,
                    semantic = "ai-call-label"
                )
            }
        }
    }
}

// TODO: Add when database is ready
/*
@Composable
private fun ZonesList(zones: List<Zone>) {
    LazyColumn {
        items(zones) { zone ->
            UI.ZoneCard(
                zoneName = zone.name,
                onClick = {
                    // TODO: Navigate to zone detail
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}
*/