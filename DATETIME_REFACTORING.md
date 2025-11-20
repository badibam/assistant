# Date/Time Refactoring - Plan d'implémentation

## Objectifs

1. Unifier la gestion date/time dans toute l'application
2. Stockage interne : Timestamp Long UTC
3. Interfaces externes (IA + UI) : ISO 8601 avec timezone locale
4. Conversion centralisée dans les services
5. Affichage UI configurable (timezone, locale, formats)

---

## Architecture finale

### Stockage DB

**Timestamps (Long UTC epoch)** :
- `tool_data.timestamp` : Date/heure de l'entrée métier
- `created_at`, `lastActivity` : Métadonnées système
- `scheduledExecutionTime`, `nextExecutionTime` : Scheduling
- Custom field `DATETIME` : Timestamp complet

**Strings** :
- Custom field `DATE` : `"2025-03-15"` (ISO format)
- Custom field `TIME` : `"14:30"` (HH:MM format)

### Format externe (IA et UI)

**ISO 8601 avec offset timezone** : `"2025-03-15T14:30:00+01:00"`

**Input** : Accepter les deux
- Avec offset : `"2025-03-15T14:30:00+01:00"` → parser l'offset explicite
- Sans offset : `"2025-03-15T14:30:00"` → assumer timezone config

**Output** : Toujours avec offset (sécurité et explicité)

**Timezone appliquée** : Config user (défaut = device timezone)

### Flow de conversion

```
UI/IA Input (ISO 8601)
  → Service parse (avec timezone config)
  → UTC timestamp Long
  → DB

DB
  → UTC timestamp Long
  → Service format (avec timezone config)
  → ISO 8601 avec offset
  → UI/IA Output
```

**Pas de différence IA/UI** : Même format, même logique de conversion.

---

## Composants à créer

### 1. DateTimeConfig (AppConfigStructures.kt)

```kotlin
data class DateTimeConfig(
    // Timezone/Locale
    val timezoneOverride: String? = null,        // "Europe/Paris" | null = device
    val localeOverride: String? = null,          // "fr-FR" | null = device

    // Display formats
    val use24HourFormat: Boolean? = null,        // true/false | null = device
    val dateFormatPattern: String? = null,       // "dd/MM/yyyy" | null = locale default
    val timeSeparator: String = ":",             // ":" | "h"

    // Métier (normalisation périodes)
    val dayStartHour: Int = 4,
    val weekStartDay: String = "MONDAY"
) {
    /**
     * Get ZoneId for app timezone
     * Returns override if set, otherwise system default
     */
    fun getZoneId(): ZoneId {
        return timezoneOverride?.let { ZoneId.of(it) }
            ?: ZoneId.systemDefault()
    }
}
```

**Extension AppConfigService** :
```kotlin
suspend fun getDateTimeConfig(): DateTimeConfig
suspend fun setTimezoneOverride(timezone: String?)
suspend fun setUse24HourFormat(use24h: Boolean?)
suspend fun setDateFormatPattern(pattern: String?)
suspend fun setTimeSeparator(separator: String)
```

**Extension AppConfigManager** :
```kotlin
// Cache volatile
private var dateTimeConfig: DateTimeConfig? = null

fun getDateTimeConfig(): DateTimeConfig
fun refresh()  // Invalider cache
```

### 2. DateTimeConverter (core/utils/)

**Conversion ISO ↔ Timestamp**

```kotlin
object DateTimeConverter {
    /**
     * Parse ISO 8601 to UTC timestamp
     * Accepts with or without offset
     * If no offset, uses appTimezone from config
     *
     * @param iso ISO 8601 string (with or without offset)
     * @param appTimezone App timezone from config (override or system default)
     * @return UTC timestamp (milliseconds)
     */
    fun isoToTimestamp(
        iso: String,
        appTimezone: ZoneId  // Always explicit, no default
    ): Long

    /**
     * Format UTC timestamp to ISO 8601 with timezone offset
     *
     * @param timestamp UTC timestamp (milliseconds)
     * @param appTimezone App timezone from config
     * @return ISO 8601 string with offset (e.g., "2025-03-15T14:30:00+01:00")
     */
    fun timestampToISO(
        timestamp: Long,
        appTimezone: ZoneId
    ): String

    /**
     * Recursive conversion JSON: ISO → timestamps
     * Converts all datetime-like strings to Long
     * Handles custom_fields DATETIME type
     *
     * @param json JSON object with ISO datetime strings
     * @param appTimezone App timezone from config
     * @return JSON object with timestamps
     */
    fun isoToTimestamps(
        json: JSONObject,
        appTimezone: ZoneId
    ): JSONObject

    /**
     * Recursive conversion JSON: timestamps → ISO
     * Converts all Long timestamps to ISO strings with offset
     * Handles custom_fields DATETIME type
     *
     * @param json JSON object with timestamp Longs
     * @param appTimezone App timezone from config
     * @return JSON object with ISO datetime strings
     */
    fun timestampsToISO(
        json: JSONObject,
        appTimezone: ZoneId
    ): JSONObject
}
```

