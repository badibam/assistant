# Interface Utilisateur

Guide des patterns et composants UI pour maintenir cohérence et simplicité.

## Architecture Hybride

**Layouts** : Compose natif (Row, Column, Box, Spacer)
**Visuels** : Composants UI.* (Button, Text, Card, FormField)
**Métier** : Composants spécialisés (ZoneCard, ToolCard)

### Layout Standard
Column avec fillMaxWidth, padding vertical `UI.Space.L` et espacement automatique entre éléments.

### Espacements
Un écran n'écrit jamais un espacement en dp : il le nomme, `UI.Space.XS`, `S`, `M`, `L` ou `XL`, et le thème en donne la taille (`ThemeContract.spacing`) — 4, 8, 12, 16 et 24 dp pour le thème par défaut, des cellules entières pour le thème rétro. Sont des espacements les arguments de `padding()`, `Arrangement.spacedBy()` et `PaddingValues()`, la hauteur ou la largeur d'un `Spacer`, et un argument `spacing =` ; `scripts/check_spacing.py` refuse un dp à ces endroits, hors du code des thèmes. Une taille (d'icône, de bloc) n'est pas un espacement.

### Sons de l'interface
Un composant `UI.*` envoie un signal de la liste fermée `UISignal` (confirmer, entrer, revenir, ouvrir, fermer, cocher, cran, bout de liste, refus) ; le thème dit quel son y répond (`ThemeContract.sound`, null pour le silence) et `UISounds` le joue sur une seule piste, si le réglage « Sons de l'interface » (catégorie `ui`) est activé. Un écran ne joue jamais de son lui-même. Un élément désactivé reçoit encore le toucher, pour le seul son de refus. Le bout d'une liste s'entend à la racine de chaque fenêtre (`Modifier.scrollEndSound()`), jamais liste par liste : une nouvelle fenêtre de dialogue faite à la main le pose à sa racine.

### Scroll Obligatoire pour Tous les Conteneurs
**Règle** : Tous les conteneurs de contenu (écrans, dialogues, formulaires) DOIVENT avoir un scroll vertical sur leur Column principale.

**S'applique à** : MainScreen, ZoneScreen, CreateZoneScreen, Settings, Dialogues de configuration, Formulaires multi-sections, etc.

**Pattern obligatoire** :
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(vertical = UI.Space.L),
    verticalArrangement = Arrangement.spacedBy(UI.Space.L)
) {
    // Contenu scrollable
}
```

**Imports requis** :
```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

**Rationale** : Le scroll garantit que tout le contenu reste accessible sur tous les appareils, quelle que soit la taille de l'écran, la quantité de contenu affichée, ou la présence du clavier.

**Exceptions** : Seuls les conteneurs avec LazyColumn/LazyRow (qui ont leur propre scroll natif) sont exemptés.

### Headers de Page
UI.PageHeader supporte titre, sous-titre optionnel, icône, boutons gauche/droite avec actions prédéfinies. Un bouton gauche BACK donne aussi son action à la touche Retour du téléphone : un écran sans ce bouton (formulaire sans en-tête) pose son propre `BackHandler` sur son annulation, et l'accueil demande confirmation avant de fermer l'app.

## Conventions Générales

### Langue par Défaut
**Tous les strings sont en français par défaut**, sauf :
- Commentaires et debug : anglais
- Noms de variables/fonctions : anglais
- Messages utilisateur : français via système de strings (s.shared/s.tool)

## Système de Texte Simplifié

