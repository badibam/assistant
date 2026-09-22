package com.assistant.core.ai.scheduling

import com.assistant.core.ai.data.ExecutionTrigger
import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.domain.currentOutage
import com.assistant.core.ai.domain.Phase
import com.assistant.core.utils.LogManager

/**
 * Decides who gets the one execution slot, and when its occupant has to give it up.
 *
 * One AI session runs at a time. Two questions follow from that, and both are answered
 * here: what to do with a session that asks to run while another one holds the slot, and
 * whether the session currently holding it has been there too long.
 *
 * Pure decisions, like AIStateMachine and for the same reason -- the answers depend only on
 * the state, the request and the clock, so they are worth being able to check without a
 * database or a device. The clock is a parameter rather than a call to System, so that a
 * test can put a session five minutes into the past instead of waiting five minutes.
 *
 * Acting on the answers belongs elsewhere: AIOrchestrator activates and evicts,
 * AIEventProcessor closes a session the watchdog has found timed out, and AISessionScheduler
 * keeps getNextSession, which reads the queue and the scheduled automations from the
 * database and so cannot live here.
 */
object SessionSlotPolicy {

    /** Chat session inactivity timeout (5 minutes) - only when automation waiting */
    const val CHAT_INACTIVITY_TIMEOUT = 300_000L

    /** Automation session inactivity timeout (2 minutes) */
    const val AUTO_INACTIVITY_TIMEOUT = 120_000L

    /** Automation session global timeout (10 minutes) - excludes network downtime */
    const val AUTOMATION_GLOBAL_TIMEOUT = 600_000L

    /**
     * Decide what to do with a session that asks to run.
     *
     * Either the slot is free and it starts, or it is taken and the answer depends on who
     * holds it and on what is asking.
     *
     * @param sessionId Session ID to activate
     * @param sessionType Session type (CHAT or AUTOMATION)
     * @param trigger Execution trigger (for automations only)
     * @param currentState Current AI state
     * @param currentTime Current timestamp, for the inactivity of whoever holds the slot
     * @return Activation result with decision
     */
    fun requestSession(
        sessionId: String,
        sessionType: SessionType,
        trigger: ExecutionTrigger,
        currentState: AIState,
        currentTime: Long
    ): ActivationResult {
        // Check if slot is available
        if (currentState.isSlotAvailable()) {
            return ActivationResult.ActivateImmediate(sessionId)
        }

        // Slot occupied - check interruption logic
        val activeSessionType = currentState.sessionType
        val inactivity = currentState.calculateInactivity(currentTime)

        LogManager.aiSession(
            "Request session: $sessionId ($sessionType/$trigger), active: ${currentState.sessionId} ($activeSessionType), " +
                "phase: ${currentState.phase}, inactivity: ${inactivity}ms",
            "INFO"
        )

        return when {
            // CHAT requests
            sessionType == SessionType.CHAT -> {
                when (activeSessionType) {
                    SessionType.CHAT -> {
                        // CHAT replaces CHAT immediately
                        ActivationResult.EvictAndActivate(
                            sessionToEvict = currentState.sessionId!!,
                            sessionToActivate = sessionId,
                            evictionReason = SessionEndReason.CANCELLED
                        )
                    }
                    SessionType.AUTOMATION -> {
                        // CHAT requesting during AUTOMATION: always enqueue
                        // User will be prompted with dialog (interrupt immediately OR wait for completion)
                        // Dialog is shown in UI layer, not here in the policy
                        LogManager.aiSession(
                            "CHAT requested during AUTOMATION: enqueuing CHAT (user will choose via dialog)",
                            "INFO"
                        )
                        ActivationResult.Enqueue(sessionId, priority = 1)
                    }
                    else -> ActivationResult.ActivateImmediate(sessionId)
                }
            }

            // AUTOMATION requests (MANUAL or SCHEDULED)
            sessionType == SessionType.AUTOMATION -> {
                checkEvictionForAutomation(
                    sessionId = sessionId,
                    trigger = trigger,
                    currentState = currentState,
                    activeSessionType = activeSessionType,
                    inactivity = inactivity
                )
            }

            else -> ActivationResult.Skip("Unknown session type/trigger combination")
        }
    }

