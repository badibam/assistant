# Spécifications Tool Type : Structured Data

## Vue d'Ensemble

**Tool Type ID** : `structured_data`
**Display Name** : `"Base de Données"`
**Concept** : Outil pour créer et gérer des référentiels de données structurées avec schémas personnalisés.

### Différenciation avec autres outils
- **Tracking** : Données temporelles (poids au fil du temps)
- **Structured Data** : Données relationnelles permanentes (base d'aliments avec propriétés nutritionnelles)

### Exemples d'usage
```
Référentiel Aliments
├── Pomme (100g) → calories: 52, glucides: 14g, fibres: 2.4g
├── Banane (100g) → calories: 89, glucides: 23g, potassium: 358mg
└── Pain complet (100g) → calories: 247, glucides: 41g, protéines: 13g

Référentiel Exercices
├── Course à pied → MET: 8, muscles: jambes, cardio: élevé
├── Natation → MET: 6, muscles: corps entier, cardio: très élevé
└── Musculation → MET: 3, muscles: variable, force: élevé
```

## Architecture Technique

### Configuration Tool Instance

```json
{
  "name": "Aliments",
  "description": "Base nutritionnelle complète",
  "icon": "database",
  "enabled": true,
  "data_schema": {
    "type": "object",
    "properties": {
      "name": {"type": "string", "required": true},
      "calories": {"type": "number", "unit": "kcal/100g"},
      "category": {"type": "string", "enum": ["fruits", "légumes", "céréales"]},
      "nutrients": {
        "type": "object",
        "properties": {
          "glucides": {"type": "number", "unit": "g"},
          "protéines": {"type": "number", "unit": "g"},
          "lipides": {"type": "number", "unit": "g"}
        }
      }
    }
  },
  "display_config": {
    "list_columns": ["name", "calories", "category"],
    "search_fields": ["name", "category"],
    "default_sort": "name"
  }
}
```

### Structure Data Entries

```json
{
  "id": "uuid",
  "toolInstanceId": "tool_instance_uuid",
  "timestamp": 1693123456789,
  "data": {
    "name": "Pomme",
    "calories": 52,
    "category": "fruits",
    "nutrients": {
      "glucides": 14,
      "protéines": 0.3,
      "lipides": 0.2
    }
  }
}
```

## Workflow de Création

### Étape 1 : Configuration Initiale
```
┌─ ToolGeneralConfigSection ─┐  ← Composant standard
│ Nom: "Aliments"            │
│ Description: "Base nutri..." │
│ Icône: database            │
│ Activé: ☑️                 │
└────────────────────────────┘

┌─ Méthode Création Schéma ─┐   ← Spécifique structured_data
│ ○ IA automatique          │
│   📁 [Sélectionner CSV]    │
│                            │
│ ○ Schéma manuel           │
│   📁 [Fichier schéma JSON] │
│   📁 [Fichier données CSV] │
└────────────────────────────┘
```

### Méthode IA Automatique
1. **Upload CSV** : Utilisateur sélectionne fichier CSV
2. **Traitement IA** : IA analyse colonnes et échantillon de données
   - Détecte types (string, number, boolean)
   - Identifie unités (kcal, g, mg)
   - Génère enums pour catégories
   - Choisit 3 colonnes importantes pour affichage liste
3. **Preview** : Utilisateur voit schéma généré + preview données
4. **Validation** : Utilisateur confirme → Création tool instance + import données

### Méthode Schéma Manuel
1. **Upload Schéma** : Fichier JSON Schema standard
2. **Upload CSV** : Fichier données correspondant au schéma
3. **Validation** : Vérification cohérence schéma/données
4. **Preview** : Affichage avant création finale
5. **Création** : Tool instance + import données

### Gestion Erreurs
- **IA échoue** → Retour à sélection méthode (possibilité schéma manuel)
- **Schéma invalide** → Message erreur + retour sélection fichier
- **CSV incohérent** → Détail des erreurs + retour sélection

## Interface Configuration Post-Création

### Écran Configuration
```
┌─ ToolGeneralConfigSection ─┐  ← Standard, non modifiable
│ Nom: "Aliments" ✓          │
│ Description: "Base..." ✓    │
└────────────────────────────┘

┌─ Schéma Données ─┐            
│                             │
│ Schema actuel:              │
│ • name (text) ✓             │
│ • calories (number) ✓       │
│ • category (choice) ✓       │
│ • bio (boolean) ✓           │
└─────────────────────────────┘

┌─ Configuration Affichage ─┐   ← Interface checkboxes
│ Colonnes en liste:         │
│ ☑️ name                    │
│ ☑️ calories                │
│ ☑️ category                │
│ ☐ bio                      │
│ ☐ nutrients                │
└────────────────────────────┘
```

### Modification Schéma via Chat IA
**Exemples de commandes :**
- `"Ajoute un champ bio oui/non"`
- `"Supprime la colonne obsolete"`
- `"Renomme calories en energie"`
- `"Ajoute champ origine avec valeurs France/Import/Bio"`

**Workflow :**
1. Chat IA traite commande
2. Génère nouveau schéma
3. Preview changements
4. Utilisateur confirme
5. `update_schema` → Migration automatique données existantes
6. Nouveaux champs = `null` pour entries existantes

## Interface Gestion Données

### Liste des Entrées (Mobile-First)
```
🔍 [Rechercher dans Aliments...]

┌─ Aliments - 1247 entrées ─┐
│ Nom        │Cal. │Catégorie │
├─────────────────────────────┤
│ Pomme      │ 52  │ Fruits   │ [edit]
│ Banane     │ 89  │ Fruits   │ [edit]
│ Pain blanc │ 265 │ Céréales │ [edit]
│ ...                         │
└─────────────────────────────┘
[+ Ajouter entrée]

Affichage: 3 colonnes max configurables
Navigation: Liste → Clic → Détail complet pour édition
```

### Recherche et Filtres
- **Recherche texte** : Sur champs configurés dans `search_fields`
- **Filtres** : Basés sur types de colonnes (enum → dropdown, etc.)
- **Tri** : Sur toutes colonnes numériques et texte

### Formulaires CRUD
- **Création/Édition** : Formulaire généré dynamiquement depuis schema
- **Validation** : Via schéma de l'instance avant soumission
- **Types supportés** : string, number, boolean, enum, object nested

## Service Operations

### Operations ExecutableService

**Operations Standard (héritées tel quel) :**
```kotlin
// Lecture/suppression sans validation custom
"get"            // Récupérer toutes entrées avec pagination et filtres
"get_single"     // Récupérer une entrée par ID
"delete_entry"   // Supprimer entrée par ID
"delete_all"     // Supprimer toutes entrées de l'instance
```

**Operations Custom structured_data :**
```kotlin
// CRUD avec validation contre schéma instance (non-standard)
"create_entry"   // Créer avec validation config.data_schema
"update_entry"   // Modifier avec validation config.data_schema

// Operations uniques à structured_data
"update_schema"  // Modifier schéma + migration auto données
"import_csv"     // Import données depuis CSV avec mapping
"export_csv"     // Export données vers CSV avec filtres
"search"         // Recherche avancée avec filtres complexes
"bulk_update"    // Modification en lot pour completion IA
"get_schema"     // Récupérer schéma pour intégration autres outils
```

**Paramètres Operations Standard :**

**get :**
```json
{
  "toolInstanceId": "uuid_instance",
  "offset": 0,
  "limit": 50,
  "sort_field": "name",
  "sort_direction": "asc",
  "filters": {
    "category": "fruits",
    "calories": {"min": 0, "max": 100}
  }
}
```
- Pagination : offset/limit obligatoires
- Tri : selon `display_config.default_sort` ou paramètre
- Filtres : selon types champs (string = contains, number = range, boolean = exact)
- Return : `{"entries": [...], "total": 1247, "has_more": true}`

**get_single :**
```json
{
  "toolInstanceId": "uuid_instance",
  "entry_id": "uuid_entry"
}
```
- Return : Entry complète ou null si introuvable

**delete_entry :**
```json
{
  "toolInstanceId": "uuid_instance",
  "entry_id": "uuid_entry"
}
```
- Validation : entry existe et appartient à l'instance
- Return : Confirmation suppression

**delete_all :**
```json
{
  "toolInstanceId": "uuid_instance",
  "confirm": true
}
```
- Sécurité : `confirm: true` obligatoire
- Return : Nombre d'entries supprimées

**Paramètres Operations Custom :**

**create_entry (validation custom) :**
```json
{
  "toolInstanceId": "uuid_instance",
  "data": {
    "name": "Pomme Golden",
    "calories": 52,
    "category": "fruits",
    "bio": true
  }
}
```
- **CUSTOM** : Validation `data` contre `config.data_schema` de l'instance
- Génère : `id` unique + `timestamp` automatique
- Return : Entry créée avec tous champs

**update_entry (validation custom) :**
```json
{
  "toolInstanceId": "uuid_instance",
  "entry_id": "uuid_entry",
  "data": {
    "name": "Pomme Golden Bio",
    "calories": 52,
    "category": "fruits",
    "bio": true
  }
}
```
- **CUSTOM** : Validation `data` contre `config.data_schema` de l'instance
- Update : seulement champs fournis (patch partiel possible)
- Return : Entry complète mise à jour

**delete_entry :**
```json
{
  "toolInstanceId": "uuid_instance",
  "entry_id": "uuid_entry"
}
```
- Validation : entry existe et appartient à l'instance
- Return : Confirmation suppression

**get :**
```json
{
  "toolInstanceId": "uuid_instance",
  "offset": 0,
  "limit": 50,
  "sort_field": "name",
  "sort_direction": "asc",
  "filters": {
    "category": "fruits",
    "calories": {"min": 0, "max": 100}
  }
}
```
- Pagination : offset/limit obligatoires
- Tri : selon `display_config.default_sort` ou paramètre
- Filtres : selon types champs (string = contains, number = range, boolean = exact)
- Return : `{"entries": [...], "total": 1247, "has_more": true}`

**get_single :**
```json
{
  "toolInstanceId": "uuid_instance",
  "entry_id": "uuid_entry"
}
```
- Return : Entry complète ou null si introuvable

**delete_all :**
```json
{
  "toolInstanceId": "uuid_instance",
  "confirm": true
}
```
- Sécurité : `confirm: true` obligatoire
- Return : Nombre d'entries supprimées

### Operations Spécifiques Structured Data

**update_schema :**
```json
{
  "toolInstanceId": "uuid_instance",
  "schema": {
    "type": "object",
    "properties": {
      "name": {"type": "string", "required": true},
      "calories": {"type": "number", "unit": "kcal"},
      "bio": {"type": "boolean"},
      "origine": {"type": "string", "enum": ["France", "Import", "Bio"]}
    }
  }
}
```
- Validation : nouveau schéma JSON Schema valide
- Migration : auto pour toutes entries existantes (nouveaux champs = null)
- Return : Détails migration (nb entries migrées, champs ajoutés/supprimés)

**get_schema :**
```json
{
  "toolInstanceId": "uuid_instance"
}
```
- Return : Schéma complet de l'instance pour intégration autres outils
- Usage : Tracking veut connaître structure base "Aliments"

**import_csv :**
```json
{
  "toolInstanceId": "uuid_instance",
  "csv_data": "name,calories,category\nPomme,52,fruits\nBanane,89,fruits",
  "field_mapping": {
    "name": "name",
    "calories": "calories",
    "category": "category"
  },
  "replace_all": false
}
```
- Validation : CSV selon schéma instance + mapping valide
- Options : `replace_all` (remplace tout) ou append (ajoute)
- Return : Nb entries importées, erreurs de validation

**export_csv :**
```json
{
  "toolInstanceId": "uuid_instance",
  "fields": ["name", "calories", "category"],
  "filters": {"category": "fruits"},
  "format": "excel"
}
```
- Export : entries filtrées au format CSV/Excel
- Return : Data CSV ou URL fichier temporaire

**search :**
```json
{
  "toolInstanceId": "uuid_instance",
  "query": "pomme bio",
  "fields": ["name", "category"],
  "filters": {
    "calories": {"min": 0, "max": 100},
    "bio": true
  },
  "limit": 20
}
```
- Recherche : texte + filtres combinés
- Return : Entries matchantes avec score pertinence

**bulk_update :**
```json
{
  "toolInstanceId": "uuid_instance",
  "updates": [
    {"entry_id": "uuid1", "data": {"bio": true}},
    {"entry_id": "uuid2", "data": {"bio": false}}
  ]
}
```
- Usage : IA completion massive de nouveaux champs
- Validation : chaque update selon schéma instance
- Return : Nb succès, nb échecs avec détails

**suggest_values :**
```json
{
  "toolInstanceId": "uuid_instance",
  "field_name": "bio",
  "entry_ids": ["uuid1", "uuid2", "uuid3"]
}
```
- IA analyse entries existantes et propose valeurs pour nouveau champ
- Return : `{"uuid1": true, "uuid2": false, "uuid3": true}` avec confidences

### Récapitulatif Operations vs Standard

**4 Operations Standard** (comme tous les outils) :
- `get`, `get_single`, `delete_entry`, `delete_all`
- Paramètres : `toolInstanceId`, `entry_id`, `offset`, `limit`, `filters`
- Pas de validation custom

**8 Operations Custom structured_data** :
- `create_entry`, `update_entry` : **validation contre schéma instance**
- `update_schema`, `import_csv`, `export_csv`, `search`, `bulk_update`, `get_schema` : **uniques**

**Paramètres Spécifiques** :
```kotlin
// Validation custom
"data"            // Données validées contre config.data_schema

// Schema management
"schema"          // Nouveau schéma pour update_schema

// Import/Export
"csv_data"        // Données CSV brutes
"field_mapping"   // Mapping colonnes CSV → champs schéma
"fields"          // Champs à exporter
"format"          // Format export (csv/excel)

// Recherche avancée
"query"           // Texte recherche libre
"filters"         // Filtres complexes par type champ

// IA assistance
"updates"         // Array modifications bulk_update
"field_name"      // Champ pour suggest_values
```

## Intégration Architecture Existante

### ToolTypeContract Implementation
```kotlin
class StructuredDataToolType : ToolTypeContract {
    override fun getDisplayName(): String = s.tool("display_name") // "Base de Données"

    override fun getConfigSchema(): String {
        return """
        {
          "type": "object",
          "properties": {
            "name": {"type": "string", "required": true},
            "description": {"type": "string"},
            "data_schema": {"type": "object", "required": true},
            "display_config": {
              "type": "object",
              "properties": {
                "list_columns": {"type": "array", "maxItems": 3},
                "search_fields": {"type": "array"},
                "default_sort": {"type": "string"}
              }
            }
          }
        }
        """.trimIndent()
    }

    override fun getDataSchema(): String {
        // Schema générique permissif - validation réelle custom dans Service
        // Chaque instance a son propre schéma dans config.data_schema
        return """
        {
          "type": "object",
          "additionalProperties": true
        }
        """.trimIndent()
    }

    override fun getService(context: Context): ExecutableService =
        StructuredDataService(context)

    override fun getDao(context: Context): Any =
        StructuredDataDatabase.getDatabase(context).structuredDataDao()

    override fun getDatabaseEntities(): List<Class<*>> =
        listOf(StructuredDataEntry::class.java)
}
```

### Database Structure
```kotlin
@Entity(tableName = "structured_data_entries")
data class StructuredDataEntry(
    @PrimaryKey val id: String,
    val toolInstanceId: String,     // Lien vers tool_instances
    val timestamp: Long,
    val data: String                // JSON data selon schéma
)

@Dao
interface StructuredDataDao {
    @Query("SELECT * FROM structured_data_entries WHERE tool_instance_id = :toolInstanceId ORDER BY timestamp DESC")
    suspend fun getEntriesForTool(toolInstanceId: String): List<StructuredDataEntry>

    @Insert
    suspend fun insertEntry(entry: StructuredDataEntry)

    @Update
    suspend fun updateEntry(entry: StructuredDataEntry)

    @Delete
    suspend fun deleteEntry(entry: StructuredDataEntry)

    @Query("SELECT * FROM structured_data_entries WHERE tool_instance_id = :toolInstanceId AND id = :entryId")
    suspend fun getEntry(toolInstanceId: String, entryId: String): StructuredDataEntry?

    @Query("DELETE FROM structured_data_entries WHERE tool_instance_id = :toolInstanceId")
    suspend fun deleteAllEntries(toolInstanceId: String)
}
```

### Service Validation Pattern
```kotlin
class StructuredDataService(private val context: Context) : ExecutableService {

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        return when (operation) {
            "create_entry", "update_entry" -> {
                // Validation avec schéma spécifique à l'instance
                val toolInstanceId = params.getString("toolInstanceId")
                val validation = validateEntryWithInstanceSchema(toolInstanceId, params)
                if (!validation.isValid) {
                    return OperationResult.error("Validation failed: ${validation.errorMessage}")
                }

                // Exécution CRUD...
                when (operation) {
                    "create_entry" -> createEntry(params, token)
                    "update_entry" -> updateEntry(params, token)
                    else -> OperationResult.error("Unknown operation")
                }
            }

            "update_schema" -> {
                val toolInstanceId = params.getString("toolInstanceId")
                val newSchema = params.getJSONObject("schema")
                updateSchemaAndMigrate(toolInstanceId, newSchema, token)
            }

            "get", "get_single", "delete_entry", "delete_all" -> {
                // Pas de validation schéma nécessaire pour lecture/suppression
                when (operation) {
                    "get" -> getEntries(params, token)
                    "get_single" -> getSingleEntry(params, token)
                    "delete_entry" -> deleteEntry(params, token)
                    "delete_all" -> deleteAllEntries(params, token)
                    else -> OperationResult.error("Unknown operation")
                }
            }

            "search" -> searchEntries(params, token)
            "import_csv" -> importFromCSV(params, token)
            "export_csv" -> exportToCSV(params, token)
            "bulk_update" -> bulkUpdateEntries(params, token)

            else -> OperationResult.error("Unknown operation: $operation")
        }
    }

    private suspend fun validateEntryWithInstanceSchema(toolInstanceId: String, params: JSONObject): ValidationResult {
        try {
            // 1. Récupérer la configuration de l'instance
            val toolInstance = getToolInstance(toolInstanceId)
                ?: return ValidationResult.error("Tool instance not found")

            // 2. Extraire le schéma spécifique de cette instance
            val configJson = JSONObject(toolInstance.config)
            val instanceSchema = configJson.getJSONObject("data_schema")

            // 3. Récupérer les données à valider
            val entryData = params.getJSONObject("data")

            // 4. Validation custom avec le schéma de l'instance
            return validateAgainstCustomSchema(entryData, instanceSchema)

        } catch (e: Exception) {
            LogManager.service("Failed to validate entry with instance schema: ${e.message}", "ERROR")
            return ValidationResult.error("Validation error: ${e.message}")
        }
    }

    private fun validateAgainstCustomSchema(data: JSONObject, schema: JSONObject): ValidationResult {
        // Validation JSON Schema manuelle adaptée à structured_data
        // Cette méthode implémente la validation selon le schéma custom
        // Alternative: utiliser une librairie JSON Schema comme everit-org/json-schema

        try {
            val properties = schema.optJSONObject("properties") ?: return ValidationResult.success()
            val required = schema.optJSONArray("required")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            } ?: emptyList()

            // Vérifier champs requis
            for (requiredField in required) {
                if (!data.has(requiredField) || data.isNull(requiredField)) {
                    return ValidationResult.error("Required field missing: $requiredField")
                }
            }

            // Vérifier types des champs présents
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val fieldSchema = properties.optJSONObject(key) ?: continue
                val fieldType = fieldSchema.optString("type")
                val fieldValue = data.get(key)

                val isValid = when (fieldType) {
                    "string" -> fieldValue is String
                    "number" -> fieldValue is Number
                    "boolean" -> fieldValue is Boolean
                    "object" -> fieldValue is JSONObject
                    "array" -> fieldValue is JSONArray
                    else -> true // Type non défini = accepté
                }

                if (!isValid) {
                    return ValidationResult.error("Invalid type for field '$key': expected $fieldType")
                }

                // Validation enum si défini
                val enumValues = fieldSchema.optJSONArray("enum")
                if (enumValues != null && fieldValue is String) {
                    val validValues = (0 until enumValues.length()).map { enumValues.getString(it) }
                    if (fieldValue !in validValues) {
                        return ValidationResult.error("Invalid value for field '$key': must be one of $validValues")
                    }
                }
            }

            return ValidationResult.success()

        } catch (e: Exception) {
            return ValidationResult.error("Schema validation error: ${e.message}")
        }
    }

    // Autres méthodes d'implémentation...
    private suspend fun createEntry(params: JSONObject, token: CancellationToken): OperationResult {
        // Implémentation création entry...
        return OperationResult.success()
    }

    // ... autres méthodes CRUD
}
```

## Strings et Internationalisation

### Strings Spécifiques (`tools/structured_data/strings.xml`)
```xml
<string name="display_name">Base de Données</string>
<string name="import_csv">Importer CSV</string>
<string name="schema_auto">IA automatique</string>
<string name="schema_manual">Schéma manuel</string>
<string name="select_csv_file">Sélectionner fichier CSV</string>
<string name="select_schema_file">Sélectionner schéma JSON</string>
<string name="processing_import">Traitement en cours...</string>
<string name="import_success">Import réussi : %1$d entrées</string>
<string name="schema_generated">Schéma généré par IA</string>
<string name="preview_data">Aperçu des données</string>
<string name="column_configuration">Configuration colonnes</string>
<string name="displayed_columns">Colonnes affichées</string>
<string name="search_fields">Champs de recherche</string>
<string name="schema_updated">Schéma mis à jour</string>
<string name="migration_complete">Migration complète : %1$d entrées</string>
<string name="add_entry">Ajouter entrée</string>
<string name="edit_entry">Modifier entrée</string>
<string name="search_in_base">Rechercher dans %1$s...</string>
<string name="no_entries">Aucune entrée</string>
<string name="entries_count">%1$d entrées</string>
```

### Usage Strings Shared
- Actions : `s.shared("action_save")`, `s.shared("action_cancel")`, `s.shared("action_delete")`
- Labels : `s.shared("label_name")`, `s.shared("label_description")`
- Messages : `s.shared("message_loading")`, `s.shared("validation_error")`

## Enregistrement Tool Type

### ToolTypeScanner Addition
```kotlin
object ToolTypeScanner {
    fun getAllToolTypes(): Map<String, ToolTypeContract> {
        return mapOf(
            "tracking" to TrackingToolType(),
            "structured_data" to StructuredDataToolType(),  // Ajout ici
            // autres outils...
        )
    }
}
```

### Discovery automatique
- Service découvert via `ToolTypeManager.getServiceForToolType("structured_data", context)`
- DAO accessible via `ToolTypeManager.getDaoForToolType("structured_data", context)`
- Aucune modification Core requise

## Aspects Techniques Avancés

### Validation Custom par Instance
**Problématique** : Chaque instance de structured_data a son propre schéma (aliments ≠ exercices ≠ médicaments).

**Solution** :
- `getDataSchema()` retourne schéma générique permissif
- Validation réelle dans `StructuredDataService.validateEntryWithInstanceSchema()`
- Chaque instance stocke son schéma dans `config.data_schema`
- Service récupère et applique le schéma spécifique lors des opérations CRUD

**Workflow Validation** :
1. `create_entry` appelé avec `toolInstanceId` + `data`
2. Service récupère `tool_instance.config.data_schema`
3. Validation custom `data` contre schéma instance
4. Si valide → Insert, sinon → Error avec détails

### Migration Schema Workflow
1. `update_schema` appelé avec nouveau schéma
2. Validation nouveau schéma (JSON Schema valide)
3. Comparaison ancien/nouveau pour identifier changements
4. Pour champs ajoutés : valeur = `null` sur toutes entries existantes
5. Pour champs supprimés : données conservées mais ignorées
6. Pour champs modifiés : tentative conversion automatique
7. Update config tool instance avec nouveau schéma
8. Return success avec détails migration

### Performance Considerations
- **Pagination** : Queries limitées (ex: 50 entries par page)
- **Indexation** : Index automatique sur `toolInstanceId` + `timestamp`
- **Recherche** : Index sur champs configurés dans `search_fields`
- **Cache** : Schéma en cache pour éviter re-parsing JSON

### Sécurité et Validation
- **Injection** : Paramètres SQL via bindings, pas de string concat
- **Schema validation** : JSON Schema strict avant storage
- **Size limits** : CSV import limité (ex: 10MB, 50k entries)
- **Type safety** : Validation types selon schéma avant insert

## Extensibilité Future

### API Export/Import
- **CSV standard** : Compatible Excel/Google Sheets
- **JSON export** : Avec métadonnées et schéma inclus
- **API REST** : Endpoints pour intégration externe (futur)

### Intégration IA Avancée
- **Auto-enrichment** : IA complète automatiquement nouveaux champs
- **Data cleaning** : IA détecte et corrige incohérences
- **Smart suggestions** : IA propose nouvelles entrées basées sur patterns

### Linking avec autres Tools
- **Tracking integration** : Autocomplete depuis structured_data
- **Calculations** : Formules basées sur données structured_data
- **Triggers** : Alertes basées sur seuils dans structured_data

---

*Spécifications complètes pour implémentation Tool Type Structured Data dans architecture Assistant existante.*
