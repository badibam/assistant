# Outils et Extensibilité

Guide pour comprendre et créer des outils dans l'architecture modulaire.

## Concepts Fondamentaux

### Tool Type vs Tool Instance
- **Tool Type** : Métadonnées statiques et comportements (ex: TrackingToolType)
- **Tool Instance** : Configuration spécifique dans une zone (ex: "Poids quotidien")

### Une Instance = Un Concept
- **Suivi** : 1 métrique spécifique (poids, humeur, etc.)
- **Objectif** : 1 objectif avec sous-objectifs et critères
- **Graphique** : 1 graphique, qui peut superposer des couches ou se répéter par catégorie
- **Liste** : 1 liste thématique (courses, tâches)
- **Données structurées** : 1 table de fiches (aliments, livres, contacts)
- **Journal** : 1 type de journal (réflexions, rêves)
- **Note** : 1 note individuelle
- **Message** : 1 message/rappel planifié
- **Alerte** : 1 règle d'alerte automatique

### Custom Fields (Champs Personnalisés)

Tous les tooltypes supportent des **champs supplémentaires** définis par l'utilisateur pour étendre les données.

**Structure** :
- **Définitions** : Dans `custom_fields` array de la config (name, display_name, type, always_visible)
- **Valeurs** : Dans `custom_fields` object des données (clé = name du champ)
- **Types** : source unique `FieldType` — TEXT, NUMERIC, SCALE, CHOICE, BOOLEAN, RANGE, DATE, TIME, DATETIME
- **Validation** : Intégrée automatiquement dans les schémas data enrichis

**Pattern** : Les schémas data nécessitent `toolInstanceId` pour enrichissement avec custom fields.

#### `name` est attribué, jamais choisi

`name` est l'identifiant technique : la clé sous laquelle les valeurs sont stockées. Il est attribué par `ToolInstanceService.processCustomFields` à partir de `display_name`, et par lui seul — ni l'écran de configuration ni l'IA n'en fournissent un. Un champ nouveau arrive donc **sans** `name` ; `display_name` reste librement modifiable.

Conséquence : un `name` envoyé dans une mise à jour de config doit désigner un champ existant, sinon la mise à jour est refusée. C'est ce qui rend un renommage inexprimable plutôt que détecté après coup — les champs sont comparés par leur `name`, donc un renommage ressortirait comme une suppression suivie d'un ajout, et la suppression efface le champ de toutes les entrées.

Retirer un champ de la liste reste une suppression ordinaire : ses valeurs sont effacées, c'est ce que ça veut dire.

## Architecture Outil

### Structure Physique
Dossier tools/[type]/ contient :
- ToolType.kt (contrat et métadonnées)
- Service.kt (logique métier)
- Dao.kt (accès données)
- Data.kt (entité base)
- ui/ (écran d'usage et affichage ; l'écran de config est celui du core)

### Interface ToolTypeContract
Interface principale avec méthodes pour :
- **Métadonnées** : getDisplayName(), getDescription(), getSuggestedIcons(), getDefaultIconName(), getDefaultDisplayMode(), getDefaultShowFieldLabels()
- **Déclarations** : getEntryFields() (champs des entrées), getConfigSettings() (réglages propres, à côté de la partie commune `ToolConfigSettings`) ; schémas, config par défaut et lecture en sont générés ; configWithOptionsAdded() pour un type qui déclare un choix ouvert dans `data` ; getOperations() (défaut : aucune), les opérations que son service mène sur ses entrées à côté des écritures génériques, chacune un nom, une phrase et ses paramètres déclarés en champs (`ToolOperation`)
- **Interface utilisateur** : getUsageScreen() @Composable ; rememberTile() @Composable, la tuile de l'outil sur une zone (`ToolTile`) : son résumé de 2×1, son corps sur les rangées que son mode lui donne, et son aperçu à la place du résumé dans les modes sans corps (LINE, CONDENSED ; le résumé par défaut, la bande du Graphique), placés par `UI.ToolCard` à côté de l'en-tête ; le corps range ses éléments par `TileGrid`, chacun écrit par `TileLine` : à gauche une case ou une icône, à droite une valeur ou des boutons qui gardent leur taille, au milieu le texte sur deux lignes au plus, ou une ligne et une seconde plus discrète dessous ; chaque type remplit tous les modes, sans tuile par défaut, et une tuile ouvre l'outil sur une entrée ou une entrée neuve (`EntryToOpen`) ; l'écran de config est généré depuis la déclaration (`ToolConfigScreen`, `SettingsForm`), une planification (`ScheduleSettings.group`) comprise
- **Discovery pattern** : getService(), getDao(), getDatabaseEntities(), getDatabaseMigrations(), getScheduler()
- **Enrichissement** : enrichData() (défaut identity, enrichissement automatique avant persistence)
- **Règle entre entrées** : settleEntries() (défaut : rien à changer), voir plus bas
- **Sans entrées** : keepsEntries() (défaut : oui) ; un outil qui montre les entrées des autres (le Graphique) n'en garde aucune : pas de champs de l'utilisateur dans sa config, et toute écriture d'une entrée est refusée
- **Contrôle de la config** : refuseConfig() (défaut : rien), ce que seule la lecture de l'app dit au-delà du schéma (une colonne qu'aucune source ne donne), demandé à chaque création et modification ; getRowFields() (défaut : aucun), les champs des lignes que décrit la config là où un réglage les nomme (`RowFields`)
- **Validation** : validateData() (délègue à SchemaValidator)

