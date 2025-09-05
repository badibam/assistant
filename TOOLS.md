# Outils et Extensibilité

Guide pour comprendre et créer des outils dans l'architecture modulaire.

## ═══════════════════════════════════
## Concepts Fondamentaux

### Tool Type vs Tool Instance

**Tool Type** : Métadonnées statiques et comportements (ex: TrackingToolType)
**Tool Instance** : Configuration spécifique dans une zone (ex: "Poids quotidien")

```
TrackingToolType (statique)
├── Suivi Poids (instance dans Zone Santé)
├── Suivi Humeur (instance dans Zone Bien-être)
└── Suivi Alimentation (instance dans Zone Nutrition)
```

### Une Instance = Un Concept

**Suivi** : 1 métrique spécifique (poids, humeur, etc.)
**Objectif** : 1 objectif avec sous-objectifs et critères  
**Graphique** : 1 groupe de visualisations cohérentes
**Liste** : 1 liste thématique (courses, tâches)
**Journal** : 1 type de journal (réflexions, rêves)
**Note** : 1 note individuelle
**Message** : 1 message/rappel planifié
**Alerte** : 1 règle d'alerte automatique

## ═══════════════════════════════════
## Architecture Outil

### Structure Physique

```
tools/tracking/
├── TrackingToolType.kt          # Contrat et métadonnées
├── TrackingService.kt           # Logique métier
├── TrackingDao.kt               # Accès données
├── TrackingData.kt              # Entité base
├── TrackingDatabase.kt          # Database standalone
└── ui/
    ├── TrackingConfigScreen.kt  # Configuration outil
    └── TrackingDisplayComponent.kt
```

### Interface ToolTypeContract

```kotlin
interface ToolTypeContract {
    // Métadonnées
    fun getDisplayName(): String
    fun getDefaultConfig(): String  
    fun getConfigSchema(): String
    fun getAvailableOperations(): List<String>
    
    // Interface utilisateur
    @Composable
    fun getConfigScreen(zoneId: String, onSave: (String) -> Unit, onCancel: () -> Unit)
    
    // Discovery pattern
    fun getService(context: Context): ExecutableService?
    fun getDao(context: Context): Any?
    fun getDatabaseEntities(): List<Class<*>>
    fun getDatabaseMigrations(): List<Migration>
    
    // Validation
    fun validateData(data: Any, operation: String): ValidationResult
}
```

## ═══════════════════════════════════
## Création d'un Nouvel Outil

### Étape 1: Structure de Base

```kotlin
// 1. Entité données
@Entity(tableName = "my_tool_data")
data class MyToolData(
    @PrimaryKey val id: String,
    val toolInstanceId: String,  // Lien vers tool_instances
    val timestamp: Long,
    val value: String,           // JSON pour flexibilité
    val metadata: String = "{}"
)

// 2. DAO
@Dao
interface MyToolDao {
    @Query("SELECT * FROM my_tool_data WHERE tool_instance_id = :toolInstanceId")
    suspend fun getDataForTool(toolInstanceId: String): List<MyToolData>
    
    @Insert
    suspend fun insertEntry(entry: MyToolData)
}

// 3. Database standalone
@Database(entities = [MyToolData::class], version = 1)
abstract class MyToolDatabase : RoomDatabase() {
    abstract fun myToolDao(): MyToolDao
}
```

### Étape 2: Service Métier

```kotlin
class MyToolService(private val context: Context) : ExecutableService {
    
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        // Validation via ToolType
        val toolType = ToolTypeManager.getToolType("my_tool")
        if (toolType != null) {
            val newData = MyToolData(...) 
            val validation = toolType.validateData(newData, operation)
            if (!validation.isValid) {
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }
        
        // Logique métier selon opération
        return when (operation) {
            "create" -> createEntry(params, token)
            "update" -> updateEntry(params, token)
            "delete" -> deleteEntry(params, token)
            else -> OperationResult.error("Unknown operation: $operation")
        }
    }
    
    private suspend fun createEntry(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val dao = ToolTypeManager.getDaoForToolType("my_tool", context) as MyToolDao
        // Logique création...
        dao.insertEntry(entry)
        
        return OperationResult.success()
    }
}
```

### Étape 3: ToolType Implementation

```kotlin
class MyToolType : ToolTypeContract {
    override fun getDisplayName(): String = "Mon Outil"
    
    override fun getDefaultConfig(): String = """
        {
            "type": "simple",
            "format": "text",
            "required_fields": ["value"]
        }
    """.trimIndent()
    
    override fun getConfigSchema(): String = """
        {
            "type": "object",
            "properties": {
                "type": {"type": "string", "enum": ["simple", "advanced"]},
                "format": {"type": "string", "enum": ["text", "number"]},
                "required_fields": {"type": "array", "items": {"type": "string"}}
            },
            "required": ["type", "format"]
        }
    """.trimIndent()
    
    @Composable
    override fun getConfigScreen(zoneId: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
        MyToolConfigScreen(zoneId = zoneId, onSave = onSave, onCancel = onCancel)
    }
    
    override fun getService(context: Context): ExecutableService = MyToolService(context)
    override fun getDao(context: Context): Any = MyToolDatabase.getDatabase(context).myToolDao()
    override fun getDatabaseEntities(): List<Class<*>> = listOf(MyToolData::class.java)
}
```

### Étape 4: Enregistrement

```kotlin
// Ajouter dans ToolTypeScanner
object ToolTypeScanner {
    fun getAllToolTypes(): Map<String, ToolTypeContract> {
        return mapOf(
            "tracking" to TrackingToolType(),
            "my_tool" to MyToolType(),  // Ajout ici
            // autres outils...
        )
    }
}
```

