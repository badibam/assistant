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

**Deux niveaux de données** :

1. **Config : Messages prédéfinis (templates)**
   - Stockés dans `config.messages[]` de l'instance
   - Définissent titre, contenu, planning
   - Modifiables par user (UI) ou IA (commands UPDATE_TOOL)

2. **Data : Instances envoyées (audit trail)**
   - Générées automatiquement par le scheduler quand scheduled_time atteint
   - Contiennent référence au message prédéfini + timestamps + statut
   - Ultra-légères : juste refs et flags

**Flow complet** :
1. User/IA configure message prédéfini avec schedule dans config
2. CoreScheduler tick() détecte scheduled_time atteint
3. MessageScheduler crée data entry + envoie notification Android
4. User voit notification → ouvre app → marque lu/archivé
5. Historique complet dans data entries

## 2. Architecture données

### 2.1 Structure config (messages prédéfinis)

```json
{
  "schema_id": "messages_config",
  "data_schema_id": "messages_data",
  "name": "Anniversaires",
  "description": "Rappels dates importantes",
  "management": "USER",
  "display_mode": "LINE",
  "validateConfig": false,
  "validateData": false,
  "always_send": false,


  // Spécifique Messages
  "priority": "high",
  "messages": [
    {
      "id": "msg_anniversary_marie",
      "title": "Anniversaire Marie",
      "content": "Penser au cadeau !",
      "schedule": {
        "pattern": {
          "type": "YearlyRecurrent",
          "dates": [{"month": 11, "day": 25, "time": "10:00"}]
        },
        "timezone": "Europe/Paris",
        "enabled": true,
        "startDate": 1704067200000,
        "endDate": null,
        "nextExecutionTime": 1732528800000
      },
      "triggers": null,
    },
    {
      "id": "msg_reality_check_1",
      "title": "Vérification réalité",
      "content": "Es-tu en train de rêver ?",
      "schedule": null,
      "triggers": null,
    }
  ],
  "external_notifications": true
}
```



**Champs spécifiques Messages** :

- **`messages`** : Array d'items prédéfinis (pattern "items prédéfinis" comme Tracking choice items)
  - Chaque item = template de notification
  - Peut avoir schedule (planifié) ou null (on-demand : IA/user ajoutera plus tard)

- **`messages[].id`** : Identifiant unique string
  - Utilisé par data entries pour référencer le template
  - Format libre (ex: "msg_anniversary_marie")

- **`messages[].title`** : Titre message (obligatoire)
  - Affiché dans notification Android
  - Limite : SHORT_LENGTH (60 chars)

- **`messages[].content`** : Corps du message (optionnel)
  - Si null : notification = juste titre
  - Limite : LONG_LENGTH (1500 chars)

- **`messages[].schedule`** : Planning (nullable)
  - Réutilise structure `ScheduleConfig` existante (voir core/utils/ScheduleConfig.kt)
  - 6 patterns disponibles : DailyMultiple, WeeklySimple, MonthlyRecurrent, WeeklyCustom, YearlyRecurrent, SpecificDates
  - Si null : message on-demand (pas auto-planifié)

- **`messages[].triggers`** : Déclencheurs événementiels (STUB)
  - Toujours null pour MVP
  - Prévu pour futur : déclenchement sur événements système (ex: "quand tracking poids < 70kg")
  - Permet cohérence future sans breaking change

- **`priority`** : Niveau notification Android
  - `"default"` : Badge drawer, pas de popup
  - `"high"` : Popup heads-up (interruption visuelle)
  - `"low"` : Silencieux, pas de son/vibration
  - Au niveau de la config (valable pour tous les messages de l'instance d'outil)

- **`external_notifications`** : Boolean global instance
  - `true` : Envoyer notifications Android
  - `false` : Messages visibles seulement dans app (pas de notifs système)

**Décisions importantes** :

1. **Schedule par item** (pas global) : Chaque message a son propre planning, indépendant des autres
2. **Schedule nullable** : Permet messages on-demand que l'IA planifiera dynamiquement plus tard
3. **Réutilisation ScheduleConfig** : Infrastructure existante automations (ScheduleCalculator, ScheduleConfigEditor UI)

### 2.2 Structure data (messages envoyées)

```json
{
  "schema_id": "messages_data",
  "message_id": "msg_anniversary_marie",
  "scheduled_time": 1732528800000,
  "status": "sent",
  "sent_at": 1732528805123,
  "read": false,
  "archived": false
}
```

