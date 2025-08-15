package com.assistant.core.database.dao;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\b\u0004\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0018\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u001c\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u000e0\r2\u0006\u0010\u000f\u001a\u00020\tH\'J\u0016\u0010\u0010\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0011\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006\u00a8\u0006\u0012"}, d2 = {"Lcom/assistant/core/database/dao/ToolInstanceDao;", "", "deleteToolInstance", "", "toolInstance", "Lcom/assistant/core/database/entities/ToolInstance;", "(Lcom/assistant/core/database/entities/ToolInstance;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteToolInstanceById", "id", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getToolInstanceById", "getToolInstancesByZone", "Lkotlinx/coroutines/flow/Flow;", "", "zoneId", "insertToolInstance", "updateToolInstance", "app_debug"})
@androidx.room.Dao()
public abstract interface ToolInstanceDao {
    
    @androidx.room.Query(value = "SELECT * FROM tool_instances WHERE zone_id = :zoneId ORDER BY order_index ASC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.assistant.core.database.entities.ToolInstance>> getToolInstancesByZone(@org.jetbrains.annotations.NotNull()
    java.lang.String zoneId);
    
    @androidx.room.Query(value = "SELECT * FROM tool_instances WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getToolInstanceById(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.assistant.core.database.entities.ToolInstance> $completion);
    
    @androidx.room.Insert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertToolInstance(@org.jetbrains.annotations.NotNull()
    com.assistant.core.database.entities.ToolInstance toolInstance, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Update()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateToolInstance(@org.jetbrains.annotations.NotNull()
    com.assistant.core.database.entities.ToolInstance toolInstance, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Delete()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteToolInstance(@org.jetbrains.annotations.NotNull()
    com.assistant.core.database.entities.ToolInstance toolInstance, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM tool_instances WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteToolInstanceById(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}