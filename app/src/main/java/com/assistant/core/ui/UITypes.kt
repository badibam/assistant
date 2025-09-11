package com.assistant.core.ui

/**
 * Types unifiés pour la nouvelle architecture UI
 * UNIQUEMENT les éléments convenus dans UI_DECISIONS.md
 */

// =====================================
// UNIFIED STATES
// =====================================

/**
 * État unifié pour tous les composants UI
 */
enum class ComponentState {
    NORMAL,     // Standard state
    LOADING,    // Traitement en cours
    DISABLED,   // Non interactif
    ERROR,      // Validation/system error
    READONLY,   // Lecture seule
    SUCCESS     // Feedback positif
}

// =====================================
// EXTENDED SIZES
// =====================================

/**
 * Système de tailles étendu
 */
enum class Size {
    XS, S, M, L, XL, XXL
}

// =====================================
// INTEGRATED VALIDATION
// =====================================

/**
 * Types de champs pour validation et comportement
 */
enum class FieldType {
    TEXT,           // 60 chars - identifiants, noms, labels
    TEXT_MEDIUM,    // 250 chars - descriptions, valeurs tracking texte
    TEXT_LONG,      // 1500 chars - contenu libre long
    TEXT_UNLIMITED, // Aucune limite - documentation, exports
    NUMERIC, 
    EMAIL, 
    PASSWORD, 
    SEARCH
}

// =====================================
// FEEDBACK UTILISATEUR
// =====================================

/**
 * Types de messages de feedback
 */
enum class FeedbackType {
    SUCCESS, ERROR, WARNING, INFO
}

/**
 * Durée d'affichage des messages
 */
enum class Duration {
    SHORT, LONG, INDEFINITE
}

// =====================================
// DISPLAY MODES TOOL INSTANCES
// =====================================

/**
 * Modes d'affichage des instances d'outils
 */
enum class DisplayMode {
    ICON,       // 1/4×1/4 - icon only
    MINIMAL,    // 1/2×1/4 - icon + title side by side
    LINE,       // 1×1/4 - icon + title left, free content right
    CONDENSED,  // 1/2×1/2 - icon + title top, free space below
    EXTENDED,   // 1×1/2 - icon + title top, free zone below
    SQUARE,     // 1×1 - icon + title top, large free zone
    FULL        // 1×∞ - icon + title top, infinite free zone
}

// =====================================
// INTEGRATED DIALOG SYSTEM
// =====================================

/**
 * Types de dialog avec logique de boutons automatique
 */
enum class DialogType {
    CONFIGURE,   // → "Valider" + "Annuler"
    CREATE,      // → "Create" + "Cancel"
    EDIT,        // → "Sauvegarder" + "Annuler"
    CONFIRM,     // → "Confirmer" + "Annuler"
    DANGER,      // → "Supprimer" + "Annuler" (rouge)
    SELECTION,   // → no predefined buttons
    INFO         // → "OK"
}

// =====================================
// PRECISE BUSINESS TYPES
// =====================================

/**
 * Types de boutons avec sémantique métier
 */
/**
 * Types de boutons simplifiés
 */
enum class ButtonType {
    PRIMARY,    // Bouton principal (ex: sauvegarder)
    SECONDARY,  // Bouton secondaire (ex: annuler)
    DEFAULT     // Neutral button (all buttons with icons)
}

/**
 * Actions prédéfinies pour les boutons standardisés
 */
enum class ButtonAction {
    SAVE, CREATE, UPDATE, DELETE, CANCEL, BACK, 
    CONFIGURE, ADD, EDIT, REFRESH, SELECT, CONFIRM, UP, DOWN, LEFT, RIGHT
}

/**
 * Modes d'affichage pour les boutons
 */
enum class ButtonDisplay {
    ICON,       // Icon only
    LABEL       // Text only (BOTH will be added later)
}


/**
 * Types de texte avec hiérarchie
 */
enum class TextType {
    TITLE, SUBTITLE, BODY, CAPTION, LABEL, ERROR, WARNING
}

/**
 * Types de cartes
 */
enum class CardType {
    DEFAULT
    // Types to be added as needed
}