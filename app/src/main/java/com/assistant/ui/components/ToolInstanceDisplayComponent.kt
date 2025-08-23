package com.assistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*

/**
 * Display modes for tool instances in zones
 */
enum class DisplayMode {
    ICON,        // 1/4 × 1/4 - Icône seule
    MINIMAL,     // 1/2 × 1/4 - Icône + Titre côte à côte  
    LINE,        // 1 × 1/4 - Icône + Titre à gauche, contenu libre à droite
    CONDENSED,   // 1/2 × 1/2 - Icône + Titre en haut, reste libre en dessous
    EXTENDED,    // 1 × 1/2 - Layout flexible selon stratégie
    FULL         // 1 × ∞ - Layout flexible selon stratégie
}

/**
 * Layout strategies for EXTENDED and FULL modes
 */
enum class LayoutStrategy {
    HORIZONTAL_SPLIT, // Icône+titre à gauche (1/4), zone libre à droite (3/4)
    VERTICAL_SPLIT,   // Icône+titre en haut (1/4), zone libre dessous (reste)
    L_SHAPE          // Icône+titre haut-gauche, zone libre droite + dessous
}

/**
 * Generic tool instance display component with responsive grid system
 * Uses 1/4 height lines as base unit
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolInstanceDisplayComponent(
    displayMode: DisplayMode,
    size: DpSize,
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    layoutStrategy: LayoutStrategy = LayoutStrategy.VERTICAL_SPLIT,
    freeContent: @Composable (zoneName: String, size: DpSize) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val lineHeight = size.height / 4 // Base unit: 1 ligne = 1/4 hauteur
    val columnWidth = size.width / 4 // Base unit: 1 colonne = 1/4 largeur
    
    UI.Card(
        type = CardType.ZONE,
        semantic = "tool-instance-${displayMode.name.lowercase()}",
        modifier = modifier
            .size(size)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        when (displayMode) {
            DisplayMode.ICON -> {
                // Icône seule, centrée
                UI.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    icon()
                }
            }
            
            DisplayMode.MINIMAL -> {
                // Icône + Titre sur toute la hauteur (1 ligne)
                UI.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    icon()
                    UI.Text(
                        text = title,
                        type = TextType.BODY,
                        semantic = "tool-instance-title"
                    )
                }
            }
            
            DisplayMode.LINE -> {
                // Icône + Titre à gauche, zone libre à droite
                UI.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    // Zone gauche : Icône + Titre
                    UI.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        icon()
                        UI.Text(
                            text = title,
                            type = TextType.BODY,
                            semantic = "tool-instance-title"
                        )
                    }
                    
                    UI.Spacer(modifier = Modifier.width(8.dp))
                    
                    // Zone libre droite
                    UI.Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        val freeSize = DpSize(
                            width = size.width - columnWidth * 2, // Estimation espace utilisé par icône+titre
                            height = lineHeight - 16.dp // Moins padding
                        )
                        freeContent("right", freeSize)
                    }
                }
            }
            
            DisplayMode.CONDENSED -> {
                // Icône + Titre en haut (1 ligne), zone libre dessous (1 ligne)
                UI.Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    // Header : Icône + Titre (1 ligne)
                    UI.Box(
                        modifier = Modifier.fillMaxWidth().height(lineHeight - 8.dp)
                    ) {
                        UI.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            icon()
                            UI.Text(
                                text = title,
                                type = TextType.SUBTITLE,
                                semantic = "tool-instance-title"
                            )
                        }
                    }
                    
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    // Zone libre (1 ligne)
                    UI.Box(
                        modifier = Modifier.fillMaxWidth().height(lineHeight - 8.dp)
                    ) {
                        val freeSize = DpSize(
                            width = size.width - 16.dp,
                            height = lineHeight - 8.dp
                        )
                        freeContent("bottom", freeSize)
                    }
                }
            }
            
            DisplayMode.EXTENDED -> {
                // Layout flexible selon stratégie pour EXTENDED
                when (layoutStrategy) {
                    LayoutStrategy.HORIZONTAL_SPLIT -> {
                        UI.Row(
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            // Zone gauche : Icône + Titre (1/4 largeur)
                            UI.Column(
                                verticalArrangement = Arrangement.Top,
                                modifier = Modifier.width(columnWidth).fillMaxHeight()
                            ) {
                                UI.Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    icon()
                                    UI.Text(
                                        text = title,
                                        type = TextType.CAPTION,
                                        semantic = "tool-instance-title"
                                    )
                                }
                            }
                            
                            UI.Spacer(modifier = Modifier.width(8.dp))
                            
                            // Zone libre droite (3/4 largeur)
                            UI.Box(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                val freeSize = DpSize(
                                    width = columnWidth * 3 - 8.dp,
                                    height = size.height - 16.dp
                                )
                                freeContent("right", freeSize)
                            }
                        }
                    }
                    
                    LayoutStrategy.VERTICAL_SPLIT -> {
                        UI.Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            // Header : Icône + Titre (1 ligne)
                            UI.Box(
                                modifier = Modifier.fillMaxWidth().height(lineHeight)
                            ) {
                                UI.Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    icon()
                                    UI.Text(
                                        text = title,
                                        type = if (displayMode == DisplayMode.FULL) TextType.TITLE else TextType.SUBTITLE,
                                        semantic = "tool-instance-title"
                                    )
                                }
                            }
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            // Zone libre dessous
                            UI.Box(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                val freeSize = DpSize(
                                    width = size.width - 16.dp,
                                    height = size.height - lineHeight - 24.dp
                                )
                                freeContent("bottom", freeSize)
                            }
                        }
                    }
                    
                    LayoutStrategy.L_SHAPE -> {
                        UI.Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            // Ligne haute
                            UI.Row(
                                modifier = Modifier.fillMaxWidth().height(lineHeight)
                            ) {
                                // Zone gauche : Icône + Titre
                                UI.Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    icon()
                                    UI.Text(
                                        text = title,
                                        type = if (displayMode == DisplayMode.FULL) TextType.TITLE else TextType.SUBTITLE,
                                        semantic = "tool-instance-title"
                                    )
                                }
                                
                                UI.Spacer(modifier = Modifier.width(8.dp))
                                
                                // Zone libre droite haute
                                UI.Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    val freeSize = DpSize(
                                        width = size.width / 2 - 12.dp,
                                        height = lineHeight
                                    )
                                    freeContent("top-right", freeSize)
                                }
                            }
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            // Zone libre dessous (toute la largeur)
                            UI.Box(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                val freeSize = DpSize(
                                    width = size.width - 16.dp,
                                    height = size.height - lineHeight - 24.dp
                                )
                                freeContent("bottom", freeSize)
                            }
                        }
                    }
                }
            }
            
            DisplayMode.FULL -> {
                // Layout fixe pour FULL : haut droite + dessous complet
                UI.Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    // Ligne haute
                    UI.Row(
                        modifier = Modifier.fillMaxWidth().height(lineHeight)
                    ) {
                        // Zone gauche : Icône + Titre
                        UI.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            icon()
                            UI.Text(
                                text = title,
                                type = TextType.TITLE,
                                semantic = "tool-instance-title"
                            )
                        }
                        
                        UI.Spacer(modifier = Modifier.width(8.dp))
                        
                        // Zone libre haut droite (petit slot)
                        UI.Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            val freeSize = DpSize(
                                width = size.width / 2 - 12.dp,
                                height = lineHeight
                            )
                            freeContent("top-right", freeSize)
                        }
                    }
                    
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    // Zone libre dessous (pleine largeur, hauteur infinie)
                    UI.Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        val freeSize = DpSize(
                            width = size.width - 16.dp,
                            height = size.height - lineHeight - 24.dp
                        )
                        freeContent("bottom", freeSize)
                    }
                }
            }
        }
    }
}