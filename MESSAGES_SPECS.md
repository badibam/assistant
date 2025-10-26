# Messages Tool - Spécifications d'Implémentation

## 1. Vue d'ensemble

### 1.1 Positionnement architectural

**Type** : Outil (tool type), pas fonctionnalité core.

**Rationale** : Les messages sont des données utilisateur organisables par zones thématiques, avec manipulation possible par l'IA via commands. Suivent le pattern existant (Tracking, Journal, Notes).

**Concept "Une Instance = Un Concept"** :
- Instance 1 : "Anniversaires" (dates annuelles récurrentes)
- Instance 2 : "Vérifications réalité" (moments aléatoires rêves lucides)
- Instance 3 : "Rappels médicaments" (planning quotidien)

### 1.2 Cas d'usage

1. **Rappels dates fixes** : Anniversaires avec notifications annuelles
2. **Messages générés par automation** : IA crée série de notifications à moments calculés (ex: 50 rappels aléatoires sur 1 semaine pour entraînement rêves lucides)
3. **Planning régulier** : Rappels quotidiens/hebdomadaires configurés par user

### 1.3 Principe de fonctionnement

**Architecture données** :

1. **Config : Settings globaux (minimal)**
   - `default_priority` : Priority par défaut pour nouveaux messages
   - `external_notifications` : Afficher notifications Android ou non

2. **Data : Messages (templates) avec historique d'exécutions**
   - Chaque data entry = un message template (titre, contenu, schedule, priority)
   - Contient array `executions[]` avec snapshots des envois effectués
   - Manipulable par IA via commands standard `tool_data.*`

**Flow complet** :
1. User/IA crée message template via `tool_data.create` (avec ou sans schedule)
2. CoreScheduler tick() détecte scheduled_time atteint
3. MessageScheduler append execution dans array + envoie notification Android
4. User voit notification → ouvre app → marque lu/archivé (modifie dernière execution)
5. Historique complet dans `executions[]` de chaque message

**Avantages architecture** :
- Manipulation IA intuitive (CREATE/UPDATE message = commands standard)
- Cohérent avec pattern "Une Instance = Un Concept" (un message = une data entry)
- Granularité priority (par message, pas global)
- Audit trail intégré (snapshots immutables dans executions)

## 2. Architecture données

### 2.1 Structure config (settings globaux)

```json
{
  "schema_id": "messages_config",
  "data_schema_id": "messages_data",
  "name": "Rappels médicaments",
  "description": "Notifications planning quotidien",
  "management": "USER",
  "display_mode": "LINE",
  "validateConfig": false,
  "validateData": false,
  "always_send": false,

  // Spécifique Messages
  "default_priority": "high",
  "external_notifications": true
}
```

**Champs spécifiques Messages** :

- **`default_priority`** : Priority par défaut pour nouveaux messages
  - `"default"` : Badge drawer, pas de popup
  - `"high"` : Popup heads-up (interruption visuelle)
  - `"low"` : Silencieux, pas de son/vibration
  - Chaque message peut override cette valeur

- **`external_notifications`** : Boolean global instance
  - `true` : Envoyer notifications Android
  - `false` : Messages visibles seulement dans app (pas de notifs système)

### 2.2 Structure data (messages templates)

```json
{
  "schema_id": "messages_data",
  "title": "Anniversaire Marie",
  "content": "Penser au cadeau !",
  "schedule": {
    "pattern": {
      "type": "YearlyRecurrent",
      "dates": [{"month": 11, "day": 25, "time": "10:00"}]
    },
    "timezone": "Europe/Paris",
    "startDate": 1704067200000,
    "endDate": null,
    "nextExecutionTime": 1732528800000
  },
  "priority": "high",
  "triggers": null,
  "executions": [
    {
      "scheduled_time": 1732528800000,
      "sent_at": 1732528805123,
      "status": "sent",
      "title_snapshot": "Anniversaire Marie",
      "content_snapshot": "Penser au cadeau !",
      "read": false,
      "archived": false
    }
  ]
}
```

