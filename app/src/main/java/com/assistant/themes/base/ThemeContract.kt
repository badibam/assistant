package com.assistant.themes.base

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Contract that all themes must implement
 * Defines how semantic components are rendered
 */
interface ThemeContract {
    
    // BASIC LAYOUT COMPONENTS
    @Composable
    fun Column(
        modifier: Modifier,
        verticalArrangement: Arrangement.Vertical,
        horizontalAlignment: Alignment.Horizontal,
        content: @Composable ColumnScope.() -> Unit
    )
    
    @Composable
    fun Row(
        modifier: Modifier,
        horizontalArrangement: Arrangement.Horizontal,
        verticalAlignment: Alignment.Vertical,
        content: @Composable RowScope.() -> Unit
    )
    
    @Composable
    fun Box(
        modifier: Modifier,
        contentAlignment: Alignment,
        content: @Composable BoxScope.() -> Unit
    )
    
    @Composable
    fun Spacer(modifier: Modifier)
    
    // SEMANTIC LAYOUT COMPONENTS
    @Composable
    fun Screen(
        type: ScreenType,
        modifier: Modifier,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun Container(
        type: ContainerType,
        modifier: Modifier,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun Card(
        type: CardType,
        semantic: String,
        modifier: Modifier,
        content: @Composable () -> Unit
    )
    
    // INTERACTIVE COMPONENTS
    @Composable
    fun Button(
        type: ButtonType,
        semantic: String,
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    )
    
    @Composable
    fun TextField(
        type: TextFieldType,
        value: String,
        onValueChange: (String) -> Unit,
        semantic: String,
        modifier: Modifier,
        placeholder: String
    )
    
    // TEXT COMPONENTS
    @Composable
    fun Text(
        text: String,
        type: TextType,
        semantic: String,
        modifier: Modifier
    )
    
    // NAVIGATION COMPONENTS
    @Composable
    fun TopBar(
        type: TopBarType,
        title: String,
        modifier: Modifier
    )
    
    @Composable
    fun NavigationItem(
        type: NavigationItemType,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    )
    
    // ZONE/TOOL SPECIFIC COMPONENTS
    @Composable
    fun ZoneCard(
        zoneName: String,
        onClick: () -> Unit,
        modifier: Modifier
    )
    
    @Composable
    fun ToolWidget(
        toolType: String,
        instanceName: String,
        onClick: () -> Unit,
        modifier: Modifier
    )
    
    // SYSTEM COMPONENTS
    @Composable
    fun Terminal(
        content: String,
        modifier: Modifier
    )
    
    @Composable
    fun LoadingIndicator(
        type: LoadingType,
        modifier: Modifier
    )
}