    /**
     * Check eviction logic for AUTOMATION requests (both MANUAL and SCHEDULED).
     *
     * Only handles AUTOMATION → CHAT eviction logic.
     * AUTOMATION → AUTOMATION doesn't need explicit eviction - automations timeout
     * automatically via shouldTimeout() (watchdog) when they exceed limits.
     *
     * Logic:
     * - CHAT active with inactivity > 5 min: Evict (SUSPENDED)
     * - CHAT active with inactivity < 5 min:
     *   - MANUAL: Enqueue (will activate when slot free)
     *   - SCHEDULED: Skip (will retry at next tick)
     * - AUTOMATION active: Wait for watchdog to timeout the automation
     *   - MANUAL: Enqueue
     *   - SCHEDULED: Skip
     */
    private fun checkEvictionForAutomation(
        sessionId: String,
        trigger: ExecutionTrigger,
        currentState: AIState,
        activeSessionType: SessionType?,
        inactivity: Long
    ): ActivationResult {
        val triggerLabel = if (trigger == ExecutionTrigger.MANUAL) "MANUAL" else "SCHEDULED"

        return when (activeSessionType) {
            SessionType.CHAT -> {
                // Check CHAT inactivity with CHAT timeout
                LogManager.aiSession(
                    "AUTOMATION $triggerLabel requesting activation: CHAT active, inactivity=${inactivity}ms, threshold=${CHAT_INACTIVITY_TIMEOUT}ms",
                    "INFO"
                )

                if (inactivity > CHAT_INACTIVITY_TIMEOUT) {
                    // Evict inactive CHAT
                    LogManager.aiSession(
                        "AUTOMATION $triggerLabel evicting inactive CHAT (inactivity > ${CHAT_INACTIVITY_TIMEOUT}ms)",
                        "INFO"
                    )
                    ActivationResult.EvictAndActivate(
                        sessionToEvict = currentState.sessionId!!,
                        sessionToActivate = sessionId,
                        evictionReason = SessionEndReason.SUSPENDED
                    )
                } else {
                    // CHAT still active
                    if (trigger == ExecutionTrigger.MANUAL) {
                        LogManager.aiSession(
                            "AUTOMATION MANUAL enqueued: CHAT still active (inactivity=${inactivity}ms < ${CHAT_INACTIVITY_TIMEOUT}ms)",
                            "INFO"
                        )
                        ActivationResult.Enqueue(sessionId, priority = 2)
                    } else {
                        LogManager.aiSession(
                            "AUTOMATION SCHEDULED skipped: CHAT still active (inactivity=${inactivity}ms < ${CHAT_INACTIVITY_TIMEOUT}ms)",
                            "INFO"
                        )
                        ActivationResult.Skip("CHAT active")
                    }
                }
            }
            SessionType.AUTOMATION -> {
                // AUTOMATION active: cannot evict explicitly
                // Wait for watchdog to timeout the automation if it exceeds limits
                LogManager.aiSession(
                    "AUTOMATION $triggerLabel requesting activation: AUTOMATION active, waiting for watchdog timeout or completion",
                    "INFO"
                )

                if (trigger == ExecutionTrigger.MANUAL) {
                    LogManager.aiSession(
                        "AUTOMATION MANUAL enqueued: will activate when current AUTOMATION completes or times out",
                        "INFO"
                    )
                    ActivationResult.Enqueue(sessionId, priority = 2)
                } else {
                    LogManager.aiSession(
                        "AUTOMATION SCHEDULED skipped: will retry at next tick",
                        "INFO"
                    )
                    ActivationResult.Skip("AUTOMATION active")
                }
            }
            else -> ActivationResult.ActivateImmediate(sessionId)
        }
    }

