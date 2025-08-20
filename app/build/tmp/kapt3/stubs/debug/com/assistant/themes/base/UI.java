package com.assistant.themes.base;

/**
 * Universal UI wrapper - Public API for semantic UI components
 *
 * Architecture role: 
 * - Acts as a bridge between app code and the current theme
 * - Delegates all calls to CurrentTheme.current (the active theme)
 * - Provides stable API that doesn't change when themes switch
 *
 * Benefits:
 * - App code uses UI.Text() instead of CurrentTheme.current.Text()  
 * - Theme changes are transparent to consuming code
 * - Easy maintenance: adding new components requires changes only here and in ThemeContract
 *
 * Usage in app:
 * UI.Text("Hello") -> CurrentTheme.current.Text("Hello") -> DefaultTheme.Text("Hello")
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00a4\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J:\u0010\u0003\u001a\u00020\u00042\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\b2\u001c\u0010\t\u001a\u0018\u0012\u0004\u0012\u00020\u000b\u0012\u0004\u0012\u00020\u00040\n\u00a2\u0006\u0002\b\f\u00a2\u0006\u0002\b\rH\u0007JO\u0010\u000e\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u00020\u00102\b\b\u0002\u0010\u0011\u001a\u00020\u00122\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\u00142\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0015\u001a\u00020\u00162\u0011\u0010\t\u001a\r\u0012\u0004\u0012\u00020\u00040\u0014\u00a2\u0006\u0002\b\fH\u0007J7\u0010\u0017\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u00020\u00182\b\b\u0002\u0010\u0011\u001a\u00020\u00122\b\b\u0002\u0010\u0005\u001a\u00020\u00062\u0011\u0010\t\u001a\r\u0012\u0004\u0012\u00020\u00040\u0014\u00a2\u0006\u0002\b\fH\u0007JD\u0010\u0019\u001a\u00020\u00042\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u001a\u001a\u00020\u001b2\b\b\u0002\u0010\u001c\u001a\u00020\u001d2\u001c\u0010\t\u001a\u0018\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u00040\n\u00a2\u0006\u0002\b\f\u00a2\u0006\u0002\b\rH\u0007J-\u0010\u001f\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u00020 2\b\b\u0002\u0010\u0005\u001a\u00020\u00062\u0011\u0010\t\u001a\r\u0012\u0004\u0012\u00020\u00040\u0014\u00a2\u0006\u0002\b\fH\u0007J\u001c\u0010!\u001a\u00020\u00042\b\b\u0002\u0010\u000f\u001a\u00020\"2\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u0007JE\u0010#\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u00020$2\b\b\u0002\u0010%\u001a\u00020\u00162\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\u00142\b\b\u0002\u0010\u0005\u001a\u00020\u00062\u0011\u0010\t\u001a\r\u0012\u0004\u0012\u00020\u00040\u0014\u00a2\u0006\u0002\b\fH\u0007JD\u0010&\u001a\u00020\u00042\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\'\u001a\u00020(2\b\b\u0002\u0010)\u001a\u00020*2\u001c\u0010\t\u001a\u0018\u0012\u0004\u0012\u00020+\u0012\u0004\u0012\u00020\u00040\n\u00a2\u0006\u0002\b\f\u00a2\u0006\u0002\b\rH\u0007J/\u0010,\u001a\u00020\u00042\b\b\u0002\u0010\u000f\u001a\u00020-2\b\b\u0002\u0010\u0005\u001a\u00020\u00062\u0011\u0010\t\u001a\r\u0012\u0004\u0012\u00020\u00040\u0014\u00a2\u0006\u0002\b\fH\u0007J\u0010\u0010.\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u001a\u0010/\u001a\u00020\u00042\u0006\u0010\t\u001a\u00020\u00122\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u0007J,\u00100\u001a\u00020\u00042\u0006\u00101\u001a\u00020\u00122\u0006\u0010\u000f\u001a\u0002022\b\b\u0002\u0010\u0011\u001a\u00020\u00122\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u0007JJ\u00103\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u0002042\u0006\u00105\u001a\u00020\u00122\u0012\u00106\u001a\u000e\u0012\u0004\u0012\u00020\u0012\u0012\u0004\u0012\u00020\u00040\n2\b\b\u0002\u0010\u0011\u001a\u00020\u00122\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u00107\u001a\u00020\u0012H\u0007J0\u00108\u001a\u00020\u00042\u0006\u00109\u001a\u00020\u00122\u0006\u0010:\u001a\u00020\u00122\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\u00142\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u0007J&\u0010;\u001a\u00020\u00042\b\b\u0002\u0010\u000f\u001a\u00020<2\b\b\u0002\u0010=\u001a\u00020\u00122\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u0007J(\u0010>\u001a\u00020\u00042\u0006\u0010?\u001a\u00020\u00122\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\u00142\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006@"}, d2 = {"Lcom/assistant/themes/base/UI;", "", "()V", "Box", "", "modifier", "Landroidx/compose/ui/Modifier;", "contentAlignment", "Landroidx/compose/ui/Alignment;", "content", "Lkotlin/Function1;", "Landroidx/compose/foundation/layout/BoxScope;", "Landroidx/compose/runtime/Composable;", "Lkotlin/ExtensionFunctionType;", "Button", "type", "Lcom/assistant/themes/base/ButtonType;", "semantic", "", "onClick", "Lkotlin/Function0;", "enabled", "", "Card", "Lcom/assistant/themes/base/CardType;", "Column", "verticalArrangement", "Landroidx/compose/foundation/layout/Arrangement$Vertical;", "horizontalAlignment", "Landroidx/compose/ui/Alignment$Horizontal;", "Landroidx/compose/foundation/layout/ColumnScope;", "Container", "Lcom/assistant/themes/base/ContainerType;", "LoadingIndicator", "Lcom/assistant/themes/base/LoadingType;", "NavigationItem", "Lcom/assistant/themes/base/NavigationItemType;", "isSelected", "Row", "horizontalArrangement", "Landroidx/compose/foundation/layout/Arrangement$Horizontal;", "verticalAlignment", "Landroidx/compose/ui/Alignment$Vertical;", "Landroidx/compose/foundation/layout/RowScope;", "Screen", "Lcom/assistant/themes/base/ScreenType;", "Spacer", "Terminal", "Text", "text", "Lcom/assistant/themes/base/TextType;", "TextField", "Lcom/assistant/themes/base/TextFieldType;", "value", "onValueChange", "placeholder", "ToolWidget", "toolType", "instanceName", "TopBar", "Lcom/assistant/themes/base/TopBarType;", "title", "ZoneCard", "zoneName", "app_debug"})
public final class UI {
    @org.jetbrains.annotations.NotNull()
    public static final com.assistant.themes.base.UI INSTANCE = null;
    
    private UI() {
        super();
    }
    
    /**
     * Column layout - vertical arrangement of components
     * Delegates to current theme for customization (spacing, animations, etc.)
     */
    @androidx.compose.runtime.Composable()
    public final void Column(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    androidx.compose.foundation.layout.Arrangement.Vertical verticalArrangement, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Alignment.Horizontal horizontalAlignment, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.ColumnScope, kotlin.Unit> content) {
    }
    
    /**
     * Row layout - horizontal arrangement of components
     * Delegates to current theme for customization
     */
    @androidx.compose.runtime.Composable()
    public final void Row(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    androidx.compose.foundation.layout.Arrangement.Horizontal horizontalArrangement, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Alignment.Vertical verticalAlignment, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.RowScope, kotlin.Unit> content) {
    }
    
    /**
     * Box layout - overlay/stacked arrangement of components
     * Delegates to current theme for customization
     */
    @androidx.compose.runtime.Composable()
    public final void Box(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Alignment contentAlignment, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.BoxScope, kotlin.Unit> content) {
    }
    
    /**
     * Spacer - creates empty space between components
     * Delegates to current theme (themes could add visual dividers, etc.)
     */
    @androidx.compose.runtime.Composable()
    public final void Spacer(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    /**
     * Main screen container - defines overall screen layout and padding
     * Delegates to current theme's Screen implementation
     */
    @androidx.compose.runtime.Composable()
    public final void Screen(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.ScreenType type, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    /**
     * Container component - groups related UI elements with consistent spacing
     * Different types provide semantic meaning (PRIMARY, SECONDARY, etc.)
     */
    @androidx.compose.runtime.Composable()
    public final void Container(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.ContainerType type, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    /**
     * Card component - elevated surface for grouping related content
     * Types: ZONE, TOOL, DATA_ENTRY, SYSTEM for different visual contexts
     */
    @androidx.compose.runtime.Composable()
    public final void Card(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.CardType type, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    /**
     * Button component - interactive element for user actions
     * Types: PRIMARY (main action), SECONDARY (outline), DANGER, GHOST, ICON
     */
    @androidx.compose.runtime.Composable()
    public final void Button(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.ButtonType type, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, boolean enabled, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    /**
     * Text input field - for user text entry
     * Types: STANDARD, SEARCH (rounded), NUMERIC, MULTILINE
     */
    @androidx.compose.runtime.Composable()
    public final void TextField(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.TextFieldType type, @org.jetbrains.annotations.NotNull()
    java.lang.String value, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onValueChange, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    java.lang.String placeholder) {
    }
    
    /**
     * Text component - displays text with semantic styling
     * Types: TITLE (large), SUBTITLE, BODY (normal), CAPTION (small), LABEL (buttons)
     */
    @androidx.compose.runtime.Composable()
    public final void Text(@org.jetbrains.annotations.NotNull()
    java.lang.String text, @org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.TextType type, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    /**
     * Top application bar - shows screen title and navigation actions
     * Types: DEFAULT, ZONE (zone-specific), TOOL (tool-specific)
     */
    @androidx.compose.runtime.Composable()
    public final void TopBar(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.TopBarType type, @org.jetbrains.annotations.NotNull()
    java.lang.String title, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    /**
     * Navigation item - individual navigation element (tab, menu item, breadcrumb)
     * Supports selection state for current navigation context
     */
    @androidx.compose.runtime.Composable()
    public final void NavigationItem(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.NavigationItemType type, boolean isSelected, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content) {
    }
    
    /**
     * Zone card - displays a zone with its name and navigation action
     * Used in main screen to show available zones
     */
    @androidx.compose.runtime.Composable()
    public final void ZoneCard(@org.jetbrains.annotations.NotNull()
    java.lang.String zoneName, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    /**
     * Tool widget - displays a tool instance with type and name
     * Used to show individual tool instances within zones
     */
    @androidx.compose.runtime.Composable()
    public final void ToolWidget(@org.jetbrains.annotations.NotNull()
    java.lang.String toolType, @org.jetbrains.annotations.NotNull()
    java.lang.String instanceName, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    /**
     * Terminal component - displays system messages and command output
     * Shows status messages, logs, and command results to user
     */
    @androidx.compose.runtime.Composable()
    public final void Terminal(@org.jetbrains.annotations.NotNull()
    java.lang.String content, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
    
    /**
     * Loading indicator - shows progress during async operations
     * Types: DEFAULT (circular), MINIMAL (linear), FULL_SCREEN (overlay)
     */
    @androidx.compose.runtime.Composable()
    public final void LoadingIndicator(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.LoadingType type, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier) {
    }
}