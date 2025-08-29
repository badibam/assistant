# Multi-Step Operations System

Permet au Coordinator de gérer des opérations lourdes qui se décomposent en 3 phases, libérant le système pendant le calcul.

## Architecture

**2 Canaux** : Queue normale (bloquant) + 1 slot background (calcul lourd)

**Flow** : Phase 1 (lecture) → Phase 2 (calcul background) → Phase 3 (écriture)

## Implémentation

Services multi-étapes implémentent `ExecutableService` (voir TOOL_ARCHITECTURE.md) et gèrent les phases via `operationId` + `phase` :

```kotlin
class MyService(context: Context) : ExecutableService {
    private val tempData = ConcurrentHashMap<String, Any>()
    
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        val operationId = params.optString("operationId")
        val phase = params.optInt("phase", 1)
        
        return when (operation) {
            "heavy_calc" -> when (phase) {
                1 -> {
                    val data = loadData()
                    tempData[operationId] = data
                    OperationResult.success(requiresBackground = true)
                }
                2 -> {
                    val result = heavyCalculation(tempData[operationId])
                    tempData[operationId] = result
                    OperationResult.success(requiresContinuation = true)
                }
                3 -> {
                    saveResult(tempData[operationId])
                    tempData.remove(operationId)
                    OperationResult.success()
                }
            }
        }
    }
}
```

## Règles

- **FIFO strict** : Ordre des opérations respecté
- **1 seul slot background** : Évite surcharge système  
- **Re-queue automatique** : Si slot occupé → fin de queue
- **Données temporaires** : Gérées par le service

## Exemple

```kotlin
// Lancement analyse corrélation
coordinator.processUserAction("create->correlation_analysis", mapOf("tool_instance_id" to "123"))

// Flow automatique :
// Phase 1: Charge données → requiresBackground = true
// Phase 2: Calcul 3sec en background → requiresContinuation = true  
// Phase 3: Sauvegarde résultat → terminé
```

**Test** : `testMultiStepOperations()` dans MainActivity