**Champs data** :

- **`message_id`** : Référence vers `config.messages[].id`
  - Permet retrouver titre/contenu/priority du template

- **`scheduled_time`** : Timestamp quand devait être envoyé
  - Stocké dans `timestamp` de l'entité DB (cohérence autres tools)
  - Calculé par ScheduleCalculator selon schedule pattern

- **`status`** : Lifecycle primaire (mutuellement exclusif)
  - `"pending"` : Créé mais pas encore envoyé (rare, fenêtre courte)
  - `"sent"` : Notification envoyée
  
- **`sent_at`** : Timestamp envoi effectif (null si pending/cancelled)
  - Permet audit : différence avec scheduled_time = latence système

- **`read`** : Flag action user (orthogonal au status)
  - User a ouvert/consulté le message dans l'app
  - Toggleable (peut marquer non-lu après lecture)

- **`archived`** : Flag action user (orthogonal au status)
  - User a marqué message comme traité/terminé
  - Messages archivés = masqués par défaut dans liste

**Génération automatique** :

Les data entries sont créées **au moment du tick** par MessageScheduler :
1. Scan config.messages[] avec schedule.enabled = true
2. Calcul nextExecutionTime via ScheduleCalculator
3. Si <= now : créer data entry + envoyer notif + update nextExecutionTime

**Pas de pré-génération** : Contrairement au pattern "générer toutes les occurrences futures", on génère à la demande (comme automations). Raison : planning peut changer, messages modifiables.

## 3. Scheduling centralisé

### 3.1 Problème architectural

**Contexte** : Actuellement le heartbeat est dans `AISessionScheduler` (core/ai) pour automations uniquement.

**Besoin** : Messages tool nécessite aussi scheduling, et futurs tools pourraient en avoir besoin (Alertes, etc.).

**Solution** : Système de scheduling centralisé avec discovery pattern.

### 3.2 CoreScheduler (nouveau)

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

**Heartbeat** (à déplacer depuis AI) :
- **Coroutine** : 1 minute (app ouverte)
- **WorkManager** : 15 minutes (app fermée, background)
- **Triggers événementiels** : CRUD automations, CRUD messages, fin de session

**Décision importante** : Un seul point d'entrée tick() qui délègue, évite duplication logique WorkManager/Coroutine.

### 3.3 ToolScheduler interface (nouveau)

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
        //    a. Charger config via coordinator
        //    b. Parser config.messages[]
        //    c. Pour chaque item avec schedule != null && schedule.enabled == true:
        //       - Calculer nextExecutionTime via ScheduleCalculator.calculateNext()
        //       - Si nextExecutionTime <= System.currentTimeMillis():
        //         * Créer data entry (status: pending) via coordinator
        //         * Appeler NotificationService.send() avec titre/contenu/priority
        //         * Update data entry (status: sent, sent_at: now)
        //         * Update config schedule.nextExecutionTime

        // 3. Gestion erreurs: Log + continue (un échec ne bloque pas autres messages)
    }
}
```

**Utilise `ScheduleCalculator`** (core/utils) :
- Classe existante calculant nextExecutionTime selon pattern
- Utilisée aussi par automations (cohérence)

**Atomicité** : Génération data + envoi notif + update status = séquence complète pour chaque message.

**Pas de retry** : Si envoi notif échoue, marquer status=sent quand même (pas d'état "error" pour MVP). Notif Android = best effort.

## 4. NotificationService (nouveau core service)

### 4.1 Positionnement

**Package** : `core/services/NotificationService.kt`

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
  "channel_id": String,         // "messages", "alerts", "system"
  "priority": String            // "default"|"high"|"low"
}
```

**Responsabilité** :
- Créer notification Android avec params fournis
- Gérer channels (création si nécessaire)
- Mapper priority → NotificationCompat.PRIORITY_*
- Retourner success ou erreur

**Pas de persistence** : Service stateless, juste façade Android API.

## 5. Services et operations

### 5.1 MessageService

**Package** : `tools/messages/MessageService.kt`

**Type** : ExecutableService (comme TrackingService, JournalService, etc.)

**Operations disponibles** (user uniquement, pas IA) :

