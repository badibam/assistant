# Custom Fields Migration System - Plan d'implémentation

## Vue d'ensemble

Système de migration automatique pour gérer les changements de configuration des custom fields avec impact sur les données existantes. Inclut également le système de snapshots self-contained pour les executions.

## Principes

1. **Discovery pattern** : Toutes les opérations passent par le CommandDispatcher
2. **Structure prévisible** : `custom_fields` a une structure garantie
3. **Migration automatique** : UI avec dialogue, IA silencieux
4. **Snapshots self-contained** : Les executions contiennent leurs propres métadonnées
5. **Réutilisable** : Système générique utilisable pour tracking, données structurées, etc.

---

# Phase 1 : Executions Self-Contained

## 1.1 Base de données

### Migration SQL
```sql
ALTER TABLE tool_executions ADD COLUMN custom_fields_metadata TEXT
```

### Entité
```kotlin
@Entity(tableName = "tool_executions")
data class ToolExecutionEntity(
    // ... champs existants
    @ColumnInfo(name = "custom_fields_metadata") val customFieldsMetadata: String?
)
```

### DAO
Mettre à jour queries insert/update pour inclure le nouveau champ.

## 1.2 Structure des données

### Données normales (tool_data)
```json
{
  "custom_fields": {
    "mood": "happy",
    "notes": "Bonne journée"
  }
}
```
→ Valeurs pures uniquement, config référencée via tool instance

### Snapshots (tool_executions)
```json
{
  "custom_fields": {
    "mood": "happy",
    "notes": "Bonne journée"
  },
  "custom_fields_metadata": [
    {
      "name": "mood",
      "display_name": "Humeur",
      "type": "CHOICE",
      "options": ["happy", "sad", "neutral"],
      "placeholder": "...",
      "always_visible": true
    },
    {
      "name": "notes",
      "display_name": "Notes",
      "type": "TEXT_LONG",
      "placeholder": "...",
      "always_visible": false
    }
  ]
}
```
→ Archive complète et auto-suffisante

## 1.3 Service tool_executions

### Operation: create

**Flow** :
1. Récupérer config tool instance via `coordinator.processUserAction("tools.get", ...)`
2. Extraire array `custom_fields` de la config
3. Sérialiser dans `custom_fields_metadata` du snapshot
4. Persister execution avec les deux champs

**Signature** :
```kotlin
// Dans ToolExecutionsService.execute()
when (operation) {
    "create" -> {
        val toolInstanceId = params.getString("template_data_id")
        val snapshotData = params.getJSONObject("snapshot_data")

        // Récupérer config pour metadata
        val configResult = coordinator.processUserAction("tools.get", mapOf(
            "tool_instance_id" to toolInstanceId
        ))

        val config = configResult.data?.get("config") as? Map<String, Any>
        val customFieldsMetadata = config?.get("custom_fields") as? List<*>

        // Ajouter aux snapshot
        snapshotData.put("custom_fields_metadata", JSONArray(customFieldsMetadata))

        // Persister...
    }
}
```

## 1.4 Schémas execution

### Tous les schémas d'execution de tools avec custom fields

Ajouter validation pour `custom_fields_metadata` :
```kotlin
"custom_fields_metadata": {
    "type": "array",
    "items": {
        "type": "object",
        "properties": {
            "name": { "type": "string" },
            "display_name": { "type": "string" },
            "type": { "type": "string" },
            // ... tous les champs de FieldDefinition
        }
    }
}
```

## 1.5 Rendering avec sources multiples

### MetadataSource (nouveau)
```kotlin
sealed class FieldMetadataSource {
    data class ConfigBased(val toolInstanceId: String) : FieldMetadataSource()
    data class SnapshotBased(val metadata: List<FieldDefinition>) : FieldMetadataSource()
}
```

### FieldInputRenderer
```kotlin
@Composable
fun FieldInputRenderer(
    field: FieldDefinition,
    value: Any?,
    onChange: (Any?) -> Unit,
    readonly: Boolean = false,
    metadataSource: FieldMetadataSource = FieldMetadataSource.ConfigBased(...)
)
```

