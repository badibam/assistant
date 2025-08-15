package com.assistant.tools.base;

/**
 * Represents a configured instance of a tool
 * Bridges between database entity and runtime tool object
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0011\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0086\b\u0018\u0000 !2\u00020\u0001:\u0001!B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\u0006\u0010\u0007\u001a\u00020\u0003\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\t\u0010\u0013\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0014\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0016\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\tH\u00c6\u0003JE\u0010\u0019\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00032\b\b\u0002\u0010\u0007\u001a\u00020\u00032\b\b\u0002\u0010\b\u001a\u00020\tH\u00c6\u0001J\u0013\u0010\u001a\u001a\u00020\u001b2\b\u0010\u001c\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001d\u001a\u00020\tH\u00d6\u0001J\u0006\u0010\u001e\u001a\u00020\u001fJ\t\u0010 \u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0007\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\fR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\f\u00a8\u0006\""}, d2 = {"Lcom/assistant/tools/base/ToolInstanceConfig;", "", "id", "", "zoneId", "toolType", "configJson", "configMetadataJson", "orderIndex", "", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V", "getConfigJson", "()Ljava/lang/String;", "getConfigMetadataJson", "getId", "getOrderIndex", "()I", "getToolType", "getZoneId", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "", "other", "hashCode", "toEntity", "Lcom/assistant/core/database/entities/ToolInstance;", "toString", "Companion", "app_debug"})
public final class ToolInstanceConfig {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String id = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String zoneId = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String toolType = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String configJson = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String configMetadataJson = null;
    private final int orderIndex = 0;
    @org.jetbrains.annotations.NotNull()
    public static final com.assistant.tools.base.ToolInstanceConfig.Companion Companion = null;
    
    public ToolInstanceConfig(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    java.lang.String zoneId, @org.jetbrains.annotations.NotNull()
    java.lang.String toolType, @org.jetbrains.annotations.NotNull()
    java.lang.String configJson, @org.jetbrains.annotations.NotNull()
    java.lang.String configMetadataJson, int orderIndex) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getId() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getZoneId() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getToolType() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getConfigJson() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getConfigMetadataJson() {
        return null;
    }
    
    public final int getOrderIndex() {
        return 0;
    }
    
    /**
     * Create database entity from this config
     */
    @org.jetbrains.annotations.NotNull()
    public final com.assistant.core.database.entities.ToolInstance toEntity() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component1() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component2() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component4() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component5() {
        return null;
    }
    
    public final int component6() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.assistant.tools.base.ToolInstanceConfig copy(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    java.lang.String zoneId, @org.jetbrains.annotations.NotNull()
    java.lang.String toolType, @org.jetbrains.annotations.NotNull()
    java.lang.String configJson, @org.jetbrains.annotations.NotNull()
    java.lang.String configMetadataJson, int orderIndex) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/assistant/tools/base/ToolInstanceConfig$Companion;", "", "()V", "fromEntity", "Lcom/assistant/tools/base/ToolInstanceConfig;", "entity", "Lcom/assistant/core/database/entities/ToolInstance;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        /**
         * Create from database entity
         */
        @org.jetbrains.annotations.NotNull()
        public final com.assistant.tools.base.ToolInstanceConfig fromEntity(@org.jetbrains.annotations.NotNull()
        com.assistant.core.database.entities.ToolInstance entity) {
            return null;
        }
    }
}