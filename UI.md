# Interface Utilisateur

Guide des patterns et composants UI pour maintenir cohérence et simplicité.

## Architecture Hybride

**Layouts** : Compose natif (Row, Column, Box, Spacer)
**Visuels** : Composants UI.* (Button, Text, Card, FormField)
**Métier** : Composants spécialisés (ZoneCard, ToolCard)

### Layout Standard
Column avec fillMaxWidth, padding vertical 16dp et espacement automatique entre éléments.

### Headers de Page
UI.PageHeader supporte titre, sous-titre optionnel, icône, boutons gauche/droite avec actions prédéfinies.

## Conventions Générales

### Langue par Défaut
**Tous les strings sont en français par défaut**, sauf :
- Commentaires et debug : anglais
- Noms de variables/fonctions : anglais
- Messages utilisateur : français via système de strings (s.shared/s.tool)

## Système de Texte Simplifié

### UI.Text - 4 paramètres maximum
Accepte text, type (TITLE, SUBTITLE, BODY, LABEL, SMALL), fillMaxWidth et textAlign optionnel.

### Séparation Layout/Contenu
**Principe** : UI.Text pour le rendu, Box+Modifier pour layout et interactions.
- Pattern weight avec Box wrapper pour répartition d'espace
- Pattern padding avec Box pour espacement
- Pattern clickable avec Box pour interactions

### Pattern Row Standardisé
Row avec fillMaxWidth, padding vertical 4dp, espacement 8dp entre colonnes.
- Colonnes avec weight + Box pour alignement précis
- **Usage** : Tableaux, listes avec actions, formulaires multi-colonnes

## Boutons et Actions

### Deux Types de Boutons

**UI.Button** - Générique et flexible avec type (PRIMARY/SECONDARY/DEFAULT), size (XS à XXL), state et content personnalisé.

**UI.ActionButton** - Actions standardisées avec action prédéfinie, display (ICON/LABEL), size et confirmation optionnelle.

### Actions Disponibles
- **Principales** : SAVE, CREATE, UPDATE, DELETE, CANCEL, CONFIRM
- **Navigation** : BACK, UP, DOWN
- **Utilitaires** : ADD, EDIT, CONFIGURE, REFRESH, SELECT

### Hiérarchie Visuelle
- **PRIMARY** (vert) : Actions critiques - SAVE, CREATE, CONFIRM, ADD, CONFIGURE, SELECT, EDIT, UPDATE
- **DEFAULT** (gris) : Navigation neutre - CANCEL, BACK, REFRESH, UP, DOWN
- **SECONDARY** (rouge) : Actions destructives - DELETE uniquement

### Confirmation Automatique
UI.ActionButton supporte requireConfirmation avec message personnalisable.

**UI.ConfirmDialog** - Dialog modal de confirmation avec title, message, confirmText/cancelText optionnels, onConfirm et onDismiss.

## Champs de Texte et Saisie

### Extensions FieldType
- **TEXT** (60 chars) : Noms, identifiants, labels
- **TEXT_MEDIUM** (250 chars) : Descriptions, valeurs tracking texte
- **TEXT_LONG** (1500 chars) : Contenu libre long
- **TEXT_UNLIMITED** : Documentation, exports
- **NUMERIC** : Clavier numérique
- **EMAIL** : Clavier email, pas d'autocorrect
- **PASSWORD** : Masqué, pas d'autocorrect
- **SEARCH** : Autocorrect + action loupe

### Autocorrection Intelligente
- **TEXT** : Words + autocorrect
- **TEXT_MEDIUM/LONG** : Sentences + autocorrect
- **EMAIL/PASSWORD** : Pas d'autocorrect (sécurité)
- **NUMERIC** : Clavier numérique uniquement
- **SEARCH** : Words + action loupe

## Formulaires et Validation

### Toast d'Erreurs Automatique
Pattern LaunchedEffect pour afficher et reset automatiquement les messages d'erreur.

### Validation Pré-Envoi (Dialogs)
**Pattern** : Validation avant envoi au service via SchemaValidator. State validationResult, fonction validateForm(), usage dans onConfirm avec vérification isValid.

### Composants Formulaire

**UI.FormField** - Champ standard avec label, value, onChange, fieldType, required, state, readonly et onClick optionnel.

**UI.FormSelection** - Sélections avec label, options, selected, onSelect et required.

**UI.FormActions** - Container standardisé pour boutons de formulaire avec ActionButton (SAVE, CANCEL, DELETE conditionnel).

### Pattern State/Controller
**RecordingController** - Logique métier expose `StateFlow<RecordingState>` observable. Actions via méthodes (start, pause, resume, validate).

**RecordingDialog** - UI pure observe state et délègue actions. Cleanup via DisposableEffect.

## Cards et Conteneurs

### Cards Pleine Largeur
UI.Card avec type CardType.DEFAULT, contenu en Column avec padding interne 16dp.

### Titres et Sections
- **Titre principal** : UI.Text avec TextType.TITLE, fillMaxWidth et textAlign Center
- **Titre section** : UI.Text avec TextType.SUBTITLE dans Box avec padding horizontal

## Changements d'Orientation

### Règle de base : rememberSaveable pour tout état "métier"
Tout état qui représente une donnée utilisateur, une navigation ou une sélection DOIT utiliser `rememberSaveable` pour survivre aux rotations d'écran.

### Pattern de conservation d'état

