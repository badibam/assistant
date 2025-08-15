package com.assistant.tools.base;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\bf\u0018\u00002\u00020\u0001J\u0012\u0010\u0002\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\u00040\u0003H&J\b\u0010\u0005\u001a\u00020\u0006H\u0016J\b\u0010\u0007\u001a\u00020\bH&\u00a8\u0006\t"}, d2 = {"Lcom/assistant/tools/base/ToolType;", "", "getEntities", "", "Lkotlin/reflect/KClass;", "getSchemaVersion", "", "getToolTypeName", "", "app_debug"})
public abstract interface ToolType {
    
    /**
     * Retourne les entités Room que ce type d'outil utilise
     * Ces entités seront automatiquement ajoutées à la base de données
     */
    @org.jetbrains.annotations.NotNull()
    public abstract java.util.List<kotlin.reflect.KClass<?>> getEntities();
    
    /**
     * Nom unique du type d'outil (ex: "tracking", "objective")
     */
    @org.jetbrains.annotations.NotNull()
    public abstract java.lang.String getToolTypeName();
    
    /**
     * Version du schéma de données de cet outil
     * Utilisé pour les migrations spécifiques à l'outil
     */
    public abstract int getSchemaVersion();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        /**
         * Version du schéma de données de cet outil
         * Utilisé pour les migrations spécifiques à l'outil
         */
        public static int getSchemaVersion(@org.jetbrains.annotations.NotNull()
        com.assistant.tools.base.ToolType $this) {
            return 0;
        }
    }
}