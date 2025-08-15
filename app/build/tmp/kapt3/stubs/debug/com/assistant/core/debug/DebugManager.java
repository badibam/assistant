package com.assistant.core.debug;

/**
 * Manager centralisé pour le debug
 * - Logs Android (adb logcat)
 * - Messages dans l'interface utilisateur
 * - Historique des actions
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0010\u0003\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0006H\u0002J\u0006\u0010\u0010\u001a\u00020\u000eJ\u0018\u0010\u0011\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00062\b\b\u0002\u0010\u0012\u001a\u00020\u0013J\u000e\u0010\u0014\u001a\u00020\u000e2\u0006\u0010\u0015\u001a\u00020\u0006J\u001a\u0010\u0016\u001a\u00020\u000e2\u0006\u0010\u0017\u001a\u00020\u00062\n\b\u0002\u0010\u0018\u001a\u0004\u0018\u00010\u0019J\u0016\u0010\u001a\u001a\u00020\u000e2\u0006\u0010\u001b\u001a\u00020\u00062\u0006\u0010\u001c\u001a\u00020\u0006R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00060\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00060\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\u001d"}, d2 = {"Lcom/assistant/core/debug/DebugManager;", "", "()V", "MAX_MESSAGES", "", "TAG", "", "_debugMessages", "Landroidx/compose/runtime/snapshots/SnapshotStateList;", "debugMessages", "", "getDebugMessages", "()Ljava/util/List;", "addToUI", "", "message", "clearDebug", "debug", "showInUI", "", "debugButtonClick", "buttonName", "debugError", "error", "exception", "", "debugNavigation", "from", "to", "app_debug"})
public final class DebugManager {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ASSISTANT_DEBUG";
    
    /**
     * Liste des messages debug pour affichage dans l'UI
     * Utilise mutableStateListOf pour recomposition automatique
     */
    @org.jetbrains.annotations.NotNull()
    private static final androidx.compose.runtime.snapshots.SnapshotStateList<java.lang.String> _debugMessages = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<java.lang.String> debugMessages = null;
    
    /**
     * Limite du nombre de messages à garder en mémoire
     */
    private static final int MAX_MESSAGES = 50;
    @org.jetbrains.annotations.NotNull()
    public static final com.assistant.core.debug.DebugManager INSTANCE = null;
    
    private DebugManager() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> getDebugMessages() {
        return null;
    }
    
    /**
     * Fonction principale de debug
     * @param message Le message à logger
     * @param showInUI Si true, affiche aussi dans l'interface (par défaut true)
     */
    public final void debug(@org.jetbrains.annotations.NotNull()
    java.lang.String message, boolean showInUI) {
    }
    
    /**
     * Ajoute un message à la zone debug de l'UI
     */
    private final void addToUI(java.lang.String message) {
    }
    
    /**
     * Vide la zone debug
     */
    public final void clearDebug() {
    }
    
    /**
     * Debug spécifique pour les clics boutons
     */
    public final void debugButtonClick(@org.jetbrains.annotations.NotNull()
    java.lang.String buttonName) {
    }
    
    /**
     * Debug pour les navigations
     */
    public final void debugNavigation(@org.jetbrains.annotations.NotNull()
    java.lang.String from, @org.jetbrains.annotations.NotNull()
    java.lang.String to) {
    }
    
    /**
     * Debug pour les erreurs
     */
    public final void debugError(@org.jetbrains.annotations.NotNull()
    java.lang.String error, @org.jetbrains.annotations.Nullable()
    java.lang.Throwable exception) {
    }
}