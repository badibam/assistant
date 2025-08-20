package com.assistant.themes.base;

/**
 * Contract that all themes must implement
 * Defines how semantic components are rendered
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00a2\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\bf\u0018\u00002\u00020\u0001J6\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u001c\u0010\b\u001a\u0018\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\u00030\t\u00a2\u0006\u0002\b\u000b\u00a2\u0006\u0002\b\fH\'JI\u0010\r\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00030\u00132\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0014\u001a\u00020\u00152\u0011\u0010\b\u001a\r\u0012\u0004\u0012\u00020\u00030\u0013\u00a2\u0006\u0002\b\u000bH\'J3\u0010\u0016\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020\u00172\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u00052\u0011\u0010\b\u001a\r\u0012\u0004\u0012\u00020\u00030\u0013\u00a2\u0006\u0002\b\u000bH\'J>\u0010\u0018\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u001c2\u001c\u0010\b\u001a\u0018\u0012\u0004\u0012\u00020\u001d\u0012\u0004\u0012\u00020\u00030\t\u00a2\u0006\u0002\b\u000b\u00a2\u0006\u0002\b\fH\'J+\u0010\u001e\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020\u001f2\u0006\u0010\u0004\u001a\u00020\u00052\u0011\u0010\b\u001a\r\u0012\u0004\u0012\u00020\u00030\u0013\u00a2\u0006\u0002\b\u000bH\'J\u0018\u0010 \u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020!2\u0006\u0010\u0004\u001a\u00020\u0005H\'JA\u0010\"\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020#2\u0006\u0010$\u001a\u00020\u00152\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00030\u00132\u0006\u0010\u0004\u001a\u00020\u00052\u0011\u0010\b\u001a\r\u0012\u0004\u0012\u00020\u00030\u0013\u00a2\u0006\u0002\b\u000bH\'J>\u0010%\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010&\u001a\u00020\'2\u0006\u0010(\u001a\u00020)2\u001c\u0010\b\u001a\u0018\u0012\u0004\u0012\u00020*\u0012\u0004\u0012\u00020\u00030\t\u00a2\u0006\u0002\b\u000b\u00a2\u0006\u0002\b\fH\'J+\u0010+\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020,2\u0006\u0010\u0004\u001a\u00020\u00052\u0011\u0010\b\u001a\r\u0012\u0004\u0012\u00020\u00030\u0013\u00a2\u0006\u0002\b\u000bH\'J\u0010\u0010-\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\'J\u0018\u0010.\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0005H\'J(\u0010/\u001a\u00020\u00032\u0006\u00100\u001a\u00020\u00112\u0006\u0010\u000e\u001a\u0002012\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0005H\'JD\u00102\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u0002032\u0006\u00104\u001a\u00020\u00112\u0012\u00105\u001a\u000e\u0012\u0004\u0012\u00020\u0011\u0012\u0004\u0012\u00020\u00030\t2\u0006\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u00106\u001a\u00020\u0011H\'J.\u00107\u001a\u00020\u00032\u0006\u00108\u001a\u00020\u00112\u0006\u00109\u001a\u00020\u00112\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00030\u00132\u0006\u0010\u0004\u001a\u00020\u0005H\'J \u0010:\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020;2\u0006\u0010<\u001a\u00020\u00112\u0006\u0010\u0004\u001a\u00020\u0005H\'J&\u0010=\u001a\u00020\u00032\u0006\u0010>\u001a\u00020\u00112\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00030\u00132\u0006\u0010\u0004\u001a\u00020\u0005H\'\u00a8\u0006?"}, d2 = {"Lcom/assistant/themes/base/ThemeContract;", "", "Box", "", "modifier", "Landroidx/compose/ui/Modifier;", "contentAlignment", "Landroidx/compose/ui/Alignment;", "content", "Lkotlin/Function1;", "Landroidx/compose/foundation/layout/BoxScope;", "Landroidx/compose/runtime/Composable;", "Lkotlin/ExtensionFunctionType;", "Button", "type", "Lcom/assistant/themes/base/ButtonType;", "semantic", "", "onClick", "Lkotlin/Function0;", "enabled", "", "Card", "Lcom/assistant/themes/base/CardType;", "Column", "verticalArrangement", "Landroidx/compose/foundation/layout/Arrangement$Vertical;", "horizontalAlignment", "Landroidx/compose/ui/Alignment$Horizontal;", "Landroidx/compose/foundation/layout/ColumnScope;", "Container", "Lcom/assistant/themes/base/ContainerType;", "LoadingIndicator", "Lcom/assistant/themes/base/LoadingType;", "NavigationItem", "Lcom/assistant/themes/base/NavigationItemType;", "isSelected", "Row", "horizontalArrangement", "Landroidx/compose/foundation/layout/Arrangement$Horizontal;", "verticalAlignment", "Landroidx/compose/ui/Alignment$Vertical;", "Landroidx/compose/foundation/layout/RowScope;", "Screen", "Lcom/assistant/themes/base/ScreenType;", "Spacer", "Terminal", "Text", "text", "Lcom/assistant/themes/base/TextType;", "TextField", "Lcom/assistant/themes/base/TextFieldType;", "value", "onValueChange", "placeholder", "ToolWidget", "toolType", "instanceName", "TopBar", "Lcom/assistant/themes/base/TopBarType;", "title", "ZoneCard", "zoneName", "app_debug"})
public abstract interface ThemeContract {
    
    @androidx.compose.runtime.Composable()
    public abstract void Column(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    androidx.compose.foundation.layout.Arrangement.Vertical verticalArrangement, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Alignment.Horizontal horizontalAlignment, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.ColumnScope, kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void Row(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    androidx.compose.foundation.layout.Arrangement.Horizontal horizontalArrangement, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Alignment.Vertical verticalAlignment, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.RowScope, kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void Box(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Alignment contentAlignment, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super androidx.compose.foundation.layout.BoxScope, kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void Spacer(@org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
    
    @androidx.compose.runtime.Composable()
    public abstract void Screen(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.ScreenType type, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void Container(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.ContainerType type, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void Card(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.CardType type, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void Button(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.ButtonType type, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, boolean enabled, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void TextField(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.TextFieldType type, @org.jetbrains.annotations.NotNull()
    java.lang.String value, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onValueChange, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    java.lang.String placeholder);
    
    @androidx.compose.runtime.Composable()
    public abstract void Text(@org.jetbrains.annotations.NotNull()
    java.lang.String text, @org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.TextType type, @org.jetbrains.annotations.NotNull()
    java.lang.String semantic, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
    
    @androidx.compose.runtime.Composable()
    public abstract void TopBar(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.TopBarType type, @org.jetbrains.annotations.NotNull()
    java.lang.String title, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
    
    @androidx.compose.runtime.Composable()
    public abstract void NavigationItem(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.NavigationItemType type, boolean isSelected, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> content);
    
    @androidx.compose.runtime.Composable()
    public abstract void ZoneCard(@org.jetbrains.annotations.NotNull()
    java.lang.String zoneName, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
    
    @androidx.compose.runtime.Composable()
    public abstract void ToolWidget(@org.jetbrains.annotations.NotNull()
    java.lang.String toolType, @org.jetbrains.annotations.NotNull()
    java.lang.String instanceName, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
    
    @androidx.compose.runtime.Composable()
    public abstract void Terminal(@org.jetbrains.annotations.NotNull()
    java.lang.String content, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
    
    @androidx.compose.runtime.Composable()
    public abstract void LoadingIndicator(@org.jetbrains.annotations.NotNull()
    com.assistant.themes.base.LoadingType type, @org.jetbrains.annotations.NotNull()
    androidx.compose.ui.Modifier modifier);
}