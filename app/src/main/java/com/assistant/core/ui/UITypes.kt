package com.assistant.core.ui

import androidx.compose.ui.focus.FocusRequester

/**
 * Unified types for new UI architecture
 * ONLY elements agreed upon in UI_DECISIONS.md
 */

// =====================================
// UNIFIED STATES
// =====================================

/**
 * Unified state for all UI components
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
 * Extended size system
 */
enum class Size {
    XS, S, M, L, XL, XXL
}

// =====================================
// INTEGRATED VALIDATION
// =====================================

/**
 * Field types for validation and behavior
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

/**
 * Controlled modifier system for FormField
 * Prevents uncontrolled modifier usage while allowing field-specific behaviors
 */
data class FieldModifier(
    val focusRequester: FocusRequester? = null,
    val onFocusChanged: ((androidx.compose.ui.focus.FocusState) -> Unit)? = null
    // Extensible for other field-specific behaviors
) {
    companion object {
        /**
         * Create FieldModifier with focus requester
         */
        fun withFocus(focusRequester: FocusRequester): FieldModifier =
            FieldModifier(focusRequester = focusRequester)
    }
}

// =====================================
// FEEDBACK UTILISATEUR
// =====================================

/**
 * Feedback message types
 */
enum class FeedbackType {
    SUCCESS, ERROR, WARNING, INFO
}

/**
 * Message display duration
 */
enum class Duration {
    SHORT, LONG, INDEFINITE
}

// =====================================
// DISPLAY MODES TOOL INSTANCES
// =====================================

/**
 * Display modes for tool instances
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
 * Dialog types with automatic button logic
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
 * Button types with business semantics
 */
/**
 * Simplified button types
 */
enum class ButtonType {
    PRIMARY,    // Primary button (e.g., save)
    SECONDARY,  // Secondary button (e.g., cancel)
    DEFAULT     // Neutral button (all buttons with icons)
}

/**
 * Predefined actions for standardized buttons
 */
enum class ButtonAction {
    SAVE, CREATE, UPDATE, DELETE, CANCEL, BACK,
    CONFIGURE, ADD, EDIT, REFRESH, SELECT, CONFIRM, UP, DOWN, LEFT, RIGHT,
    AI_CHAT, RESET, INTERRUPT, STOP, PAUSE, RESUME, START
}

/**
 * Display modes for buttons
 */
enum class ButtonDisplay {
    ICON,       // Icon only
    LABEL       // Text only (BOTH will be added later)
}


/**
 * Text types with hierarchy
 */
enum class TextType {
    TITLE, SUBTITLE, BODY, CAPTION, LABEL, ERROR, WARNING
}

/**
 * Card types
 */
enum class CardType {
    DEFAULT
    // Types to be added as needed
}