**rememberSaveable (survit rotation)** :
- États de navigation : `navigateToEntryId`, `selectedScreen`, `currentTab`
- Données de formulaire : `title`, `content`, `timestamp`, `selectedDate`
- Sélections utilisateur : `selectedPeriod`, `isEditing`, `filterType`
- Configuration temporaire : `showAdvancedOptions`, `expandedSectionId`

**remember (réinitialisé à la rotation)** :
- États de chargement : `isLoading`, `isSaving`, `isProcessing`
- Messages temporaires : `errorMessage`, `successMessage`
- Données rechargées : `entries`, `toolInstance`, `stats`
- États UI volatils : `showDialog`, `showDatePicker`

### Exemples concrets

```kotlin
// États formulaire (survie rotation)
var title by rememberSaveable { mutableStateOf("") }
var content by rememberSaveable { mutableStateOf("") }
var isEditing by rememberSaveable { mutableStateOf(false) }
var timestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

// États navigation (survie rotation)
var navigateToDetailId by rememberSaveable { mutableStateOf<String?>(null) }
var navigateIsCreating by rememberSaveable { mutableStateOf(false) }
var selectedTab by rememberSaveable { mutableStateOf(0) }

// États temporaires (réinitialisation rotation)
var isLoading by remember { mutableStateOf(true) }
var errorMessage by remember { mutableStateOf<String?>(null) }
var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
```

### Pattern ID pour objets complexes
Types complexes non-sérialisables → sauvegarder l'ID avec `rememberSaveable`, retrouver l'objet via find() dans `LaunchedEffect`.

## Tableaux et Listes

### Pattern Weight pour Tableaux
Row avec fillMaxWidth, colonnes en Box avec weight pour répartition (ex: 1f pour actions, 4f pour contenu), contentAlignment Center pour boutons.

### Composants Métier Spécialisés

**UI.ZoneCard** - Zone avec logique métier intégrée, onClick et contentDescription.

**UI.ToolCard** - Tool instance avec displayMode (ICON, MINIMAL, LINE, CONDENSED, EXTENDED, SQUARE, FULL), onClick et onLongClick.

## Navigation et États

### Navigation Conditionnelle
Pattern if/else avec state boolean pour navigation simple sans NavController.

### Feedback Utilisateur
UI.Toast avec context, message et Duration (SHORT/LONG) pour messages temporaires.

## Thèmes et Personnalisation

### Palette Personnalisée
Système de thème avec palette personnalisée branchée sur Material Design.
- **Formes** : Définies au niveau thème pour boutons et cards
- **Couleurs** : Palette Material adaptée
- **Typography** : Cohérente via TextType enum

### Espacement Standard
- **Entre sections** : spacedBy(16.dp)
- **Vertical screens** : padding(vertical = 16.dp)
- **Cards internes** : padding(16.dp)
- **Sections hors cards** : padding(horizontal = 16.dp)

## Pattern Loading/Error Standard

### États Obligatoires
States data, isLoading et errorMessage obligatoires pour tous composants async.

### Affichage Conditionnel
- Early return si isLoading avec UI.Text loading
- Toast automatique pour erreurs avec LaunchedEffect et reset

### INTERDIT : Valeurs par défaut silencieuses
Toujours vérifier config != null avant utilisation, pas de valeurs par défaut qui masquent les erreurs.

## Sélection Temporelle

### Types de Période

```kotlin
data class Period(val timestamp: Long, val type: PeriodType)  // Période absolue
data class RelativePeriod(val offset: Int, val type: PeriodType)  // Période relative (offset depuis maintenant)
```

### Composants de Période

**SinglePeriodSelector** - Navigation avec flèches, period, onPeriodChange, showDatePicker, useOnlyRelativeLabels.

**PeriodRangeSelector** - Sélection plages avec start/end PeriodType, periods, customDates, callbacks, useOnlyRelativeLabels et mode returnRelative (retourne RelativePeriod au lieu de Period).

### Logique Labels
**useOnlyRelativeLabels** :
- `false` : "Semaine du 15 mars"
- `true` : "il y a 125 semaines"

### Utilitaires
- `normalizeTimestampWithConfig()` : Normalise timestamp à début de période
- `getPeriodEndTimestamp()` : Calcule fin de période
- `getNextPeriod()` / `getPreviousPeriod()` : Navigation périodes
- `resolveRelativePeriod()` : Résout RelativePeriod → Period absolu
- `calculatePeriodOffset()` : Calcule offset entre timestamp et maintenant

## Règles d'Usage

### À Faire
- **ActionButton** pour actions standard (SAVE, DELETE, BACK)
- **UI.Button** pour textes dynamiques et contenu complexe
- **FormActions** pour tous boutons de formulaire
- **Box wrappers** pour layout et interactions UI.Text
- **Validation centralisée** via SchemaValidator
- **isLoading pattern** pour tous états async
- **rememberSaveable** pour états métier et navigation (rotation safe)
- **remember** pour états temporaires rechargés (isLoading, données)

### À Éviter
- Mélanger ActionButton et UI.Button dans même écran
- Boutons Row/Column manuels au lieu de FormActions
- Layout modifiers directement sur UI.Text
- Valeurs par défaut qui masquent erreurs de config
- Strings hardcodées (toujours s.shared/s.tool)

---

*L'interface UI privilégie cohérence et simplicité sans sacrifier flexibilité.*