### UI.Text - 4 paramètres maximum
Accepte text, type (TITLE, SUBTITLE, HEADING, BODY, STRONG, CAPTION, LABEL, ERROR, WARNING), fillMaxWidth et textAlign optionnel. STRONG est un texte courant qui doit ressortir de ses voisins (un message non lu) : en gras dans le thème par défaut. HEADING nomme une tuile ou une section de tuiles (le nom d'un outil, d'une zone, d'un groupe), un cran au-dessus du texte courant.

### Séparation Layout/Contenu
**Principe** : UI.Text pour le rendu, Box+Modifier pour layout et interactions.
- Pattern weight avec Box wrapper pour répartition d'espace
- Pattern padding avec Box pour espacement
- Pattern clickable avec Box pour interactions

### Pattern Row Standardisé
Row avec fillMaxWidth, padding vertical `UI.Space.XS`, espacement `UI.Space.S` entre colonnes.
- Colonnes avec weight + Box pour alignement précis
- **Usage** : Tableaux, listes avec actions, formulaires multi-colonnes

## Boutons et Actions

### Deux Types de Boutons

**UI.Button** - Générique et flexible avec type (PRIMARY/SECONDARY/DEFAULT), size (XS à XXL), state et content personnalisé.

**UI.ActionButton** - Actions standardisées avec action prédéfinie, display (ICON/LABEL), size et confirmation optionnelle. En ICON, le bouton montre l'icône Lucide que porte l'action (`ButtonAction.iconName`), dessinée par le thème. `active` le montre allumé tant que dure ce qu'il ouvre (le mode d'édition d'un groupe), dessiné par le thème (le thème par défaut : un fond plein de la couleur principale). Le thème par défaut dessine un bouton en icône sans fond, l'icône dans la couleur de son type, et le bouton principal en cadre de la couleur principale.

**UI.FloatingButton** - Un bouton posé au-dessus du contenu (le chat, sur l'accueil), sur un fond à lui, pour que ce qui défile dessous ne se voie pas au travers.

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
- **TEXT_MEDIUM** (250 chars) : Descriptions courtes
- **TEXT_LONG** (1500 chars) : Contenu textuel substantiel
- **TEXT_UNLIMITED** : Contenu long sans limite (journaux, etc.)
- **NUMERIC** : Clavier numérique
- **EMAIL** : Clavier email, pas d'autocorrect
- **PASSWORD** : Masqué, pas d'autocorrect
- **SEARCH** : Autocorrect + action loupe

### Limites Définies (FieldLimits.kt)
Les constantes suivantes sont référencées dans les schémas JSON :
- `SHORT_LENGTH = 60` : FieldType.TEXT
- `MEDIUM_LENGTH = 250` : FieldType.TEXT_MEDIUM
- `LONG_LENGTH = 1500` : FieldType.TEXT_LONG
- `UNLIMITED_LENGTH = Int.MAX_VALUE` : FieldType.TEXT_UNLIMITED

### Cohérence Schéma/UI - RÈGLE CRITIQUE

**Principe** : La limite UI (FieldType) DOIT correspondre à la limite schéma (maxLength).

**Cas d'usage** :
- **Contenu transcrit** (Journal, etc.) : `TEXT_UNLIMITED` UI + pas de maxLength schéma
- **Notes textuelles** : `TEXT_LONG` UI + `maxLength: LONG_LENGTH` schéma
- **Messages IA** : `TEXT_UNLIMITED` UI (pas de schéma pour le moment)
- **Tracking TEXT** : `TEXT_LONG` UI + `maxLength: LONG_LENGTH` schéma
- **Noms/labels** : `TEXT` UI + `maxLength: SHORT_LENGTH` schéma
- **Descriptions** : `TEXT_MEDIUM` UI + `maxLength: MEDIUM_LENGTH` schéma

**Vérification** :
```kotlin
// Schéma (ToolType)
"field": {
    "type": "string",
    "maxLength": ${FieldLimits.LONG_LENGTH} // Doit correspondre
}

// UI (ConfigScreen)
UI.FormField(
    fieldType = FieldType.TEXT_LONG // Doit correspondre au schéma
)
```

**INTERDIT** : Une limite UI plus restrictive que le schéma (ex: TEXT_MEDIUM pour un schéma LONG_LENGTH).

### Autocorrection Intelligente
- **TEXT** : Words + autocorrect
- **TEXT_MEDIUM/LONG/UNLIMITED** : Sentences + autocorrect
- **EMAIL/PASSWORD** : Pas d'autocorrect (sécurité)
- **NUMERIC** : Clavier numérique uniquement
- **SEARCH** : Words + action loupe

## Formulaires et Validation

### Toast d'Erreurs Automatique
Pattern LaunchedEffect pour afficher et reset automatiquement les messages d'erreur.

### Validation Pré-Envoi (Dialogs)
**Pattern** : Validation avant envoi au service via SchemaValidator. State validationResult, fonction validateForm(), usage dans onConfirm avec vérification isValid.

### Composants Formulaire

**UI.FormField** - Champ standard avec label, value, onChange, fieldType, required, state, readonly et onClick optionnel. Le thème n'a qu'un champ de texte, qui reçoit un `TextFieldValue` (le texte, sa sélection, sa composition) : la forme sur une chaîne garde le curseur elle-même, la forme sur un `TextFieldValue` le laisse à l'appelant, pour insérer là où il se trouve (l'éditeur de formule).

**UI.FormSelection** - Sélections avec label, options, selected, onSelect et required.

**UI.BooleanField** - Oui/non en deux boutons. Une réponse (`Boolean?`) part sans bouton choisi et se vide si elle est facultative ; un état (`Boolean`) a toujours un bouton choisi et ne se vide jamais : « On » / « Off » par défaut, en boutons compacts qui se posent à côté de ce qu'ils commutent (une automation) ; avec ses propres libellés, c'est un choix entre deux modes, sur toute la largeur. Une liste où l'on coche plusieurs éléments est faite de `UI.Checkbox`.

**UI.Switch, UI.Tabs** - Un réglage qui prend effet dès qu'on le bascule, son libellé au début ; une rangée d'onglets, un par libellé. Dessinés par le thème.

**UI.FullScreen, UI.HeaderBar** - Une vue qui couvre l'écran sur le fond du thème (la racine de l'app, et le chat par `FullScreenDialog`, dont la fenêtre couvre tout l'écran, masque la barre de navigation et peint sous la barre d'état la bande de l'activité, sa couleur et le ton de ses icônes recopiés) ; le bandeau en haut d'une telle vue, son titre et ses boutons en rangée.

**UI.StatusIndicator** - Une pastille d'état : l'écran nomme l'état (`StatusColor` : succès, avertissement, erreur, info, discret), le thème en donne la couleur (`UI.statusColor` pour la teinte d'une icône).

