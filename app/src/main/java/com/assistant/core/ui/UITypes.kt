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

/**
 * The spaces of a screen, from the smallest to the largest: the gaps between elements, the
 * margins inside a container, the blanks. Screens name one (UI.Space) and the theme gives its
 * size, so that a retro theme can make each a whole number of cells.
 */
enum class Spacing {
    XS, S, M, L, XL
}

/**
 * What a colored mark says about the state of something (an execution, a log line, an action
 * to validate). A screen names the state; the theme gives its color, which must be seen.
 */
enum class StatusColor {
    SUCCESS, WARNING, ERROR, INFO, MUTED
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
    PRIMARY,    // Primary button (e.g., save, create, confirm) - uses primary color
    SECONDARY,  // Secondary button (e.g., alternative actions) - uses secondary color
    TERTIARY,   // Tertiary button (e.g., less important actions) - uses tertiary color
    DANGER,     // Destructive actions (e.g., delete, stop)
    DEFAULT     // Neutral button (e.g., cancel, back)
}

/**
 * Predefined actions for standardized buttons.
 *
 * [iconName] is the icon an action shows as a button in ButtonDisplay.ICON: a Lucide name, which
 * the theme draws. Which icon means an action is the app's vocabulary, shared by every theme.
 */
enum class ButtonAction(val iconName: String) {
    SAVE("check"), CREATE("plus"), UPDATE("pencil"), DELETE("trash"), CANCEL("x"), BACK("arrow-left"),
    CONFIGURE("settings"), ADD("plus"), EDIT("pencil"), REFRESH("refresh-cw"), SELECT("check"), CONFIRM("check"),
    LEFT("chevron-left"), RIGHT("chevron-right"),
    AI_CHAT("message-circle"), RESET("rotate-ccw"), INTERRUPT("pause"), STOP("square"), PAUSE("pause"),
    RESUME("play"), START("play"), VIEW("eye"), ATTACH("paperclip"), REPEAT("repeat"), ARRANGE("layout-grid"), UP("chevron-up"), DOWN("chevron-down")
}

/**
 * Display modes for buttons
 */
enum class ButtonDisplay {
    ICON,       // Icon only
    LABEL       // Text only (BOTH will be added later)
}


/**
 * Text types with hierarchy. STRONG is body text that must stand out among its neighbors (an
 * unread message among read ones): bold where the theme's font has a bold, its own way otherwise.
 */
enum class TextType {
    TITLE, SUBTITLE, BODY, STRONG, CAPTION, LABEL, ERROR, WARNING
}

/**
 * Card types
 */
enum class CardType {
    DEFAULT,
    SECTION_HEADER  // For group/section headers - uses surfaceVariant for subtle contrast
    // Types to be added as needed
}