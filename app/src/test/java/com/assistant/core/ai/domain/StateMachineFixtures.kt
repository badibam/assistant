package com.assistant.core.ai.domain

import com.assistant.core.ai.data.AIMessage
import com.assistant.core.ai.data.CommandResult
import com.assistant.core.ai.data.CommandStatus
import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.SessionType

/**
 * Shared material for the AIStateMachine tests.
 *
 * The machine is a pure function of (state, event, limits, currentTime), so a test only has
 * to build those four things. What it does need is to build them the same way every time:
 * these helpers exist so that a case reads as the one thing it varies -- a phase, a session
 * type, a flag on the AI's answer -- rather than as a wall of constructor arguments.
 */

/** Any fixed instant. Transitions record it on the state; none of them branch on its value. */
internal const val T0 = 1_700_000_000_000L

/** Later than T0 by a visible amount, to tell a refreshed timestamp from a carried-over one. */
internal const val T1 = T0 + 60_000L

/** The limit used unless a test is about the limit itself. Small, so overruns are obvious. */
internal val testLimits = SessionLimits(maxAutonomousRoundtrips = 3)

/** A running CHAT session sitting at a given phase. */
internal fun chatAt(
    phase: Phase,
    roundtrips: Int = 0,
    waitingContext: WaitingContext? = null,
    awaitingCompletionConfirmation: Boolean = false
) = AIState(
    sessionId = "chat-session",
    phase = phase,
    sessionType = SessionType.CHAT,
    totalRoundtrips = roundtrips,
    sessionCreatedAt = T0,
    lastNetworkAvailableTime = T0,
    lastEventTime = T0,
    lastUserInteractionTime = T0,
    waitingContext = waitingContext,
    awaitingCompletionConfirmation = awaitingCompletionConfirmation
)

/** A running AUTOMATION session sitting at a given phase. */
internal fun automationAt(
    phase: Phase,
    roundtrips: Int = 0,
    waitingContext: WaitingContext? = null,
    awaitingCompletionConfirmation: Boolean = false
) = AIState(
    sessionId = "automation-session",
    phase = phase,
    sessionType = SessionType.AUTOMATION,
    automationId = "automation-under-test",
    totalRoundtrips = roundtrips,
    sessionCreatedAt = T0,
    lastNetworkAvailableTime = T0,
    lastEventTime = T0,
    lastUserInteractionTime = T0,
    waitingContext = waitingContext,
    awaitingCompletionConfirmation = awaitingCompletionConfirmation
)

/**
 * An AI answer, empty unless a test says otherwise.
 *
 * preText is the only required field, and the routing in handleAIResponseParsed keys off the
 * optional ones, so a default of "all absent" is the neutral case: text and nothing else.
 */
internal fun aiMessage(
    dataCommands: List<DataCommand>? = null,
    actionCommands: List<DataCommand>? = null,
    communicationModule: CommunicationModule? = null,
    completed: Boolean? = null,
    keepControl: Boolean? = null,
    validationRequest: Boolean? = null
) = AIMessage(
    preText = "some analysis",
    validationRequest = validationRequest,
    dataCommands = dataCommands,
    actionCommands = actionCommands,
    postText = null,
    keepControl = keepControl,
    communicationModule = communicationModule,
    completed = completed
)

/** One command. The routing looks at whether the list is empty, never at what is inside. */
internal fun command(type: String = "tool_data.get") = DataCommand(
    id = "command-$type",
    type = type,
    params = emptyMap(),
    isRelative = false
)

/** A question put to the user, for the cases where the AI hands control back. */
internal fun question() = CommunicationModule.MultipleChoice(
    data = mapOf("question" to "which one?", "options" to listOf("a", "b"))
)

/**
 * Some context left on the state by a waiting phase.
 *
 * Several transitions clear waitingContext on their way out, and a null that was never set
 * cannot tell a cleared field from an untouched one. Communication is the cheap variant to
 * build; which variant it is never matters to the machine, only whether it is still there.
 */
internal fun someWaitingContext() = WaitingContext.Communication(
    communicationModule = question(),
    aiMessageId = "ai-message-under-test"
)

/** One executed command, successful or not. */
internal fun commandResult(status: CommandStatus = CommandStatus.SUCCESS) = CommandResult(
    command = "tool_data.create",
    status = status,
    details = null,
    data = null,
    error = if (status == CommandStatus.FAILED) "rejected by the service" else null,
    isActionCommand = true
)