**UI.SliderField** - Échelle ; sans réponse, pas de poignée et « — ».

**UI.Divider** - Un trait horizontal qui sépare deux parties d'un écran ou d'une carte, dessiné par le thème.

**UI.MarkedIcon** - L'icône d'une tuile d'outil ou de zone et ses deux pastilles, chacune dans son coin : en haut `UI.WaitingMark`, quand une de ses entrées attend l'utilisateur (`ToolTypeContract.getWaiting`, compté par `tools.waiting`, lu dans `LocalWaiting`) ; en bas `UI.RunningMark`, quand un chronomètre tourne sur une de ses entrées, pour tout type d'outil (`tools.running`, lu dans `LocalRunning`). Leur forme est au thème (`ThemeContract.WaitingMark`, `RunningMark`).

**UI.Drawing** - Un dessin fait de formes placées en pixels (`core/drawing` : rectangle, tracé, symbole, arc, segment, texte, pointe), mis en page par qui le fait — un graphique (`tools/chart`), plus tard un aperçu sur une tuile — et dessiné par le thème (`ThemeContract.Drawing`). Les couleurs y sont des sens, jamais des valeurs : un nom de la palette (`TagColor`), le mélange de deux pour une quantité, ou l'une des encres du thème (forte, moyenne, légère) ; un rectangle « manquant » marque ce qui n'a pas pu être lu. Les textes se mesurent dans le style que le thème donne aux dessins (`drawingTextStyle`) avant d'être placés. Le thème donne aussi la taille d'un pixel de ses dessins (`drawingUnit` : un pixel écran pour le thème par défaut, le facteur entier pour le rétro), sur laquelle qui met en page cale la largeur d'une barre. Aucune primitive ne sait ce qu'elle représente : ce qu'un toucher trouve est l'affaire de qui a fait le dessin.

**UI.ReorderableColumn** - Une liste qu'on réordonne en glissant la poignée de chaque élément (`DragHandle()`, posée où l'élément le veut). Le geste, la place d'arrivée et le défilement près d'un bord sont au cœur ; la poignée et l'élément soulevé sont au thème (`ThemeContract.DragHandle`, `ReorderItem`). Chaque liste garde son ordre, imbriquée ou non : un élément ne quitte jamais sa liste. Le nouvel ordre part une seule fois, au lâcher (`onMove(from, to)`) ; « monter » et « descendre » restent des actions d'accessibilité de la poignée.

**`required`** - Chaque saisie le reçoit, sans valeur par défaut : vrai pour un champ qui peut être vide et bloque la validation tant qu'il l'est ; un champ qui a toujours une valeur (filtre, sélecteur) ne l'est pas. Le thème le marque à sa façon (`FieldLabel`, un astérisque dans le thème par défaut) ; une saisie en plusieurs parties (plage, durée, choix multiple) marque son libellé commun.

**UI.FormActions** - Container standardisé pour boutons de formulaire avec ActionButton (SAVE, CANCEL, DELETE conditionnel).

### Pattern State/Controller
**RecordingController** - Logique métier expose `StateFlow<RecordingState>` observable. Actions via méthodes (start, pause, resume, validate).

**RecordingDialog** - UI pure observe state et délègue actions. Cleanup via DisposableEffect.

## Cards et Conteneurs

