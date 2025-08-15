package com.assistant.core.database;

/**
 * Registre des types d'outils disponibles dans l'application
 * Permet la découverte automatique des entités pour la base de données
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0006\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\b0\u0007J\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00050\u0007J\u0010\u0010\n\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0005R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/assistant/core/database/ToolTypeRegistry;", "", "()V", "registeredToolTypes", "", "Lcom/assistant/tools/base/ToolType;", "getAllEntities", "", "Lkotlin/reflect/KClass;", "getAllToolTypes", "getToolType", "typeName", "", "registerToolType", "", "toolType", "app_debug"})
public final class ToolTypeRegistry {
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<com.assistant.tools.base.ToolType> registeredToolTypes = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.assistant.core.database.ToolTypeRegistry INSTANCE = null;
    
    private ToolTypeRegistry() {
        super();
    }
    
    /**
     * Enregistre un nouveau type d'outil
     * À appeler au démarrage de l'application pour chaque outil disponible
     */
    public final void registerToolType(@org.jetbrains.annotations.NotNull()
    com.assistant.tools.base.ToolType toolType) {
    }
    
    /**
     * Retourne toutes les entités de tous les outils enregistrés
     * Utilisé par AppDatabase pour configurer Room
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<kotlin.reflect.KClass<?>> getAllEntities() {
        return null;
    }
    
    /**
     * Récupère un type d'outil par son nom
     */
    @org.jetbrains.annotations.Nullable()
    public final com.assistant.tools.base.ToolType getToolType(@org.jetbrains.annotations.NotNull()
    java.lang.String typeName) {
        return null;
    }
    
    /**
     * Retourne tous les types d'outils enregistrés
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.assistant.tools.base.ToolType> getAllToolTypes() {
        return null;
    }
}