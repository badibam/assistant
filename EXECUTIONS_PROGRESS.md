# Tool Executions - Avancement Implémentation

**Dernière mise à jour** : 2025-01-30

## ✅ Complété

### Phase 1 : Backend Core
- ✅ ToolExecutionEntity (table + indexes)
- ✅ BaseToolExecutionDao (queries CRUD + filters)
- ✅ BaseSchemas.getBaseExecutionSchema()
- ✅ ToolExecutionService (CRUD + batch + stats)
- ✅ ServiceRegistry (`tool_executions` mapping)
- ✅ Migration DB 13→14 (table + migration données Messages)
- ✅ JsonTransformer pour retrait champ executions

### Phase 2 : Messages Refactoring (Backend)
- ✅ SchemaCategory.TOOL_EXECUTION
- ✅ messages_execution schema (MessageToolType)
- ✅ Retrait champ executions de messages_data schema
- ✅ ToolTypeContract.supportsExecutions()
- ✅ MessageToolType.supportsExecutions() = true
- ✅ MessageScheduler → utilise tool_executions.create
- ✅ MessageService → délègue à tool_executions service
  - get_history → tool_executions.get
  - mark_read/archived → tool_executions.update
  - stats → tool_executions.stats

### Phase 4 : POINTER Extension
- ✅ PointerContext enum (GENERIC/CONFIG/DATA/EXECUTIONS)
- ✅ NavigationConfig extension
  - allowedContexts: List<PointerContext>
  - defaultContext: PointerContext
  - useRelativeLabels: Boolean (CHAT vs AUTOMATION)
  - Retrait showFieldSpecificSelectors et showQueryPreview (obsolètes)
- ✅ SelectionResult extension
  - selectedContext: PointerContext
  - selectedResources: List<String>
  - Périodes stockées directement via timestampSelection (pas de TemporalFilter séparé)
- ✅ ZoneScopeState simplifié
  - Retrait field-level navigation (selectedValues, fieldSpecificType, nameSelection)
  - Focus sur ZONE → INSTANCE → CONTEXT+RESOURCES → PERIOD
- ✅ ZoneScopeSelector refactorisé (933 lignes → 668 lignes)
  - Navigation simplifiée (ZONE et INSTANCE uniquement)
  - Section unifiée Context + Resources
  - Sélecteur de contexte (FormSelection)
  - Toggles de ressources dynamiques (ToggleField)
  - Filtrage contextes basé sur toolType.supportsExecutions()
  - Support useRelativeLabels pour AUTOMATION
- ✅ Strings localization
  - label_context_* (generic, config, data, executions)
  - label_resource_* (config, config_schema, data, data_schema, executions, executions_schema)
  - label_period_* (reference, data, execution)
  - scope_select_* (context, resources, zone, tool, item)
  - label_zone, label_tool, label_item
- ✅ Compilation réussie

## ⏳ Reste à faire

### Phase 4 : POINTER Extension (suite)
- ⏳ EnrichmentProcessor.generatePointerQueries()
  - Adapter pour utiliser selectedContext et selectedResources
  - Générer commands selon contexte :
    - GENERIC → aucun command automatique
    - CONFIG → TOOL_CONFIG si "config" sélectionné, SCHEMA si "config_schema"
    - DATA → TOOL_DATA si "data" sélectionné, SCHEMA si "data_schema"
    - EXECUTIONS → TOOL_EXECUTIONS si "executions" sélectionné, SCHEMA si "executions_schema"
  - Retirer anciens toggles (includeSchemaConfig, includeToolConfig, etc.)

- ⏳ CommandTransformer
  - Ajouter type TOOL_EXECUTIONS
  - Mapping TOOL_EXECUTIONS → tool_executions.get
  - Params : toolInstanceId, startTime, endTime (selon période)

### Phase 2 : Messages UI
- ⏳ MessagesScreen → lire executions depuis tool_executions table
- ⏳ Adapter affichage historique pour nouvelle structure
- ⏳ Tests end-to-end Messages

### Tests & Validation
- ⏳ Tests POINTER avec différents contextes
- ⏳ Tests Messages complets
- ⏳ Tests création/exécution Messages via scheduler

## 📝 Décisions Clés

### Architecture
1. **BaseSchemas** : `snapshotData`, `executionResult`, `metadata` sont spécifiques (pas dans base)
2. **Pas de TemporalFilter séparé** : Périodes stockées directement dans SelectionResult via timestampSelection (réutilise structure existante)
3. **Naming** : `_schema` dans le code (pas `_doc`), ex: `config_schema`, `data_schema`, `executions_schema`

### Contextes POINTER
1. **GENERIC** : Référence vide, AUCUN command automatique, période optionnelle pour contexte AI
2. **CONFIG** : Configuration + schemas, PAS de période
3. **DATA** : Données + schemas, période optionnelle sur tool_data.timestamp
4. **EXECUTIONS** : Historique + schemas, période optionnelle sur tool_executions.executionTime, visible seulement si toolType.supportsExecutions()

### UI
1. **Contexte + Resources = 1 étape** : Sélection unifiée (pas 2 étapes séparées)
2. **Toggles de ressources dynamiques** : Affichage conditionnel selon contexte
3. **Defaults** : DATA → "data" coché, EXECUTIONS → "executions" coché, CONFIG → rien coché
4. **Simplification ZoneScopeSelector** :
   - Retrait field-level navigation (non utilisée par POINTER)
   - Focus sur zones et tool instances uniquement
   - 933 lignes → 668 lignes

### Session Types
1. **CHAT** : useRelativeLabels = false, périodes absolues (Period)
2. **AUTOMATION** : useRelativeLabels = true, périodes relatives (RelativePeriod)
3. **EnrichmentProcessor** : Reçoit isRelative selon session type, génère commands appropriés

## 🎯 Prochaines Étapes

1. **Adapter EnrichmentProcessor** : Utiliser selectedContext + selectedResources pour générer les bons commands
2. **Étendre CommandTransformer** : Ajouter mapping TOOL_EXECUTIONS
3. **Adapter Messages UI** : Lire depuis tool_executions au lieu du JSON data.executions
4. **Tests** : Vérifier tous les contextes POINTER et flow Messages complet
