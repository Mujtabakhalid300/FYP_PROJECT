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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.mnn_llm_test.ui.BottomNavigationStrip
import com.example.mnn_llm_test.ui.chatview.ChatViewModel
import com.example.mnn_llm_test.ui.chatview.ChatViewModelFactory
import com.example.mnn_llm_test.ui.chatview.SENDER_USER
import com.example.mnn_llm_test.ui.chatview.SENDER_MODEL
import com.example.mnn_llm_test.ui.chatview.SENDER_IMAGE
import com.example.mnntest.ChatApplication
import com.example.mnntest.data.ChatThread
import com.example.mnn_llm_test.VoskHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import java.sql.Timestamp

// Custom ProgressListener interface for ChatScreen that includes finalizeMessage
interface ChatProgressListener : MnnLlmJni.ProgressListener {
    fun finalizeMessage()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    chatSession: MnnLlmJni.ChatSession?,
    threadId: Int,
    currentScreen: String,
    hasChatHistory: Boolean,
    onNavigateToCamera: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ChatApplication).repository
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(repository, threadId)
    )

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val uiMessages by viewModel.messages.collectAsState()
    val currentChatImage by viewModel.chatImage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var imageAlreadySentInConversation by remember { mutableStateOf(false) }
    var initialImageProcessed by remember { mutableStateOf(false) }

    // Vosk STT State
    var isVoskInitialized by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val voskHelper = remember(context) {
        VoskHelper(
            context = context,
            onFinalTranscription = {
                inputText = TextFieldValue(inputText.text + " " + it)
                isRecording = false
            },
            onError = {
                Log.e("VoskHelper", "Vosk Error: $it")
                isRecording = false
            }
        )
    }

    LaunchedEffect(Unit) {
        Log.d("ChatScreen", "Attempting to initialize Vosk model...")
        voskHelper.initModel()
        isVoskInitialized = true
        Log.d("ChatScreen", "Vosk model initialization sequence started.")
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                voskHelper.stopRecording()
            }
        }
    }

    LaunchedEffect(key1 = threadId, key2 = chatSession) {
        if (chatSession == null || chatSession.nativePtr == 0L) {
            Log.w("ChatScreen", "ChatSession not available or not initialized. Cannot set history.")
            return@LaunchedEffect
        }
        Log.d("ChatScreen", "LaunchedEffect for setNewChatHistory triggered. Thread ID: $threadId")

        val historyToSet = uiMessages.map { it.text }

        Log.d("ChatScreen", "Attempting to set new chat history for thread $threadId. History size: ${historyToSet.size}")
        MnnLlmJni.setNewChatHistory(
            llmPtr = chatSession.nativePtr,
            newChatHistory = if (historyToSet.isEmpty() && threadId != 0) {
                emptyList()
            } else {
                historyToSet
            },
            isR1Session = false
        )
        Log.i("ChatScreen", "setNewChatHistory called for thread $threadId with ${historyToSet.size} messages.")
    }

    LaunchedEffect(threadId, currentChatImage) {
        imageAlreadySentInConversation = false
        initialImageProcessed = false

        currentChatImage?.let { image ->
            image.imagePath?.let { path ->
                initialImageProcessed = true
            }
        }
    }

    val progressListener = remember {
        object : ChatProgressListener {
            private var currentDbMessageId: Int? = null
            private val responseBuilder = StringBuilder()

            override fun onProgress(progress: String): Boolean {
                coroutineScope.launch(Dispatchers.Main) {
                    responseBuilder.append(progress)
                    val fullResponse = responseBuilder.toString()

                    if (currentDbMessageId == null) {
                        launch {
                            val newId = viewModel.addNewMessageAndGetId(fullResponse, SENDER_MODEL)
                            newId?.let {
                                currentDbMessageId = it.toInt()
                            }
                        }
                    } else {
                        currentDbMessageId?.let {
                            viewModel.updateMessage(it, fullResponse)
                        }
                    }
                    if (uiMessages.isNotEmpty()) {
                        listState.animateScrollToItem(uiMessages.size - 1)
                    }
                }
                return isGenerating
            }

            override fun finalizeMessage() {
                coroutineScope.launch(Dispatchers.Main) {
                    viewModel.chatThread.value?.let { thread ->
                        val app = context.applicationContext as ChatApplication
                        app.repository.updateChatThread(thread.copy(updatedAt = Timestamp(System.currentTimeMillis())))
                    }

                    responseBuilder.clear()
                    currentDbMessageId = null
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.chatThread.collectAsState().value?.title ?: "Chat") }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // Chat messages area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
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
            
            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
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
                    if (isVoskInitialized) {
                        if (isRecording) {
                            voskHelper.stopRecording()
                            isRecording = false
                            Log.d("ChatScreen", "Stopped Vosk recording.")
                        } else {
                            voskHelper.startRecording()
                            isRecording = true
                            Log.d("ChatScreen", "Started Vosk recording.")
                        }
                    }
                }, enabled = !isGenerating && isVoskInitialized) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Record Audio"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = sendMessage,
                    enabled = !isGenerating && inputText.text.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send Message")
                }
            }
            
            // Bottom Navigation Strip
            BottomNavigationStrip(
                currentScreen = currentScreen,
                hasChatHistory = hasChatHistory,
                onCameraClick = onNavigateToCamera,
                onChatClick = { /* Already on chat */ },
                onHistoryClick = onNavigateToHistory
            )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.sender == SENDER_USER) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 16.sp
            )
        }
    }
} 