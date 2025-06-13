package com.example.mnn_llm_test.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.mnntest.ChatApplication
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.mnn_llm_test.MainActivity

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ChatApplication).repository
    val allChatThreads by repository.getAllChatThreads().collectAsState(initial = emptyList())
    val hasExistingChats = allChatThreads.isNotEmpty()
    val coroutineScope = rememberCoroutineScope()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Section
            NavigationSection(
                icon = Icons.Default.Camera,
                label = "Camera",
                isSelected = currentRoute == Screen.CameraView.route,
                enabled = true,
                onClick = {
                    if (currentRoute != Screen.CameraView.route) {
                        // 🔇 Stop TTS before navigation
                        MainActivity.globalTtsHelper?.forceStop()
                        navController.navigate(Screen.CameraView.route) {
                            // Clear back stack to make camera the root
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            
            // Chat Section
            NavigationSection(
                icon = Icons.Default.Chat,
                label = "Chat",
                isSelected = currentRoute?.startsWith("chat_view") == true,
                enabled = hasExistingChats,
                onClick = {
                    if (hasExistingChats && currentRoute?.startsWith("chat_view") != true) {
                        // 🔇 Stop TTS before navigation
                        MainActivity.globalTtsHelper?.forceStop()
                        coroutineScope.launch {
                            val latestThread = allChatThreads.firstOrNull()
                            latestThread?.let { thread ->
                                navController.navigate(Screen.ChatView.routeWithArgs(threadId = thread.id)) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            
            // History Section
            NavigationSection(
                icon = Icons.Default.History,
                label = "History",
                isSelected = currentRoute == Screen.ChatHistory.route,
                enabled = hasExistingChats,
                onClick = {
                    if (hasExistingChats && currentRoute != Screen.ChatHistory.route) {
                        // 🔇 Stop TTS before navigation
                        MainActivity.globalTtsHelper?.forceStop()
                        navController.navigate(Screen.ChatHistory.route) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavigationSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                enabled = enabled,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = iconColor
        )
    }
} 