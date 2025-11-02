---
description: Database schema upgrade sequence (SQL changes)
allowed-tools: Bash, Edit, Write, Read, Grep
---

**Upgrade DB schema : vX → vY**

## Séquence :

1. **Déterminer versions** :
   - Lire `@Database(version = X)` dans AppDatabase.kt
   - Nouvelle version = X + 1

2. **Incrémenter versions** :
   - `@Database(version = Y)` (ligne ~41)
   - `const val VERSION = Y` (ligne ~67)

3. **Créer migration** `MIGRATION_X_Y` dans companion object :
   ```kotlin
   private val MIGRATION_X_Y = object : Migration(X, Y) {
       override fun migrate(database: SupportSQLiteDatabase) {
           // SQL changes here
           database.execSQL("ALTER TABLE ...")

           // OR JSON transformations (if no SQL changes)
           val cursor = database.query("SELECT id, tool_type, config_json FROM tool_instances")
           while (cursor.moveToNext()) {
               val id = cursor.getString(0)
               val tooltype = cursor.getString(1)
               val oldJson = cursor.getString(2)

               val newJson = JsonTransformers.transformToolConfig(oldJson, tooltype, X, Y)
               database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?",
                   arrayOf(newJson, id))
           }
           cursor.close()
       }
   }
   ```

4. **Ajouter à .addMigrations()** dans getDatabase()

5. **Si changement JSON** : Ajouter transformation dans `JsonTransformers.kt` (pour imports backup)

6. **EXPORT** : Gérer sérialisation de l'export dans dans backupservice

## Rappels :

- ⚠️ Changement JSON = migration Room requise (pour upgrade existants)
- ⚠️ JsonTransformers = double usage (migration + imports)
- ⚠️ Tester avec données réelles avant commit
