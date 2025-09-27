# Gestion des Données

Guide technique pour la navigation, validation et manipulation des données dans l'architecture Assistant.

## ═══════════════════════════════════
## Navigation Hiérarchique

### DataNavigator

Architecture pour navigation dans les données via schémas avec chargement à la demande et résolution conditionnelle.

```kotlin
// DataNavigator.kt
class DataNavigator(private val context: Context) {
    suspend fun getRootNodes(): List<SchemaNode>
    suspend fun getChildren(path: String): List<SchemaNode>
    suspend fun getDistinctValues(path: String): ContextualDataResult
}
```

**Structure** : App → Zones → Outils → Champs avec navigation à la demande.

**Usage** : Permet de naviguer dans la structure des données sans accès direct aux données métier.

### ZoneScopeSelector

Sélecteur hiérarchique pour navigation Zone → Tool Instance → Data Fields avec configuration flexible et gestion temporelle.

```kotlin
@Composable
fun ZoneScopeSelector(
    config: NavigationConfig,
    onDismiss: () -> Unit,
    onConfirm: (SelectionResult) -> Unit
)
```

#### NavigationConfig

```kotlin
data class NavigationConfig(
    val allowZoneSelection: Boolean = true,         // Peut-on CONFIRMER aux zones ?
    val allowInstanceSelection: Boolean = true,     // Peut-on CONFIRMER aux instances ?
    val allowFieldSelection: Boolean = true,        // Peut-on CONFIRMER aux champs ?
    val allowValueSelection: Boolean = true,        // Afficher les sélecteurs de valeurs ?
    val title: String = "",                         // Titre custom ou utilise scope_selector_title
    val showQueryPreview: Boolean = false,          // Afficher preview SQL ?
    val showFieldSpecificSelectors: Boolean = true  // Timestamp/Name selectors ?
)
```

#### Intégration Temporelle

**Paramètre `useOnlyRelativeLabels`** propagé vers PeriodRangeSelector :
- **Chat context** : Labels absolus ("Semaine du 15 mars") pour navigation claire
- **Automation context** : Labels relatifs ("il y a 3 semaines") pour cohérence

**Gestion période de fin** : Les sélections de période dans ZoneScopeSelector utilisent automatiquement les timestamps de fin appropriés via `Period.getEndTimestamp()`.

### Cas d'usage

```kotlin
// Sélection zones seulement
NavigationConfig(
    allowZoneSelection = true,
    allowInstanceSelection = false,
    allowFieldSelection = false,
    allowValueSelection = false
)

// Navigation complète pour graphiques avec sélection de valeurs
NavigationConfig(
    allowZoneSelection = true,
    allowInstanceSelection = true,
    allowFieldSelection = true,
    allowValueSelection = true,
    showQueryPreview = true
)

// Sélection d'outils uniquement (passage obligé par zones, arrêt possible aux instances)
NavigationConfig(
    allowZoneSelection = false,
    allowInstanceSelection = true,
    allowFieldSelection = false,
    allowValueSelection = false
)

// Doit aller jusqu'aux champs et DOIT s'arrêter là (pas de sélecteurs de valeurs)
NavigationConfig(
    allowZoneSelection = false,
    allowInstanceSelection = false,
    allowFieldSelection = true,
    allowValueSelection = false
)

// Navigation jusqu'aux champs, PEUT continuer vers les valeurs (sélecteurs affichés)
NavigationConfig(
    allowZoneSelection = false,
    allowInstanceSelection = false,
    allowFieldSelection = true,
    allowValueSelection = true
)
```

#### SelectionResult

```kotlin
data class SelectionResult(
    val selectedPath: String,                       // Chemin complet sélectionné
    val selectedValues: List<String>,               // Valeurs sélectionnées
    val selectionLevel: SelectionLevel,             // Niveau d'arrêt
    val fieldSpecificData: FieldSpecificData? = null // Données spécialisées
)
```

**Niveaux de sélection** : ZONE, INSTANCE, FIELD

**Données spécialisées** :
- TimestampData : Plages temporelles avec min/max timestamps
- NameData : Sélection de noms d'entrées disponibles
- DataValues : Valeurs de champs data génériques

## ═══════════════════════════════════
## Validation par Schema ID

### Architecture Schema

Système de validation basé sur identifiants de schémas explicites avec objets Schema autonomes.

```kotlin
data class Schema(
    val id: String,         // "tracking_config_numeric"
    val content: String     // JSON Schema complet
)
```

