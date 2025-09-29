# TODOs Système IA - 114 items

## data/AIContext.kt (1)
- Line 20: Implémenter avec vrais services

## data/AIMessage.kt (1)
- Line 46: Add Slider, DataSelector modules when needed

## data/SessionMessage.kt (1)
- Line 59: Define structure when implementing scheduling

## enrichments/Enrichment.kt (5)
- Line 61: implement UseEnrichmentSchema
- Line 73: implement CreateEnrichmentSchema
- Line 85: implement ModifyEnrichmentSchema
- Line 97: implement OrganizeEnrichmentSchema
- Line 109: implement DocumentEnrichmentSchema

## enrichments/EnrichmentProcessor.kt (16) ✅ 2/18 DONE
- Line 155: resolve tool instance name from ID for better readability
- Line 173: resolve tool instance name from ID for better readability
- Line 177: Implement ORGANIZE enrichment type (📁) - lower priority
- Line 184: Implement DOCUMENT enrichment type (📚) - lower priority
- Line 236: Get schema IDs from tool instance config - for now using placeholders
- Line 240: resolve from tool instance (config_schema_id)
- Line 246: resolve from tool instance (data_schema_id)
- Line 308: Get schema IDs from tool instance config - for now using placeholders
- Line 312: resolve from tool instance (config_schema_id)
- Line 318: resolve from tool instance (data_schema_id)
- Line 342: Implement CREATE enrichment with schema-driven tooltype selection
- Line 361: Get schema ID from tool instance config - for now using placeholder
- Line 365: resolve from tool instance (config_schema_id)
- ✅ Line 396: Implement relative period encoding for automation (DONE)
- Line 421: Implement proper period formatting based on timestamps
- ✅ ADDED: Implement absolute period timestamp calculation for CHAT mode (DONE)

## orchestration/AIOrchestrator.kt (18)
- Line 61: Load actual session
- Line 85: Get providerId from session
- Line 207: Parse schedule config for AUTOMATION sessions (currently null)
- Line 270: Parse system messages when implemented
- Line 273: Parse execution metadata for automation messages
- Line 296: Implement complete JSON deserialization with segment parsing
- Line 301: Implement proper JSON parsing for RichMessage
- Line 303: Implement RichMessage JSON parsing
- Line 307: Parse MessageSegment list from JSON
- Line 308: Extract linearText from JSON
- Line 309: Parse DataCommand list from JSON
- Line 319: Implement complete JSON deserialization with all AIMessage fields
- Line 324: Implement proper JSON parsing for AIMessage
- Line 326: Implement AIMessage JSON parsing
- Line 330: Extract preText from JSON
- Line 331: Parse ValidationRequest from JSON
- Line 332: Parse DataCommand list from JSON (dataCommands)
- Line 333: Parse DataCommand list from JSON (actionCommands)
- Line 334: Extract postText from JSON
- Line 335: Parse CommunicationModule from JSON

## processing/AICommandProcessor.kt (4)
- Line 30-36: Implement AI data command processing with validation, security, token management
- Line 54-61: Implement AI action command processing with validation, security, cascade failure

## processing/UserCommandProcessor.kt (2) ✅ 5/7 DONE
- ✅ Line 85: Transform TOOL_CONFIG command to tools.get call
- ✅ Line 95: Transform TOOL_DATA command to tool_data.get call
- Line 107: Transform TOOL_STATS command to tool_data.stats call (LOW PRIORITY - stub)
- Line 117: Transform TOOL_DATA_SAMPLE command to tool_data.get with sampling (LOW PRIORITY - stub)
- ✅ Line 127: Transform ZONE_CONFIG command to zones.get call
- ✅ Line 136: Transform ZONES command to zones.list call
- ✅ Line 145: Transform TOOL_INSTANCES command to tools.list call

## prompts/CommandExecutor.kt (0) ✅ DONE
- ✅ CommandResult structure with dataTitle, formattedData, systemMessage
- ✅ Format results properly for prompt inclusion (JSON metadata first)
- ✅ Generate system messages for queries and actions
- ✅ Support batch operations (batch_create, batch_update)
- ✅ Deduplication handled by PromptManager (previousCommands removed)

