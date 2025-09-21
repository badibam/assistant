# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Flow principal
```
User message → AIOrchestrator → PromptManager → QueryExecutor → AIClient
              ↕                ↕             ↕
          AISessionService   QueryDeduplicator  AIProviderConfigService
          (via coordinator)                     (via coordinator)
```

### Session types
- **CHAT** : Conversation temps réel, queries absolues, modules communication
- **AUTOMATION** : Prompt programmable, queries relatives, feedback exécutions

## 2. Architecture des services

### Séparation des responsabilités selon CORE.md

**Services ExecutableService (accès DB via coordinator) :**
- `AISessionService` : CRUD sessions et messages
- `AIProviderConfigService` : CRUD configurations providers

**Classes métier pures (logique sans DB) :**
- `AIOrchestrator` : Orchestration complète du flow IA
- `AIClient` : Interface vers providers AI externes
- `PromptManager` : Génération prompts 4 niveaux avec queries unifiées
- `QueryExecutor` : Exécution queries tous niveaux avec déduplication cross-niveaux
- `QueryDeduplicator` : Déduplication queries par hash et inclusion métier

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
1. Ajouter enrichments Level 4 (validation tokens)
2. Stocker message utilisateur via `AISessionService`
3. Builder prompt via `PromptManager`
4. Envoyer à `AIClient`
5. Traiter réponse et stocker via `AISessionService`

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

