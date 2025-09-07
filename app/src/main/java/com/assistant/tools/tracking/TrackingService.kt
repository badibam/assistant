package com.assistant.tools.tracking

import android.content.Context
import android.util.Log
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.coordinator.Operation
import com.assistant.core.services.ToolDataService
import org.json.JSONObject

/**
 * Tracking Service - Wrapper autour de ToolDataService pour compatibilité
 * Redirige toutes les opérations vers le service centralisé
 */
class TrackingService(private val context: Context) : ExecutableService {
    
    private val toolDataService = ToolDataService(context)
    
    init {
        Log.d("TrackingService", "TrackingService initialized as wrapper around ToolDataService")
    }
    
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): Operation.OperationResult {
        // Ajouter le tooltype automatiquement
        val paramsWithTooltype = JSONObject(params.toString()).apply {
            put("tooltype", "tracking")
        }
        
        Log.d("TrackingService", "Delegating $operation to ToolDataService with params: $paramsWithTooltype")
        
        return toolDataService.execute(operation, paramsWithTooltype, token)
    }
}