# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Flow principal
```
User message → AIOrchestrator → PromptManager → CommandExecutor → AIClient
              ↕                ↕             ↕
          AISessionService   QueryDeduplicator  AIProviderConfigService
          (via coordinator)  (prompt dedup)    (via coordinator)
```

### Session types
- **CHAT** : Conversation temps réel, queries absolues, modules communication
- **AUTOMATION** : Prompt programmable, queries relatives, feedback exécutions

## 2. Services et responsabilités

### Séparation des responsabilités selon CORE.md

**Services ExecutableService (accès DB via coordinator) :**
- `AISessionService` : CRUD sessions et messages
- `AIProviderConfigService` : CRUD configurations providers

**Classes métier pures (logique sans DB) :**
- `AIOrchestrator` : Orchestration complète du flow IA
- `AIClient` : Interface vers providers AI externes
- `PromptManager` : Génération prompts 4 niveaux avec commands unifiées
- `EnrichmentProcessor` : Génération commands depuis enrichments UI bruts
- `UserCommandProcessor` : Transformation commands user (résolution relatives)
- `AICommandProcessor` : Transformation commands AI (validation sécurité)
- `CommandExecutor` : Exécution commands vers coordinator, formatage résultats
- `QueryDeduplicator` : Déduplication cross-niveaux pour prompts
- `AppConfigManager` : Cache singleton pour config app (dayStartHour, weekStartDay)

### AIOrchestrator (orchestrateur central)
```kotlin
class AIOrchestrator(private val context: Context) {
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult
    suspend fun createSession(name: String, type: SessionType, providerId: String): String
    suspend fun setActiveSession(sessionId: String): OperationResult
    suspend fun loadSession(sessionId: String): AISession?
}
```

**Flow complet orchestré :**
1. Stocker message utilisateur via `AISessionService`
2. Builder prompt via `PromptManager` (pipeline command processing complet)
3. Envoyer à `AIClient`
4. Traiter réponse et stocker via `AISessionService`

### Command Processing Pipeline
```
EnrichmentBlock (stocké) → EnrichmentProcessor → UserCommandProcessor → CommandExecutor
                                            ↓
                                 List<DataCommand> → List<ExecutableCommand> → coordinator calls
```

**Responsabilités séparées :**
- `EnrichmentProcessor` : EnrichmentBlock brut → N DataCommands
- `UserCommandProcessor` : Résolution périodes relatives, UI abstractions
- `AICommandProcessor` : Validation sécurité, limites données, token management
- `CommandExecutor` : ExecutableCommand → resource.operation + coordinator
- `QueryDeduplicator` : Déduplication cross-niveaux pour assemblage prompt final

### AISessionService (ExecutableService)
```kotlin
class AISessionService(context: Context) : ExecutableService {
    // Operations: create_session, get_session, set_active_session, create_message, etc.
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult
}
```

**Ressource coordinator :** `ai_sessions.operation`

### AIProviderConfigService (ExecutableService)
```kotlin
class AIProviderConfigService(context: Context) : ExecutableService {
    // Operations: get, set, list, delete, set_active, get_active
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult
}
```

**Ressource coordinator :** `ai_provider_config.operation`

### AIClient (logique métier pure)
```kotlin
class AIClient(private val context: Context) {
    suspend fun query(promptResult: PromptResult, providerId: String): OperationResult
    suspend fun getAvailableProviders(): List<AIProviderInfo>
    suspend fun getActiveProviderId(): String?
}
```

Interface vers providers externes, utilise `AIProviderConfigService` via coordinator pour récupérer configurations.

## 3. Structures de données unifiées

### AISession (complète)
```kotlin
data class AISession(
    val id: String,
    val name: String,
    val type: SessionType,
    val providerId: String,
    val providerSessionId: String,
    val schedule: ScheduleConfig?,        // Pour AUTOMATION seulement
    val createdAt: Long,
    val lastActivity: Long,
    val messages: List<SessionMessage>,
    val isActive: Boolean
)
```

### SessionMessage (structure unifiée)
```kotlin
data class SessionMessage(
    val id: String,
    val timestamp: Long,
    val sender: MessageSender,     // USER, AI, SYSTEM
    val richContent: RichMessage?, // Messages enrichis utilisateur
    val textContent: String?,      // Messages simples (réponses modules)
    val aiMessage: AIMessage?,     // Structure IA parsée pour UI
    val aiMessageJson: String?,    // JSON original pour historique prompts
    val systemMessage: SystemMessage?, // Messages système auto
    val executionMetadata: ExecutionMetadata? // Automations uniquement
)
```

