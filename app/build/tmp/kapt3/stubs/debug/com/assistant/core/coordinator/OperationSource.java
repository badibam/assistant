package com.assistant.core.coordinator;

/**
 * Possible sources of an operation
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0007\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007\u00a8\u0006\b"}, d2 = {"Lcom/assistant/core/coordinator/OperationSource;", "", "(Ljava/lang/String;I)V", "USER", "AI", "SCHEDULER", "TRIGGER", "CASCADE", "app_debug"})
public enum OperationSource {
    /*public static final*/ USER /* = new USER() */,
    /*public static final*/ AI /* = new AI() */,
    /*public static final*/ SCHEDULER /* = new SCHEDULER() */,
    /*public static final*/ TRIGGER /* = new TRIGGER() */,
    /*public static final*/ CASCADE /* = new CASCADE() */;
    
    OperationSource() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.assistant.core.coordinator.OperationSource> getEntries() {
        return null;
    }
}