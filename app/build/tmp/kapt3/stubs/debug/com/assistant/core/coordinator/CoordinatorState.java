package com.assistant.core.coordinator;

/**
 * Possible states of the Coordinator
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0007\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007\u00a8\u0006\b"}, d2 = {"Lcom/assistant/core/coordinator/CoordinatorState;", "", "(Ljava/lang/String;I)V", "IDLE", "OPERATION_IN_PROGRESS", "VALIDATION_REQUIRED", "AI_DIALOGUE", "ERROR", "app_debug"})
public enum CoordinatorState {
    /*public static final*/ IDLE /* = new IDLE() */,
    /*public static final*/ OPERATION_IN_PROGRESS /* = new OPERATION_IN_PROGRESS() */,
    /*public static final*/ VALIDATION_REQUIRED /* = new VALIDATION_REQUIRED() */,
    /*public static final*/ AI_DIALOGUE /* = new AI_DIALOGUE() */,
    /*public static final*/ ERROR /* = new ERROR() */;
    
    CoordinatorState() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.assistant.core.coordinator.CoordinatorState> getEntries() {
        return null;
    }
}