**Logique** :
- `ConfigBased` : Récupère metadata depuis config actuelle (comportement actuel)
- `SnapshotBased` : Utilise metadata fournie (pour executions historiques)

### CustomFieldsEditor
```kotlin
@Composable
fun CustomFieldsEditor(
    toolInstanceId: String? = null,
    customFieldsMetadata: List<FieldDefinition>? = null,
    values: Map<String, Any?>,
    onValuesChange: (Map<String, Any?>) -> Unit,
    readonly: Boolean = false
)
```

**Logique** :
- Si `customFieldsMetadata` fourni → mode snapshot
- Sinon → charge depuis config via `toolInstanceId`

## 1.6 Backup/Restore

### BackupService
- Export : Inclure `custom_fields_metadata` dans les executions exportées
- Import : Valider et persister `custom_fields_metadata`

### JsonTransformers
Si structure `custom_fields_metadata` change dans futures versions :
- Ajouter transformers pour migrer format metadata

---

# Phase 2 : Système de Migration

## 2.1 Détection des changements

### FieldChange.kt
```kotlin
sealed class FieldChange {
    data class Added(val field: FieldDefinition) : FieldChange()
    data class Removed(val name: String) : FieldChange()
    data class NameChanged(val oldName: String, val newName: String) : FieldChange()
    data class TypeChanged(
        val name: String,
        val oldType: FieldType,
        val newType: FieldType
    ) : FieldChange()
    data class ChoiceOptionsRemoved(
        val name: String,
        val removedOptions: List<String>
    ) : FieldChange()
    data class CosmeticChange(val name: String) : FieldChange()
}
```

### FieldConfigComparator.kt
```kotlin
object FieldConfigComparator {
    /**
     * Compare old and new custom_fields config arrays
     * Returns list of detected changes
     */
    fun compare(
        oldFields: List<FieldDefinition>,
        newFields: List<FieldDefinition>
    ): List<FieldChange>
}
```

**Logique** :
1. Parcourir old fields : si absent dans new → `Removed`
2. Parcourir new fields : si absent dans old → `Added`
3. Pour champs communs (même name) :
   - Si type différent → `TypeChanged`
   - Si name différent... IMPOSSIBLE (identifié par name)
   - Si CHOICE et options supprimées → `ChoiceOptionsRemoved`
   - Si seulement display_name/placeholder/always_visible → `CosmeticChange`

**Note** : Changement de `name` détecté comme `Removed` + `Added`

## 2.2 Stratégies de migration

### MigrationStrategy.kt
```kotlin
enum class MigrationStrategy {
    NONE,                     // Aucune action
    STRIP_FIELD,             // Supprimer champ de toutes les entrées
    STRIP_FIELD_IF_VALUE,    // Supprimer si valeur spécifique
    ERROR                    // Interdire le changement
}
```

### MigrationPolicy.kt
```kotlin
object MigrationPolicy {
    /**
     * Determine migration strategy for each detected change
     * Returns map of change → strategy
     */
    fun getStrategies(
        changes: List<FieldChange>
    ): Map<FieldChange, MigrationStrategy> {
        return changes.associateWith { change ->
            when (change) {
                is FieldChange.Added -> MigrationStrategy.NONE
                is FieldChange.Removed -> MigrationStrategy.STRIP_FIELD
                is FieldChange.NameChanged -> MigrationStrategy.ERROR
                is FieldChange.TypeChanged -> MigrationStrategy.ERROR
                is FieldChange.ChoiceOptionsRemoved -> MigrationStrategy.STRIP_FIELD_IF_VALUE
                is FieldChange.CosmeticChange -> MigrationStrategy.NONE
            }
        }
    }

    /**
     * Generate user-friendly description of what will happen
     */
    fun getDescription(
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>
    ): String
}
```

## 2.3 Migration executor

