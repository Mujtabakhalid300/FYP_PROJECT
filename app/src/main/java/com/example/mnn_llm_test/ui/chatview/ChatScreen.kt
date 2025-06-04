package com.example.mnn_llm_test.ui.chatview

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.mnn_llm_test.MnnLlmJni
import com.example.mnn_llm_test.model.ChatMessage
import com.example.mnn_llm_test.navigation.Screen
import com.example.mnn_llm_test.ui.chatview.ChatViewModel
import com.example.mnn_llm_test.ui.chatview.ChatViewModelFactory
import com.example.mnntest.ChatApplication
import com.example.mnntest.data.ChatThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import java.sql.Timestamp

// Define message sender types
const val SENDER_USER = "user"
const val SENDER_MODEL = "model"
const val SENDER_IMAGE = "image_display" // Special sender type for image display message

// Custom ProgressListener interface for ChatScreen that includes finalizeMessage
interface ChatProgressListener : MnnLlmJni.ProgressListener {
    fun finalizeMessage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    chatSession: MnnLlmJni.ChatSession?,
    threadId: Int
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ChatApplication).repository
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(repository, threadId)
    )

    // Add drawer state and collect all chat threads
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val allThreads by viewModel.allChatThreads.collectAsState()

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val uiMessages by viewModel.messages.collectAsState()
    val currentChatImage by viewModel.chatImage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var imageAlreadySentInConversation by remember { mutableStateOf(false) }
    var initialImageProcessed by remember { mutableStateOf(false) }

    // LaunchedEffect to set new chat history when threadId or chatSession changes
    LaunchedEffect(key1 = threadId, key2 = chatSession) {
        if (chatSession == null || chatSession.nativePtr == 0L) {
            Log.w("ChatScreen", "ChatSession not available or not initialized. Cannot set history.")
            return@LaunchedEffect
        }
        Log.d("ChatScreen", "LaunchedEffect for setNewChatHistory triggered. Thread ID: $threadId")

        // Collect the messages from the ViewModel for the current threadId
        // This assumes viewModel.messages updates when threadId changes its underlying data source.
        // We use distinctUntilChanged to avoid re-processing if the list reference changes but content is same.
        // However, since messages are already collected with collectAsState, we can just use that.
        // The key point is to wait for messages for *this* threadId to be loaded.

        // Convert UiChatMessage list to List<String> for the JNI call
        // The roles (user/assistant) will be inferred by the JNI layer based on order.
        val historyToSet = uiMessages.map { it.text } // Assuming uiMessages is now up-to-date for the current threadId

        Log.d("ChatScreen", "Attempting to set new chat history for thread $threadId. History size: ${historyToSet.size}")
        MnnLlmJni.setNewChatHistory(
            llmPtr = chatSession.nativePtr,
            newChatHistory = if (historyToSet.isEmpty() && threadId != 0) {
                // If loading an existing thread that genuinely has no messages yet (rare), pass emptyList.
                // If it's a truly new thread (e.g. threadId might be 0 or a special value indicating new),
                // passing null or emptyList results in only system prompt, which is correct.
                emptyList()
            } else {
                historyToSet
            },
            isR1Session = false // Defaulting this flag
        )
        Log.i("ChatScreen", "setNewChatHistory called for thread $threadId with ${historyToSet.size} messages.")
    }

    LaunchedEffect(threadId, currentChatImage) {
        imageAlreadySentInConversation = false
        initialImageProcessed = false

        currentChatImage?.let { image ->
            image.imagePath?.let { path ->
                val imageMessageExists = uiMessages.any { it.sender == SENDER_IMAGE && it.text == path }
                if (!imageMessageExists) {
                    // Adding a placeholder via ViewModel ensures it's stored if needed,
                    // or the ViewModel can decide not to store it if it's purely for display.
                    // For now, we assume addMessage handles it.
                    // Consider a specific ViewModel function if SENDER_IMAGE messages aren't actual DB messages.
                    // viewModel.addMessage(path, SENDER_IMAGE) // This line adds it to the DB.
                    // For a non-DB placeholder, you'd manage a separate list in the Composable or ViewModel state.
                    // We will keep it as is for now, it will be stored in the DB as a message.
                }
                initialImageProcessed = true
            }
        }
    }

    val progressListener = remember {
        object : ChatProgressListener {
            private var currentModelMessageId: String? = null // This is the UI Model ID (String)
            private var currentDbMessageId: Int? = null      // This will be the DB ID (Int)
            private val responseBuilder = StringBuilder()

            override fun onProgress(progress: String): Boolean {
                coroutineScope.launch(Dispatchers.Main) {
                    responseBuilder.append(progress)
                    val fullResponse = responseBuilder.toString()

                    if (currentDbMessageId == null) { // Use DB ID to check if it's the first token
                        // First token, create new message
                        launch {
                            val newId = viewModel.addNewMessageAndGetId(fullResponse, SENDER_MODEL)
                            newId?.let {
                                currentDbMessageId = it.toInt() // Store DB ID
                                // We need a way to get the String UI ID if the list updates immediately.
                                // For now, let's assume the list will update and we can find it,
                                // or rely on the fact that updates will use currentDbMessageId.
                                // To make the UI update immediately with a placeholder, we might need a different approach.
                            }
                        }
                    } else {
                        // Subsequent tokens, update existing message
                        currentDbMessageId?.let {
                            viewModel.updateMessage(it, fullResponse)
                        }
                    }
                    // Scroll to bottom is good
                    if (uiMessages.isNotEmpty()) {
                        listState.animateScrollToItem(uiMessages.size - 1)
                    }
                }
                return isGenerating
            }

            override fun finalizeMessage() {
                coroutineScope.launch(Dispatchers.Main) {
                    // Update the final state of the message in DB (already done by onProgress typically)
                    // chatThread.value might need an update for its updatedAt timestamp
                    viewModel.chatThread.value?.let { thread ->
                        val app = context.applicationContext as ChatApplication
                        app.repository.updateChatThread(thread.copy(updatedAt = Timestamp(System.currentTimeMillis())))
                    }

                    responseBuilder.clear()
                    currentModelMessageId = null // Reset UI model ID tracker (if used)
                    currentDbMessageId = null    // Reset DB ID tracker
                    isGenerating = false
                }
            }
        }
    }

    val sendMessage = {
        if (inputText.text.isNotBlank() && chatSession != null && !isGenerating) {
            val userMessageText = inputText.text
            viewModel.addMessage(userMessageText, SENDER_USER)
            inputText = TextFieldValue("")

            coroutineScope.launch {
                if (uiMessages.isNotEmpty()) listState.animateScrollToItem(uiMessages.size - 1)
            }

            isGenerating = true
            progressListener.finalizeMessage()

            val currentImagePath = currentChatImage?.imagePath

            val formattedPrompt = if (currentImagePath != null && !imageAlreadySentInConversation && initialImageProcessed) {
                imageAlreadySentInConversation = true
                "<img>${currentImagePath}</img>$userMessageText"
            } else {
                userMessageText
            }

            Log.d("ChatScreen", "Sending to LLM: Prompt: '$formattedPrompt'")

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    chatSession?.generate(formattedPrompt, progressListener)
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error during LLM prediction: ${e.message}", e)
                    launch(Dispatchers.Main) {
                        viewModel.addMessage("Error: ${e.message}", SENDER_MODEL)
                        progressListener.finalizeMessage()
                    }
                }
            }
        }
    }

    // Encapsulate existing Scaffold within ModalNavigationDrawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Chat Sessions", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                Divider()
                if (allThreads.isEmpty()) {
                    Text("No chat sessions yet.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        items(allThreads, key = { it.id }) { thread ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(NavigationDrawerItemDefaults.ItemPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavigationDrawerItem(
                                    label = { Text(thread.title ?: "Chat ${thread.id}") },
                                    selected = thread.id == threadId, // Highlight current thread
                                    onClick = {
                                        coroutineScope.launch {
                                            drawerState.close()
                                            if (thread.id != threadId) { // Avoid navigating to the same screen
                                                navController.navigate(Screen.ChatView.routeWithArgs(threadId = thread.id)) {
                                                    // Clear back stack up to the start destination and launch as single top
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        inclusive = false
                                                    }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                // Delete button for each thread
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            drawerState.close()
                                            
                                            // Handle deletion logic
                                            val deleteSuccess = viewModel.deleteThread(thread.id)
                                            if (deleteSuccess) {
                                                if (thread.id == threadId) {
                                                    // Current thread is being deleted, find next available thread
                                                    val nextThreadId = viewModel.getNextAvailableThreadId(thread.id)
                                                    if (nextThreadId != null) {
                                                        // Navigate to next available thread
                                                        navController.navigate(Screen.ChatView.routeWithArgs(threadId = nextThreadId)) {
                                                            popUpTo(navController.graph.startDestinationId) {
                                                                inclusive = false
                                                            }
                                                            launchSingleTop = true
                                                        }
                                                    } else {
                                                        // No more threads, navigate to main menu
                                                        navController.navigate(Screen.MainMenu.route) {
                                                            popUpTo(navController.graph.startDestinationId) {
                                                                inclusive = false
                                                            }
                                                        }
                                                    }
                                                }
                                                // If deleting a different thread (not current), stay on current screen
                                            } else {
                                                // Handle delete failure if needed
                                                Log.e("ChatScreen", "Failed to delete thread ${thread.id}")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete Chat ${thread.title ?: thread.id}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(viewModel.chatThread.collectAsState().value?.title ?: "Chat") },
                    navigationIcon = {
                        // IconButton to open drawer
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open Navigation Drawer")
                        }
                    }
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        enabled = !isGenerating
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        Log.d("ChatScreen", "Mic button pressed")
                    }, enabled = !isGenerating) {
                        Icon(Icons.Default.Mic, contentDescription = "Record Audio")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = sendMessage,
                        enabled = !isGenerating && inputText.text.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send Message")
                    }
                }
            }
        ) { contentPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 8.dp),
                reverseLayout = false
            ) {
                if (initialImageProcessed) {
                    currentChatImage?.imagePath?.let { path ->
                        item("displayed_image") {
                            ImageCard(imagePath = path)
                        }
                    }
                }

                items(uiMessages, key = { it.id }) { message ->
                    MessageBubble(message = message, onRetry = {
                        Log.d("ChatScreen", "Retry logic for message: ${message.text}")
                        if (message.sender == SENDER_MODEL && inputText.text.isBlank()) {
                            // Potentially find the user message that led to this error and resend
                        } else if (message.sender == SENDER_USER) {
                            val userMessageText = message.text
                            isGenerating = true
                            progressListener.finalizeMessage()

                            val imagePathForRetry = currentChatImage?.imagePath
                            val promptForRetry = if (imagePathForRetry != null && !imageAlreadySentInConversation) {
                                "<img>${imagePathForRetry}</img>$userMessageText"
                            } else {
                                userMessageText
                            }
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    chatSession?.generate(promptForRetry, progressListener)
                                } catch (e: Exception) {
                                    Log.e("ChatScreen", "Error during LLM prediction on retry: ${e.message}", e)
                                    launch(Dispatchers.Main) {
                                        viewModel.addMessage("Retry Error: ${e.message}", SENDER_MODEL)
                                        progressListener.finalizeMessage()
                                    }
                                }
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun ImageCard(imagePath: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(4.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(data = Uri.fromFile(File(imagePath)))
                        .crossfade(true)
                        .build()
                ),
                contentDescription = "Captured Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun MessageBubble(message: com.example.mnn_llm_test.model.ChatMessage, onRetry: () -> Unit) {
    if (message.sender == SENDER_IMAGE) {
        return
    }

    val bubbleColor = if (message.sender == SENDER_USER) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.sender == SENDER_USER) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val alignment = if (message.sender == SENDER_USER) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = message.text,
            color = textColor,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 300.dp),
            fontSize = 16.sp
        )
    }
} 