**`get`** : Liste messages avec filtres
```kotlin
params: {
  toolInstanceId: String,
  filters?: {
    status?: "pending|sent|cancelled",
    read?: Boolean,
    archived?: Boolean
  },
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

**`mark_read`** : Toggle flag read
```kotlin
params: {id: String, read: Boolean}
returns: {success: true}
```

**`archive`** : Marquer archivé
```kotlin
params: {id: String}
returns: {success: true}
```

**`delete`** : Supprimer data entry
```kotlin
params: {id: String}
returns: {success: true}
```

**`stats`** : Statistiques instance
```kotlin
params: {toolInstanceId: String}
returns: {unread: Int, archived: Int, sent: Int, pending: Int}
```

**Pattern d'appel** : `coordinator.processUserAction("messages.operation", params)`

### 5.2 Manipulation messages prédéfinis (config)

IA et user utilisent operations standard tools :

- **Lecture** : `tools.get` avec `tool_instance_id`
  - Retourne config complet incluant `messages[]`

- **Modification** : `tools.update` avec `tool_instance_id` + `config` complet
  - Modifier array messages (ajouter/éditer/supprimer items)
  - Service valide via `messages_config` schema

**Flow IA typique** :
1. `TOOL_CONFIG` → récupère config avec messages existants
2. Modifie JSON (ajoute item dans array ou édite schedule)
3. `UPDATE_TOOL` → sauvegarde config complet

## 6. Interface utilisateur

### 6.1 Écran principal Messages

**Package** : `tools/messages/ui/MessagesScreen.kt`

**Déclenchement** : Click sur ToolCard Messages dans ZoneScreen

**Structure** :

**Header** : PageHeader avec titre instance + bouton settings (→ ConfigScreen)

**Filtre** : FormSelection avec options
- "Tous"
- "Non lus" (défaut au chargement)
- "Lus"
- "Archivés"

**Liste messages** : LazyColumn avec data entries selon filtre
- Tri : plus récents en premier (par scheduled_time DESC)
- Affichage par entry :
  - Date/heure (scheduled_time formaté)
  - Titre (depuis config.messages[] via message_id)
  - Badge status si non-sent (pending, cancelled)
  - Badge "Non lu" si sent && !read

**Actions par message** :
- Click sur entry → toggle read
- bouton → Archiver
- bouton → Supprimer (confirmation)

**Empty state** : "Aucun message" si liste vide selon filtre

**Loading/Error** : Pattern standard (isLoading, errorMessage avec Toast)

### 6.2 ConfigScreen

**Package** : `tools/messages/ui/MessagesConfigScreen.kt`

**Structure** : Column scrollable avec sections

**Section 1 : Configuration générale**
- `ToolGeneralConfigSection` (8 champs standard)

**Section 2 : Champs spécifiques**
1) notifs android
- Toggle `external_notifications`
- Label : "Afficher notifications système"
2) priority

**Section 3 : Messages prédéfinis**
- Liste items config.messages[]
- Affichage par item :
  - Titre
  - Schedule summary ou "On-demand"
  - Boutons : Éditer, Supprimer
- Bouton : "Ajouter message"

**Dialog édition item** : Modal avec formulaire
- FormField titre (TEXT, requis)
- FormField contenu (TEXT_LONG, optionnel)
- Bouton "Configurer planning" → ouvre ScheduleConfigEditor
  - Réutilise composable existant (core/ai/ui/automation/ScheduleConfigEditor.kt)
  - OnConfirm : stocke ScheduleConfig dans item.schedule
  - Peut retourner null (pas de schedule = on-demand)
- Section triggers : Disabled/grayed avec label "Pas encore implémenté"

**Sauvegarde** : ValidationHelper.validateAndSave() puis tools.update via coordinator

**Pattern référence** : Journal pour structure (outil simple), Tracking pour items prédéfinis array (outil complexe)

## 7. Base de données

### 7.1 Entity

**Package** : `tools/messages/data/MessageData.kt`

```kotlin
@Entity(tableName = "message_data")
data class MessageData(
    @PrimaryKey val id: String,
    val toolInstanceId: String,
    val timestamp: Long,           // = scheduled_time
    val value: String,             // JSON: {message_id, status, sent_at, read, archived}
    val metadata: String?,         // Vide pour MVP, cohérence autres tools
    val createdAt: Long
)
```

**Index** :
- `toolInstanceId` (queries par instance)
- `timestamp` (tri chronologique)

**Pattern uniforme** : Même structure que TrackingData, JournalData, NotesData.

### 7.2 DAO

**Package** : `tools/messages/data/MessageDao.kt`

```kotlin
@Dao
interface MessageDao {
    @Query("SELECT * FROM message_data WHERE toolInstanceId = :toolInstanceId ORDER BY timestamp DESC")
    suspend fun getByToolInstance(toolInstanceId: String): List<MessageData>

