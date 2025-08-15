package com.assistant.tools.tracking.data;

/**
 * Repository for tracking data operations
 * Handles CRUD operations for tracking entries
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0086@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\n\u001a\u00020\u00062\u0006\u0010\u000b\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010\rJ\u001a\u0010\u000e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\fJ*\u0010\u0012\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\f2\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0014J\u0018\u0010\u0016\u001a\u0004\u0018\u00010\b2\u0006\u0010\u000b\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010\rJ\u0016\u0010\u0017\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0086@\u00a2\u0006\u0002\u0010\tR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0018"}, d2 = {"Lcom/assistant/tools/tracking/data/TrackingRepository;", "", "dao", "Lcom/assistant/tools/tracking/data/TrackingDao;", "(Lcom/assistant/tools/tracking/data/TrackingDao;)V", "addEntry", "", "entry", "Lcom/assistant/tools/tracking/entities/TrackingData;", "(Lcom/assistant/tools/tracking/entities/TrackingData;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteEntry", "id", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEntries", "Lkotlinx/coroutines/flow/Flow;", "", "instanceId", "getEntriesByDateRange", "startTime", "", "endTime", "getEntry", "updateEntry", "app_debug"})
public final class TrackingRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.assistant.tools.tracking.data.TrackingDao dao = null;
    
    public TrackingRepository(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.tracking.data.TrackingDao dao) {
        super();
    }
    
    /**
     * Get all entries for a specific tool instance
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.assistant.tools.tracking.entities.TrackingData>> getEntries(@org.jetbrains.annotations.NotNull()
    java.lang.String instanceId) {
        return null;
    }
    
    /**
     * Get entries within a date range
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.assistant.tools.tracking.entities.TrackingData>> getEntriesByDateRange(@org.jetbrains.annotations.NotNull()
    java.lang.String instanceId, long startTime, long endTime) {
        return null;
    }
    
    /**
     * Get a specific entry by ID
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getEntry(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.assistant.tools.tracking.entities.TrackingData> $completion) {
        return null;
    }
    
    /**
     * Add a new tracking entry
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object addEntry(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.tracking.entities.TrackingData entry, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Update an existing entry
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object updateEntry(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.tracking.entities.TrackingData entry, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Delete an entry
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object deleteEntry(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
}