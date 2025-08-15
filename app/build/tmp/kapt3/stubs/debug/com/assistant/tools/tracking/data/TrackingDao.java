package com.assistant.tools.tracking.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0006\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ,\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\r0\f2\u0006\u0010\u000e\u001a\u00020\t2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0010H\'J\u001c\u0010\u0012\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\r0\f2\u0006\u0010\u000e\u001a\u00020\tH\'J\u0018\u0010\u0013\u001a\u0004\u0018\u00010\u00052\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0016\u0010\u0014\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0015\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006\u00a8\u0006\u0016"}, d2 = {"Lcom/assistant/tools/tracking/data/TrackingDao;", "", "deleteEntry", "", "entry", "Lcom/assistant/tools/tracking/entities/TrackingData;", "(Lcom/assistant/tools/tracking/entities/TrackingData;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteEntryById", "id", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEntriesByDateRange", "Lkotlinx/coroutines/flow/Flow;", "", "instanceId", "startTime", "", "endTime", "getEntriesByInstance", "getEntryById", "insertEntry", "updateEntry", "app_debug"})
@androidx.room.Dao()
public abstract interface TrackingDao {
    
    @androidx.room.Query(value = "SELECT * FROM tracking_data WHERE tool_instance_id = :instanceId ORDER BY recorded_at DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.assistant.tools.tracking.entities.TrackingData>> getEntriesByInstance(@org.jetbrains.annotations.NotNull()
    java.lang.String instanceId);
    
    @androidx.room.Query(value = "SELECT * FROM tracking_data WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEntryById(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.assistant.tools.tracking.entities.TrackingData> $completion);
    
    @androidx.room.Insert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertEntry(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.tracking.entities.TrackingData entry, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Update()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateEntry(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.tracking.entities.TrackingData entry, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Delete()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteEntry(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.tracking.entities.TrackingData entry, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM tracking_data WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteEntryById(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM tracking_data WHERE tool_instance_id = :instanceId AND recorded_at BETWEEN :startTime AND :endTime ORDER BY recorded_at DESC")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.assistant.tools.tracking.entities.TrackingData>> getEntriesByDateRange(@org.jetbrains.annotations.NotNull()
    java.lang.String instanceId, long startTime, long endTime);
}