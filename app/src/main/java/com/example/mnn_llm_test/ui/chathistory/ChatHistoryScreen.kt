package com.example.mnn_llm_test.ui.chathistory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.mnn_llm_test.navigation.Screen
import com.example.mnn_llm_test.ui.BottomNavigationStrip
import com.example.mnntest.ChatApplication
import com.example.mnntest.data.ChatThread
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    navController: NavHostController,
    currentScreen: String,
    hasChatHistory: Boolean,
    onNavigateToCamera: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ChatApplication).repository
    val coroutineScope = rememberCoroutineScope()
    
    val allChatThreads by repository.getAllChatThreads().collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat History") }
            )
        },
        bottomBar = {
            BottomNavigationStrip(
                currentScreen = currentScreen,
                hasChatHistory = hasChatHistory,
                onCameraClick = onNavigateToCamera,
                onChatClick = onNavigateToChat,
                onHistoryClick = { /* Already on history */ }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (allChatThreads.isEmpty()) {
                // Show empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No chat history yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start by capturing an image with the camera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allChatThreads, key = { it.id }) { chatThread ->
                        ChatHistoryItem(
                            chatThread = chatThread,
                            onChatClick = {
                                navController.navigate(Screen.ChatView.routeWithArgs(threadId = chatThread.id))
                            },
                            onDeleteClick = {
                                coroutineScope.launch {
                                    repository.deleteChatThreadById(chatThread.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatHistoryItem(
    chatThread: ChatThread,
    onChatClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chatThread.title ?: "Chat ${chatThread.id}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(chatThread.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row {
                // Open Chat Button
                TextButton(
                    onClick = onChatClick
                ) {
                    Text("Open")
                }
                
                // Delete Button
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Chat",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: java.sql.Timestamp): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp.time))
} 