### Cards Pleine Largeur
UI.Card avec type CardType.DEFAULT, contenu en Column avec padding interne `UI.Space.L`. Un en-tête de section (`CardType.SECTION_HEADER`) n'est pas une carte, dans aucun thème : son titre sur le fond de l'écran au-dessus d'un trait, les tuiles dessous restant les seules cartes.

### Titres et Sections
- **Titre principal** : UI.Text avec TextType.TITLE, fillMaxWidth et textAlign Center
- **Titre section** : UI.Text avec TextType.HEADING dans Box avec padding horizontal

## Recréation de l'activité

L'app est en portrait seul, mais l'activité se recrée encore (thème sombre basculé, langue changée, activité non conservée par le système) : tout `remember` repart de zéro et tout `LaunchedEffect` se rejoue. Trois règles, chacune avec l'outil qui l'applique.

### 1. rememberSaveable pour ce que l'utilisateur a produit

**rememberSaveable** : saisies de formulaire, sélections, filtres, page courante, onglet, fenêtre ouverte et ce qu'elle édite.

**remember** : données rechargées (`entries`, `toolInstance`), indicateurs de chargement et d'envoi (`isSaving`), messages temporaires (`errorMessage`), menus déroulants.

Un type que le Bundle ne sait pas porter passe par un saver de `core/ui/StateSavers.kt` (`JsonObjectSaver`, `FieldDefinitionsSaver`, `FieldValuesSaver`, `NullablePeriodSaver`, `MessageSegmentsSaver`, `serializableSaver(serializer)` pour tout type `@Serializable`…) : `rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(...) }`. Un objet chargé qu'on édite (entité, occurrence) se garde par son id et se retrouve dans la liste chargée. C'est cet id qu'on transmet à l'écran enfant et qui décide mise à jour ou création, jamais l'objet retrouvé : la liste se recharge après la recréation, et l'objet vaut `null` en attendant. Un type non couvert → ajouter un saver dans ce fichier, pas au site d'appel.

### 2. Charger le contenu stocké une fois par écran : rememberLoadOnce

Un `LaunchedEffect` qui remplit le formulaire se rejoue après la recréation et écrase la saisie restaurée. `rememberLoadOnce(keys) { ...; true }` (`core/ui/LoadState.kt`) ne charge qu'une fois par écran — à nouveau si les clés changent — et rend un `LoadState` :

```kotlin
val configLoad = rememberLoadOnce(existingToolId) {
    if (existingToolId == null) return@rememberLoadOnce true  // création : rien à charger
    val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to existingToolId))
    if (!result.isSuccess) return@rememberLoadOnce false
    name = ...                                                  // remplit les états saveable
    true
}

if (configLoad == LoadState.LOADING) { /* chargement */ return }
UI.ToolConfigActions(..., saveEnabled = !isSaving && configLoad == LoadState.LOADED)
```

### 3. Une action ne travaille que sur une donnée chargée

Avant un chargement ou après son échec, l'écran montre des valeurs par défaut : enregistrées, elles écraseraient le contenu stocké, sans erreur. Enregistrer (et toute action qui lit la donnée chargée) est donc désactivé hors de `LoadState.LOADED`, ou caché tant que l'écran charge.

Pour revenir à la première page quand un filtre change, `OnChangedEffect("$filtre|$periode") { currentPage = 1 }` : un `LaunchedEffect` sur les filtres se déclencherait aussi à la recréation et perdrait la page restaurée.

## Tableaux et Listes

### Pattern Weight pour Tableaux
Row avec fillMaxWidth, colonnes en Box avec weight pour répartition (ex: 1f pour actions, 4f pour contenu), contentAlignment Center pour boutons.

### Composants Métier Spécialisés

**UI.ZoneCard** - Zone avec logique métier intégrée, onClick et contentDescription.

**UI.ToolCard** - Tool instance avec displayMode (ICON, MINIMAL, LINE, CONDENSED, EXTENDED, SQUARE, FULL), onClick et onLongClick.

**ToolGrid** - Les outils d'une section sur leur grille de quatre colonnes. En mode d'édition (`GridEditor`, un groupe à la fois, ouvert par le bouton `ARRANGE` de son titre), le thème dessine les cases (`ThemeContract.GridCell`), un toucher sélectionne une tuile, et `GridEditBar` la déplace aux flèches (`Grid.move`) ; la validation l'écrit en une fois (`tools.place`), « Annuler » remet la section comme elle était ; le reste de l'écran s'atténue et ne réagit pas (`Faded`). L'accueil range ses zones de la même façon (`GridLayout`, `ZonePositions`, `zones.place`), une zone en ICON, MINIMAL, LINE ou CONDENSED (`UI.ZoneCard`), son mode réglé avec elle (`display_mode`).

