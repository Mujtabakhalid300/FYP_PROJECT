package com.example.mnn_llm_test.ui.chathistory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.mnn_llm_test.navigation.Screen
import com.example.mnntest.ChatApplication
import com.example.mnn_llm_test.MainActivity
import com.example.mnntest.data.ChatThread
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ChatApplication).repository
    val allChatThreads by repository.getAllChatThreads().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    
    // Search functionality
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var isRecording by remember { mutableStateOf(false) }
    val globalSttHelper = MainActivity.globalSttHelper
    val isSttReady = globalSttHelper?.isModelReady() == true

    // Define callbacks for this screen
    val historySttFinalCallback: (String) -> Unit = { transcription ->
        Log.d("ChatHistorySearch", "📝 STT detected speech: '$transcription'")
        searchText = TextFieldValue(searchText.text + " " + transcription)
        isRecording = false
        Log.d("ChatHistorySearch", "🔍 Updated search text to: '${searchText.text}'")
    }
    
    val historySttErrorCallback: (String) -> Unit = { error ->
        Log.e("ChatHistorySearch", "❌ STT Error: $error")
        isRecording = false
    }

    // Setup STT callbacks for search
    LaunchedEffect(globalSttHelper) {
        globalSttHelper?.setCallbacks(
            onFinalTranscription = historySttFinalCallback,
            onError = historySttErrorCallback
        )
        Log.d("ChatHistorySearch", "🎙️ ChatHistoryScreen STT callbacks registered")
    }

    // Cleanup STT callbacks when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                globalSttHelper?.stopRecording()
                isRecording = false
                Log.d("ChatHistorySearch", "🛑 Stopped STT recording on ChatHistoryScreen dispose")
            }
            // Safely clear callbacks only if they're still ours
            globalSttHelper?.clearCallbacksIfMatching(historySttFinalCallback, historySttErrorCallback)
            Log.d("ChatHistorySearch", "🧹 ChatHistoryScreen disposed - attempted safe callback cleanup")
        }
    }

    // Fuzzy search filtering
    val filteredThreads = remember(allChatThreads, searchText.text) {
        if (searchText.text.isBlank()) {
            allChatThreads
        } else {
            val query = searchText.text.lowercase()
            Log.d("ChatHistorySearch", "🔍 Filtering ${allChatThreads.size} threads with query: '$query'")
            
            val filtered = allChatThreads.filter { thread ->
                val title = thread.title?.lowercase() ?: ""
                val dateString = formatDate(thread.updatedAt).lowercase()
                
                // Simple fuzzy search: check if query words are contained in title or date
                val queryWords = query.split(" ").filter { it.isNotBlank() }
                val matches = queryWords.fastAny { word ->
                    title.contains(word) || dateString.contains(word)
                }
                
                if (matches) {
                    Log.d("ChatHistorySearch", "✅ Match found: '${thread.title}' for query '$query'")
                }
                matches
            }
            
            Log.d("ChatHistorySearch", "🎯 Filtered to ${filtered.size} threads")
            filtered
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Static header with search bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics {
                    contentDescription = "Chat History. Browse your previous conversations. Use the search bar to find specific chats, or use the microphone to search by voice. Tap any chat to continue the conversation."
                }
        ) {
            Text(
                text = "Chat History",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Search bar with STT support
            OutlinedTextField(
                value = searchText,
                onValueChange = { 
                    searchText = it
                    Log.d("ChatHistorySearch", "⌨️ Keyboard input changed search to: '${it.text}'")
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search chat history...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    Row {
                        // Clear button
                        if (searchText.text.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchText = TextFieldValue("")
                                    Log.d("ChatHistorySearch", "🗑️ Cleared search text")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                        
                        // STT button
                        IconButton(
                            onClick = {
                                if (isSttReady && globalSttHelper != null) {
                                    if (isRecording) {
                                        globalSttHelper.stopRecording()
                                        isRecording = false
                                        Log.d("ChatHistorySearch", "🛑 Stopped STT recording")
                                    } else {
                                        globalSttHelper.startRecording()
                                        isRecording = true
                                        Log.d("ChatHistorySearch", "🎤 Started STT recording")
                                    }
                                }
                            },
                            enabled = isSttReady && globalSttHelper != null
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop Recording" else "Record Audio",
                                tint = if (isRecording) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                    }
                },
                singleLine = true
            )
        }

        // Scrollable content
        if (allChatThreads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chat history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else if (filteredThreads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chats match your search",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredThreads, key = { it.id }) { thread ->
                    ChatHistoryItem(
                        thread = thread,
                        onItemClick = {
                            // 🔇 Stop TTS and STT before navigation
                            MainActivity.globalTtsHelper?.forceStop()
                            if (isRecording) {
                                globalSttHelper?.stopRecording()
                                isRecording = false
                                Log.d("ChatHistorySearch", "🛑 Stopped STT recording before navigation")
                            }
                            navController.navigate(Screen.ChatView.routeWithArgs(threadId = thread.id)) {
                                launchSingleTop = true
                            }
                        },
                        onDeleteClick = {
                            coroutineScope.launch {
                                repository.deleteChatThreadById(thread.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryItem(
    thread: ChatThread,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = thread.title ?: formatDate(thread.updatedAt)
    val dateCreated = formatDate(thread.createdAt)
    
    // Create TalkBack-friendly description
    val talkBackDescription = "Chat History Item Titled: $title, created at $dateCreated"
    
    // Detect TalkBack state
    val accessibilityManager = remember { 
        ContextCompat.getSystemService(context, AccessibilityManager::class.java) 
    }
    val isTalkBackEnabled = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Check TalkBack state periodically
        while (true) {
            val isEnabled = accessibilityManager?.isTouchExplorationEnabled == true
            isTalkBackEnabled.value = isEnabled
            kotlinx.coroutines.delay(500) // Check every 500ms
        }
    }
    
    // Create fresh interaction source based on TalkBack state to avoid conflicts
    val interactionSource = remember(isTalkBackEnabled.value) { MutableInteractionSource() }
    val rippleIndication = androidx.compose.material.ripple.rememberRipple()
    
    // Apply different touch handling strategies based on TalkBack state
    if (isTalkBackEnabled.value) {
        // TalkBack is enabled - use semantic touch handling with no Card onClick
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = talkBackDescription
                    onClick(label = null, action = { onItemClick(); true })
                }
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Date created: $dateCreated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Chat",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    } else {
        // TalkBack is disabled - use normal Card with onClick
        Card(
            onClick = onItemClick,
            modifier = modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = talkBackDescription
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = rippleIndication,
                    onClick = onItemClick
                )
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Date created: $dateCreated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Chat",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: java.sql.Timestamp): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp.time))
} 