**Détection automatique** : Détecter les champs timestamp par nom (`timestamp`, `created_at`, etc.) ou custom_fields DATETIME.

### 3. DateTimeFormatter (core/utils/)

**Affichage UI selon config**

```kotlin
object DateTimeFormatter {
    /**
     * Format ISO or timestamp for user display
     * Applies timezone, locale, and format preferences from config
     */
    fun formatForDisplay(
        value: Any,  // ISO String or Long timestamp
        context: Context,
        includeTime: Boolean = true,
        includeDate: Boolean = true
    ): String  // "15/03/2025 14h30" | "15/03/2025" | "14h30"

    /**
     * Format date only (no time)
     */
    fun formatDateOnly(value: Any, context: Context): String

    /**
     * Format time only (no date)
     */
    fun formatTimeOnly(value: Any, context: Context): String

    /**
     * Get current time as ISO 8601 with user timezone
     */
    fun nowISO(context: Context): String

    /**
     * Get current timestamp
     */
    fun nowTimestamp(): Long
}
```

**Utilise** : `AppConfigManager.getDateTimeConfig()`, `LocaleUtils.getAppLocale()`

### 4. Modification DateUtils.kt

**Obsolète** : Remplacer progressivement par DateTimeFormatter.

**Transition** : Garder temporairement, marquer `@Deprecated`, migrer usage.

**Helpers ISO 8601 (lignes 152-280)** : Supprimer, logique dans DateTimeConverter.

---

## Modifications services

### Pattern général (tous les services avec timestamps)

**ToolDataService.execute()** :

```kotlin
"create", "batch_create", "update", "batch_update" -> {
    // 1. Récupérer data JSON depuis params
    val dataJson = params["data"] as JSONObject

    // 2. Récupérer timezone app (override ou system default)
    val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

    // 3. Convertir ISO → timestamps (récursif)
    val dataWithTimestamps = DateTimeConverter.isoToTimestamps(dataJson, appTimezone)

    // 4. Validation avec timestamps
    val validation = toolType.validateData(dataWithTimestamps, context)

    // 5. Persistence avec timestamps
    dao.insert(...)

    OperationResult.success()
}

"get", "get_single" -> {
    // 1. Récupération DB (timestamps)
    val entries = dao.getEntries(...)

    // 2. Récupérer timezone app (override ou system default)
    val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

    // 3. Convertir timestamps → ISO (récursif)
    val entriesWithISO = entries.map { entry ->
        entry.copy(
            data = DateTimeConverter.timestampsToISO(entry.data, appTimezone)
        )
    }

    // 4. Retour avec ISO
    OperationResult.success(data = mapOf("entries" to entriesWithISO))
}
```

**Services concernés** :
- ToolDataService (principal)
- MessageService (scheduledExecutionTime)
- AISessionService (timestamps session)
- BackupService (export/import avec conversion)

**IMPORTANT** : La conversion doit être **transparente** pour la logique métier. Les schémas validation acceptent timestamps en interne.

---

## Modifications UI

### Pickers

**DatePicker, TimePicker, DateTimePicker** :

```kotlin
@Composable
fun DatePicker(
    initialValue: String?,  // ISO "2025-03-15" ou null
    onDateSelected: (String) -> Unit,  // Callback avec ISO
    context: Context
)

@Composable
fun TimePicker(
    initialValue: String?,  // ISO "14:30:00" ou HH:MM
    onTimeSelected: (String) -> Unit,
    use24Hour: Boolean? = null  // null = config
)

@Composable
fun DateTimePicker(
    initialValue: String?,  // ISO avec offset ou timestamp
    onDateTimeSelected: (String) -> Unit,  // ISO avec offset
    context: Context
)
```

**Implémentation** : Convertir affichage user ↔ ISO en interne.

### Formulaires

**Utilisation** :
```kotlin
// Envoyer ISO au service
coordinator.processUserAction("tool_data.create", mapOf(
    "data" to JSONObject(mapOf(
        "timestamp" to dateTimePickerValue,  // ISO string
        "value" to numericValue
    ))
))
```

