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
    
    @Composable
    fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit
    )
}
```

## Configuration des Outils

**Chaque outil fournit son écran de configuration via `getConfigScreen()`.**

**Champs communs recommandés :**
- Nom (obligatoire)
- Description 
- Mode de gestion (Manuel/IA/Collaboratif)
- Mode d'affichage (Minimal/Condensé/Détaillé)

**Organisation des écrans :**
- Chaque outil a son dossier : `tools/[outil]/ui/ConfigScreen.kt`
- Interface custom pour flexibilité maximale
- Callbacks `onSave`/`onCancel` centralisés dans ZoneScreen

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
