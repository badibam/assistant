package com.assistant.themes.default

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.themes.base.TextFieldType
import androidx.compose.foundation.layout.Column as ComposeColumn
import androidx.compose.foundation.layout.Row as ComposeRow
import androidx.compose.foundation.layout.Box as ComposeBox
import androidx.compose.foundation.layout.Spacer as ComposeSpacer

/**
 * Default theme implementation - clean Material 3 design
 */
object DefaultTheme : ThemeContract {
    
    // BASIC LAYOUT COMPONENTS
    @Composable
    override fun Column(
        modifier: Modifier,
        verticalArrangement: Arrangement.Vertical,
        horizontalAlignment: Alignment.Horizontal,
        content: @Composable ColumnScope.() -> Unit
    ) {
        ComposeColumn(
            modifier = modifier,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
    
    @Composable
    override fun Row(
        modifier: Modifier,
        horizontalArrangement: Arrangement.Horizontal,
        verticalAlignment: Alignment.Vertical,
        content: @Composable RowScope.() -> Unit
    ) {
        ComposeRow(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )
    }
    
    @Composable
    override fun Box(
        modifier: Modifier,
        contentAlignment: Alignment,
        content: @Composable BoxScope.() -> Unit
    ) {
        ComposeBox(
            modifier = modifier,
            contentAlignment = contentAlignment,
            content = content
        )
    }
    
    @Composable
    override fun Spacer(modifier: Modifier) {
        ComposeSpacer(modifier = modifier)
    }
    
    // SEMANTIC LAYOUT COMPONENTS
    @Composable
    override fun Screen(
        type: ScreenType,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        ComposeColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }
    }
    
    @Composable
    override fun Container(
        type: ContainerType,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        val containerModifier = when (type) {
            ContainerType.PRIMARY -> modifier.fillMaxWidth()
            ContainerType.SECONDARY -> modifier.fillMaxWidth().padding(8.dp)
            ContainerType.SIDEBAR -> modifier.width(250.dp)
            ContainerType.FLOATING -> modifier.padding(16.dp)
        }
        
        ComposeColumn(modifier = containerModifier) {
            content()
        }
    }
    
    @Composable
    override fun Card(
        type: CardType,
        semantic: String,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        val cardColors = when (type) {
            CardType.ZONE -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            CardType.TOOL -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            CardType.DATA_ENTRY -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            CardType.SYSTEM -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        }
        
        Card(
            modifier = modifier.padding(4.dp),
            colors = cardColors,
            shape = RoundedCornerShape(12.dp)
        ) {
            ComposeBox(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
    
    @Composable
    override fun Button(
        type: ButtonType,
        semantic: String,
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        content: @Composable () -> Unit
    ) {
        when (type) {
            ButtonType.PRIMARY -> Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { content() }
            
            ButtonType.SECONDARY -> OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            ) { content() }
            
            ButtonType.DANGER -> Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { content() }
            
            ButtonType.TERTIARY -> FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            ) { content() }
            
            ButtonType.GHOST -> TextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            ) { content() }
            
            ButtonType.ICON -> IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            ) { content() }
        }
    }
    
    @Composable
    override fun TextField(
        type: TextFieldType,
        value: String,
        onValueChange: (String) -> Unit,
        semantic: String,
        modifier: Modifier,
        placeholder: String
    ) {
        when (type) {
            TextFieldType.STANDARD -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { MaterialText(placeholder) }
            )
            
            TextFieldType.SEARCH -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { MaterialText(placeholder) },
                shape = RoundedCornerShape(50.dp)
            )
            
            TextFieldType.NUMERIC -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { MaterialText(placeholder) }
            )
            
            TextFieldType.MULTILINE -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { MaterialText(placeholder) },
                minLines = 3,
                maxLines = 6
            )
        }
    }
    
    @Composable
    override fun Text(
        text: String,
        type: TextType,
        semantic: String,
        modifier: Modifier
    ) {
        when (type) {
            TextType.TITLE -> MaterialText(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                modifier = modifier
            )
            
            TextType.SUBTITLE -> MaterialText(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = modifier
            )
            
            TextType.BODY -> MaterialText(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
            
            TextType.CAPTION -> MaterialText(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
            
            TextType.LABEL -> MaterialText(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = modifier
            )
        }
    }
    
    // TopAppBar uses experimental Material3 API
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun TopBar(
        type: TopBarType,
        title: String,
        modifier: Modifier
    ) {
        TopAppBar(
            title = { MaterialText(title) },
            modifier = modifier,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
    
    @Composable
    override fun NavigationItem(
        type: NavigationItemType,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = if (isSelected) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            } else {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
            }
        ) {
            content()
        }
    }
    
    @Composable
    override fun ZoneCard(
        zoneName: String,
        onClick: () -> Unit,
        modifier: Modifier
    ) {
        Card(
            type = CardType.ZONE,
            semantic = "zone",
            modifier = modifier
        ) {
            Button(
                type = ButtonType.GHOST,
                semantic = "zone-navigation",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) {
                MaterialText(
                    text = zoneName,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    
    @Composable
    override fun ToolWidget(
        toolType: String,
        instanceName: String,
        onClick: () -> Unit,
        modifier: Modifier
    ) {
        Card(
            type = CardType.TOOL,
            semantic = "tool-$toolType",
            modifier = modifier
        ) {
            Button(
                type = ButtonType.GHOST,
                semantic = "tool-navigation",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) {
                ComposeColumn {
                    MaterialText(
                        text = instanceName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    MaterialText(
                        text = toolType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    @Composable
    override fun Terminal(
        content: String,
        modifier: Modifier
    ) {
        Card(
            type = CardType.SYSTEM,
            semantic = "terminal",
            modifier = modifier
        ) {
            MaterialText(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    @Composable
    override fun LoadingIndicator(
        type: LoadingType,
        modifier: Modifier
    ) {
        when (type) {
            LoadingType.DEFAULT -> CircularProgressIndicator(modifier = modifier)
            LoadingType.MINIMAL -> LinearProgressIndicator(modifier = modifier.fillMaxWidth())
            LoadingType.FULL_SCREEN -> ComposeBox(modifier = modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }
    }
}