### Affichage

**Remplacer** :
```kotlin
// Ancien
DateUtils.formatDateForDisplay(timestamp)

// Nouveau
DateTimeFormatter.formatForDisplay(isoOrTimestamp, context)
```

**Composants UI à migrer** :
- TrackingHistory
- JournalEntryScreen
- NotesScreen
- MessagesScreen
- Tous affichages de dates

---

## Modifications IA

### SystemMessage.formattedData

**Conversion timestamps → ISO** avant envoi prompt :

```kotlin
// Dans CommandExecutor ou AIEventProcessor
val formattedData = if (commandResults.isNotEmpty()) {
    val resultsJson = buildResultsJSON(commandResults)

    // Récupérer timezone app (override ou system default)
    val appTimezone = AppConfigManager.getDateTimeConfig().getZoneId()

    // Convertir timestamps → ISO récursivement
    val resultsWithISO = DateTimeConverter.timestampsToISO(resultsJson, appTimezone)

    resultsWithISO.toString(2)  // Pretty JSON
} else null
```

### Input IA

**Déjà géré** par services (conversion ISO → timestamp).

**Validation stricte** : Format ISO 8601 valide (avec ou sans offset).

---

## Custom Fields

### Schémas FieldType

**DATE** : Garder string
```json
{
  "type": "string",
  "format": "date",
  "description": "ISO 8601 date (YYYY-MM-DD)"
}
```

**TIME** : Garder string
```json
{
  "type": "string",
  "pattern": "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
  "description": "Time in HH:MM format"
}
```

**DATETIME** : Modifier pour timestamp
```json
{
  "type": "integer",
  "description": "Unix timestamp UTC (milliseconds)"
}
```

**Mais exposition IA** : Converti en ISO 8601 avec offset dans formattedData.

### Rendering custom fields

**CustomFieldsRenderer** (si existe) :

```kotlin
when (fieldType) {
    FieldType.DATE -> {
        // Value = "2025-03-15" (string)
        // Affichage selon locale
        formatDateString(value, context)
    }

    FieldType.TIME -> {
        // Value = "14:30" (string)
        // Affichage selon 12h/24h config
        formatTimeString(value, context)
    }

    FieldType.DATETIME -> {
        // Value = timestamp Long (reçu comme ISO du service)
        DateTimeFormatter.formatForDisplay(value, context)
    }
}
```

---

## FormatSettingsScreen

**Implémenter l'écran config** (actuellement STUB) :

### UI Sections

1. **Timezone**
   - Sélecteur timezone (liste standard)
   - Toggle "Utiliser timezone système"

2. **Locale**
   - Sélecteur locale
   - Toggle "Utiliser locale système"

3. **Formats d'affichage**
   - Format date : dd/MM/yyyy, MM/dd/yyyy, yyyy-MM-dd
   - Format heure : 12h/24h
   - Séparateur temps : ":" ou "h"

4. **Périodes (métier)**
   - dayStartHour : Slider 0-23
   - weekStartDay : Dropdown

**Sauvegarde** : Via AppConfigService.

---

## Migration DB

### Custom fields DATETIME

**Vérifier** : Y a-t-il des custom fields DATETIME déjà créés ?

**Si non** : Pas de migration nécessaire, créer directement avec type timestamp.

**Si oui** : Migration pour convertir ISO string → timestamp Long.

```kotlin
// Migration XX_YY
override fun migrate(database: SupportSQLiteDatabase) {
    // SELECT tool instances avec custom fields DATETIME
    // Pour chaque entry avec custom field DATETIME
    //   Parser ISO → timestamp
    //   UPDATE data JSON
}
```

### Aucune autre migration

Les timestamps existants restent inchangés (déjà Long UTC).

---

## Plan d'implémentation par phases

### Phase 1 : Infrastructure (pas de breaking changes)

**Objectif** : Créer les composants sans toucher aux données existantes.

1. Créer `DateTimeConfig` dans AppConfigStructures
2. Ajouter getters/setters dans AppConfigService
3. Ajouter cache dans AppConfigManager
4. Créer `DateTimeConverter` avec tests unitaires
5. Créer `DateTimeFormatter` avec tests unitaires
6. Tests : Conversion ISO ↔ timestamp, formats affichage

**Validation** : Aucun impact sur app existante, juste nouveaux utils disponibles.

### Phase 2 : Services conversion (breaking)

**Objectif** : Modifier services pour conversion input/output.

