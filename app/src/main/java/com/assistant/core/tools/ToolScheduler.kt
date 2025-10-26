package com.assistant.core.tools

import android.content.Context

/**
 * Interface for tool types that require periodic scheduling.
 *
 * Tools implementing this interface can participate in the centralized
 * scheduling system managed by CoreScheduler.
 *
 * Discovery pattern: CoreScheduler discovers schedulers via ToolTypeManager,
 * no hardcoded dependencies required.
 *
 * Use cases:
 * - Messages tool: Check for scheduled messages to send
 * - Future Alerts tool: Evaluate alert conditions
 * - Any tool needing periodic background execution
 *
 * Scheduling lifecycle:
 * 1. CoreScheduler.tick() called every 1 min (app-open) or 15 min (app-closed)
 * 2. CoreScheduler discovers all tools via ToolTypeManager.getAllToolTypes()
 * 3. For each tool with getScheduler() != null, calls checkScheduled()
 * 4. Tool scheduler performs its periodic checks and actions
 *
 * @see com.assistant.core.scheduling.CoreScheduler
 */
interface ToolScheduler {

    /**
     * Called periodically by CoreScheduler to check for scheduled actions.
     *
     * Implementation responsibilities:
     * - Load tool instances for this tool type
     * - Check configuration for scheduled items
     * - Execute scheduled actions if conditions met
     * - Handle errors gracefully (log + continue, don't throw)
     *
     * Pattern: Autonomous execution, no user interaction expected
     *
     * @param context Android context for accessing services and resources
     */
    suspend fun checkScheduled(context: Context)
}
