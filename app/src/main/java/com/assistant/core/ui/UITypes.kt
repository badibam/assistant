package com.assistant.core.ui

/**
 * Types unifiés pour la nouvelle architecture UI
 * UNIQUEMENT les éléments convenus dans UI_DECISIONS.md
 */

// =====================================
// ÉTATS UNIFIÉS
// =====================================

/**
 * État unifié pour tous les composants UI
 */
enum class ComponentState {
    NORMAL,     // État standard
    LOADING,    // Traitement en cours
    DISABLED,   // Non interactif
    ERROR,      // Erreur de validation/système
    READONLY,   // Lecture seule
    SUCCESS     // Feedback positif
}

// =====================================
// TAILLES ÉTENDUES
// =====================================

/**
 * Système de tailles étendu
 */
enum class Size {
    XS, S, M, L, XL, XXL
}

// =====================================
// VALIDATION INTÉGRÉE
// =====================================

/**
 * Règles de validation de base
 */
enum class ValidationRule {
    NONE, EMAIL, NUMERIC, REQUIRED, MIN_LENGTH, MAX_LENGTH
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
    ICON,       // 1/4×1/4 - icône seule
    MINIMAL,    // 1/2×1/4 - icône + titre côte à côte
    LINE,       // 1×1/4 - icône + titre à gauche, contenu libre droite
    CONDENSED,  // 1/2×1/2 - icône + titre en haut, reste libre dessous
    EXTENDED,   // 1×1/2 - icône + titre en haut, zone libre dessous
    SQUARE,     // 1×1 - icône + titre en haut, grande zone libre
    FULL        // 1×∞ - icône + titre en haut, zone libre infinie
}

// =====================================
// DIALOG SYSTEM INTÉGRÉ
// =====================================

/**
 * Types de dialog avec logique de boutons automatique
 */
enum class DialogType {
    CONFIGURE,   // → "Valider" + "Annuler"
    CREATE,      // → "Créer" + "Annuler"
    EDIT,        // → "Sauvegarder" + "Annuler"
    CONFIRM,     // → "Confirmer" + "Annuler"
    DANGER,      // → "Supprimer" + "Annuler" (rouge)
    SELECTION,   // → pas de boutons prédéfinis
    INFO         // → "OK"
}

// =====================================
// TYPES MÉTIER PRÉCIS
// =====================================

/**
 * Types de boutons avec sémantique métier
 */
enum class ButtonType {
    SAVE, DELETE, CANCEL, ADD, BACK, CONFIRM_DELETE
}

/**
 * Types de champs texte
 */
enum class TextFieldType {
    TEXT, NUMERIC, SEARCH, PASSWORD
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
    // Types à ajouter au fur et à mesure des besoins
}