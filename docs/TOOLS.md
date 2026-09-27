# Outils et Extensibilité

Guide pour comprendre et créer des outils dans l'architecture modulaire.

## Concepts Fondamentaux

### Tool Type vs Tool Instance
- **Tool Type** : Métadonnées statiques et comportements (ex: TrackingToolType)
- **Tool Instance** : Configuration spécifique dans une zone (ex: "Poids quotidien")

### Une Instance = Un Concept
- **Suivi** : 1 métrique spécifique (poids, humeur, etc.)
- **Objectif** : 1 objectif avec sous-objectifs et critères
- **Graphique** : 1 groupe de visualisations cohérentes
- **Liste** : 1 liste thématique (courses, tâches)
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
- **Métadonnées** : getDisplayName(), getDescription(), getSuggestedIcons(), getDefaultIconName(), getDefaultDisplayMode()
- **Déclarations** : getEntryFields() (champs des entrées), getConfigSettings() (réglages propres, à côté de la partie commune `ToolConfigSettings`) ; schémas, config par défaut et lecture en sont générés ; configWithOptionsAdded() pour un type qui déclare un choix ouvert dans `data`
- **Interface utilisateur** : getUsageScreen() @Composable ; TileContent() @Composable, ce que montre la tuile de l'outil sur une zone à côté de son en-tête (la moitié droite d'une tuile LINE, le dessous des plus grandes ; par défaut, le nom du type en LINE et rien ailleurs) ; l'écran de config est généré depuis la déclaration (`ToolConfigScreen`, `SettingsForm`), et getConfigEditors() y branche les parties qu'un type dessine lui-même, par nom de réglage (la planification de Messages)
- **Discovery pattern** : getService(), getDao(), getDatabaseEntities(), getDatabaseMigrations(), getScheduler()
- **Enrichissement** : enrichData() (défaut identity, enrichissement automatique avant persistence)
- **Règle entre entrées** : settleEntries() (défaut : rien à changer), voir plus bas
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
- getUsageScreen() @Composable, getConfigEditors() si besoin
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
**Principe** : une règle qui porte sur toutes les entrées d'une instance, et non sur une seule. `ToolDataService` l'appelle à chaque création, modification ou suppression d'une entrée du tooltype, avec toutes les entrées de l'instance telles qu'elles seront après l'écriture, et l'id de l'entrée écrite (null après une suppression). Il enregistre les entrées renvoyées dans la même transaction que l'écriture. Unifié UI + IA, comme enrichData.

**Ordre manuel** : `ManualOrder` (cœur) garde les positions 0, 1, 2… sans trou ni doublon, sous `state.position`, pour un type d'outil dont l'utilisateur ordonne les entrées à la main (Notes, Liste). Écrire la position p place l'entrée en p et décale les suivantes ; une entrée sans position va en dernier.

### Tooltypes passifs et actifs

**Principe** : il n'y a que deux plans de données. La **config** est ce que l'outil *est* (sa définition : template, formule, planning) ; **tool_data** est tout ce qu'il *enregistre ou produit* (saisies, occurrences, résultats). Pas de troisième plan — l'audit technique des échecs relève du système de logs.

**Passifs** (Tracking, Journal, Note) : l'utilisateur écrit dans tool_data.

**Actifs** (Messages, futurs Calcul, Alertes, Objectifs) : le système écrit dans tool_data, piloté par la config. Un tooltype actif expose une opération `execute` (`{tooltype}.execute` avec `tool_instance_id`) et généralement un `getScheduler()`. Ses occurrences sont des entrées tool_data ordinaires : requêtables, statistiquables, migrables, visibles par l'IA, comme n'importe quelle autre entrée.

**Occurrences à cycle de vie** : une occurrence n'est pas forcément instantanée. Elle peut vivre (créée → active → close), auquel cas son schéma data porte un champ `status` et ses exigences en dépendent. Le filtre `status` de `tool_data.get` existe pour ces tooltypes-là.

### Enregistrement
Ajout dans ToolTypeScanner.getAllToolTypes() pour discovery automatique.

## Exemples de Flows Complets

### Flow Nutritionnel
1. SUIVI alimentaire (manuel) → saisie repas
2. DONNÉES STRUCTURÉES nutrition (IA) → référentiel aliments + AJR
3. SUIVI nutritionnel (IA + #1 + #2) → calculs automatiques
4. JOURNAL (IA, basé sur #3) → rapports quotidiens
5. CALCUL (App, basé sur #3) → moyennes périodiques
6. GRAPHIQUE (App, basé sur #5) → visualisations vs AJR
7. ALERTES (App, critères sur #3) → carences/excès détectées

### Configuration par l'IA
**Principe** : L'IA configure et gère automatiquement les outils complexes via commandes JSON.
- **Utilisateur** : Définit l'objectif ("suivre ma nutrition")
- **IA** : Crée la chaîne d'outils, configure les calculs, définit les alertes

## Types d'Outils Disponibles

### Suivi (Tracking)
**Usage** : Données temporelles quantitatives/qualitatives
**Configuration** : Type de valeur (numeric, counter, text, scale, choice, timer…), unités (numérique : la liste où chaque entrée prend la sienne, une unité nouvelle saisie la rejoint ; compteur : une unité fixe), items prédéfinis
**Exemples** : Poids, humeur échelle 1-10, alimentation libre

### Objectif (Goal)
**Usage** : Critères de réussite avec poids relatifs
**Structure** : 3 niveaux - objectif → sous-objectifs → items
**Validation** : Confirmation obligatoire avant finalisation

### Graphique (Chart)
**Usage** : Visualisations basées sur données existantes
**Configuration** : Sources de données, type de graphique, période

### Journal (Journal)
**Usage** : Entrées textuelles/audio libres avec dates
**Configuration** : Template d'entrée, fréquence suggérée

### Liste (List)
**Usage** : L'état présent de ce qui reste à faire (courses, tâches, check-list), sans historique : ce qui a été fait et quand relève d'un suivi « occurrence »
**Configuration** : Aucun réglage propre ; ce qu'un élément porte au-delà de son nom (quantité, échéance) est un champ personnalisé
**Données** : Une entrée par élément : son nom, et dans `state` sa position (`ManualOrder`) et `checked_at`, l'instant où il a été coché, absent sinon ; décocher l'efface. L'écran montre les non cochés dans l'ordre manuel, réordonnés en glissant, puis les cochés grisés dans l'ordre où ils l'ont été ; « Tout décocher » et « Retirer les cochés » agissent en un lot. La tuile se coche sans ouvrir l'outil (`ListTile`)

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

- **ICON** (1/4×1/4) : icône seule
- **MINIMAL** (1/2×1/4) : icône + titre côte à côte
- **LINE** (1×1/4) : icône + titre gauche, contenu libre droite
- **CONDENSED** (1/2×1/2) : icône + titre haut, zone libre dessous
- **EXTENDED** (1×1/2) : icône + titre haut, zone libre dessous
- **SQUARE** (1×1) : icône + titre haut, grande zone libre
- **FULL** (1×∞) : icône + titre haut, zone libre infinie

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
