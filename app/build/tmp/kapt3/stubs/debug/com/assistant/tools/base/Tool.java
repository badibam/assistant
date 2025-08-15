package com.assistant.tools.base;

/**
 * Abstract base class for all tools
 * Provides common functionality and enforces the contract
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b&\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u0004H\u0096@\u00a2\u0006\u0002\u0010\u0005J\u0014\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\t0\u0007H\u0016J\u0016\u0010\n\u001a\u00020\u00042\u0006\u0010\u000b\u001a\u00020\fH\u0096@\u00a2\u0006\u0002\u0010\rJ\"\u0010\u000e\u001a\u00020\u000f2\u0012\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\t0\u0007H\u0096@\u00a2\u0006\u0002\u0010\u0011\u00a8\u0006\u0012"}, d2 = {"Lcom/assistant/tools/base/Tool;", "Lcom/assistant/tools/base/ToolContract;", "()V", "cleanup", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSettings", "", "", "", "initialize", "instance", "Lcom/assistant/core/database/entities/ToolInstance;", "(Lcom/assistant/core/database/entities/ToolInstance;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateSettings", "Lcom/assistant/tools/base/OperationResult;", "settings", "(Ljava/util/Map;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public abstract class Tool implements com.assistant.tools.base.ToolContract {
    
    public Tool() {
        super();
    }
    
    /**
     * Initialize the tool with a specific instance configuration
     */
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object initialize(@org.jetbrains.annotations.NotNull()
    com.assistant.core.database.entities.ToolInstance instance, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Clean up tool resources
     */
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object cleanup(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Handle tool-specific settings or preferences
     */
    @org.jetbrains.annotations.NotNull()
    public java.util.Map<java.lang.String, java.lang.Object> getSettings() {
        return null;
    }
    
    /**
     * Update tool settings
     */
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object updateSettings(@org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, ? extends java.lang.Object> settings, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.assistant.tools.base.OperationResult> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String getMetadataPath() {
        return null;
    }
}