    /**
     * Check if the session holding the slot should be timed out.
     *
     * CHAT: Timeout only if automation waiting + inactivity > 5 min
     * AUTOMATION: Timeout if (global > 10 min OR inactivity > 2 min) AND not waiting for network
     *
     * @param currentState Current AI state
     * @param hasWaitingAutomations True if queue or scheduled automation exists (CHAT only)
     * @param currentTime Current timestamp
     * @return true if session should timeout
     */
    fun shouldTimeout(currentState: AIState, hasWaitingAutomations: Boolean, currentTime: Long): Boolean {
        if (currentState.sessionType == null) {
            return false // No active session
        }

        when (currentState.sessionType) {
            SessionType.CHAT -> {
                // CHAT timeouts only if automation waiting
                if (!hasWaitingAutomations) {
                    return false
                }

                val inactivity = currentState.calculateInactivity(currentTime)
                val shouldTimeout = inactivity > CHAT_INACTIVITY_TIMEOUT

                if (shouldTimeout) {
                    LogManager.aiSession(
                        "CHAT timeout: automation waiting, inactivity=${inactivity}ms > ${CHAT_INACTIVITY_TIMEOUT}ms",
                        "INFO"
                    )
                }

                return shouldTimeout
            }

            SessionType.AUTOMATION -> {
                // Skip timeout if waiting for network (actively retrying)
                if (currentState.phase == Phase.WAITING_NETWORK_RETRY) {
                    return false
                }

                // Check global timeout (excluding network downtime)
                val activeTime = calculateActiveTime(currentState, currentTime)
                if (activeTime > AUTOMATION_GLOBAL_TIMEOUT) {
                    LogManager.aiSession(
                        "AUTOMATION timeout: global time exceeded, activeTime=${activeTime}ms > ${AUTOMATION_GLOBAL_TIMEOUT}ms",
                        "INFO"
                    )
                    return true
                }

                // Check inactivity timeout
                val inactivity = currentState.calculateInactivity(currentTime)
                if (inactivity > AUTO_INACTIVITY_TIMEOUT) {
                    LogManager.aiSession(
                        "AUTOMATION timeout: inactivity=${inactivity}ms > ${AUTO_INACTIVITY_TIMEOUT}ms",
                        "INFO"
                    )
                    return true
                }

                return false
            }

            else -> return false
        }
    }

    /**
     * Calculate active time for automation (excluding network downtime).
     *
     * @param state Current AI state
     * @param currentTime Current timestamp
     * @return Active time in milliseconds (excluding time spent waiting for network)
     */
    private fun calculateActiveTime(state: AIState, currentTime: Long): Long {
        val totalTime = currentTime - state.sessionCreatedAt

        // The outages already waited out, plus the one under way if there is one. Reading it off
        // the current phase was the bug: the only phase that carried a downtime to subtract was
        // the one shouldTimeout returns on a few lines above, so the subtraction never ran and
        // an automation that had spent eight minutes offline was stopped two minutes later.
        val downTime = state.networkDownTime + state.currentOutage(currentTime)

        return totalTime - downTime
    }
}

/**
 * Result of session activation request.
 */
sealed class ActivationResult {
    /** Activate session immediately (slot was free) */
    data class ActivateImmediate(val sessionId: String) : ActivationResult()

    /** Evict current session and activate the requested one */
    data class EvictAndActivate(
        val sessionToEvict: String,
        val sessionToActivate: String,
        val evictionReason: SessionEndReason
    ) : ActivationResult()

    /** Enqueue session, it will activate when the slot frees up */
    data class Enqueue(val sessionId: String, val priority: Int) : ActivationResult()

    /** Skip activation, it will be retried at the next tick */
    data class Skip(val reason: String) : ActivationResult()
}