## Méthodologie d'Implémentation

**Règle d'or** : Toujours copier-coller Tracking d'abord, adapter ensuite.

### Ordre d'Implémentation
1. **ToolType** avec ses déclarations (getEntryFields, getConfigSettings, défauts compris)
2. **Service** avec validation stricte
3. **UI screens** avec parsing robuste
4. **Enregistrement** dans ToolTypeScanner

### Points de Vérification Critiques
- API SchemaValidator : schemaType = "config|data"
- Services : tools.* utilise tool_instance_id, tool_data.* utilise toolInstanceId
- LaunchedEffect : toutes variables vérifiées dans le scope = dépendances
- Validation : au save uniquement, pas préventive

## Création d'un Nouvel Outil

### Structure de Base
1. **Entité données** : @Entity avec id, toolInstanceId, timestamp, value (JSON), metadata
2. **DAO** : Interface avec queries pour récupérer/insérer/modifier les données

### Service Métier
Class implémentant ExecutableService avec :
- execute() qui valide via ToolType puis route selon opération (create/update/delete)
- Méthodes privées pour chaque opération avec gestion CancellationToken
- Validation automatique via SchemaValidator

### ToolType Implementation
Class implémentant ToolTypeContract avec :
- getDisplayName(), getDescription(), getDefaultDisplayMode()
- getEntryFields(), getConfigSettings()
- getUsageScreen() @Composable
- getService(), getDao(), getDatabaseEntities()

### enrichData Pattern
**Principe** : Enrichissement automatique des données avant persistence (appelé par ToolDataService pour toutes les entrées).

```kotlin
override fun enrichData(data: Map<String, Any>, context: Context): Map<String, Any> {
    // Calculs, validations, enrichissements automatiques
    // Exemple Tracking : valeur brute → champ d'affichage formaté
    return enrichedData
}
```

**Usage** : Unifié UI + IA, logique pré-persistence sans interception manuelle.

### settleEntries Pattern
**Principe** : une règle qui porte sur toutes les entrées d'une instance, et non sur une seule. `ToolDataService` l'appelle à chaque création, modification ou suppression d'une entrée du tooltype, avec de quoi lire toutes les entrées de l'instance telles qu'elles seront après l'écriture — lues seulement s'il les demande, un type d'outil sans règle n'en payant pas la lecture —, et l'id de l'entrée écrite (null après une suppression). Il enregistre les entrées renvoyées dans la même transaction que l'écriture. Unifié UI + IA, comme enrichData.

**Ordre manuel** : `ManualOrder` (cœur) garde les positions 0, 1, 2… sans trou ni doublon, sous `state.position`, pour un type d'outil dont l'utilisateur ordonne les entrées à la main (Notes, Liste). Écrire la position p place l'entrée en p et décale les suivantes ; une entrée sans position va en dernier.

### Tooltypes passifs et actifs

**Principe** : il n'y a que deux plans de données. La **config** est ce que l'outil *est* (sa définition : template, formule, planning) ; **tool_data** est tout ce qu'il *enregistre ou produit* (saisies, occurrences, résultats). Pas de troisième plan — l'audit technique des échecs relève du système de logs.