## 3. Structures de données

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
    val dataQueries: List<DataQuery>  // Calculé : queries pour prompts
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
    val dataRequests: List<DataQuery>?,               // OU actions (exclusif)
    val actions: List<AIAction>?,                     // OU dataRequests
    val postText: String?,                            // Seulement si actions
    val communicationModule: CommunicationModule?     // Toujours en dernier
)
```

**Contrainte importante** : `dataRequests` et `actions` mutuellement exclusifs.

### DataQuery (dual mode)
```kotlin
data class DataQuery(
    val id: String,              // Hash déterministe de (type + params + isRelative)
    val type: String,            // Query type standardisé (voir types disponibles)
    val params: Map<String, Any>, // Paramètres absolus ou relatifs
    val isRelative: Boolean = false // true pour automation, false pour chat
)
```

**Types de queries unifiés :**
- **SYSTEM_SCHEMAS**, **SYSTEM_DOC**, **APP_CONFIG** (Level 1)
- **USER_TOOLS_CONTEXT** (Level 2)
- **APP_STATE** (Level 3)
- **ZONE_CONFIG**, **ZONE_STATS** (Level 4)
- **TOOL_CONFIG**, **TOOL_DATA_FULL**, **TOOL_DATA_SAMPLE**, **TOOL_DATA_FIELD**, **TOOL_STATS** (Level 4)

## 4. Système de prompts unifié

### Architecture 4 niveaux avec QueryExecutor
- **Level 1** : Documentation + schémas système (SYSTEM_SCHEMAS, SYSTEM_DOC, APP_CONFIG)
- **Level 2** : Contexte utilisateur dynamique (USER_TOOLS_CONTEXT)
- **Level 3** : État app complet (APP_STATE)
- **Level 4** : Enrichissements session (ZONE_CONFIG, TOOL_DATA_SAMPLE, etc.)

### Déduplication cross-niveaux
**Principe** : QueryExecutor déduplique incrémentalement niveau par niveau
```kotlin
// Déduplication incrémentale préservant l'ordre
level1Content = queryExecutor.executeQueries(level1Queries, "Level1")
level2Content = queryExecutor.executeQueries(level2Queries, "Level2", previousQueries = level1Queries)
level3Content = queryExecutor.executeQueries(level3Queries, "Level3", previousQueries = level1Queries + level2Queries)
level4Content = queryExecutor.executeQueries(level4Queries, "Level4", previousQueries = level1Queries + level2Queries + level3Queries)
```

**Mécanismes de déduplication :**
1. **Hash identité** : Queries identiques supprimées (premier occurrence gardée)
2. **Inclusion métier** : Queries plus générales incluent spécifiques (stub)
3. **Schémas identiques** : Post-résolution par `x-schema-id`

### Système de schémas avec déduplication
**Config vs Data schemas :**
- **CONFIG** : Schémas conditionnels simples avec `x-schema-id` par type
- **DATA** : Schémas conditionnels complexes avec `x-schema-id` par résolution

**Format uniforme :**
```json
{
  "x-schema-id": "tracking_data_numeric",
  "x-schema-display-name": "{{TRACKING_DATA_NUMERIC_SCHEMA_DISPLAY_NAME}}",
  "properties": { ... }
}
```

### Dual mode résolution
**CHAT** (`isRelative = false`) :
- "cette semaine" → timestamps figés absolus (`1705276800000, 1705881599999`)
- Cohérence conversationnelle reproductible

**AUTOMATION** (`isRelative = true`) :
- "cette semaine" → paramètre relatif (`"period": "current_week"`)
- Résolu au moment exécution via `resolveRelativeParams()`

### TokenCalculator
```kotlin
TokenCalculator.estimateTokens(text: String, providerId: String, context: Context): Int
TokenCalculator.checkTokenLimit(content: String, context: Context, isQuery: Boolean): TokenLimitResult
```

Validation pré-envoi :
- **CHAT** : Dialogue confirmation si dépassement
- **AUTOMATION** : Refus automatique

## 5. Enrichissements multi-queries

### Types et génération queries
- **🔍 POINTER** - Référencer données → Multi-queries selon niveau sélection
- **📝 USE** - Modifier données outils → Multi-queries (stub)
- **✨ CREATE** - Créer éléments → Pas de query (orientation seulement)
- **🔧 MODIFY_CONFIG** - Config outils → Multi-queries (stub)

### EnrichmentSummarizer
```kotlin
class EnrichmentSummarizer {
    fun generateSummary(type: EnrichmentType, config: String): String
    fun generateQueries(type: EnrichmentType, config: String, isRelative: Boolean): List<DataQuery>
}
```

### Logique POINTER multi-queries
**Selon niveau de sélection ZoneScopeSelector :**
- **ZONE** → `[ZONE_CONFIG, ZONE_STATS]`
- **INSTANCE** → `[TOOL_CONFIG, TOOL_DATA_SAMPLE]` + gestion temporelle
- **FIELD** → `[TOOL_DATA_FIELD]` + mode sample_entries + gestion temporelle

Transformation automatique : `EnrichmentBlock` → `List<DataQuery>` selon niveau et importance. Ces queries sont stockées dans le message utilisateur et réutilisées depuis l'historique pour les prompts suivants.

## 6. Providers

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

### Validation et permissions
Système hiérarchique avec 4 niveaux :
- `autonomous` - IA agit directement
- `validation_required` - Confirmation utilisateur
- `forbidden` - Action interdite
- `ask_first` - Permission avant proposition

## 7. Components UI

### RichComposer
```kotlin
@Composable
fun UI.RichComposer(
    segments: List<MessageSegment>,
    onSegmentsChange: (List<MessageSegment>) -> Unit,
    onSend: (RichMessage) -> Unit,
    sessionType: SessionType = SessionType.CHAT
)
```

Fonctionnalités :
- Textarea + enrichment blocks
- Configuration via dialogs overlay
- Génération automatique `linearText` et `dataQueries` multi-queries
- Integration `EnrichmentSummarizer.generateQueries()`

### AIFloatingChat
Interface 100% écran avec header + messages + composer. Orchestration complète via `AIOrchestrator`.

### Communication Modules
Générés par l'IA, remplis par le user
```kotlin
sealed class CommunicationModule {
    data class MultipleChoice(val question: String, val options: List<String>)
    data class Validation(val message: String)
    // TODO: Slider, DataSelector
}
```

## 8. Base de données

### AISessionEntity (schéma final)
```kotlin
@Entity(tableName = "ai_sessions")
data class AISessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: SessionType,
    val providerId: String,
    val providerSessionId: String,
    val scheduleConfigJson: String?,     // Pour AUTOMATION seulement
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean
)
```

**Note importante :** Les Level 4 queries (enrichissements) sont extraites de l'historique des messages utilisateur lors de la génération des prompts.