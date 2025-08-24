package com.assistant.themes.base

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

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
object UI {
    
    // BASIC LAYOUT COMPONENTS
    
    /**
     * Column layout - vertical arrangement of components
     * Delegates to current theme for customization (spacing, animations, etc.)
     */
    @Composable
    fun Column(
        modifier: Modifier = Modifier,
        verticalArrangement: Arrangement.Vertical = Arrangement.Top,
        horizontalAlignment: Alignment.Horizontal = Alignment.Start,
        content: @Composable ColumnScope.() -> Unit
    ) = CurrentTheme.current.Column(modifier, verticalArrangement, horizontalAlignment, content)
    
    /**
     * Row layout - horizontal arrangement of components
     * Delegates to current theme for customization
     */
    @Composable
    fun Row(
        modifier: Modifier = Modifier,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
        verticalAlignment: Alignment.Vertical = Alignment.Top,
        content: @Composable RowScope.() -> Unit
    ) = CurrentTheme.current.Row(modifier, horizontalArrangement, verticalAlignment, content)
    
    /**
     * Box layout - overlay/stacked arrangement of components
     * Delegates to current theme for customization
     */
    @Composable
    fun Box(
        modifier: Modifier = Modifier,
        contentAlignment: Alignment = Alignment.TopStart,
        content: @Composable BoxScope.() -> Unit
    ) = CurrentTheme.current.Box(modifier, contentAlignment, content)
    
    /**
     * Spacer - creates empty space between components
     * Delegates to current theme (themes could add visual dividers, etc.)
     */
    @Composable
    fun Spacer(modifier: Modifier) = CurrentTheme.current.Spacer(modifier)
    
    // SEMANTIC LAYOUT COMPONENTS
    