## Navigation et États

### Navigation Conditionnelle
Pattern if/else avec state boolean pour navigation simple sans NavController.

### Feedback Utilisateur
UI.Toast avec context, message et Duration (SHORT/LONG) pour messages temporaires.

## Thèmes et Personnalisation

### Thèmes
Deux thèmes, inscrits dans `ThemeScanner` : le défaut (Material) et le rétro (pixel art, `docs/design/retro-theme.md`), qui dessine tout lui-même. Chacun implémente `ThemeContract` et nomme lui-même ce qui lui appartient, son nom et ses palettes, dans ses propres textes (`themes/<id>/strings.xml`, lus par `s.theme()`) : le cœur n'en connaît aucun. Un thème a des couleurs claires et sombres (`PaletteMode`). L'utilisateur choisit dans Réglages › Interface le thème, le mode (clair, sombre, ou comme le téléphone, suivi quand il change), la teinte (un décalage de 0 à 359° qui tourne toutes les couleurs du thème sur le cercle OKLCH, leur clarté et leur saturation gardées, sauf celles des états et des tags : `Appearance.hueShift`, `Oklch`, `HueShiftTest`) et le cran de taille (0 à 3, qu'un thème au pixel applique à son facteur entier, vers le haut seulement) ; `AppConfigManager` les applique à `CurrentTheme` (`Appearance`) à chaque lecture des réglages. L'écran montre chaque choix aussitôt fait et revient à l'apparence enregistrée si on le quitte sans enregistrer. Pour que l'écran en cours survive au changement de thème, `MainActivity` déplace le contenu de l'app d'un cadre d'écran (`FullScreen`) à l'autre sans le recréer (`movableContentOf`) : un `FullScreen` ne compose donc jamais son contenu à part (pas de `BoxWithConstraints`, dont le contenu déplacé ne suivrait plus le thème), et aucun champ ne garde le focus pendant le déplacement. Les bancs des thèmes s'ouvrent par `./run themes` (`themes/bench.html`) : celui du rétro règle et enregistre ses deux palettes, celui du défaut montre ses couleurs à tout décalage, recopiées de `DefaultTheme` et tenues à elles par `DefaultColorsBenchTest`. La grille des tuiles prend du thème la largeur d'une case (`gridCellPx`), la hauteur d'une rangée (`gridRowPx`, la largeur dans le thème par défaut) et l'écart entre deux colonnes et deux rangées (`gridColumnGapPx`, `gridRowGapPx`, nuls dans le thème par défaut, qui garde l'écart dans ses tuiles).

### Espacement Standard
- **Entre sections** : spacedBy(UI.Space.L)
- **Vertical screens** : padding(vertical = UI.Space.L)
- **Cards internes** : padding(UI.Space.L)
- **Sections hors cards** : padding(horizontal = UI.Space.L)

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
data class Period(val timestamp: Long, val type: PeriodType) // Période absolue
data class RelativePeriod(val offset: Int, val type: PeriodType) // Période relative (offset depuis maintenant)
```

### Composants de Période

**SinglePeriodSelector** - Navigation avec flèches, period, onPeriodChange, showDatePicker, useOnlyRelativeLabels.

**InstantPicker** - La seule saisie d'une date ou d'un instant (champ DATE ou DATETIME d'une entrée, borne d'une période) : une date personnalisée, une date relative (unité, décalage, début ou fin), maintenant, sans limite pour une borne. Elle édite un `TimePoint`. Avec une référence (`hasReference`), un relatif et maintenant s'enregistrent tels quels, résolus à chaque fois ; sans référence, l'horloge résout le choix sur-le-champ et c'est la date obtenue qui s'enregistre.

**PeriodPicker** - Deux `InstantPicker` (depuis, jusqu'à) qui éditent une `EntryPeriod` ; il nomme une fois la référence du contexte (« Par rapport à : l'instant lu »).

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
- **rememberSaveable** pour états métier et navigation (survit à la recréation)
- **remember** pour états temporaires rechargés (isLoading, données)

### À Éviter
- Mélanger ActionButton et UI.Button dans même écran
- Boutons Row/Column manuels au lieu de FormActions
- Layout modifiers directement sur UI.Text
- Valeurs par défaut qui masquent erreurs de config
- Strings hardcodées (toujours s.shared/s.tool)

---

*L'interface UI privilégie cohérence et simplicité sans sacrifier flexibilité.*
