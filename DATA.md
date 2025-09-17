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

Sélecteur hiérarchique pour navigation Zone → Tool Instance → Data Fields avec configuration flexible.

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
    val allowInstanceSelection: Boolean = true,     // Peut-on s'arrêter aux instances ?
    val allowFieldSelection: Boolean = true,        // Peut-on aller jusqu'aux champs ?
    val title: String = "",                         // Titre custom ou utilise scope_selector_title
    val showQueryPreview: Boolean = false,          // Afficher preview SQL ?
    val showFieldSpecificSelectors: Boolean = true, // Timestamp/Name selectors ?
    val requireCompleteSelection: Boolean = false   // Forcer sélection jusqu'au bout ?
)
```

#### Cas d'usage

```kotlin
// Sélection zones seulement
NavigationConfig(allowInstanceSelection = false, allowFieldSelection = false)

// Navigation complète pour graphiques
NavigationConfig(allowInstanceSelection = true, allowFieldSelection = true, showQueryPreview = true)

// Sélection d'outils uniquement
NavigationConfig(allowInstanceSelection = true, allowFieldSelection = false)
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