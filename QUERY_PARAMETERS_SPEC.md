# Query Parameters Specification

## Vue d'ensemble

Extension du système `resource.operation` pour supporter les **requêtes de données filtrées** via des paramètres standardisés inspirés des conventions REST et SQL.

## Architecture

Les Query Parameters s'intègrent dans le système existant :
- **Même CommandDispatcher** : `resource.operation` inchangé
- **Mêmes services** : Extension des services existants
- **Nouveaux paramètres** : `where`, `limit`, `offset`, `orderBy`, `select`

## Format standard

```json
{
  // Paramètres existants (inchangés)
  "toolInstanceId": "abc123",

  // Nouveaux paramètres de filtrage
  "where": {
    "field1": "value",
    "field2": {"operator": "value"},
    "field3": {"in": ["val1", "val2"]}
  },
  "limit": 50,
  "offset": 0,
  "orderBy": [
    {"field": "timestamp", "direction": "DESC"},
    {"field": "value", "direction": "ASC"}
  ],
  "select": ["field1", "field2"] // optionnel, par défaut = tous
}
```

## Opérateurs WHERE supportés

### Opérateurs de comparaison
- `"field": "value"` → égalité simple
- `"field": {"eq": "value"}` → égalité explicite
- `"field": {"ne": "value"}` → différent
- `"field": {"gt": 100}` → supérieur
- `"field": {"gte": 100}` → supérieur ou égal
- `"field": {"lt": 100}` → inférieur
- `"field": {"lte": 100}` → inférieur ou égal

### Opérateurs de liste
- `"field": {"in": ["val1", "val2"]}` → dans la liste
- `"field": {"nin": ["val1", "val2"]}` → pas dans la liste

### Opérateurs de chaîne
- `"field": {"like": "%text%"}` → contient (LIKE SQL)
- `"field": {"ilike": "%text%"}` → contient insensible à la casse

### Opérateurs logiques
- `"field": {"exists": true}` → champ non null
- `"field": {"exists": false}` → champ null

## Exemples d'usage

### Requête simple
```json
// tool_data.get
{
  "toolInstanceId": "tracking_weight",
  "where": {"timestamp": {"gte": 1234567890}},
  "limit": 10,
  "orderBy": [{"field": "timestamp", "direction": "DESC"}]
}
```

### Requête complexe
```json
// tool_data.get
{
  "toolInstanceId": "tracking_mood",
  "where": {
    "timestamp": {"gte": 1234567890, "lte": 1234999999},
    "value": {"in": ["happy", "excited"]},
    "metadata": {"exists": true}
  },
  "limit": 50,
  "orderBy": [{"field": "timestamp", "direction": "ASC"}],
  "select": ["timestamp", "value", "metadata"]
}
```

### Requête zone complète
```json
// zones.get
{
  "where": {"active": true},
  "orderBy": [{"field": "name", "direction": "ASC"}],
  "select": ["id", "name", "description"]
}
```

## Intégration par resource type

### tool_data.*
- **Opérations étendues** : `get`, `list`, `stats`
- **Champs filtrables** : `timestamp`, `value`, `metadata`, `toolInstanceId`
- **Ordre par défaut** : `timestamp DESC`

### tools.*
- **Opérations étendues** : `get`, `list`
- **Champs filtrables** : `name`, `toolType`, `zoneId`, `enabled`, `createdAt`
- **Ordre par défaut** : `name ASC`

### zones.*
- **Opérations étendues** : `get`, `list`
- **Champs filtrables** : `name`, `description`, `active`, `createdAt`
- **Ordre par défaut** : `name ASC`

## Limites et validation

### Système de limites intelligentes
- **Token-based limits** : Via `TokenCalculator` selon provider IA
- **Preview mode** : Calcul taille → dialogue confirmation (CHAT) ou refus auto (AUTOMATION)
- **offset** : max 10000, défaut 0
- **orderBy** : max 3 champs
- **select** : tous les champs par défaut

### Configuration via AppSettingsCategory.AI_LIMITS
```json
{
  "queryLimits": {
    "defaultMaxTokens": 2000,
    "charsPerToken": 4
  },
  "providerOverrides": {
    "claude": {"maxTokens": 3000, "charsPerToken": 3.8},
    "openai": {"maxTokens": 1500, "charsPerToken": 4.2}
  }
}
```

### Validation
- Champs filtrables définis par resource type
- Opérateurs validés selon le type de champ
- Valeurs typées (string, number, boolean)
- Protection contre injection via paramètres typés

## Implémentation technique

### Architecture avec AIQueryProcessor
```
PromptManager → AIQueryProcessor → CommandDispatcher → Service
                      ↓
[TokenCalculator + Preview Logic + Dialogue si dépassement]
```

### Dans les services (inchangé)
```kotlin
// ToolDataService.kt - Reste identique
override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
    return when (operation) {
        "get" -> {
            val queryParams = QueryParametersParser.parse(params)
            val results = dao.getFilteredData(queryParams)
            OperationResult.success(mapOf("entries" to results))
        }
    }
}
```

### AIQueryProcessor (middleware IA)
```kotlin
class AIQueryProcessor(context: Context) {
    suspend fun executeQuery(
        command: String,
        params: Map<String, Any>,
        sessionType: SessionType
    ): QueryResult {
        // 1. Exécute requête via CommandDispatcher
        // 2. Calcule tokens via TokenCalculator
        // 3. Si dépassement:
        //    - CHAT: Dialogue confirmation
        //    - AUTOMATION: Refus automatique
        // 4. Retourne données + métadonnées tokens
    }
}
```

### Parser centralisé (étendu)
```kotlin
data class QueryParameters(
    val where: Map<String, Any> = emptyMap(),
    val limit: Int = 50,
    val offset: Int = 0,
    val orderBy: List<OrderByClause> = emptyList(),
    val select: List<String>? = null,
    val aggregate: AggregateClause? = null,
    val join: List<JoinClause> = emptyList()
)
```

## Rétrocompatibilité

- **Paramètres existants** : Inchangés
- **Nouveau comportement** : Seulement si Query Parameters présents
- **Fallback** : Comportement actuel si pas de paramètres de filtrage

## Extensions avancées

### Agrégations (prioritaires)
```json
{
  "aggregate": {
    "groupBy": ["zone", "toolType"],
    "functions": [
      {"field": "value", "function": "avg", "alias": "moyenne"},
      {"field": "value", "function": "sum", "alias": "total"},
      {"field": "value", "function": "min", "alias": "minimum"},
      {"field": "value", "function": "max", "alias": "maximum"},
      {"field": "*", "function": "count", "alias": "nombre"}
    ]
  }
}
```

**TODO agrégations** : percentiles, stddev, variance, first/last par groupe

### Jointures détaillées
```json
// Données d'outil + métadonnées instance
{
  "toolInstanceId": "abc123",
  "join": {
    "resource": "tools",
    "on": "toolInstanceId",
    "select": ["name", "toolType", "config"],
    "type": "left"
  }
}

// Données zone + tous ses outils
{
  "zoneId": "health",
  "join": {
    "resource": "tools",
    "on": "zoneId",
    "select": ["name", "toolType", "enabled"],
    "where": {"enabled": true}
  }
}

// Données tracking + config outil + zone
{
  "toolInstanceId": "weight_tracker",
  "join": [
    {
      "resource": "tools",
      "on": "toolInstanceId",
      "select": ["name", "zoneId"]
    },
    {
      "resource": "zones",
      "on": "zoneId",
      "select": ["name", "description"]
    }
  ]
}
```

---

*Cette spécification étend le système resource.operation existant tout en gardant la simplicité et la cohérence architecturale.*