    /**
     * Main screen container - defines overall screen layout and padding
     * Delegates to current theme's Screen implementation
     */
    @Composable
    fun Screen(
        type: ScreenType = ScreenType.MAIN,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Screen(type, modifier, content)
    
    /**
     * Container component - groups related UI elements with consistent spacing
     * Different types provide semantic meaning (PRIMARY, SECONDARY, etc.)
     */
    @Composable
    fun Container(
        type: ContainerType,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Container(type, modifier, content)
    
    /**
     * Card component - elevated surface for grouping related content
     * Types: ZONE, TOOL, DATA_ENTRY, SYSTEM for different visual contexts
     */
    @Composable
    fun Card(
        type: CardType,
        semantic: String = "",
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Card(type, semantic, modifier, content)
    
    // INTERACTIVE COMPONENTS
    
    /**
     * Button component - interactive element for user actions
     * Types: PRIMARY (main action), SECONDARY (outline), DANGER, GHOST, ICON
     */
    @Composable
    fun Button(
        type: ButtonType,
        semantic: String = "",
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Button(type, semantic, onClick, modifier, enabled, content)
    
    /**
     * Text input field - for user text entry
     * Types: STANDARD, SEARCH (rounded), NUMERIC, MULTILINE
     */
    @Composable
    fun TextField(
        type: TextFieldType,
        value: String,
        onValueChange: (String) -> Unit,
        semantic: String = "",
        modifier: Modifier = Modifier,
        placeholder: String = ""
    ) = CurrentTheme.current.TextField(type, value, onValueChange, semantic, modifier, placeholder)
    
    // TEXT COMPONENTS
    
    /**
     * Text component - displays text with semantic styling
     * Types: TITLE (large), SUBTITLE, BODY (normal), CAPTION (small), LABEL (buttons)
     */
    @Composable
    fun Text(
        text: String,
        type: TextType,
        semantic: String = "",
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.Text(text, type, semantic, modifier)
    
    // NAVIGATION COMPONENTS
    
    /**
     * Top application bar - shows screen title and navigation actions
     * Types: DEFAULT, ZONE (zone-specific), TOOL (tool-specific)
     */
    @Composable
    fun TopBar(
        type: TopBarType = TopBarType.DEFAULT,
        title: String = "",
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.TopBar(type, title, modifier)
    
    /**
     * Navigation item - individual navigation element (tab, menu item, breadcrumb)
     * Supports selection state for current navigation context
     */
    @Composable
    fun NavigationItem(
        type: NavigationItemType,
        isSelected: Boolean = false,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.NavigationItem(type, isSelected, onClick, modifier, content)
    
    // ZONE/TOOL SPECIFIC COMPONENTS
    
    /**
     * Zone card - displays a zone with its name and navigation action
     * Used in main screen to show available zones
     */
    @Composable
    fun ZoneCard(
        zoneName: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.ZoneCard(zoneName, onClick, modifier)
    
    /**
     * Tool widget - displays a tool instance with type and name
     * Used to show individual tool instances within zones
     */
    @Composable
    fun ToolWidget(
        toolType: String,
        instanceName: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.ToolWidget(toolType, instanceName, onClick, modifier)
    
    // SYSTEM COMPONENTS
    
    /**
     * Terminal component - displays system messages and command output
     * Shows status messages, logs, and command results to user
     */
    @Composable
    fun Terminal(
        content: String,
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.Terminal(content, modifier)
    
    /**
     * Loading indicator - shows progress during async operations
     * Types: DEFAULT (circular), MINIMAL (linear), FULL_SCREEN (overlay)
     */
    @Composable
    fun LoadingIndicator(
        type: LoadingType = LoadingType.DEFAULT,
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.LoadingIndicator(type, modifier)
    
    // DIALOG COMPONENTS
    
    /**
     * Selection dialog - modal selection UI for choosing from a list of items
     * Generic component that can handle any type T with custom item rendering
     */
    @Composable
    fun <T> SelectionDialog(
        isVisible: Boolean,
        title: String,
        items: List<T>,
        selectedItem: T? = null,
        onItemSelected: (T) -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier,
        itemContent: @Composable (T) -> Unit
    ) = CurrentTheme.current.SelectionDialog(
        isVisible = isVisible,
        title = title,
        items = items,
        selectedItem = selectedItem,
        onItemSelected = onItemSelected,
        onDismiss = onDismiss,
        modifier = modifier,
        itemContent = itemContent
    )
    
    /**
     * Confirmation dialog - modal dialog for confirming dangerous actions
     * Shows a title, message, and confirm/cancel buttons
     */
    @Composable
    fun ConfirmDialog(
        isVisible: Boolean,
        title: String,
        message: String,
        confirmText: String = "Confirmer",
        cancelText: String = "Annuler",
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        modifier: Modifier = Modifier
    ) = CurrentTheme.current.ConfirmDialog(
        isVisible = isVisible,
        title = title,
        message = message,
        confirmText = confirmText,
        cancelText = cancelText,
        onConfirm = onConfirm,
        onCancel = onCancel,
        modifier = modifier
    )
    
    /**
     * Form dialog - modal dialog with custom content
     * Shows a title and allows custom form content
     */
    @Composable
    fun FormDialog(
        isVisible: Boolean,
        title: String,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.FormDialog(
        isVisible = isVisible,
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        content = content
    )
}

// SEMANTIC TYPES
enum class ScreenType { MAIN, ZONE_DETAIL, TOOL_INSTANCE, SETTINGS }
enum class ContainerType { PRIMARY, SECONDARY, SIDEBAR, FLOATING }
enum class CardType { ZONE, TOOL, DATA_ENTRY, SYSTEM }
enum class ButtonType { PRIMARY, SECONDARY, TERTIARY, DANGER, GHOST, ICON }
enum class TextFieldType { STANDARD, SEARCH, NUMERIC, MULTILINE }
enum class TextType { TITLE, SUBTITLE, BODY, CAPTION, LABEL }
enum class TopBarType { DEFAULT, ZONE, TOOL }
enum class NavigationItemType { TAB, BREADCRUMB, MENU }
enum class LoadingType { DEFAULT, MINIMAL, FULL_SCREEN }