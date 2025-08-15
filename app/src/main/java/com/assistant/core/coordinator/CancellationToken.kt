package com.assistant.core.coordinator

import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cancellation token for operations
 * Allows operations to check if they should be cancelled
 */
class CancellationToken {
    private val _isCancelled = AtomicBoolean(false)
    private var job: Job? = null
    
    /**
     * Check if operation should be cancelled
     */
    val isCancelled: Boolean
        get() = _isCancelled.get() || job?.isCancelled == true
    
    /**
     * Cancel this operation
     */
    fun cancel() {
        _isCancelled.set(true)
        job?.cancel()
    }
    
    /**
     * Associate with a coroutine job
     */
    internal fun setJob(job: Job) {
        this.job = job
    }
    
    /**
     * Throw if cancelled (convenience method)
     */
    fun throwIfCancelled() {
        if (isCancelled) {
            throw OperationCancelledException("Operation was cancelled")
        }
    }
}

/**
 * Exception thrown when operation is cancelled
 */
class OperationCancelledException(message: String) : Exception(message)