## ═══════════════════════════════════
## Exemples de Flows Complets

### Flow Nutritionnel

```
1. SUIVI alimentaire (manuel)
   ↓ saisie repas
2. DONNÉES STRUCTURÉES nutrition (IA)
   ↓ référentiel aliments + AJR
3. SUIVI nutritionnel (IA + #1 + #2)
   ↓ calculs automatiques
4. JOURNAL (IA, basé sur #3)
   ↓ rapports quotidiens
5. CALCUL (App, basé sur #3)
   ↓ moyennes périodiques
6. GRAPHIQUE (App, basé sur #5)
   ↓ visualisations vs AJR
7. ALERTES (App, critères sur #3)
   ↓ carences/excès détectées
```

### Configuration par l'IA

**Principe** : L'IA configure et gère automatiquement les outils complexes via commandes JSON.

**Utilisateur** : Définit l'objectif ("suivre ma nutrition")
**IA** : Crée la chaîne d'outils, configure les calculs, définit les alertes

## ═══════════════════════════════════
## Types d'Outils Disponibles

### Suivi (Tracking)
**Usage** : Données temporelles quantitatives/qualitatives
**Configuration** : Type de valeur, unité, fréquence, items prédéfinis
**Exemples** : Poids, humeur échelle 1-10, alimentation libre

```kotlin
// Configuration JSON
{
    "value_type": "numeric",     // numeric, text, scale, choice, timer
    "unit": "kg",
    "default_quantity": 1.0,
    "predefined_items": ["Pomme", "Banane"] // pour type choice
}
```

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

## ═══════════════════════════════════
## Display Modes pour Tool Cards

```kotlin
enum class DisplayMode {
    ICON,       // 1/4×1/4 - icône seule
    MINIMAL,    // 1/2×1/4 - icône + titre côte à côte  
    LINE,       // 1×1/4 - icône + titre gauche, contenu libre droite
    CONDENSED,  // 1/2×1/2 - icône + titre haut, zone libre dessous
    EXTENDED,   // 1×1/2 - icône + titre haut, zone libre dessous
    SQUARE,     // 1×1 - icône + titre haut, grande zone libre
    FULL        // 1×∞ - icône + titre haut, zone libre infinie
}
```

## ═══════════════════════════════════
## Validation JSON Schema V3

Validation unifiée pour tous les types d'outils via SchemaValidator.

### API Standard

```kotlin
// Récupération du ToolType (SchemaProvider)
val toolType = ToolTypeManager.getToolType("tracking")

// Validation données métier (entries)
val dataResult = SchemaValidator.validate(toolType, entryData, context, useDataSchema = true)

// Validation configuration outil
val configResult = SchemaValidator.validate(toolType, configData, context, useDataSchema = false)

// Gestion résultat
if (dataResult.isValid) {
    // Validation réussie
} else {
    // Erreur traduite automatiquement : dataResult.errorMessage
}
```

### Validation Service Pattern

```kotlin
class MyToolService(private val context: Context) : ExecutableService {
    
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        // Validation automatique via ToolType
        val toolType = ToolTypeManager.getToolType("my_tool")
        if (toolType != null) {
            val validation = SchemaValidator.validate(toolType, dataMap, context, useDataSchema = true)
            if (!validation.isValid) {
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }
        
        // Logique métier...
        return OperationResult.success()
    }
}
```

### Configuration Screen Pattern

```kotlin
@Composable
fun MyToolConfigScreen(zoneId: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var toolType by remember { mutableStateOf("simple") }
    
    // Validation temps réel (optionnelle)
    val validationResult = remember(name, toolType) {
        val toolTypeProvider = ToolTypeManager.getToolType("my_tool")
        val configData = mapOf("name" to name, "type" to toolType)
        toolTypeProvider?.let { 
            SchemaValidator.validate(it, configData, context, useDataSchema = false) 
        }
    }
    
    val handleSave = {
        val config = JSONObject().apply {
            put("name", name)
            put("type", toolType)
        }
        onSave(config.toString())
    }
    
    Column {
        UI.FormField(
            label = "Nom de l'outil",
            value = name,
            onChange = { name = it },
            required = true,
            state = if (validationResult?.isValid == false) ComponentState.ERROR else ComponentState.NORMAL
        )
        
        UI.FormSelection(
            label = "Type",
            options = listOf("simple", "advanced"),
            selected = toolType,
            onSelect = { toolType = it }
        )
        
        // Toast d'erreur automatique
        validationResult?.errorMessage?.let { errorMsg ->
            LaunchedEffect(errorMsg) {
                android.widget.Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
        
        UI.FormActions {
            UI.ActionButton(
                action = ButtonAction.SAVE, 
                enabled = validationResult?.isValid != false,
                onClick = handleSave
            )
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onCancel)
        }
    }
}
```

## ═══════════════════════════════════
## Règles d'Extension

### Discovery Pure

- Aucun import hardcodé dans Core
- Enregistrement automatique via ToolTypeScanner
- Service et DAO découverts dynamiquement

### Consistency Patterns

- Standalone database par tool type
- Validation unifiée via SchemaValidator
- Configuration JSON avec schéma
- Event sourcing pour toutes modifications

### Interface Contracts

- ToolTypeContract étend SchemaProvider pour validation unifiée
- ExecutableService pour logique métier
- SchemaValidator pour validation UI/Service

---

*L'architecture d'outils garantit extensibilité sans modification du Core et cohérence des patterns.*