1. Modifier ToolDataService (create, update, get operations)
2. Modifier MessageService si timestamps exposés
3. Modifier BackupService (export/import)
4. Tests services : Envoi ISO, réception ISO

**Validation** : UI et IA reçoivent ISO au lieu de timestamps. Nécessite Phase 3/4.

**ATTENTION** : Phase 2-3-4 doivent être coordonnées (breaking change).

### Phase 3 : UI Input (pickers)

**Objectif** : Pickers retournent ISO au lieu de timestamp.

1. Créer/modifier DatePicker, TimePicker, DateTimePicker
2. Modifier formulaires pour envoyer ISO
3. Tests : Saisie date → ISO → service

**Validation** : Input UI compatible avec services Phase 2.

### Phase 4 : UI Display

**Objectif** : Affichage via DateTimeFormatter.

1. Identifier tous les usages DateUtils.formatXxx()
2. Remplacer par DateTimeFormatter.formatForDisplay()
3. Tests affichage selon config

**Validation** : Dates affichées correctement selon config.

### Phase 5 : IA Layer

**Objectif** : Vérifier/ajuster exposition IA.

1. Modifier SystemMessage.formattedData (conversion timestamps → ISO)
2. Vérifier parsing input IA (déjà géré par services)
3. Tests : Queries IA, actions IA

**Validation** : IA reçoit/envoie ISO correctement.

### Phase 6 : Custom Fields DATETIME

**Objectif** : Migrer custom field DATETIME vers timestamp.

1. Vérifier existence custom fields DATETIME
2. Si oui : Migration DB
3. Modifier schémas FieldType
4. Modifier rendering si nécessaire
5. Tests

**Validation** : Custom fields DATETIME fonctionnels.

### Phase 7 : FormatSettingsScreen

**Objectif** : UI de configuration.

1. Implémenter écran settings (timezone, locale, formats)
2. Connecter à AppConfigService
3. Tests : Changement config → affichage mis à jour

**Validation** : User peut configurer affichage dates.

### Phase 8 : Cleanup

**Objectif** : Supprimer code obsolète.

1. Supprimer DateUtils (garder seulement helpers nécessaires)
2. Supprimer helpers ISO provisoires
3. Documentation mise à jour
4. Tests régression complets

---

## Points d'attention

### 1. Timezone config vs dayStartHour

**Clarification** :
- **Timezone config** : Affichage uniquement (conversion UTC ↔ local)
- **dayStartHour** : Logique métier (normalisation périodes dans PeriodUtils)

Ces deux concepts sont **indépendants**.

### 2. Export/Import backup

**Métadonnées backup** : Inclure timezone au moment de l'export.

```json
{
  "metadata": {
    "export_version": 10,
    "export_timestamp": 1710460800000,
    "export_timezone": "Europe/Paris"
  }
}
```

**Import** : Informer user si timezone différente, permettre ajustement.

### 3. Performance conversion

**Conversion récursive JSON** : Peut être coûteuse sur gros volumes.

**Optimisation** : Identifier champs par nom (whitelist) au lieu de parser tout.

### 4. Validation schémas

**Schémas internes** : Acceptent timestamps (integer).

**Schémas exposés IA** : Documentent ISO 8601 (string).

**Divergence intentionnelle** : Conversion gérée par services.

### 5. Tests

**Tests unitaires** : DateTimeConverter, DateTimeFormatter.

**Tests integration** : Services avec conversion.

**Tests E2E** : UI input → service → DB → service → UI display.

---

## Dépendances externes

**Java Time API** : `java.time.*` (disponible Android API 26+, desugaring si besoin)

**Vérifier minSdk** : Si < 26, activer desugaring dans build.gradle.

---

## Résumé décisions clés

1. **Stockage** : Timestamp Long UTC partout (sauf custom fields DATE/TIME string)
2. **Format externe** : ISO 8601 avec offset timezone
3. **Input** : Accepter avec ou sans offset
4. **Output** : Toujours avec offset
5. **Conversion** : Centralisée dans services (pas dans DAO)
6. **UI = IA** : Même format, même traitement
7. **Timezone** : Config user (défaut device), affichage + conversion
8. **dayStartHour** : Séparé, logique métier périodes
9. **Custom fields** : DATE/TIME string, DATETIME timestamp
10. **Migration** : Custom fields DATETIME si existants

---

## Validation finale

Avant de commencer :
- [ ] Vérifier existence custom fields DATETIME en production
- [ ] Vérifier minSdk Android (desugaring si < 26)
- [ ] Planifier coordination Phases 2-3-4 (breaking changes groupées)
- [ ] Préparer tests régression complets
