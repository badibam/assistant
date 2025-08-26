package com.assistant.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.assistant.themes.default.DefaultTheme

/**
 * CurrentTheme - Gestionnaire global de thème
 * 
 * Point central pour la gestion du thème actuel :
 * - UI.* → CurrentTheme.current.* → DefaultTheme.*
 * - Changement de thème transparent pour l'app
 * - Un seul thème actif à la fois
 */
object CurrentTheme {
    
    /**
     * Thème actuellement actif
     * Par défaut : DefaultTheme
     * Changeable à l'exécution via switchTheme()
     */
    var current: ThemeContract by mutableStateOf(DefaultTheme)
        private set
    
    /**
     * Change le thème actuel
     * Tous les composants UI seront automatiquement re-rendus
     */
    fun switchTheme(newTheme: ThemeContract) {
        current = newTheme
    }
}