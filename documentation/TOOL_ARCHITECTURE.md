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
    fun getConfigScreen(zoneId: String, onSave: (String) -> Unit, onCancel: () -> Unit)
    
    // Discovery pattern
    fun getService(context: Context): ExecutableService?  // TrackingService (implémente ExecutableService)
    fun getDao(context: Context): Any?                    // TrackingDao  
    fun getDatabaseEntities(): List<Class<*>>             // TrackingData
}
```

## Interface ExecutableService

Tous les services découverts doivent implémenter cette interface :

```kotlin
interface ExecutableService {
    suspend fun execute(
        operation: String, 
        params: JSONObject, 
        token: CancellationToken
    ): OperationResult
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

// Discovery service/DAO
val service = ToolTypeManager.getServiceForToolType("tracking", context)
val dao = ToolTypeManager.getDaoForToolType("tracking", context)

// Nom d'affichage
val name = ToolTypeManager.getToolTypeName("tracking") // "Suivi"

// Tous les types disponibles
val allTypes = ToolTypeManager.getAllToolTypes()
```

## Ajouter un nouveau tool type

1. **Créer le service** :
```kotlin
class MonService(context: Context) : ExecutableService {
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        // Implémentation du service
    }
}
```

2. **Créer l'objet ToolType avec discovery** :
```kotlin
object MonToolType : ToolTypeContract {
    override fun getDisplayName() = "Mon Outil"
    override fun getService(context: Context): ExecutableService = MonService(context)
    override fun getDao(context: Context) = MonDatabase.getDatabase(context).monDao()
    override fun getDatabaseEntities() = listOf(MonData::class.java)
}
```

3. **Créer database standalone** :
```kotlin
@Database(entities = [MonData::class], version = 1)
abstract class MonDatabase : RoomDatabase()
```

4. **L'enregistrer dans ToolTypeScanner** :
```kotlin
fun scanForToolTypes() = mapOf(
    "tracking" to TrackingToolType,
    "mon-outil" to MonToolType  // ← Ajouter ici
)
```

## Discovery et Databases

- **Discovery flow complet** : voir DISCOVERY_ARCHITECTURE.md
- **Databases standalone** : 1 database par tool type pour isolation
