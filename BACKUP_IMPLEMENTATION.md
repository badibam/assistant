# Backup System Implementation

## 1. Architecture Overview

### Components
- **BackupService** : Business logic (JSON generation/import, wipe, version handling)
- **BackupScreen** : UI (SAF file picker, loading states, confirmations)
- **Migration system** : JSON transformations for DB version compatibility

### Separation of Concerns
- **Service**: Pure logic, returns/accepts JSON strings, no file I/O, no Android framework dependencies
- **UI**: SAF handling, file read/write via URIs, user interactions, app restart

---

## 2. BackupService Operations

### Operation: `export`
**Input**: None
**Output**: `OperationResult` with `json_data: String`

**Logic**:
1. Access all DAOs via AppDatabase instance
2. Query all tables in dependency order:
   - `app_config`
   - `zones`
   - `tool_instances`
   - Tool data tables (tracking_data, journal_data, etc.)
   - `ai_sessions`, `ai_messages`
   - Transcription provider configs, AI provider configs
3. Build JSON structure with metadata:
   ```json
   {
     "metadata": {
       "app_version": "0.2",
       "db_version": 1,
       "export_timestamp": 1234567890
     },
     "data": {
       "app_config": [...],
       "zones": [...],
       ...
     }
   }
   ```
4. Return serialized JSON string

### Operation: `import`
**Input**: `json_data: String`
**Output**: `OperationResult` with success/error

**Logic**:
1. Parse JSON and extract `metadata.db_version`
2. Validate version:
   - If `db_version > current` → error "backup too recent"
   - If `db_version < current` → apply transformations
   - If `db_version == current` → direct import
3. Apply transformations if needed (see Migration System)
4. **Wipe all tables** in reverse dependency order
5. Insert data in dependency order (preserve original IDs)
6. Return success

### Operation: `reset`
**Input**: None
**Output**: `OperationResult` with success/error

**Logic**:
1. **Wipe all tables** in reverse dependency order
2. Insert default `app_config` (dayStartHour, weekStartDay defaults)
3. Return success

---

## 3. Migration System

### Concept
- **Room migrations**: Handle existing DB schema updates on device
- **JSON transformations**: Transform imported backup data before insertion

### Version Reference
- Use **Room Database version** as single source of truth
- Current version defined in `@Database(version = X)`
- Export stores this version in `metadata.db_version`

### Transformation Logic
Located in BackupService, structure:
```kotlin
private fun transformBackupData(jsonData: JSONObject, fromVersion: Int, toVersion: Int): JSONObject {
    var transformed = jsonData
    for (version in fromVersion until toVersion) {
        transformed = when (version) {
            1 -> migrateFrom1To2(transformed)
            2 -> migrateFrom2To3(transformed)
            // etc.
            else -> transformed
        }
    }
    return transformed
}
```

Each migration function transforms JSON structure (add/remove/rename fields, restructure data).

---

## 4. UI Implementation

### BackupScreen
Located: `core/ui/screens/settings/BackupScreen.kt`

**Access**: Via SettingsDialog, option `"backup"`

**UI Elements**:
1. **Export button**
   - Click → loading state → `coordinator.processUserAction("backup.export")`
   - Receive JSON → launch SAF `ACTION_CREATE_DOCUMENT` with `.assistant-backup` extension
   - Write JSON to selected URI
   - Toast success/error

2. **Import button**
   - Click → confirmation dialog "All current data will be erased"
   - If confirmed → launch SAF `ACTION_OPEN_DOCUMENT` (.assistant-backup)
   - Read JSON from URI
   - Loading state → `coordinator.processUserAction("backup.import", json)`
   - If success → **restart app** (Intent FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK)
   - If error → toast error message

3. **Reset button**
   - Click → confirmation dialog "All data will be permanently deleted"
   - If confirmed → loading state → `coordinator.processUserAction("backup.reset")`
   - If success → **restart app**
   - If error → toast error message