### FieldDataMigrator.kt
```kotlin
object FieldDataMigrator {
    /**
     * Migrate custom_fields data for a tool instance
     * Uses CommandDispatcher for all data access (respects discovery)
     */
    suspend fun migrateCustomFields(
        coordinator: Coordinator,
        toolInstanceId: String,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>,
        token: CancellationToken
    ): OperationResult
}
```

**Flow** :
1. Récupérer toutes données via `tool_data.get` (sans pagination v1)
2. Pour chaque entrée :
   - Parser `custom_fields` map
   - Appliquer transformations selon stratégies :
     - `STRIP_FIELD` : supprimer field du map
     - `STRIP_FIELD_IF_VALUE` : supprimer si value dans removedOptions
   - Construire update partiel
3. Batch update via `tool_data.batch_update` avec :
   - `partialValidation = true` (mode update)
   - Seulement champ `custom_fields` modifié
4. Retourner résultat

**Gestion erreurs** :
- Check cancellation token régulièrement
- Si batch_update échoue : retourner erreur (pas de rollback v1)
- Logger toutes opérations

## 2.4 Validation config

### FieldConfigValidator.kt (extension)

Ajouter validations :

```kotlin
/**
 * Validate that field names haven't changed
 * Compare by position or need explicit mapping
 */
fun validateNoNameChanges(
    oldFields: List<FieldDefinition>,
    newFields: List<FieldDefinition>
): ValidationResult

/**
 * Validate that field types haven't changed
 */
fun validateNoTypeChanges(
    oldFields: List<FieldDefinition>,
    newFields: List<FieldDefinition>
): ValidationResult
```

**Stratégie détection** :
- Les fields n'ont pas d'ID stable
- Considérer qu'un field avec même `name` = même field
- Si name disparu → field supprimé (OK)
- Si nouveau name → field ajouté (OK)
- Changement name impossible à détecter directement (= suppression + ajout)

## 2.5 UI - CustomFieldsEditor

### Flow de sauvegarde avec migration

**Avant save** :
1. Charger config actuelle du tool instance
2. `FieldConfigComparator.compare(oldFields, newFields)`
3. Si changements détectés :
   - `MigrationPolicy.getStrategies(changes)`
   - Si stratégie ERROR → afficher erreur, bloquer save
   - Si stratégies STRIP/STRIP_IF_VALUE → afficher dialogue confirmation

**Dialogue confirmation** :
```kotlin
@Composable
fun MigrationConfirmationDialog(
    changes: List<FieldChange>,
    strategies: Map<FieldChange, MigrationStrategy>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
)
```

**Messages** :
- "X champs supprimés : leurs données seront effacées"
- "Y options CHOICE supprimées : données concernées seront effacées"
- Pas de comptage précis d'entrées impactées

**Après confirmation** :
1. `FieldDataMigrator.migrateCustomFields(...)`
2. Si succès → save config
3. Si échec → afficher erreur, annuler save

## 2.6 AI - ToolInstanceService

### Operation: update avec custom_fields modifiés

**Flow** :
1. Extraire old et new custom_fields arrays
2. Validation via `FieldConfigValidator` :
   - Check no name changes
   - Check no type changes
   - Si erreurs → return OperationResult.error()
3. `FieldConfigComparator.compare(oldFields, newFields)`
4. Si changements :
   - `MigrationPolicy.getStrategies(changes)`
   - Si ERROR → return OperationResult.error()
   - Sinon → `FieldDataMigrator.migrateCustomFields()` silencieux
5. Save config normalement

**Pas de message système** pour l'IA après migration (silencieux)

---

# Phase 3 : Strings et Tests

## 3.1 Strings requis

