package com.assistant.core.services

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.themes.ThemeIconManager
import com.assistant.core.strings.Strings
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for preloading theme icons in background
 * Uses multi-step operations via Coordinator for non-blocking performance
 */
class IconPreloadService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    companion object {
        private const val TAG = "IconPreloadService"

        // Temporary data storage for multi-step operations (shared across instances)
        private val tempData = ConcurrentHashMap<String, Any>()
    }
    
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        val operationId = params.optString("operationId")
        val phase = params.optInt("phase", 1)
        
        return when (operation) {
            "preload_theme_icons" -> when (phase) {
                1 -> {
                    // Phase 1: Gather icon list for current theme
                    val currentThemeId = CurrentTheme.getCurrentThemeId()
                    val availableIcons = ThemeIconManager.getAvailableIcons(context, currentThemeId)
                    
                    LogManager.service("Phase 1: Found ${availableIcons.size} icons for theme '$currentThemeId'")
                    LogManager.service("Phase 1: Storing data for operationId='$operationId'")
                    tempData[operationId] = availableIcons
                    LogManager.service("Phase 1: tempData size = ${tempData.size}, keys = ${tempData.keys}")
                    
                    OperationResult.success(requiresBackground = true)
                }
                2 -> {
                    if (token.isCancelled) return OperationResult.cancelled()
                    
                    // Phase 2: Background preloading of icons
                    @Suppress("UNCHECKED_CAST")
                    val icons = tempData[operationId] as? List<com.assistant.core.themes.AvailableIcon> 
                        ?: return OperationResult.error(s.shared("service_error_no_data_found").format(operationId))
                    
                    var successCount = 0
                    var errorCount = 0
                    
                    icons.forEach { icon ->
                        if (token.isCancelled) return OperationResult.cancelled()
                        
                        try {
                            // Preload icon resource (this caches it in Android's drawable cache)
                            val drawable = context.getDrawable(icon.resourceId)
                            drawable?.let { 
                                // Force drawable to be loaded
                                it.bounds.isEmpty()
                                successCount++
                            } ?: errorCount++
                        } catch (e: Exception) {
                            LogManager.service("Failed to preload icon ${icon.id}: ${e.message}", "WARN")
                            errorCount++
                        }
                    }
                    
                    tempData[operationId] = mapOf(
                        "successCount" to successCount,
                        "errorCount" to errorCount,
                        "totalCount" to icons.size
                    )
                    
                    LogManager.service("Phase 2: Preloaded $successCount/${icons.size} icons (${errorCount} errors)")
                    
                    OperationResult.success(requiresContinuation = true)
                }
                3 -> {
                    // Phase 3: Cleanup and final reporting
                    @Suppress("UNCHECKED_CAST")
                    val results = tempData[operationId] as? Map<String, Int>
                        ?: return OperationResult.error(s.shared("service_error_no_results_found").format(operationId))
                    
                    tempData.remove(operationId)
                    
                    val successCount = results["successCount"] ?: 0
                    val errorCount = results["errorCount"] ?: 0
                    val totalCount = results["totalCount"] ?: 0
                    
                    LogManager.service("Icon preloading completed: $successCount/$totalCount icons loaded successfully")
                    
                    OperationResult.success(
                        mapOf(
                            "message" to "Icon preloading completed",
                            "loaded" to successCount,
                            "errors" to errorCount,
                            "total" to totalCount
                        )
                    )
                }
                else -> OperationResult.error(s.shared("service_error_invalid_phase").format(phase))
            }
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    /**
     * Verbalize icon preload operation
     * Icon preloading is typically not exposed to AI actions
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}