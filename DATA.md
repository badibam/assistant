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
## Validation Centralisée

### SchemaValidator V3

Architecture centralisée de validation basée sur JSON Schema avec traduction automatique.

```kotlin
// API Unifiée
val toolType = ToolTypeManager.getToolType("tracking")
val result = SchemaValidator.validate(toolType, data, context, schemaType = "data")

if (result.isValid) {
    // Validation réussie
} else {
    // Erreur traduite en français : result.errorMessage
}
```

#### Interface SchemaProvider

Tous les ToolTypes implémentent SchemaProvider :

```kotlin
interface SchemaProvider {
    fun getConfigSchema(): String        // Schéma configuration outil
    fun getDataSchema(): String?         // Schéma données métier
    fun getFormFieldName(String): String // Traductions champs
}
```

#### Types de Validation

```kotlin
// Validation configuration outil (création/modification)
SchemaValidator.validate(toolType, configData, context, schemaType = "config")

// Validation données métier (entries)
SchemaValidator.validate(toolType, entryData, context, schemaType = "data")
```

#### Fonctionnalités Automatiques

- **Filtrage valeurs vides** : Supprime `""` et `null` avant validation
- **Traduction erreurs** : Messages français avec noms traduits
- **Schémas conditionnels** : Support `allOf/if/then` natif
- **Cache performance** : Schémas mis en cache automatiquement

## ═══════════════════════════════════
## Event Sourcing

Toutes les modifications passent par des événements pour garantir cohérence et traçabilité.

### Avantages

- Logging automatique des modifications possible
- Cohérence sans synchronisation manuelle
- Historique pour IA et audit

### Schémas JSON Auto-descriptifs

```kotlin
// Validation et navigation IA sans accès aux données
val schema = toolType.getConfigSchema()
val validation = toolType.validateData(data, operation)
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

### Service Implementation

- Hériter `ExecutableService`
- Validation via `SchemaValidator.validate(toolType, data, context, useDataSchema)`
- Logs d'erreur explicites
- Gestion token cancellation

### Discovery Pattern

- Jamais d'imports hardcodés dans Core
- Services découverts via ToolTypeManager
- Extension automatique par ajout au Scanner

### Data Consistency

- Event sourcing obligatoire pour modifications
- **Validation centralisée** : SchemaValidator pour config/data
- Schémas JSON pour validation automatique
- Standalone databases pour discovery

---

*L'architecture de données garantit cohérence, validation automatique et extensibilité via patterns découplés.*