### Patterns de Validation

#### Configuration d'outils

Le `schema_id` est intégré dans les données de configuration au même niveau que les champs métier :

```kotlin
val configData = mapOf(
    "schema_id" to "tracking_config_numeric",      // Pour validation
    "data_schema_id" to "tracking_data_numeric",   // Pour usage runtime
    "name" to "Mon suivi",
    "type" to "numeric"
)

// Validation via helper unifié
UI.ValidationHelper.validateAndSave(
    toolTypeName = "tracking",
    configData = configData,
    context = context,
    schemaType = "config",
    onSuccess = { configJson -> /* sauvegarde */ }
)
```

#### Données d'entrée

Le `schema_id` est passé au niveau des paramètres de service, séparé du JSON des données :

```kotlin
val params = mapOf(
    "toolInstanceId" to toolInstanceId,
    "tooltype" to "tracking",
    "schema_id" to "tracking_data_numeric",   // Pour validation service
    "data" to JSONObject(dataJson)            // JSON propre sans schema_id
)

coordinator.processUserAction("tool_data.create", params)
```

### Validation Service

ToolDataService récupère le `schema_id` depuis les paramètres et l'ajoute à la structure de validation :

```kotlin
val schemaId = params.optString("schema_id")
if (schemaId.isNotEmpty()) {
    fullDataMap["schema_id"] = schemaId  // Ajout au niveau racine
}
val schema = toolType.getSchema(schemaId, context)
SchemaValidator.validate(schema, fullDataMap, context)
```

### Schémas de Base

BaseSchemas définit les champs communs incluant les identifiants de schémas :

**Configuration** : `schema_id`, `data_schema_id`, `name`, `description`, `management`, `display_mode`, etc.

**Données** : `schema_id`, `tool_instance_id`, `tooltype`, `name`, `timestamp`, `created_at`, etc.

### ValidationHelper

API centralisée pour validation de configuration avec extraction automatique du schema_id :

```kotlin
object ValidationHelper {
    fun validateAndSave(
        toolTypeName: String,
        configData: Map<String, Any>,
        context: Context,
        schemaType: String,
        onSuccess: (String) -> Unit
    ): Boolean
}
```

## ═══════════════════════════════════
## Event Sourcing

Toutes les modifications passent par des événements pour garantir cohérence et traçabilité.

### Avantages

- Logging automatique des modifications possible
- Cohérence sans synchronisation manuelle
- Historique pour IA et audit

### Schémas Auto-descriptifs

```kotlin
// Récupération et validation via schema ID
val schema = toolType.getSchema(schemaId, context)
val validation = SchemaValidator.validate(schema, data, context)
```

### Databases Standalone

**Structure** : 1 database par tool type pour discovery pure
- TrackingDatabase → TrackingData
- JournalDatabase → JournalData
- Pas de foreign keys, indices de performance seulement

### Verbalisation

Système de templates pour actions, états et résultats :

```kotlin
// Template
"[source] [verb] le titre [old_value] en [new_value]"

// Résultat
"L'IA a modifié le titre Blup en Blip"
```

**Usage** : Historique, validation utilisateur, feedback IA.

## ═══════════════════════════════════
## Règles de Développement

### Patterns d'Implémentation

#### Service Implementation
- Hériter `ExecutableService`
- **Configs** : utiliser `UI.ValidationHelper.validateAndSave()` avec schema_id dans les données
- **Données** : ajouter `schema_id` aux paramètres service, récupérer via `params.optString("schema_id")`
- Validation directe via `SchemaValidator.validate(schema, data, context)`
- Logs d'erreur explicites et gestion token cancellation

#### Schema ID Management
- **Configuration** : `schema_id` et `data_schema_id` ajoutés automatiquement lors du nettoyage des données
- **Données d'entrée** : `schema_id` calculé selon pattern `${tooltype}_data_${type}` dans InputManager
- **Service validation** : schema_id ajouté à `fullDataMap` au niveau racine pour validation

#### Discovery Pattern
- Jamais d'imports hardcodés dans Core
- Services découverts via ToolTypeManager
- Extension automatique par ajout au Scanner
- ToolTypes implémentent `getSchema(schemaId, context): Schema?`

#### Data Consistency
- Event sourcing obligatoire pour modifications
- Validation centralisée via objets Schema explicites
- Schémas autonomes avec ID déterministes
- Standalone databases pour discovery

---

*L'architecture de données garantit cohérence, validation automatique et extensibilité via patterns découplés.*