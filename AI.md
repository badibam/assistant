# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Flow principal
```
User message → AISessionManager → PromptManager → AIService → Response processing
```

### Session types
- **CHAT** : Conversation temps réel, queries absolues, modules communication
- **AUTOMATION** : Prompt programmable, queries relatives, feedback exécutions

## 2. Structures de données

### Session
```kotlin
data class AISession(
    val id: String,
    val name: String,
    val type: SessionType,
    val providerId: String,
    val providerSessionId: String,
    val isActive: Boolean,
    val messages: List<SessionMessage>
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

## 3. Système de prompts

### Architecture 4 niveaux
- **Level 1** : Documentation système (stable) - Rôle IA, commandes disponibles
- **Level 2** : Contexte utilisateur (stable) - Outils avec `include_in_ai_context: true`
- **Level 3** : État app (moyennement stable) - Zones/outils actuels, permissions
- **Level 4** : Données session (volatile) - Enrichissements + données correspondantes

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

## 4. Enrichissements

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

## 5. Providers et services

### AIProvider interface
```kotlin
interface AIProvider {
    fun getDisplayName(): String
    fun getConfigSchema(): String
    @Composable fun getConfigScreen(config: String, onSave: (String) -> Unit)
    suspend fun query(prompt: String, config: String): AIResponse
}
```

### AISessionManager (orchestrateur central)
```kotlin
class AISessionManager(context: Context) : ExecutableService {
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult
    suspend fun addEnrichmentsToSession(sessionId: String, dataQueries: List<DataQuery>): OperationResult
    suspend fun createSession(name: String, type: SessionType, providerId: String): String
    suspend fun setActiveSession(sessionId: String): OperationResult
}
```

**Flow complet** :
1. Ajouter enrichments Level 4 (et validation tokens)
2. Stocker message utilisateur
3. Builder prompt via PromptManager
4. Envoyer AIService
5. Traiter réponse et stocker

### Validation et permissions
Système hiérarchique avec 4 niveaux :
- `autonomous` - IA agit directement
- `validation_required` - Confirmation utilisateur
- `forbidden` - Action interdite
- `ask_first` - Permission avant proposition

## 6. Components UI

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
Interface 100% écran avec header + messages + composer. Orchestration complète via `AISessionManager`.

### Communication Modules
Générés par l'IA, remplis par le user
```kotlin
sealed class CommunicationModule {
    data class MultipleChoice(val question: String, val options: List<String>)
    data class Validation(val message: String)
    // TODO: Slider, DataSelector
}
```