### RichMessage (messages utilisateur avec enrichissements)
```kotlin
data class RichMessage(
    val segments: List<MessageSegment>,
    val linearText: String,           // Calculé : version textuelle pour IA
    val dataCommands: List<DataCommand>  // Calculé : commands pour prompts
)

sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()
    data class EnrichmentBlock(
        val type: EnrichmentType,
        val config: String,       // Configuration JSON
        val preview: String       // Preview lisible "données nutrition zone Santé"
    ) : MessageSegment()
}
```

### AIMessage (réponses IA structurées)
```kotlin
data class AIMessage(
    val preText: String,                              // Obligatoire
    val validationRequest: ValidationRequest?,        // Validation avant actions
    val dataCommands: List<DataCommand>?,             // OU actions (exclusif)
    val actionCommands: List<DataCommand>?,           // OU dataCommands
    val postText: String?,                            // Seulement si actions
    val communicationModule: CommunicationModule?     // Toujours en dernier
)
```

**Contrainte importante** : `dataCommands` et `actionCommands` mutuellement exclusifs.

### DataCommand (unifié)
```kotlin
data class DataCommand(
    val id: String,              // Hash déterministe de (type + params + isRelative)
    val type: String,            // Command type standardisé (voir types disponibles)
    val params: Map<String, Any>, // Paramètres absolus ou relatifs
    val isRelative: Boolean = false // true pour automation, false pour chat
)
```

### ExecutableCommand
```kotlin
data class ExecutableCommand(
    val resource: String,         // "zones", "tool_data"
    val operation: String,        // "get", "create", "update"
    val params: Map<String, Any>  // Paramètres résolus pour coordinator
)
```

**Types de commands utilisateur (enrichments) :**
- **SCHEMA** → id
- **TOOL_CONFIG** → id
- **TOOL_DATA** → id + périodes + filtres
- **TOOL_STATS** → id + périodes + agrégation
- **TOOL_DATA_SAMPLE** → id + limit
- **ZONE_CONFIG** → id
- **ZONES** → aucun paramètre
- **TOOL_INSTANCES** → zone_id optionnel

**Operations batch IA :**
- `batch_create` : Création multiple tool_data
- `batch_update` : Mise à jour multiple tool_data

## 4. Communication bidirectionnelle

### Messages riches (texte + informations de données / instructions)
Les `RichMessage` combinent texte libre et blocs d'enrichissement pour créer des interactions contextuelles :

```kotlin
data class RichMessage(
    val segments: List<MessageSegment>,    // Texte + EnrichmentBlock
    val linearText: String,                // Version textuelle pour IA
    val dataCommands: List<DataCommand>    // Commands générées automatiquement
)
```

### Communication Modules
Modules de communication générés par l'IA pour obtenir des réponses structurées de l'utilisateur :

```kotlin
sealed class CommunicationModule {
    data class MultipleChoice(val question: String, val options: List<String>)
    data class Validation(val message: String)
    // TODO: Slider, DataSelector
}
```

### Validation et permissions
Système hiérarchique contrôlant les actions IA :
- `autonomous` - IA agit directement
- `validation_required` - Confirmation utilisateur obligatoire
- `forbidden` - Action interdite
- `ask_first` - Permission avant proposition

### Flow de réponse utilisateur
1. IA génère `AIMessage` avec `CommunicationModule`
2. UI affiche le module approprié (choix multiple, validation, etc.)
3. Réponse utilisateur stockée dans `SessionMessage.textContent`
4. IA traite la réponse pour actions suivantes

## 5. Enrichissements et composition des messages

