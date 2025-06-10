package com.example.mnn_llm_test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomNavigationStrip(
    currentScreen: String,
    hasChatHistory: Boolean,
    onCameraClick: () -> Unit,
    onChatClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Button
            NavigationButton(
                icon = Icons.Default.Camera,
                text = "Camera",
                isSelected = currentScreen == "camera_view",
                isEnabled = true,
                onClick = onCameraClick
            )
            
            // Chat Button
            NavigationButton(
                icon = Icons.Default.Chat,
                text = "Chat",
                isSelected = currentScreen == "chat_view",
                isEnabled = hasChatHistory,
                onClick = onChatClick
            )
            
            // History Button
            NavigationButton(
                icon = Icons.Default.History,
                text = "History",
                isSelected = currentScreen == "chat_history",
                isEnabled = hasChatHistory,
                onClick = onHistoryClick
            )
        }
    }
}

@Composable
private fun NavigationButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isEnabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = { if (isEnabled) onClick() },
            enabled = isEnabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = buttonColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = buttonColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
} 