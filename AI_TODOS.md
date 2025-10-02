# TODOs Système IA

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

## enrichments/EnrichmentProcessor.kt (14)
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
- Line 421: Implement proper period formatting based on timestamps

## orchestration/AIOrchestrator.kt (10)
- Line 207: Parse schedule config for AUTOMATION sessions (currently null)
- Line 273: Parse execution metadata for automation messages
- Line 319: Implement complete JSON deserialization with all AIMessage fields
- Line 324: Implement proper JSON parsing for AIMessage
- Line 326: Implement AIMessage JSON parsing
- Line 330: Extract preText from JSON
- Line 331: Parse ValidationRequest from JSON
- Line 332: Parse DataCommand list from JSON (dataCommands)
- Line 333: Parse DataCommand list from JSON (actionCommands)
- Line 334: Extract postText from JSON
- Line 335: Parse CommunicationModule from JSON

## processing/AICommandProcessor.kt (2)
- Line 30-34: Add AI-specific validations for data commands (token limits, permissions, sanitization, rate limiting)
- Line 56-62: Implement AI action command strict validations (permissions, scope, sanitization, rate limiting, batch limits)

## processing/CommandTransformer.kt (2)
- Line 142: Transform TOOL_STATS command to tool_data.stats call (LOW PRIORITY - stub)
- Line 151: Transform TOOL_DATA_SAMPLE command to tool_data.get with sampling (LOW PRIORITY - stub)

## prompts/PromptManager.kt (3)
- Line 100: Implement according to "Send history to AI" setting
- Line 165: better estimation
- Line 177: Implement in Phase 2A+ when enrichments need it

## prompts/QueryDeduplicator.kt (2)
- Line 105: Implement business logic inclusion rules for DataCommand
- Line 111: commandIncludes() business logic - returning false for now
- Line 115: Implement helper methods for command inclusion logic when needed

## orchestration/AIOrchestrator.kt (NEW - 1)
- Line 506-512: Implement validation flow for action commands (store validation request, wait for user confirmation, execute on confirm, cascade failure on rejection)

## providers/AIProviderRegistry.kt (1)
- Line 12: Load providers dynamically via discovery pattern

## providers/ClaudeProvider.kt (1)
- Line 101-106: Implement real Claude API call with authentication, parsing, error handling

## services/AISessionService.kt (2)
- Line 341: Parse AIMessage when implementing
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
