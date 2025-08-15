package com.assistant.core.coordinator;

/**
 * Priority levels for operations
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/assistant/core/coordinator/OperationPriority;", "", "(Ljava/lang/String;I)V", "LOW", "NORMAL", "HIGH", "CRITICAL", "app_debug"})
public enum OperationPriority {
    /*public static final*/ LOW /* = new LOW() */,
    /*public static final*/ NORMAL /* = new NORMAL() */,
    /*public static final*/ HIGH /* = new HIGH() */,
    /*public static final*/ CRITICAL /* = new CRITICAL() */;
    
    OperationPriority() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.assistant.core.coordinator.OperationPriority> getEntries() {
        return null;
    }
}