## prompts/PromptManager.kt (9)
- Line 27: Implement new command pipeline integration
- Line 34: PromptManager.buildPrompt() - new pipeline integration needed
- Line 39: Implement new command pipeline
- Line 50: Replace DataQuery builders with DataCommand builders
- Line 56-57: Implement Level 1 command generation
- Line 61: Implement Level 2 and Level 3 command builders as stubs
- Line 69: Implement new Level 4 pipeline
- Line 75: getLevel4Commands() - new pipeline needed
- Line 100: Implement according to "Send history to AI" setting
- Line 165: better estimation
- Line 177: Implement in Phase 2A+ when enrichments need it

## prompts/QueryDeduplicator.kt (2)
- Line 105: Implement business logic inclusion rules for DataCommand
- Line 111: commandIncludes() business logic - returning false for now
- Line 115: Implement helper methods for command inclusion logic when needed

## providers/AIClient.kt (1)
- Line 187: Parse communication module

## providers/AIProviderRegistry.kt (4)
- Line 12: Load providers dynamically via discovery pattern
- Line 37: Load from app config or database
- Line 46: Check if provider has valid configuration
- Line 62: Load from app config
- Line 84: Save to app config

## providers/ClaudeProvider.kt (4)
- Line 84: Implement real form with API key field
- Line 88: Implement UI configuration form
- Line 101-106: Implement real Claude API call with authentication, parsing, error handling

## services/AIProviderConfigService.kt (5)
- Line 74: Implement actual database retrieval
- Line 85: Check real config validation (stub for testing)
- Line 101: Implement actual database storage with validation
- Line 115: Implement actual database retrieval
- Line 146: Implement actual database deletion
- Line 162: Implement actual database update
- Line 174: Implement actual database retrieval

## services/AISessionService.kt (6)
- Line 68: Implement all CRUD operations
- Line 321-326: Store richContent as proper RichMessage JSON structure
- Line 341: Parse AIMessage when implementing
- Line 342: Implement system messages
- Line 343: Implement automation metadata

## ui/chat/AIFloatingChat.kt (1)
- Line 191: Show proper error UI or toast

## ui/components/RichComposer.kt (9)
- Line 130: Handle editing existing blocks
- Line 439: Recalculate token count when filters change (if toggle enabled)
- Line 457: Recalculate token count when filters change (if includeData toggle enabled)
- Line 461: Recalculate token count when filters change (if includeData toggle enabled)
- Line 465: Recalculate token count when filters change (if includeData toggle enabled)
- Line 469: Recalculate token count when filters change (if includeData toggle enabled)
- Line 473: Recalculate token count when filters change (if includeData toggle enabled)
- Line 477: Recalculate token count when filters change (if includeData toggle enabled)
- Line 533: Implement enrichment configuration UI (USE, CREATE, MODIFY_CONFIG, ORGANIZE, DOCUMENT)

## utils/TokenCalculator.kt (2)
- Line 83: Get provider-specific limits from active provider configuration
- Line 140: Get provider-specific overrides from provider configuration

---

## Regroupement par priorité/thématique

### 🔴 CRITIQUE - Pipeline de base
- **PromptManager.kt**: Intégration complète pipeline commandes (lines 27-75)
- ✅ **UserCommandProcessor.kt**: Transformations commandes utilisateur (5/7 done, 2 stubs low priority)
- **AICommandProcessor.kt**: Validation commandes IA (2 processeurs)
- ✅ **CommandExecutor.kt**: Formatage résultats + system messages (DONE)

### 🟠 IMPORTANT - Données réelles
- **AIProviderConfigService.kt**: Persistance DB configurations (5 opérations)
- **AISessionService.kt**: Sérialisation RichMessage/AIMessage (6 items)
- **orchestration/AIOrchestrator.kt**: Parsing JSON messages (18 items)
- **EnrichmentProcessor.kt**: Résolution schema IDs et tool instance names (10 items)

### 🟡 MOYEN - Provider IA réel
- **ClaudeProvider.kt**: API calls réelles (4 items)
- **AIProviderRegistry.kt**: Discovery pattern et config (4 items)
- **AIClient.kt**: Parsing communication modules (1 item)

### 🟢 FAIBLE PRIORITÉ - Features avancées
- **Enrichment.kt**: Schémas enrichissements (5 types)
- **RichComposer.kt**: Edition blocs + recalcul tokens (9 items)
- **SessionMessage.kt**: Support automation (1 item)
- **EnrichmentProcessor.kt**: ORGANIZE et DOCUMENT types (2 items)
- **TokenCalculator.kt**: Overrides provider-specific (2 items)
- **AIFloatingChat.kt**: Error UI (1 item)