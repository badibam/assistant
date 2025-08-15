package com.assistant.tools.base;

/**
 * Contract interface that all tools must implement
 * Defines mandatory elements for tool integration
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010$\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J*\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0012\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00010\u0007H\u00a6@\u00a2\u0006\u0002\u0010\bJ\u000e\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00050\nH&J\b\u0010\u000b\u001a\u00020\u0005H&J\b\u0010\f\u001a\u00020\u0005H\u0016J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0005H&\u00a8\u0006\u0010"}, d2 = {"Lcom/assistant/tools/base/ToolContract;", "", "execute", "Lcom/assistant/tools/base/OperationResult;", "operation", "", "params", "", "(Ljava/lang/String;Ljava/util/Map;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAvailableOperations", "", "getDefaultConfig", "getMetadataPath", "validateConfig", "Lcom/assistant/tools/base/ConfigValidationResult;", "configJson", "app_debug"})
public abstract interface ToolContract {
    
    /**
     * Get path to metadata.json file for this tool
     */
    @org.jetbrains.annotations.NotNull()
    public abstract java.lang.String getMetadataPath();
    
    /**
     * Get default configuration template
     */
    @org.jetbrains.annotations.NotNull()
    public abstract java.lang.String getDefaultConfig();
    
    /**
     * Validate a configuration JSON
     */
    @org.jetbrains.annotations.NotNull()
    public abstract com.assistant.tools.base.ConfigValidationResult validateConfig(@org.jetbrains.annotations.NotNull()
    java.lang.String configJson);
    
    /**
     * Execute an operation with given parameters
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object execute(@org.jetbrains.annotations.NotNull()
    java.lang.String operation, @org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, ? extends java.lang.Object> params, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.assistant.tools.base.OperationResult> $completion);
    
    /**
     * Get available operations for this tool
     */
    @org.jetbrains.annotations.NotNull()
    public abstract java.util.List<java.lang.String> getAvailableOperations();
    
    /**
     * Contract interface that all tools must implement
     * Defines mandatory elements for tool integration
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        /**
         * Get path to metadata.json file for this tool
         */
        @org.jetbrains.annotations.NotNull()
        public static java.lang.String getMetadataPath(@org.jetbrains.annotations.NotNull()
        com.assistant.tools.base.ToolContract $this) {
            return null;
        }
    }
}