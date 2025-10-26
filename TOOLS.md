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

## Architecture Outil

### Structure Physique
Dossier tools/[type]/ contient :
- ToolType.kt (contrat et métadonnées)
- Service.kt (logique métier)
- Dao.kt (accès données)
- Data.kt (entité base)
- ui/ (ConfigScreen et DisplayComponent)

### Interface ToolTypeContract
Interface principale implémentant SchemaProvider avec méthodes pour :
- **Métadonnées** : getDisplayName(), getDescription(), getSuggestedIcons(), getDefaultConfig(), getAvailableOperations()
- **Schémas** : getAllSchemaIds(), getSchema(schemaId, context) via SchemaProvider
- **Interface utilisateur** : getConfigScreen() @Composable
- **Discovery pattern** : getService(), getDao(), getDatabaseEntities(), getDatabaseMigrations(), getScheduler()
- **Enrichissement** : enrichData() (défaut identity, enrichissement automatique avant persistence)
- **Validation** : validateData() (délègue à SchemaValidator)

## Méthodologie d'Implémentation

**Règle d'or** : Toujours copier-coller Tracking d'abord, adapter ensuite.

### Ordre d'Implémentation
1. **ToolType** avec configuration complète par défaut
2. **Schémas externes** (MyToolSchemas.kt)
3. **ConfigScreen** avec ToolGeneralConfigSection
4. **Service** avec validation stricte
5. **UI screens** avec parsing robuste
6. **Enregistrement** dans ToolTypeScanner

### Points de Vérification Critiques
- API SchemaValidator : schemaType = "config|data"
- Services : tools.* utilise tool_instance_id, tool_data.* utilise toolInstanceId
- LaunchedEffect : toutes variables vérifiées dans le scope = dépendances
- ToolGeneralConfigSection : 7 champs obligatoires
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
- getDisplayName(), getDescription(), getDefaultConfig()
- getAllSchemaIds(), getSchema(schemaId, context)
- getConfigScreen() @Composable
- getService(), getDao(), getDatabaseEntities()

### enrichData Pattern
**Principe** : Enrichissement automatique des données avant persistence (appelé par ToolDataService pour toutes les entrées).

```kotlin
override fun enrichData(data: Map<String, Any>, context: Context): Map<String, Any> {
    // Calculs, validations, enrichissements automatiques
    // Exemple Messages : schedule config → nextExecutionTime
    return enrichedData
}
```

**Usage** : Unifié UI + IA, logique pré-persistence sans interception manuelle.

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
**Configuration** : Type de valeur (numeric, text, scale, choice, timer), unité, fréquence, items prédéfinis
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
**Usage** : Items à cocher thématiques
**Configuration** : Items prédéfinis, ajout dynamique

### Note (Note)
**Usage** : Titre et contenu libre
**Configuration** : Template, catégories

### Message (Message)
**Usage** : Notifications et rappels planifiés
**Configuration** : Fréquence, contenu, conditions

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
- Validation données métier avec schemaType = "data"
- Validation configuration avec schemaType = "config"
- Gestion résultat : isValid et errorMessage traduit automatiquement

### Validation Service Pattern
Service execute() valide automatiquement via ToolType puis retourne OperationResult.error() si validation échoue.

### Configuration Screen Pattern
- States pour champs de formulaire
- Validation temps réel optionnelle avec remember(dépendances)
- Toast automatique pour erreurs via LaunchedEffect
- FormActions avec bouton SAVE enabled selon validation

## Schema Provider Pattern

### Relation ToolType ↔ SchemaProvider ↔ Schema IDs
ToolTypeContract étend SchemaProvider pour accès aux schémas via IDs.

**ToolTypeManager.getSchemaIdsForTooltype()**
```kotlin
// Récupère tous les schema IDs d'un tooltype via SchemaProvider
val schemaIds = ToolTypeManager.getSchemaIdsForTooltype("tracking")
// Retourne: ["tracking_config_numeric", "tracking_data_numeric", ...]
```

**Règle importante** : Pas de présomption de patterns de noms. Utiliser SchemaProvider.getAllSchemaIds() pour découverte.

## BaseSchemas et Configuration

### Champs Obligatoires Configuration
- **schema_id** : ID du schéma de validation config
- **data_schema_id** : ID du schéma de validation data
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

**Interface UI** : Toggle dans ToolGeneralConfigSection (8 champs obligatoires total).

## Patterns de Parsing Robuste

### entity.data peut être String ou Map
Parsing robuste avec try/catch :
- Si Map : cast direct
- Si String : conversion via JSONObject
- Sinon : emptyMap()
- Log d'erreur si exception

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
- ToolTypeContract étend SchemaProvider pour validation unifiée
- ExecutableService pour logique métier
- SchemaValidator pour validation UI/Service

---

*L'architecture d'outils garantit extensibilité sans modification du Core et cohérence des patterns.*
