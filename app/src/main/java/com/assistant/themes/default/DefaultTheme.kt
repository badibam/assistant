package com.assistant.themes.default

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*

/**
 * Default theme implementation - clean Material 3 design
 */
object DefaultTheme : ThemeContract {
    
    @Composable
    override fun Screen(
        type: ScreenType,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
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
        
        Column(modifier = containerModifier) {
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
            Box(modifier = Modifier.padding(16.dp)) {
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
        content: @Composable () -> Unit
    ) {
        when (type) {
            ButtonType.PRIMARY -> Button(
                onClick = onClick,
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { content() }
            
            ButtonType.SECONDARY -> OutlinedButton(
                onClick = onClick,
                modifier = modifier
            ) { content() }
            
            ButtonType.DANGER -> Button(
                onClick = onClick,
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { content() }
            
            ButtonType.GHOST -> TextButton(
                onClick = onClick,
                modifier = modifier
            ) { content() }
            
            ButtonType.ICON -> IconButton(
                onClick = onClick,
                modifier = modifier
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
                placeholder = { Text(placeholder) }
            )
            
            TextFieldType.SEARCH -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                shape = RoundedCornerShape(50.dp)
            )
            
            TextFieldType.NUMERIC -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) }
            )
            
            TextFieldType.MULTILINE -> OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
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
            TextType.TITLE -> Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                modifier = modifier
            )
            
            TextType.SUBTITLE -> Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = modifier
            )
            
            TextType.BODY -> Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = modifier
            )
            
            TextType.CAPTION -> Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
            
            TextType.LABEL -> Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = modifier
            )
        }
    }
    
    @Composable
    override fun TopBar(
        type: TopBarType,
        title: String,
        modifier: Modifier
    ) {
        TopAppBar(
            title = { Text(title) },
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = zoneName,
                    type = TextType.SUBTITLE,
                    semantic = "zone-name"
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = instanceName,
                        type = TextType.BODY,
                        semantic = "tool-instance-name"
                    )
                    Text(
                        text = toolType,
                        type = TextType.CAPTION,
                        semantic = "tool-type"
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
            Text(
                text = content,
                type = TextType.CAPTION,
                semantic = "terminal-output"
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
            LoadingType.FULL_SCREEN -> Box(modifier = modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }
    }
}