**Champs data** :

- **`title`** : Titre message (obligatoire)
  - Affiché dans notification Android et UI
  - Limite : SHORT_LENGTH (60 chars)
  - Modifiable (futures executions utiliseront nouvelle valeur)

- **`content`** : Corps du message (optionnel)
  - Si null : notification = juste titre
  - Limite : LONG_LENGTH (1500 chars)
  - Modifiable (futures executions utiliseront nouvelle valeur)

- **`schedule`** : Planning (nullable)
  - Réutilise structure `ScheduleConfig` existante (voir core/utils/ScheduleConfig.kt)
  - 6 patterns disponibles : DailyMultiple, WeeklySimple, MonthlyRecurrent, WeeklyCustom, YearlyRecurrent, SpecificDates
  - Si null : message on-demand (pas auto-planifié, peut être planifié plus tard par IA)

- **`priority`** : Niveau notification Android pour CE message
  - `"default"` | `"high"` | `"low"`
  - Override la `default_priority` de la config

- **`triggers`** : Déclencheurs événementiels (STUB)
  - Toujours null pour MVP
  - Prévu pour futur : déclenchement sur événements système (ex: "quand tracking poids < 70kg")
  - Permet cohérence future sans breaking change

- **`executions`** : Array d'exécutions envoyées (SYSTEM-MANAGED)
  - **CRITIQUE** : Champ géré automatiquement par scheduler, strippé des commands IA
  - Keyword schema : `"systemManaged": true`
  - Contient snapshots immutables des envois effectués

### 2.3 Structure execution (dans array executions)

```json
{
  "scheduled_time": 1732528800000,
  "sent_at": 1732528805123,
  "status": "sent",
  "title_snapshot": "Anniversaire Marie",
  "content_snapshot": "Penser au cadeau !",
  "read": false,
  "archived": false
}
```

**Champs execution** :

- **`scheduled_time`** : Timestamp quand devait être envoyé
  - Calculé par ScheduleCalculator selon schedule pattern

- **`sent_at`** : Timestamp envoi effectif (null si pending)
  - Permet audit : différence avec scheduled_time = latence système

- **`status`** : Lifecycle execution
  - `"pending"` : Créé mais pas encore traité (fenêtre courte pendant tick)
  - `"sent"` : Notification envoyée avec succès
  - `"failed"` : Échec technique lors de l'envoi

- **`title_snapshot`** : Snapshot titre au moment de l'envoi
  - Immutable : reflète exactement ce qui a été envoyé
  - Permet modification template sans casser historique

