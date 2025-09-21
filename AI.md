# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Flow principal
```
User message → AIOrchestrator → PromptManager → AIClient → Response processing
              ↕                ↕
          AISessionService   AIProviderConfigService
          (via coordinator)  (via coordinator)
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
- `PromptManager` : Génération prompts 4 niveaux
- `QueryExecutor` : Résolution queries pour prompts

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
    val id: String,              // "type.param1_value1.param2_value2"
    val type: String,            // Query type (ZONE_DATA, TOOL_ENTRIES, etc.)
    val params: Map<String, Any>, // Paramètres absolus ou relatifs
    val isRelative: Boolean = false // true pour automation, false pour chat
)
```

## 4. Système de prompts

### Architecture 4 niveaux (logique révisée)
- **Level 1** : Documentation système (stable) - Rôle IA, commandes disponibles
- **Level 2** : Contexte utilisateur (généré dynamiquement) - Outils avec `include_in_ai_context: true`
- **Level 3** : État app (moyennement stable) - Zones/outils actuels, permissions
- **Level 4** : Données session (stockées) - Enrichissements + données correspondantes

### Level 2 vs Level 4 - Différence cruciale

**Level 2 queries :**
- **Générées dynamiquement** à chaque prompt par `PromptManager.buildUserContext()`
- Basées sur les outils actuels avec `include_in_ai_context: true`
- **Jamais stockées** en session

**Level 4 queries :**
- **Stockées en session** dans `AISessionEntity.level4QueriesJson`
- Enrichissements spécifiques ajoutés par l'utilisateur
- Persistent durant toute la session

### Principe fondamental
**Aucun cache interne** - Prompt rebuilé à chaque requête, cache géré par providers API.

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

## 5. Enrichissements

Référence complète : `SPECS_ENRICHMENTS.md`

### Types et génération queries
- **🔍 POINTER** - Référencer données → Génère query si importance != 'optionnelle'
- **📝 USE** - Modifier données outils → Génère query (config instance)
- **✨ CREATE** - Créer éléments → Pas de query (orientation seulement)
- **🔧 MODIFY_CONFIG** - Config outils → Génère query (config instance)

### EnrichmentSummarizer
```kotlin
class EnrichmentSummarizer {
    fun generateSummary(type: EnrichmentType, config: String): String
    fun generateQuery(type: EnrichmentType, config: String, isRelative: Boolean): DataQuery?
}
```

Transformation automatique : `EnrichmentBlock` → `DataQuery` selon importance et type.

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
- Génération automatique `linearText` et `dataQueries`
- Integration `EnrichmentSummarizer`

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
    val level4QueriesJson: String?,      // Enrichissements session uniquement
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean
)
```

**Note importante :** Seules les Level 4 queries sont stockées. Les Level 2 queries sont générées dynamiquement.