package com.assistant.tools.tracking;

/**
 * Tracking Tool Type implementation
 * Provides static metadata for tracking tool instances
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u0016J\b\u0010\u0006\u001a\u00020\u0005H\u0016JK\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\u00052!\u0010\n\u001a\u001d\u0012\u0013\u0012\u00110\u0005\u00a2\u0006\f\b\f\u0012\b\b\r\u0012\u0004\b\b(\u000e\u0012\u0004\u0012\u00020\b0\u000b2\f\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\b0\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0005H\u0017J\b\u0010\u0012\u001a\u00020\u0005H\u0016J\b\u0010\u0013\u001a\u00020\u0005H\u0016\u00a8\u0006\u0014"}, d2 = {"Lcom/assistant/tools/tracking/TrackingToolType;", "Lcom/assistant/core/tools/base/ToolTypeContract;", "()V", "getAvailableOperations", "", "", "getConfigSchema", "getConfigScreen", "", "zoneId", "onSave", "Lkotlin/Function1;", "Lkotlin/ParameterName;", "name", "config", "onCancel", "Lkotlin/Function0;", "existingConfig", "getDefaultConfig", "getDisplayName", "app_debug"})
public final class TrackingToolType implements com.assistant.core.tools.base.ToolTypeContract {
    @org.jetbrains.annotations.NotNull()
    public static final com.assistant.tools.tracking.TrackingToolType INSTANCE = null;
    
    private TrackingToolType() {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String getDisplayName() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String getDefaultConfig() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String getConfigSchema() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.util.List<java.lang.String> getAvailableOperations() {
        return null;
    }
    
    @java.lang.Override()
    @androidx.compose.runtime.Composable()
    public void getConfigScreen(@org.jetbrains.annotations.NotNull()
    java.lang.String zoneId, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onSave, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onCancel, @org.jetbrains.annotations.Nullable()
    java.lang.String existingConfig) {
    }
}