- **`content_snapshot`** : Snapshot contenu au moment de l'envoi
  - Nullable (si message n'avait pas de contenu)
  - Immutable

- **`read`** : Flag action user
  - User a ouvert/consulté le message dans l'app
  - Toggleable (peut marquer non-lu après lecture)
  - Manipulé via operation `mark_read`

- **`archived`** : Flag action user
  - User a marqué message comme traité/terminé
  - Messages archivés = masqués par défaut dans liste UI
  - Manipulé via operation `mark_archived`

**Rationale snapshots** :
- User/IA peut modifier `title`/`content` du template
- Historique reste fidèle à ce qui a été VRAIMENT affiché dans la notification
- Pattern event sourcing standard (immutabilité audit trail)
- Priority non snapshotée : détail technique sans valeur audit (juste quel channel Android utilisé)

**Génération automatique** :

Les executions sont créées **au moment du tick** par MessageScheduler :
1. Scan toutes instances Messages via coordinator
2. Pour chaque message avec schedule != null : calcul nextExecutionTime via ScheduleCalculator
3. Si <= now : append execution (status: pending) + envoyer notif + update status (sent/failed) + update nextExecutionTime

**Pas de pré-génération** : Contrairement au pattern "générer toutes les occurrences futures", on génère à la demande (comme automations). Raison : planning peut changer, messages modifiables.

## 3. Scheduling centralisé

### 3.1 Problème architectural

**Contexte** : Actuellement le heartbeat est dans `AISessionScheduler` (core/ai) pour automations uniquement.

**Besoin** : Messages tool nécessite aussi scheduling, et futurs tools pourraient en avoir besoin (Alertes, etc.).

**Solution** : Système de scheduling centralisé avec discovery pattern.

### 3.2 CoreScheduler

**Package** : `core/scheduling/CoreScheduler.kt`

**Responsabilité** : Point d'entrée unique pour tous les scheduling systèmes.

```kotlin
object CoreScheduler {
    suspend fun tick(context: Context) {
        // 1. AI scheduling (core functionality, appelé directement)
        AISessionScheduler.checkScheduledSessions()

        // 2. Tool scheduling (discovery via ToolTypeManager)
        ToolTypeManager.getAllToolTypes().forEach { (name, toolType) ->
            toolType.getScheduler()?.checkScheduled(context)
        }
    }
}
```

**Heartbeat** (déplacé depuis AI) :
- **Coroutine** : 1 minute (app ouverte)
- **WorkManager** : 15 minutes (app fermée, background)
- **Triggers événementiels** : CRUD automations, CRUD messages, fin de session

**Décision importante** : Un seul point d'entrée tick() qui délègue, évite duplication logique WorkManager/Coroutine.

### 3.3 ToolScheduler interface

**Package** : `core/tools/ToolScheduler.kt`

```kotlin
interface ToolScheduler {
    /**
     * Appelé périodiquement par CoreScheduler
     * Responsabilité : Vérifier si des actions planifiées doivent être exécutées
     */
    suspend fun checkScheduled(context: Context)
}
```

**Extension ToolTypeContract** :

```kotlin
interface ToolTypeContract : SchemaProvider {
    // ... existing methods (getDisplayName, getDescription, etc.)

    /**
     * Retourne le scheduler de ce tool type, ou null si pas de scheduling
     * Pattern discovery : core ne connaît pas les tools, ils s'enregistrent
     */
    fun getScheduler(): ToolScheduler?
}
```

**Implémentation outils sans scheduling** : `return null` (Tracking, Journal, Notes actuels)

**Implémentation Messages** : `return MessageScheduler`

**Avantage discovery** : Futurs tools avec scheduling s'ajoutent automatiquement, aucune modification core nécessaire.

### 3.4 MessageScheduler

**Package** : `tools/messages/scheduler/MessageScheduler.kt`

**Type** : Object singleton implémentant ToolScheduler

**Responsabilité** : Vérifier messages à envoyer et déclencher notifications.

```kotlin
object MessageScheduler : ToolScheduler {
    override suspend fun checkScheduled(context: Context) {
        // 1. Récupérer toutes instances Messages via coordinator
        //    coordinator.processUserAction("tools.list", mapOf("tool_type" to "messages"))

        // 2. Pour chaque instance:
        //    a. Charger tous messages (data entries) via tool_data.get
        //    b. Pour chaque message avec schedule != null:
        //       - Calculer nextExecutionTime via ScheduleCalculator.calculateNext()
        //       - Si nextExecutionTime <= System.currentTimeMillis():
        //         * Créer execution entry (status: pending)
        //         * Appeler NotificationService.send() avec snapshots
        //         * Update execution (status: sent/failed, sent_at: now)
        //         * Update schedule.nextExecutionTime via MessageService interne

        // 3. Gestion erreurs: Log + continue (un échec ne bloque pas autres messages)
    }
}
```

**Utilise `ScheduleCalculator`** (core/utils) :
- Classe existante calculant nextExecutionTime selon pattern
- Utilisée aussi par automations (cohérence)

**Atomicité** : Création execution + envoi notif + update status = séquence complète pour chaque message.

**Pas de retry** : Si envoi notif échoue, marquer status=failed. Notif Android = best effort.

**Méthode interne MessageService** : `appendExecution()` pas exposée aux commands, appelée seulement par scheduler.

## 4. NotificationService (core service)

### 4.1 Positionnement

**Package** : `core/notifications/NotificationService.kt`

**Type** : ExecutableService simple (pas de provider pattern)

**Rationale provider** :
- Pas besoin : une seule implémentation (Android Notification API)
- Contrairement à transcription (Vosk vs online) ou IA (Claude vs autres)
- Pattern simple comme BackupService

**Réutilisabilité** : Futurs tools (Alertes) et système (notifications app) pourront utiliser.

### 4.2 Interface service

```kotlin
class NotificationService(context: Context) : ExecutableService {
    override suspend fun execute(
        operation: String,
        params: Map<String, Any>,
        token: CancellationToken
    ): OperationResult
}
```

**Operation unique** : `send`

**Params** :
```kotlin
{
  "title": String,              // Requis
  "content": String?,           // Optionnel
  "priority": String            // "default"|"high"|"low"
}
```

**Responsabilité** :
- Créer notification Android avec params fournis
- Router vers channel approprié selon priority (assistant_high/default/low)
- Mapper priority → NotificationCompat.PRIORITY_*
- Retourner success ou erreur

**Pas de persistence** : Service stateless, juste façade Android API.

**Channels** (déjà créés en Phase 2) :
- `assistant_high` : Notifications urgentes
- `assistant_default` : Notifications standard
- `assistant_low` : Notifications silencieuses

## 5. Protection champs system-managed

### 5.1 Keyword schema custom

**Problème** : `executions` doit être modifiable par scheduler mais pas par IA.

**Solution** : Keyword custom `systemManaged` dans schema.

```json
"executions": {
  "type": "array",
  "systemManaged": true,
  "description": "Execution history. Managed automatically by scheduler. Do not modify directly.",
  "items": {...}
}
```

**Comportement** :
- **SchemaValidator** : Ignore `systemManaged` (ne bloque pas validation)
- **Pipeline IA** : Strip champs avec `systemManaged: true` avant passage au service
- **Service** : Accepte ces champs (pour operations scheduler internes)

### 5.2 Point de stripping dans pipeline IA

**Pipeline** : DataCommand → CommandTransformer → CommandExecutor → Service

**Location** : Dans `CommandTransformer` ou middleware avant `CommandExecutor`

```kotlin
// Pseudo-code
fun transformAICommand(command: DataCommand): ExecutableCommand {
    val toolType = ToolTypeManager.getToolType(command.tooltype)
    val schema = toolType.getSchema(command.schema_id, context)

    // Strip system-managed fields
    val cleanedData = stripSystemManagedFields(command.params, schema)

    return ExecutableCommand(...)
}
```

**Implémentation** : À définir précisément lors de Phase 4 (voir où exactement dans le flow IA).

## 6. Services et operations

### 6.1 MessageService

**Package** : `tools/messages/MessageService.kt`

**Type** : ExecutableService (comme TrackingService, JournalService, etc.)

**Operations disponibles** :

**`create`** : Créer nouveau message template
```kotlin
params: {
  toolInstanceId: String,
  tooltype: "messages",
  schema_id: "messages_data",
  data: {
    title: String,
    content: String?,
    schedule: ScheduleConfig?,
    priority: String,
    triggers: null
    // executions: [] initialisé automatiquement
  }
}
returns: {id: String}
```

**`update`** : Modifier message template
```kotlin
params: {
  id: String,
  data: {
    title: String?,
    content: String?,
    schedule: ScheduleConfig?,
    priority: String?
    // executions: strippé par pipeline IA, conservé si présent
  }
}
returns: {success: true}
```

**`delete`** : Supprimer message
```kotlin
params: {id: String}
returns: {success: true}
```

**`get`** : Liste messages templates
```kotlin
params: {
  toolInstanceId: String,
  limit?: Int,
  offset?: Int
}
returns: {entries: [...], total: Int}
```

**`get_single`** : Un message par ID
```kotlin
params: {id: String}
returns: {entry: {...}}
```

**`get_history`** : Liste executions avec filtres
```kotlin
params: {
  toolInstanceId: String,
  filters: {
    read?: Boolean,      // null = ignore filtre
    archived?: Boolean   // null = ignore filtre
  }
}
returns: {executions: [{messageId, execution, messageTitle}...]}
// Retourne toutes les executions de tous les messages, avec join pour afficher titre
```

**`mark_read`** : Toggle flag read sur execution
```kotlin
params: {
  message_id: String,
  execution_index: Int,  // Index dans array executions
  read: Boolean
}
returns: {success: true}
```

**`mark_archived`** : Toggle flag archived sur execution
```kotlin
params: {
  message_id: String,
  execution_index: Int,
  archived: Boolean
}
returns: {success: true}
```

**`stats`** : Statistiques instance
```kotlin
params: {toolInstanceId: String}
returns: {
  total_messages: Int,
  total_executions: Int,
  unread: Int,
  archived: Int,
  pending: Int,
  sent: Int,
  failed: Int
}
```

**Méthode interne (pas dans execute)** :

```kotlin
internal suspend fun appendExecution(
    messageId: String,
    execution: ExecutionEntry
): OperationResult {
    // Direct DAO access
    // Appelée uniquement par MessageScheduler
    // Pas de command exposée aux IA/user
}
```

**Pattern d'appel** : `coordinator.processUserAction("messages.operation", params)`

## 7. Interface utilisateur

### 7.1 Écran principal Messages

**Package** : `tools/messages/ui/MessagesScreen.kt`

**Déclenchement** : Click sur ToolCard Messages dans ZoneScreen

**Structure** : 2 tabs horizontaux

---

#### Tab 1 : "Messages reçus"

**Focus** : Historique des executions (tous messages confondus)

**Filtres** : Checkboxes avec logique OR
- ☑ Non lus (défaut coché)
- ☐ Lus
- ☐ Archivés
- Logique : aucun coché = liste vide, tous cochés = tout affiché

**Liste** : LazyColumn avec toutes executions selon filtres
- **Tri** : `sent_at` DESC (envoi effectif le plus récent en premier)
- **Source** : `get_history` avec filtres selon checkboxes cochées
- **Affichage par execution** :
  - Date/heure (sent_at formaté)
  - Titre (title_snapshot)
  - Badge status si non-sent (pending 🕐, failed ❌)
  - Badge "Non lu" 🔴 si sent && !read

**Actions par execution** :
- Click sur entry → toggle read via `mark_read`
- Bouton → Archiver via `mark_archived`
- (Pas de suppression individuelle execution, on supprime le message template entier)

**Empty state** : "Aucun message" si liste vide selon filtres

---

#### Tab 2 : "Gestion messages"

**Focus** : Configuration messages templates

**Liste** : LazyColumn avec data entries (messages templates)
- **Tri** : Par `title` alphabétique
- **Source** : `get` standard
- **Affichage par template** :
  - Titre
  - Schedule summary ("Quotidien 08:00", "Annuel 25 nov", "On-demand" si null)
  - Badge priority si différent du default_priority
  - Badge nombre executions ("12 envois")

**Actions par template** :
- Bouton Éditer → Dialog édition (voir 7.2)
- Bouton Supprimer → Confirmation puis delete (supprime aussi toutes executions)

**Bouton fab** : "Ajouter message" → Dialog création

---

**Header global** : PageHeader avec titre instance + bouton settings (→ ConfigScreen)

**Loading/Error** : Pattern standard (isLoading, errorMessage avec Toast)

### 7.2 Dialog édition/création message

**Modal** : FullScreen dialog ou BottomSheet selon espace

**Formulaire** :

1. **FormField titre** (TEXT, requis)
   - Limite : SHORT_LENGTH (60 chars)

2. **FormField contenu** (TEXT_LONG, optionnel)
   - Limite : LONG_LENGTH (1500 chars)

3. **FormSelection priority**
   - Options : Default, High, Low
   - Défaut : `default_priority` de la config

4. **Section planning** :
   - Bouton "Configurer planning" → ouvre ScheduleConfigEditor
   - Réutilise composable existant (core/ai/ui/automation/ScheduleConfigEditor.kt)
   - OnConfirm : stocke ScheduleConfig dans state
   - OnClear : set null (on-demand)
   - Affichage summary si schedule défini

5. **Section triggers** :
   - Disabled/grayed avec label "Déclencheurs événementiels (à venir)"

**Actions** :
- FormActions avec SAVE, CANCEL
- Validation via `messages_data` schema (sans executions)
- Save via `create` ou `update`

### 7.3 ConfigScreen

**Package** : `tools/messages/ui/MessagesConfigScreen.kt`

**Structure** : Column scrollable avec sections

**Section 1 : Configuration générale**
- `ToolGeneralConfigSection` (8 champs standard)

**Section 2 : Champs spécifiques**

1. **FormSelection default_priority**
   - Options : Default, High, Low
   - Label : "Priorité par défaut"

2. **Toggle external_notifications**
   - Label : "Afficher notifications système"

**Sauvegarde** : ValidationHelper.validateAndSave() puis tools.update via coordinator

**Pattern référence** : Journal pour structure (outil simple), Note pour config minimale

### 7.4 DisplayComponent

**Package** : `tools/messages/ui/MessagesDisplayComponent.kt`

**Responsabilité** : Affichage minimal dans ToolCard (ZoneScreen)

**Affichage** :
- **LINE** : Icône + Titre + Badge unread count
- **CONDENSED/EXTENDED/SQUARE** : Icône + Titre + Liste 3 derniers messages non lus (title_snapshot)

**Source** : `stats` pour unread count, `get_history` pour derniers messages

## 8. Base de données

### 8.1 Entity

**Package** : `tools/messages/data/MessageData.kt`

```kotlin
@Entity(
    tableName = "message_data",
    indices = [
        Index(value = ["toolInstanceId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageData(
    @PrimaryKey val id: String,
    val toolInstanceId: String,
    val timestamp: Long,           // Pas utilisé (cohérence pattern), scheduled_time dans JSON
    val value: String,             // JSON: {title, content, schedule, priority, triggers, executions[]}
    val metadata: String?,         // Vide pour MVP, cohérence autres tools
    val createdAt: Long
)
```

**Index** :
- `toolInstanceId` (queries par instance)
- `timestamp` (cohérence pattern, tri sur executions[].sent_at via JSON)

**Pattern uniforme** : Même structure que TrackingData, JournalData, NotesData.

### 8.2 DAO

**Package** : `tools/messages/data/MessageDao.kt`

```kotlin
@Dao
interface MessageDao {
    @Query("SELECT * FROM message_data WHERE toolInstanceId = :toolInstanceId ORDER BY createdAt DESC")
    suspend fun getByToolInstance(toolInstanceId: String): List<MessageData>

    @Query("SELECT * FROM message_data WHERE id = :id")
    suspend fun getById(id: String): MessageData?

    @Insert
    suspend fun insert(data: MessageData)

    @Update
    suspend fun update(data: MessageData)

    @Delete
    suspend fun delete(data: MessageData)

    @Query("DELETE FROM message_data WHERE toolInstanceId = :toolInstanceId")
    suspend fun deleteByToolInstance(toolInstanceId: String)
}
```

**Pattern standard** : Comme autres tools DAOs.

### 8.3 Migration DB

**Version** : Incrémenter AppDatabase version (11 → 12)

```kotlin
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS message_data (
                id TEXT PRIMARY KEY NOT NULL,
                toolInstanceId TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                value TEXT NOT NULL,
                metadata TEXT,
                createdAt INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_message_data_toolInstanceId ON message_data(toolInstanceId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_message_data_timestamp ON message_data(timestamp)")
    }
}
```

## 9. ToolType implementation

### 9.1 MessageToolType

**Package** : `tools/messages/MessageToolType.kt`

**Type** : Object singleton implémentant ToolTypeContract

```kotlin
object MessageToolType : ToolTypeContract {
    // Métadonnées
    override fun getDisplayName(context: Context): String
    override fun getDescription(context: Context): String
    override fun getSuggestedIcons(): List<String>  // notification, bell, message, alarm
    override fun getDefaultConfig(): String         // JSON avec default_priority: "default", external_notifications: true
    override fun getAvailableOperations(): List<String>

    // Schémas (via SchemaProvider)
    override fun getAllSchemaIds(): List<String>    // [messages_config, messages_data]
    override fun getSchema(schemaId: String, context: Context): Schema?

    // UI
    @Composable
    override fun getConfigScreen(...)               // MessagesConfigScreen

    // Discovery
    override fun getService(): ExecutableService    // MessageService
    override fun getDao(): Any                      // MessageDao
    override fun getDatabaseEntities(): List<KClass<*>>  // [MessageData::class]
    override fun getDatabaseMigrations(): List<Migration>  // [MIGRATION_11_12]

    // Scheduling
    override fun getScheduler(): ToolScheduler      // MessageScheduler
}
```

**Enregistrement** : Ajouter dans `ToolTypeScanner.getAllToolTypes()` :
```kotlin
"messages" to MessageToolType
```

## 10. Schémas JSON

### 10.1 messages_config

```json
{
  "type": "object",
  "properties": {
    "schema_id": {"type": "string", "const": "messages_config"},
    "data_schema_id": {"type": "string", "const": "messages_data"},
    "name": {"type": "string", "maxLength": 60},
    "description": {"type": "string", "maxLength": 250},
    "management": {"type": "string", "enum": ["AI", "USER", "HYBRID"]},
    "display_mode": {"type": "string", "enum": ["ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL"]},
    "validateConfig": {"type": "boolean", "default": false},
    "validateData": {"type": "boolean", "default": false},
    "always_send": {"type": "boolean", "default": false},

    "default_priority": {
      "type": "string",
      "enum": ["default", "high", "low"],
      "default": "default"
    },
    "external_notifications": {
      "type": "boolean",
      "default": true
    }
  },
  "required": ["schema_id", "data_schema_id", "name", "default_priority", "external_notifications"]
}
```

### 10.2 messages_data

```json
{
  "type": "object",
  "properties": {
    "schema_id": {"type": "string", "const": "messages_data"},
    "title": {
      "type": "string",
      "maxLength": 60,
      "description": "Message title (displayed in notification and UI)"
    },
    "content": {
      "type": "string",
      "maxLength": 1500,
      "description": "Message content (optional)"
    },
    "schedule": {
      "{{SCHEDULE_CONFIG_PLACEHOLDER}}": true,
      "description": "Scheduling configuration (null = on-demand)"
    },
    "priority": {
      "type": "string",
      "enum": ["default", "high", "low"],
      "description": "Notification priority for this message"
    },
    "triggers": {
      "type": "null",
      "description": "Event-based triggers (STUB - not implemented yet)"
    },
    "executions": {
      "type": "array",
      "systemManaged": true,
      "description": "Execution history. Managed automatically by scheduler. Do not modify directly.",
      "items": {
        "type": "object",
        "properties": {
          "scheduled_time": {"type": "integer"},
          "sent_at": {"type": "integer"},
          "status": {"type": "string", "enum": ["pending", "sent", "failed"]},
          "title_snapshot": {"type": "string"},
          "content_snapshot": {"type": "string"},
          "read": {"type": "boolean"},
          "archived": {"type": "boolean"}
        },
        "required": ["scheduled_time", "status", "title_snapshot", "read", "archived"]
      },
      "default": []
    }
  },
  "required": ["schema_id", "title", "priority", "triggers", "executions"]
}
```

**Note** : `{{SCHEDULE_CONFIG_PLACEHOLDER}}` remplacé par SchemaUtils.embedScheduleConfig()

## 11. Décisions architecturales récapitulatives

### 11.1 Choix structurels

1. **Tool vs Core** : Outil pour bénéficier zones, IA commands, extensibilité
2. **Scheduling centralisé** : CoreScheduler découvre schedulers via interface ToolTypeContract
3. **NotificationService core** : Réutilisable futurs tools, pas de provider (une seule impl)
4. **Config minimal** : Juste settings globaux (default_priority, external_notifications)
5. **Data = templates** : Messages manipulables comme données normales via tool_data.* commands

### 11.2 Choix fonctionnels

6. **Schedule par message** : Chaque message son propre planning indépendant
7. **Schedule nullable** : Permet on-demand (IA planifiera plus tard)
8. **Réutilisation ScheduleConfig** : Infrastructure existante (6 patterns, ScheduleCalculator, UI)
9. **Executions snapshots** : Immutables, reflètent exactement ce qui a été envoyé
10. **Priority par message** : Override default_priority de la config
11. **Status + flags** : Status lifecycle (pending/sent/failed) + flags user orthogonaux (read/archived)
12. **Triggers STUB** : Prévu mais non implémenté (cohérence future, pas de breaking change)
13. **systemManaged keyword** : Protection executions via stripping pipeline IA

### 11.3 Choix UI

14. **2 tabs** : "Messages reçus" (focus executions) vs "Gestion messages" (focus templates)
15. **Filtres checkboxes OR** : Non lus + Lus + Archivés, défaut "Non lus" seul
16. **Tri executions** : Par sent_at DESC (plus récent en premier)
17. **Snapshot display** : Affiche title_snapshot/content_snapshot (pas valeurs actuelles template)

## 12. Ordre d'implémentation recommandé

**Méthodologie** : Lire fichiers de référence d'autres outils (Note pour config minimal, Tracking pour logique complexe)

### ✅ Phase 1 : Infrastructure scheduling (DONE)
1. Interface `ToolScheduler` + extension `ToolTypeContract.getScheduler()`
2. `CoreScheduler` avec logique tick()
3. Déplacement heartbeat depuis AI vers CoreScheduler
4. Test : Vérifier automations fonctionnent toujours

### ✅ Phase 2 : Notification service (DONE)
5. `NotificationService` (core simple)
6. Test : Appel manuel send() affiche notif Android

### 🔄 Phase 3 : Data layer (IN PROGRESS)
7. Schémas JSON (`MessagesSchemas.kt` : messages_config, messages_data)
8. Entity `MessageData` + DAO
9. Migration DB (ajout table message_data)

### Phase 4 : AI protection
10. Implémentation `systemManaged` keyword stripping dans pipeline IA
11. Test : IA ne peut pas modifier executions

### Phase 5 : Business logic
12. `MessageService` (operations CRUD + get_history + mark_read + mark_archived)
13. `MessageScheduler` (logique tick + appendExecution)
14. Test : Tick manuel crée execution + envoie notif

### Phase 6 : Tool contract
15. `MessageToolType` (implémentation complète contrat)
16. Enregistrement `ToolTypeScanner`
17. Test : Instance créable, schémas accessibles

### Phase 7 : UI
18. `MessagesConfigScreen` (settings globaux + default_priority)
19. `MessagesScreen` (2 tabs : Messages reçus + Gestion messages)
20. Dialog édition message (réutilise ScheduleConfigEditor)
21. `MessagesDisplayComponent` (minimal pour ToolCard)
22. Strings (shared + tool)

### Phase 8 : Validation
23. Tests end-to-end : Créer instance → créer message → attendre tick → vérifier notif + execution
24. Tests edge cases : Schedule null, external_notifications false, priority variations, modification template
25. Tests UI : Filtres, snapshots display, mark read/archived

---