### shared.xml
```xml
<!-- Migration -->
<string name="migration_confirm_title">Confirmation des changements</string>
<string name="migration_confirm_message">Ces modifications affecteront les données existantes.</string>
<string name="migration_fields_removed">%1$d champ(s) supprimé(s) : leurs données seront effacées</string>
<string name="migration_choice_options_removed">%1$d option(s) CHOICE supprimée(s) : données concernées seront effacées</string>
<string name="migration_in_progress">Migration en cours...</string>
<string name="migration_success">Migration réussie</string>
<string name="migration_failed">Échec de la migration</string>

<!-- Validation errors -->
<string name="error_field_name_changed">Le nom d'un champ ne peut pas être modifié</string>
<string name="error_field_type_changed">Le type d'un champ ne peut pas être modifié</string>
```

## 3.2 Scénarios de test

### Test 1 : Suppression field
1. Créer tool avec custom field "notes"
2. Créer 5 entrées avec valeurs dans "notes"
3. Supprimer field "notes" de config
4. Vérifier dialogue affiché
5. Confirmer
6. Vérifier données migrées (field supprimé)

### Test 2 : Options CHOICE supprimées
1. Créer field CHOICE "mood" avec options [happy, sad, neutral]
2. Créer 10 entrées : 5 "happy", 3 "sad", 2 "neutral"
3. Supprimer option "sad" de config
4. Vérifier dialogue
5. Confirmer
6. Vérifier : 5 "happy" OK, 3 "sad" supprimés, 2 "neutral" OK

### Test 3 : Changement type interdit (UI)
1. Créer field TEXT "notes"
2. Tenter de changer en TEXT_LONG via UI
3. Vérifier erreur affichée
4. Save bloqué

### Test 4 : Changement type interdit (IA)
1. Créer tool via IA avec field TEXT
2. Commander IA pour changer type → TEXT_LONG
3. Vérifier erreur retournée
4. Config non modifiée

### Test 5 : Changement name interdit
1. Créer field "old_name"
2. Tenter de renommer en "new_name"
3. Vérifier erreur (UI et IA)

### Test 6 : Changements cosmétiques
1. Créer field avec display_name "Ancien"
2. Changer display_name en "Nouveau"
3. Vérifier aucune migration déclenchée
4. Save direct

### Test 7 : Executions self-contained
1. Créer tool avec custom fields
2. Créer execution avec snapshot
3. Vérifier `custom_fields_metadata` présent dans DB
4. Modifier config (supprimer field)
5. Afficher ancienne execution
6. Vérifier field supprimé toujours visible (via metadata)

### Test 8 : Backup/Restore avec metadata
1. Créer tool avec custom fields + executions
2. Export backup
3. Vérifier `custom_fields_metadata` dans JSON export
4. Reset app
5. Import backup
6. Vérifier executions avec metadata restaurées

---

# Ordre d'implémentation

1. **Phase 1.1-1.2** : DB + entities pour custom_fields_metadata
2. **Phase 1.3-1.4** : Service tool_executions + schémas
3. **Phase 1.5** : Refactor rendering avec metadata sources
4. **Phase 1.6** : Backup/Restore
5. **Phase 2.1-2.2** : Détection changements + stratégies
6. **Phase 2.3** : Migration executor
7. **Phase 2.4** : Validation config
8. **Phase 2.5** : UI CustomFieldsEditor
9. **Phase 2.6** : AI ToolInstanceService
10. **Phase 3** : Strings + tests

---

# Notes techniques

## Performance
- V1 : Pas de pagination, charge toutes données
- Si problème : ajouter batch processing avec limit/offset

## Atomicité
- V1 : Pas de rollback
- Future : Transaction système pour garantir intégrité

## Réutilisabilité

### Pour Tracking (futur)
Le système sera réutilisable en adaptant :
- Path field : `"value.main_field"` au lieu de `"custom_fields"`
- Context : `MigrationContext.TrackingMainField` au lieu de `CustomFields`

### Pour Données structurées (futur)
Idem avec path et context appropriés.

## Extensibilité

Pour ajouter nouvelles stratégies de migration :
1. Ajouter enum dans `MigrationStrategy`
2. Ajouter logique dans `MigrationPolicy.getStrategies()`
3. Ajouter transformation dans `FieldDataMigrator.migrateCustomFields()`
