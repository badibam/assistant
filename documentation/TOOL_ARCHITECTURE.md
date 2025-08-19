# Architecture des Outils

## Concepts

- **Tool Type** : Métadonnées statiques (ex: TrackingToolType)
- **Tool Instance** : Configuration spécifique dans une zone

## Structure

```
core/tools/
├── base/ToolTypeContract.kt     # Interface commune
├── ToolTypeManager.kt           # API unifiée  
└── ToolTypeScanner.kt           # Découverte automatique

tools/tracking/
└── TrackingToolType.kt          # Implémentation
```

## Interface ToolTypeContract

```kotlin
interface ToolTypeContract {
    fun getDisplayName(): String
    fun getDefaultConfig(): String  
    fun getConfigSchema(): String
    fun getAvailableOperations(): List<String>
}
```

## Utilisation

```kotlin
// Récupérer un tool type
val toolType = ToolTypeManager.getToolType("tracking")

// Nom d'affichage
val name = ToolTypeManager.getToolTypeName("tracking") // "Suivi"

// Tous les types disponibles
val allTypes = ToolTypeManager.getAllToolTypes()
```

## Ajouter un nouveau tool type

1. **Créer l'objet** :
```kotlin
object MonToolType : ToolTypeContract {
    override fun getDisplayName() = "Mon Outil"
    // ... autres méthodes
}
```

2. **L'enregistrer** dans `ToolTypeScanner` :
```kotlin
fun scanForToolTypes() = mapOf(
    "tracking" to TrackingToolType,
    "mon-outil" to MonToolType  // ← Ajouter ici
)
```

## Flux

1. Scanner découvre les tool types → ToolTypeManager
2. UI utilise ToolTypeManager pour lister/configurer  
3. Instances sauvées comme ToolInstance (Room)
4. Affichage via métadonnées du tool type