### SAF Implementation
- Use `ActivityResultLauncher<Intent>` with `registerForActivityResult`
- Export: `Intent(ACTION_CREATE_DOCUMENT)` with MIME type `application/octet-stream`
- Import: `Intent(ACTION_OPEN_DOCUMENT)` with MIME type `application/octet-stream`
- Read/write via `contentResolver.openInputStream/OutputStream(uri)`

### Restart Logic
```kotlin
val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
context.startActivity(intent)
exitProcess(0)
```

---

## 5. Integration Points

### SettingsDialog
Add new option:
```kotlin
"backup" to s.shared("settings_backup")
```

### MainScreen
Add state and navigation:
```kotlin
var showBackup by rememberSaveable { mutableStateOf(false) }

if (showBackup) {
    BackupScreen(onBack = { showBackup = false })
    return
}
```

Handle option in SettingsDialog callback:
```kotlin
"backup" -> showBackup = true
```

### ServiceRegistry
Already registered: `backup → BackupService`

### Strings
Add to `core/strings/sources/shared.xml`:
- `settings_backup`: "Sauvegarde & Réinitialisation"
- `backup_export`: "Exporter toutes les données"
- `backup_import`: "Importer un fichier de sauvegarde"
- `backup_reset`: "Réinitialiser toutes les données"
- `backup_export_success`: "Export réussi"
- `backup_import_confirm`: "Toutes les données actuelles seront écrasées. Continuer ?"
- `backup_reset_confirm`: "Toutes les données seront définitivement supprimées. Continuer ?"
- `backup_import_success`: "Import réussi, redémarrage..."
- `backup_reset_success`: "Réinitialisation réussie, redémarrage..."
- `backup_version_too_recent`: "Ce fichier provient d'une version plus récente de l'application"
- `backup_invalid_file`: "Fichier de sauvegarde invalide"

---

## 6. Database Version Management

### Current State
- Check `@Database` annotation in AppDatabase.kt for current version
- If version is 1 (initial), no migrations needed yet
- Future versions increment annotation and add Room migrations + JSON transformations

### First Migration Example (when moving to v2)
**Room migration**:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Alter existing DB on device
        database.execSQL("ALTER TABLE zones ADD COLUMN color TEXT")
    }
}
```

**JSON transformation**:
```kotlin
private fun migrateFrom1To2(data: JSONObject): JSONObject {
    // Transform imported backup data
    val zones = data.getJSONObject("data").getJSONArray("zones")
    for (i in 0 until zones.length()) {
        val zone = zones.getJSONObject(i)
        if (!zone.has("color")) {
            zone.put("color", "")
        }
    }
    return data
}
```

---

## 7. Error Handling

### Validation Errors
- Invalid JSON → "Fichier de sauvegarde invalide"
- Missing metadata → "Fichier de sauvegarde invalide"
- Version too recent → "Ce fichier provient d'une version plus récente"

### Import Errors
- All errors during wipe/insert → rollback automatically (transaction)
- Show error toast, don't restart app

### Export Errors
- DAO access errors → log + return OperationResult.error()
- JSON serialization errors → log + return error

### SAF Errors
- User cancels → no action
- Read/write errors → toast error message

---

## 8. Implementation Order

1. Update strings (shared.xml + generate)
2. Implement BackupService operations (export, import, reset)
3. Create BackupScreen with SAF + UI
4. Integrate in SettingsDialog + MainScreen navigation
5. Test with current DB version (v1, no transformations)
6. Document Room migration pattern for future versions

---

## 9. Notes

### Tool-specific migrations
- **Ignored for now**: Tool config/data JSON format changes
- Handled by separate system (not DB structure)
- Will be implemented later when needed

### Performance
- Export/import run on IO dispatcher (suspend functions)
- Large DBs may take time → loading indicators essential
- Consider background operation for very large datasets

### Testing
- Test export → manual inspection of JSON
- Test import same version → verify data integrity
- Test reset → verify clean state + defaults
- Future: test transformations when migrations added