**Passifs** (Tracking, Journal, Note) : l'utilisateur écrit dans tool_data.

**Actifs** (Messages, futurs Alertes, Objectifs) : le système écrit dans tool_data, piloté par la config. Un tooltype actif expose une opération `execute` (`{tooltype}.execute` avec `tool_instance_id`), déclarée dans getOperations(), et généralement un `getScheduler()`. Ses occurrences sont des entrées tool_data ordinaires : requêtables, statistiquables, migrables, visibles par l'IA, comme n'importe quelle autre entrée.

**Occurrences à cycle de vie** : une occurrence n'est pas forcément instantanée. Elle peut vivre (créée → active → close), auquel cas son schéma data porte un champ `status` et ses exigences en dépendent. Le filtre `status` de `tool_data.get` existe pour ces tooltypes-là.

### Enregistrement
Ajout dans ToolTypeScanner.getAllToolTypes() pour discovery automatique.

## Exemples de Flows Complets

### Flow Nutritionnel
1. SUIVI alimentaire (manuel) → saisie repas
2. DONNÉES STRUCTURÉES nutrition (IA) → référentiel aliments + AJR
3. SUIVI nutritionnel (IA + #1 + #2) → calculs automatiques
4. JOURNAL (IA, basé sur #3) → rapports quotidiens
5. VARIABLES (cœur, basées sur #3) → totaux du jour, moyennes périodiques
6. GRAPHIQUE (App, basé sur #5) → visualisations vs AJR
7. ALERTES (App, critères sur #3) → carences/excès détectées

### Configuration par l'IA
**Principe** : L'IA configure et gère automatiquement les outils complexes via commandes JSON.
- **Utilisateur** : Définit l'objectif ("suivre ma nutrition")
- **IA** : Crée la chaîne d'outils, configure les calculs, définit les alertes

## Types d'Outils Disponibles

### Suivi (Tracking)
**Usage** : Données temporelles quantitatives/qualitatives
**Configuration** : Type de valeur (numeric, counter, text, scale, choice, timer…), unités (numérique : la liste où chaque entrée prend la sienne, une unité nouvelle saisie la rejoint ; compteur : une unité fixe), items prédéfinis. Un choix montre en plus chacune de ses options, comme le oui/non ses réponses : enregistrée d'un toucher (nommée par son libellé, les champs de l'utilisateur à leur valeur par défaut), ou par la fenêtre de saisie préremplie
**Exemples** : Poids, humeur échelle 1-10, alimentation libre

### Objectif (Goal)
**Usage** : Critères de réussite avec poids relatifs
**Structure** : 3 niveaux - objectif → sous-objectifs → items
**Validation** : Confirmation obligatoire avant finalisation

### Graphique (Chart)
**Usage** : Montrer les entrées d'autres outils et des variables, sans rien calculer : un total par jour est une colonne de grille ou une variable, jamais une agrégation
**Configuration** : un sous-ensemble de Vega-Lite, déclaré réglage par réglage (le formulaire et le schéma de l'IA en sont générés), ses clés celles de Vega-Lite (`strokeDash` et `strokeWidth` compris). `period`, la période affichée, relative au moment de l'affichage ; `composition` : `layer` (une vue), `vconcat`, `hconcat`, `concat` (des vues `{"title", "layer"}`, `columns` par rangée), `facet` (`facet.field`), `repeat` (des colonnes, que prennent les canaux dont le `field` vaut `repeat`). Une couche : `source` — `entries` (`selection` d'un outil sans sa période, une ligne par entrée : `timestamp`, `name`, ses champs par chemin) ou `grid` (`step` du calendrier, `columns` : un nom et un terme, variable ou lecture, lu à la fin de chaque pas ; une lecture sans période lit son pas ; la ligne a aussi `timestamp`, `weekday`, `week`) ; `transform` (`fold` avec `as`, `flatten`) ; `mark` (line, point, bar, area, text, arc, tick, rect, avec ses styles) ; `encoding` (x, y, x2, y2, color, size, shape, opacity, strokeDash, detail, order, text, theta, radius : `field` parmi les colonnes de la couche, `type`, `scale` — `domain`, `zero`, `nice`, `reverse`, `range` en noms de la palette —, `axis` — `grid`, `orient` gauche ou droite —, `stack` — `none`, `zero`, `normalize`, obligatoire sur des barres ou des aires séparées par une catégorie —, `legend`, `condition` — la brique Condition sur les colonnes de la ligne — et `value`). `ChartCheck` refuse ce que le schéma ne dit pas : une colonne qu'aucune source ne donne, un canal qui manque à sa marque, deux sortes de valeurs sur un axe partagé, deux unités d'un même côté
**Données** : Aucune entrée à lui (`keepsEntries`). `ChartSources` lit une table par couche : les entrées par `tool_data.get`, une grille pas à pas (`GridSteps`, le pas en cours lu à maintenant) par `variables.evaluate` et `readings.read` avec `at`, une colonne par appel ; une valeur en échec est une cellule marquée, dessinée en trou. L'outil met le graphique en page (`ChartSceneBuilder` : échelles, graduations, empilement, légende) en un dessin de formes du cœur (`core/drawing`), que le thème dessine. L'écran : le graphique, sa légende, et ce que touche le doigt (toutes les colonnes de la ligne, ou la cause d'un trou et ses entrées, qui s'ouvrent dans leur outil) ; relu quand une entrée ou un outil change. La tuile (`rememberChartTile`) : en résumé la période et la dernière valeur de la première série ; en corps ses marques en bandeau (EXTENDED), le graphique réduit (SQUARE) ou entier (FULL) (`ChartDetail`) ; un toucher ouvre l'outil

### Journal (Journal)
**Usage** : Entrées textuelles/audio libres avec dates
**Configuration** : Template d'entrée, fréquence suggérée

### Liste (List)
**Usage** : L'état présent de ce qui reste à faire (courses, tâches, check-list), sans historique : ce qui a été fait et quand relève d'un suivi « occurrence »
**Configuration** : `remove_when_checked`, un élément coché est supprimé aussitôt (les courses) ; `due_dates`, les éléments peuvent porter une échéance ; ce qu'un élément porte d'autre au-delà de son nom (une quantité) est un champ personnalisé
**Données** : Une entrée par élément : son nom, dans `data` son échéance `due_at` (déclarée seulement avec `due_dates`), et dans `state` sa position (`ManualOrder`), `checked_at`, l'instant où il a été coché, absent sinon — décocher l'efface —, et `due_notified`, l'échéance déjà notifiée. L'écran montre les non cochés dans l'ordre manuel, réordonnés en glissant, puis, sous un trait, les cochés dans l'ordre où ils l'ont été ; « Tout décocher » agit en un lot. La tuile se coche sans ouvrir l'outil (`ListTile`)
**Échéances** : une chose à faire une fois avant un moment (un rythme fixe relève de Messages). `ListDueScheduler` notifie une fois chaque échéance passée d'un élément non coché et la marque dans `due_notified` ; un élément marqué et non coché attend (le point), les conditions d'attente ne connaissant pas l'heure ; `DueNotice` efface la marque quand l'échéance qu'elle nomme change. L'écran montre l'échéance sous le nom, « En retard » une fois passée ; la tuile met les éléments en retard en tête et les compte

### Objectif (Goal)
**Usage** : Un objectif jugé par comptage de ses critères, une tentative par période
**Configuration** : des critères et des sous-objectifs (deux niveaux), « au moins N » (tous par défaut) et « indispensable » ; un critère est une valeur — une variable, un champ d'un outil réduit sur la période de la tentative (choisi parmi ses champs, `SettingNode.Field.fieldOf`), ou saisie (oui/non, nombre, durée, échelle) avec « comment le remplir » — et une condition ; sa clé est donnée à sa création et gardée ensuite (`completeConfig`) : le renommer garde ses valeurs, le supprimer puis le recréer en fait un autre. Ouverture ponctuelle (`start`, `deadline`) ou récurrente (`schedule`), `duration`, `enabled`, `expiry_delay` (7 jours), `notify`
**Données** : une entrée par tentative, datée du début de sa période : `data.definition` (la copie qui l'a jugée), un champ par critère saisi ; `state` : `status` (active, to_validate, succeeded, failed, expired), `period_end`, et une fois validée `judgement` (valeurs et verdict figés), `validated_by` (l'origine de l'appel : une personne ou l'IA, jamais un planificateur), `validated_at`, `reopened_at`. Une tentative ouverte suit la définition courante ; une tentative validée ou expirée garde sa copie et refuse toute écriture (`refuseChange`), sauf `goal.reopen`. `GoalService` : `evaluate`, `validate` (refusé tant qu'une valeur manque), `reopen` ; `GoalScheduler` : à valider à la fin de la période (une notification), expirée après le délai, la tentative de la dernière occurrence venue ouverte sans rattraper les autres. L'écran : la tentative en cours, ses critères face à leur condition, saisis sur place, le compte, le temps restant et « Valider » ; les tentatives à valider ; l'historique en pastilles, chaque tentative relue figée avec « Rouvrir ». La tuile : le compte et le verdict en cours, les dernières pastilles, le nombre à valider

### Questionnaire (Questionnaire)
**Usage** : Des questions posées une par écran, à la demande ou à heures prévues, par l'utilisateur ou avec l'IA
**Configuration** : les questions sont les champs de l'utilisateur (`extra_fields`) ; `schedule` (les invitations), `enabled`, `ai_message` (« Message à l'IA », prérempli d'une consigne modifiable)
**Données** : une entrée par passation, datée du moment qu'elle décrit, sans nom ; `state.status` (to_fill, filled, ignored), `state.filled_at`. `QuestionnaireScheduler` crée à chaque heure prévue l'entrée « à remplir », les heures venues pendant une absence comprises (60 au plus, celles après la dernière déjà créée), et une notification. `QuestionnaireService` : `complete`, `ignore`, `ignore_all`. L'écran : « Remplir maintenant » (rien n'est écrit avant la fin), « Avec l'IA » (un chat ouvert avec le message et les pointeurs vers l'outil et l'entrée, `ChatRequests`), « À remplir (n) » avec « Tout ignorer », l'historique où une entrée se relit et se modifie ; la passation d'une entrée prévue enregistre chaque réponse et reprend à la première sans réponse. Le titre d'une entrée, nom et moment en relatif, se calcule à l'affichage. La tuile : le nombre à remplir, sinon la dernière réponse

### Données structurées (Structured)
**Usage** : Des fiches faites des champs de l'utilisateur, chacune retrouvée par son nom (les aliments et leurs calories, que lit une RÉFÉRENCE d'un repas)
**Configuration** : les colonnes sont les champs de l'utilisateur (`extra_fields`) ; `table_columns`, combien d'entre eux le tableau montre après le nom (2 par défaut)
**Données** : Une entrée par fiche : son nom, obligatoire et unique dans l'outil sans compter la casse ni les espaces autour (`EntryFields.nameUnique` : `ToolDataService` refuse un doublon dans sa transaction en nommant la fiche existante, un filtre sur `name` compare de même), pas de date, `data` vide. L'écran (`StructuredScreen`) : un tableau trié au toucher d'un en-tête, l'écran d'une fiche où un glissement mène aux voisines, un en-tête de filtre commun aux deux (recherche, filtres, tri) replié en une ligne qui les résume avec la position, gardé le temps de la visite ; une fiche se modifie entière et s'écrit en une fois, une nouvelle rien avant « Enregistrer ». La tuile compte les fiches

### Note (Note)
**Usage** : Titre et contenu libre
**Configuration** : Template, catégories

### Message (Message)
**Usage** : Un message de notification et ses envois
**Configuration** : Titre et corps communs, priorité, récurrence, jours d'avance de création, fenêtre de validité
**Données** : Une entrée par envoi (`pending`, `sent`, `expired`, `cancelled`), portant la part écrite pour ce jour-là et, une fois parti, la part commune recopiée

### Alerte (Alert)
**Usage** : Déclenchement automatique sur seuils
**Configuration** : Source de données, conditions, actions

## Display Modes pour Tool Cards

Les outils d'une section de groupe sont posés sur une grille de quatre colonnes à cases carrées (`ToolGrid`), dont le thème donne le côté selon la largeur disponible (`gridCellPx` : un quart, la grille large au plus de 480 dp, dans le thème par défaut). Chaque outil y tient à `grid_x`/`grid_y` (`tool_instances`), placé par `ToolPositions` et `Grid` ; sa taille en cases vient de son `display_mode`, que sa config porte toujours :

- **ICON** (1×1) : l'icône seule
- **MINIMAL** (2×1) : l'en-tête, icône et nom
- **LINE** (4×1) : l'en-tête à gauche, le résumé (ou l'aperçu) à droite
- **CONDENSED** (2×2) : l'en-tête en haut, le résumé (ou l'aperçu) dessous
- **EXTENDED** (4×2) : l'en-tête et le résumé en haut, une rangée de corps
- **SQUARE** (4×4) : l'en-tête et le résumé en haut, trois rangées de corps
- **FULL** (4 × sa hauteur) : l'en-tête et le résumé en haut, le corps aussi haut que son contenu, arrondi à la case

Une case fait deux lignes, et toute hauteur dans une tuile se compte en lignes. Seules ICON, MINIMAL et CONDENSED partagent une ligne de la grille. Rien ne défile dans une tuile : ce qui ne tient pas est l'affaire du type d'outil. Un élément tactile de la tuile fait son action, un toucher ailleurs ouvre l'outil (ou l'entrée qui attend), l'appui long sa config ; en édition, un toucher sélectionne. L'IA ne voit ni ne change les positions ; changer le mode d'un outil par sa config fait descendre ce que sa tuile recouvre.

## Validation JSON Schema V3

Validation unifiée pour tous les types d'outils via SchemaValidator.

### API Standard
- Récupération ToolType via ToolTypeManager.getToolType()
- Schéma des entrées d'un outil : `BaseSchemas.getEntrySchema` (champs de l'utilisateur compris) ; schéma de config : `ToolConfigSettings.schema`
- Les services valident toute écriture (`ToolDataService`, `ToolInstanceService`)
- Gestion résultat : isValid et errorMessage traduit automatiquement

### Validation Service Pattern
Service execute() valide automatiquement via ToolType puis retourne OperationResult.error() si validation échoue.

### Configuration Screen Pattern
- States pour champs de formulaire
- Validation temps réel optionnelle avec remember(dépendances)
- Toast automatique pour erreurs via LaunchedEffect
- FormActions avec bouton SAVE enabled selon validation

## BaseSchemas et Configuration

### Réglages communs (`ToolConfigSettings`)
- **name** : Nom de l'instance
- **description** : Description
- **management** : Mode de gestion (AI/USER/HYBRID)
- **display_mode** : Mode d'affichage (ICON/MINIMAL/LINE/etc.)
- **validateConfig** : Boolean - Requiert validation utilisateur avant modification configuration (default: false)
- **validateData** : Boolean - Requiert validation utilisateur avant modification données (default: false)

### Champ always_send (Level 2 AI)
```kotlin
"always_send": {
    "type": "boolean",
    "default": false,
    "description": "Toujours envoyer les données à l'IA (Level 2)"
}
```

**Usage** : Si `always_send = true`, les données de cette tool instance sont incluses systématiquement en Level 2 des prompts IA pour contexte permanent.

**Interface UI** : réglage commun, déclaré dans `ToolConfigSettings`.

## Patterns de Parsing Robuste

### les services rendent des objets
`tool_data.*` rend `data` et `custom_fields` en `Map`, et `tools.*` prend et rend `config` en objet — jamais en chaîne. La sérialisation JSON ne vit qu'au bord de la base, où `tool_instances.config_json` garde son nom parce que la colonne, elle, contient bien une chaîne. Un appelant lit ses clés directement, sans parsing ni try/catch.

### LaunchedEffect avec Dépendances Complètes
Inclure TOUTES les variables vérifiées dans le scope comme dépendances pour éviter les états obsolètes.

## Règles d'Extension

### Discovery Pure
- Aucun import hardcodé dans Core
- Enregistrement automatique via ToolTypeScanner
- Service et DAO découverts dynamiquement

### Consistency Patterns
- Validation unifiée via SchemaValidator
- Configuration JSON avec schéma
- Event sourcing pour toutes modifications

### Interface Contracts
- ToolTypeContract déclare ses champs et réglages ; les schémas en sont générés
- ExecutableService pour logique métier
- SchemaValidator pour validation UI/Service

---

*L'architecture d'outils garantit extensibilité sans modification du Core et cohérence des patterns.*