    @Query("SELECT * FROM message_data WHERE id = :id")
    suspend fun getById(id: String): MessageData?

    @Insert
    suspend fun insert(data: MessageData)

    @Update
    suspend fun update(data: MessageData)

    @Delete
    suspend fun delete(data: MessageData)
}
```

**Pattern standard** : Comme autres tools DAOs.

## 8. ToolType implementation

### 8.1 MessageToolType

**Package** : `tools/messages/MessageToolType.kt`

**Type** : Object singleton implémentant ToolTypeContract

```kotlin
object MessageToolType : ToolTypeContract {
    // Métadonnées
    override fun getDisplayName(context: Context): String
    override fun getDescription(context: Context): String
    override fun getSuggestedIcons(): List<String>  // notification, bell, message
    override fun getDefaultConfig(): String         // JSON avec messages: [], external_notifications: true
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
    override fun getDatabaseMigrations(): List<Migration>

    // Scheduling (nouveau)
    override fun getScheduler(): ToolScheduler      // MessageScheduler
}
```

**Enregistrement** : Ajouter dans `ToolTypeScanner.getAllToolTypes()` :
```kotlin
"messages" to MessageToolType
```

## 9. Décisions architecturales récapitulatives

### 9.1 Choix structurels

1. **Tool vs Core** : Outil pour bénéficier zones, IA commands, extensibilité
2. **Scheduling centralisé** : CoreScheduler découvre schedulers via interface ToolTypeContract
3. **NotificationService core** : Réutilisable futurs tools, pas de provider (une seule impl)
4. **Pattern items prédéfinis** : Config = templates, Data = instances (comme Tracking choice items)

### 9.2 Choix fonctionnels

5. **Schedule par item** : Chaque message son propre planning indépendant
6. **Schedule nullable** : Permet on-demand (IA planifiera plus tard)
7. **Réutilisation ScheduleConfig** : Infrastructure existante (6 patterns, ScheduleCalculator, UI)
8. **Data ultra-simple** : Juste refs + timestamps + flags, générée au tick (pas pré-générée)
9. **Status + flags** : Status lifecycle (pending/sent/cancelled) + flags user orthogonaux (read/archived)
10. **Priority par config globale**
11. **Triggers STUB** : Prévu mais non implémenté (cohérence future, pas de breaking change)

## 10. Ordre d'implémentation recommandé

**Méthodologie** : Lire fichier de référence d'un autre outil (simple : Note ou Journal, complexe : Tracking)

### Phase 1 : Infrastructure scheduling
1. Interface `ToolScheduler` + extension `ToolTypeContract.getScheduler()`
2. `CoreScheduler` avec logique tick()
3. Déplacement heartbeat depuis AI vers CoreScheduler
4. Test : Vérifier automations fonctionnent toujours

### Phase 2 : Notification service
5. `NotificationService` (core simple)
6. Test : Appel manuel send() affiche notif Android

### Phase 3 : Data layer
7. Schémas JSON (`MessagesSchemas.kt` : messages_config, messages_data)
8. Entity `MessageData` + DAO
9. Migration DB (ajout table message_data)

### Phase 4 : Business logic
10. `MessageService` (operations user)
11. `MessageScheduler` (logique tick)
12. Test : Tick manuel crée data + envoie notif

### Phase 5 : Tool contract
13. `MessageToolType` (implémentation complète contrat)
14. Enregistrement `ToolTypeScanner`
15. Test : Instance créable, schémas accessibles

### Phase 6 : UI
16. `MessagesConfigScreen` (réutilise ScheduleConfigEditor)
17. `MessagesScreen` (liste + filtres + actions)
18. `MessagesDisplayComponent` (minimal)
19. Strings (shared + tool)

### Phase 7 : Validation
20. Tests end-to-end : Créer instance → configurer message → attendre tick → vérifier notif + data
21. Tests edge cases : Schedule null, external_notifications false, priority variations

---
