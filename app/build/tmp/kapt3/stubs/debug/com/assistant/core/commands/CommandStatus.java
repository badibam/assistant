package com.assistant.core.commands;

/**
 * Status of command execution
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\b\u00a8\u0006\t"}, d2 = {"Lcom/assistant/core/commands/CommandStatus;", "", "(Ljava/lang/String;I)V", "SUCCESS", "ERROR", "VALIDATION_REQUIRED", "PERMISSION_DENIED", "INVALID_FORMAT", "UNKNOWN_ACTION", "app_debug"})
public enum CommandStatus {
    /*public static final*/ SUCCESS /* = new SUCCESS() */,
    /*public static final*/ ERROR /* = new ERROR() */,
    /*public static final*/ VALIDATION_REQUIRED /* = new VALIDATION_REQUIRED() */,
    /*public static final*/ PERMISSION_DENIED /* = new PERMISSION_DENIED() */,
    /*public static final*/ INVALID_FORMAT /* = new INVALID_FORMAT() */,
    /*public static final*/ UNKNOWN_ACTION /* = new UNKNOWN_ACTION() */;
    
    CommandStatus() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.assistant.core.commands.CommandStatus> getEntries() {
        return null;
    }
}