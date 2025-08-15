package com.assistant.themes.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Universal UI wrapper - encapsulates all Android components with semantic approach
 * Themes implement these interfaces to provide consistent visual identity
 */
object UI {
    
    // LAYOUT COMPONENTS
    @Composable
    fun Screen(
        type: ScreenType = ScreenType.MAIN,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.Screen(type, modifier, content)
    
    @Composable
    fun Container(
        type: ContainerType,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.Container(type, modifier, content)
    
    @Composable
    fun Card(
        type: CardType,
        semantic: String = "",
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.Card(type, semantic, modifier, content)
    
    // INTERACTIVE COMPONENTS
    @Composable
    fun Button(
        type: ButtonType,
        semantic: String = "",
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.Button(type, semantic, onClick, modifier, content)
    
    @Composable
    fun TextField(
        type: TextFieldType,
        value: String,
        onValueChange: (String) -> Unit,
        semantic: String = "",
        modifier: Modifier = Modifier,
        placeholder: String = ""
    ) = CurrentTheme.TextField(type, value, onValueChange, semantic, modifier, placeholder)
    
    // TEXT COMPONENTS
    @Composable
    fun Text(
        text: String,
        type: TextType,
        semantic: String = "",
        modifier: Modifier = Modifier
    ) = CurrentTheme.Text(text, type, semantic, modifier)
    
    // NAVIGATION COMPONENTS
    @Composable
    fun TopBar(
        type: TopBarType = TopBarType.DEFAULT,
        title: String = "",
        modifier: Modifier = Modifier
    ) = CurrentTheme.TopBar(type, title, modifier)
    
    @Composable
    fun NavigationItem(
        type: NavigationItemType,
        isSelected: Boolean = false,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) = CurrentTheme.NavigationItem(type, isSelected, onClick, modifier, content)
    
    // ZONE/TOOL SPECIFIC COMPONENTS
    @Composable
    fun ZoneCard(
        zoneName: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) = CurrentTheme.ZoneCard(zoneName, onClick, modifier)
    
    @Composable
    fun ToolWidget(
        toolType: String,
        instanceName: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) = CurrentTheme.ToolWidget(toolType, instanceName, onClick, modifier)
    
    // SYSTEM COMPONENTS
    @Composable
    fun Terminal(
        content: String,
        modifier: Modifier = Modifier
    ) = CurrentTheme.Terminal(content, modifier)
    
    @Composable
    fun LoadingIndicator(
        type: LoadingType = LoadingType.DEFAULT,
        modifier: Modifier = Modifier
    ) = CurrentTheme.LoadingIndicator(type, modifier)
}

// SEMANTIC TYPES
enum class ScreenType { MAIN, ZONE_DETAIL, TOOL_INSTANCE, SETTINGS }
enum class ContainerType { PRIMARY, SECONDARY, SIDEBAR, FLOATING }
enum class CardType { ZONE, TOOL, DATA_ENTRY, SYSTEM }
enum class ButtonType { PRIMARY, SECONDARY, DANGER, GHOST, ICON }
enum class TextFieldType { STANDARD, SEARCH, NUMERIC, MULTILINE }
enum class TextType { TITLE, SUBTITLE, BODY, CAPTION, LABEL }
enum class TopBarType { DEFAULT, ZONE, TOOL }
enum class NavigationItemType { TAB, BREADCRUMB, MENU }
enum class LoadingType { DEFAULT, MINIMAL, FULL_SCREEN }