### Types d'enrichissements
- **🔍 POINTER** - Référencer données (zones ou instances)
- **📝 USE** - Utiliser données d'outils (config + schemas + data sample + stats)
- **✨ CREATE** - Créer éléments (schemas pour type d'outil)
- **🔧 MODIFY_CONFIG** - Modifier config outils (schema + config actuelle)

### EnrichmentProcessor
```kotlin
class EnrichmentProcessor {
    fun generateSummary(type: EnrichmentType, config: String): String
    fun generateCommands(
        type: EnrichmentType,
        config: String,
        isRelative: Boolean,
        dayStartHour: Int,
        weekStartDay: String
    ): List<DataCommand>
}
```

**Responsabilités périodes :**
- **CHAT** (`isRelative=false`) : Calcule timestamps absolus (début + fin période) via Period objects
- **AUTOMATION** (`isRelative=true`) : Encode périodes relatives format "offset_TYPE" (ex: "-1_WEEK")

### UserCommandProcessor
```kotlin
class UserCommandProcessor(private val context: Context) {
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand>
}
```

**Transformations par type :**
- `TOOL_CONFIG` → `tools.get` (id → tool_instance_id)
- `TOOL_DATA` → `tool_data.get` (résolution périodes relatives si `isRelative=true`)
- `TOOL_STATS` → `tool_data.stats` (agrégation données)
- `TOOL_DATA_SAMPLE` → `tool_data.get` (limit + orderBy recent)
- `ZONE_CONFIG` → `zones.get` (id → zone_id)
- `ZONES` → `zones.list`
- `TOOL_INSTANCES` → `tools.list` (avec zone_id) ou `tools.list_all` (sans zone_id)

**Résolution périodes relatives :** Parse "offset_TYPE" → timestamps absolus via `resolveRelativePeriod()` + `AppConfigManager`

### AICommandProcessor
```kotlin
class AICommandProcessor {
    fun processDataCommands(commands: List<DataCommand>): List<ExecutableCommand>
    fun processActionCommands(commands: List<DataCommand>): List<ExecutableCommand>
}
```

**Responsabilités :**
- Validation sécurité et permissions
- Vérification limites données
- Token management
- Cascade failure pour actions (arrêt sur première erreur)

**Logique enrichissement par type :**

**POINTER** :
- Zone → `ZONE_CONFIG` + `TOOL_INSTANCES`
- Instance → `SCHEMA(config)` + `SCHEMA(data)` + `TOOL_CONFIG` + `TOOL_DATA_SAMPLE` + optionnellement `TOOL_DATA` réelles (toggle "inclure données")

**CREATE** : `SCHEMA(config_schema_id)` + `SCHEMA(data_schema_id)` pour type d'outil

**MODIFY_CONFIG** : `SCHEMA(config_schema_id)` + `TOOL_CONFIG(tool_instance_id)`

**USE** : `TOOL_CONFIG` + `SCHEMA(config)` + `SCHEMA(data)` + `TOOL_DATA_SAMPLE` + `TOOL_STATS`

### Composition via RichComposer
Le `RichComposer` permet à l'utilisateur de combiner texte et enrichissements, générant automatiquement `linearText` et `dataCommands` via `EnrichmentProcessor.generateCommands()`.

### Différences User vs AI Commands

**User Commands** :
- Source : EnrichmentBlocks dans RichMessage
- Types : POINTER, USE, CREATE, MODIFY_CONFIG uniquement
- But : Fournir données contextuelles à l'IA
- **Jamais d'actions directes**

**AI Commands** :
- Source : AIMessage.dataCommands + AIMessage.actionCommands
- Types : Data queries + actions réelles (create, update, delete)
- But : Demander données + exécuter actions

## 6. Architecture des niveaux de prompts

**Level 1: DOC** - Documentation système statique
- Rôle IA + intro application
- Documentation API (format réponse, commandes, schema_ids)
- Schéma zone complet + liste tous schema_ids
- Tooltypes (nom + description) + leurs schema_ids

**Level 2: USER DATA** - Données utilisateur systématiques
- Config IA utilisateur (non implémenté)
- Données complètes des tool instances avec `always_send: true`

**Level 3: APP STATE** - État application complet
- Toutes les zones avec configs + tool instances avec configs

**Level 4: SPECIFIC DATA** - Données ciblées
- Résultats enrichissements utilisateur
- Résultats commandes IA précédentes

### Pipeline de génération
**Tous niveaux regénérés à chaque prompt** pour données fraîches

**Level 4 extraction :** `EnrichmentBlock` (stocké) → `List<DataCommand>` (généré) dans pipeline. `PromptManager.getLevel4Commands()` extrait EnrichmentBlocks depuis historique messages et génère commands à la volée.

### Pipeline de traitement commands
**Pipeline stateless :** Régénération complète à chaque message
```kotlin
// Flow paramètres : UI → EnrichmentProcessor → UserCommandProcessor → Services
EnrichmentBlocks → EnrichmentProcessor → UserCommandProcessor → CommandExecutor
```

**Échec cascade :** Géré par AICommandProcessor pour les actions (arrêt sur échec action pour cohérence état app)

**Flow paramètres :**
1. **UI** : Paramètres bruts (périodes relatives, sélections UI)
2. **EnrichmentProcessor** : Traduction relatif/absolu selon session type
3. **UserCommandProcessor** : Transformation en paramètres service (QUERY_PARAMETERS_SPEC)

### CommandExecutor
```kotlin
class CommandExecutor(private val context: Context) {
    suspend fun executeCommands(commands: List<ExecutableCommand>, level: String): List<CommandResult>
}

data class CommandResult(
    val dataTitle: String,       // Titre section données prompt (ex: "Data from tool 'X', period: ...")
    val formattedData: String,   // JSON avec métadonnées en premier (vide pour actions)
    val systemMessage: String    // Résumé pour historique conversation
)
```

**Formatage par opération :**
- **Queries** (get, list) : dataTitle + formattedData (JSON métadonnées avant bulk data) + systemMessage
- **Actions** (create, update, delete, batch_create, batch_update) : systemMessage uniquement

**System messages exemples :**
- Query : "150 data points from tool 'Sleep Tracker' added"
- Action : "Created tool instance 'Morning Routine'"
- Batch : "Created 50 data points in tool 'Sleep Tracker'"

### QueryDeduplicator
**Principe** : Déduplication cross-niveaux dans PromptManager avant exécution

**Mécanismes :**
1. **Hash identité** : Commands identiques supprimées (première occurrence gardée)
2. **Inclusion métier** : Commands générales incluent spécifiques selon règles business

### Storage Policy

**Ce qui EST stocké :**
- `AISession.messages: List<SessionMessage>`
- `SessionMessage` avec données brutes : EnrichmentBlocks dans RichMessage, aiMessageJson

**Ce qui N'EST JAMAIS stocké :**
- DataCommand (temporaire pipeline)
- ExecutableCommand (temporaire pipeline)
- Résultats commands dans prompt
- Prompt final assemblé

**Régénération complète :** Pipeline stateless à chaque message pour données toujours fraîches

**Stratégie Cache :** Envoi complet du prompt à chaque message. L'API (Claude, OpenAI, etc.) gère le cache automatiquement via préfixes identiques. **Décision architecturale** : Cohérence données > optimisation tokens.

### Dual mode résolution (CHAT absolu vs AUTOMATION relatif)

**Types de périodes :**
```kotlin
data class Period(val timestamp: Long, val type: PeriodType)  // Période absolue (point dans le temps)
data class RelativePeriod(val offset: Int, val type: PeriodType)  // Offset depuis maintenant
```

**CHAT** (`isRelative=false`) :
- "cette semaine" → `Period` avec timestamps absolus (début + fin calculés via `getPeriodEndTimestamp()`)
- Stocké dans `timestampSelection.minPeriod` / `maxPeriod`
- Garantit cohérence conversation sur plusieurs jours

**AUTOMATION** (`isRelative=true`) :
- "cette semaine" → `RelativePeriod(offset=0, type=WEEK)` → encodé "0_WEEK"
- Stocké dans `timestampSelection.minRelativePeriod` / `maxRelativePeriod`
- Résolu à l'exécution via `resolveRelativePeriod()` + `AppConfigManager`

**AppConfigManager :** Singleton cache pour `dayStartHour` et `weekStartDay` (initialisé au démarrage app)

**Token Management :** Validation globale prompt par PromptManager. Gestion différenciée CHAT (dialogue confirmation) vs AUTOMATION (refus automatique) si dépassement.

## 7. Providers

### AIProvider interface
```kotlin
interface AIProvider {
    fun getDisplayName(): String
    fun getConfigSchema(): String
    @Composable fun getConfigScreen(config: String, onSave: (String) -> Unit)
    suspend fun query(prompt: String, config: String): AIResponse
}
```

### Configuration et découverte
- Configurations gérées par `AIProviderConfigService` via coordinator
- Providers découverts via `AIProviderRegistry`
- `AIClient` utilise coordinator pour récupérer configs (pas d'accès direct DB)
