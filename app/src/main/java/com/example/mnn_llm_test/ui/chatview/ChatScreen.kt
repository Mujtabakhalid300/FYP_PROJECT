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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.mnn_llm_test.MnnLlmJni
import com.example.mnn_llm_test.model.ChatMessage
import com.example.mnn_llm_test.ui.chatview.ChatViewModel
import com.example.mnn_llm_test.ui.chatview.ChatViewModelFactory
import com.example.mnntest.ChatApplication
import com.example.mnn_llm_test.VoskHelper
import com.example.mnn_llm_test.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
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

    // Collect chat thread info
    val currentThread by viewModel.chatThread.collectAsState()

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val uiMessages by viewModel.messages.collectAsState()
    val currentChatImage by viewModel.chatImage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var imageAlreadySentInConversation by remember { mutableStateOf(false) }
    var initialImageProcessed by remember { mutableStateOf(false) }

    // Global STT State
    var isRecording by remember { mutableStateOf(false) }
    val globalSttHelper = MainActivity.globalSttHelper
    val isVoskInitialized = globalSttHelper?.isModelReady() == true

    // TalkBack detection for dual touch handling
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

    // Define callbacks for this screen
    val chatSttFinalCallback: (String) -> Unit = { transcription ->
        Log.d("ChatScreen", "📝 STT detected speech: '$transcription'")
        inputText = TextFieldValue(inputText.text + " " + transcription)
        isRecording = false
        Log.d("ChatScreen", "💬 Updated chat input to: '${inputText.text}'")
    }
    
    val chatSttErrorCallback: (String) -> Unit = { error ->
        Log.e("ChatScreen", "❌ STT Error: $error")
        isRecording = false
    }

    // Setup STT callbacks for this screen
    LaunchedEffect(globalSttHelper) {
        globalSttHelper?.setCallbacks(
            onFinalTranscription = chatSttFinalCallback,
            onError = chatSttErrorCallback
        )
        Log.d("ChatScreen", "🎙️ ChatScreen STT callbacks registered")
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                globalSttHelper?.stopRecording()
                isRecording = false
                Log.d("ChatScreen", "🛑 Stopped STT recording on ChatScreen dispose")
            }
            // Safely clear callbacks only if they're still ours
            globalSttHelper?.clearCallbacksIfMatching(chatSttFinalCallback, chatSttErrorCallback)
            Log.d("ChatScreen", "🧹 ChatScreen disposed - attempted safe callback cleanup")
        }
    }

    LaunchedEffect(key1 = threadId, key2 = chatSession) {
        if (chatSession == null || chatSession.nativePtr == 0L) {
            Log.w("ChatScreen", "ChatSession not available or not initialized. Cannot set history.")
            return@LaunchedEffect
        }
        Log.d("ChatScreen", "LaunchedEffect for setNewChatHistory triggered. Thread ID: $threadId")

        // Convert UiChatMessage list to List<String> for the JNI call
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
                val imageMessageExists = uiMessages.any { it.sender == SENDER_IMAGE && it.text == path }
                if (!imageMessageExists) {
                    // Handle image display logic here if needed
                }
                initialImageProcessed = true
            }
        }
    }

    val progressListener = remember {
        object : ChatProgressListener {
            private var currentModelMessageId: String? = null
            private var currentDbMessageId: Int? = null
            private val responseBuilder = StringBuilder()

            override fun onProgress(progress: String): Boolean {
                coroutineScope.launch(Dispatchers.Main) {
                    responseBuilder.append(progress)
                    val fullResponse = responseBuilder.toString()

                    if (currentDbMessageId == null) {
                        // First token, create new message
                        launch {
                            val newId = viewModel.addNewMessageAndGetId(fullResponse, SENDER_MODEL)
                            newId?.let {
                                currentDbMessageId = it.toInt()
                            }
                        }
                    } else {
                        // Subsequent tokens, update existing message
                        currentDbMessageId?.let {
                            viewModel.updateMessage(it, fullResponse)
                        }
                    }
                    
                    // Scroll to bottom
                    if (uiMessages.isNotEmpty()) {
                        listState.animateScrollToItem(uiMessages.size - 1)
                    }
                }
                return isGenerating
            }

            override fun finalizeMessage() {
                coroutineScope.launch(Dispatchers.Main) {
                    // Update the final state of the message in DB
                    viewModel.chatThread.value?.let { thread ->
                        val app = context.applicationContext as ChatApplication
                        app.repository.updateChatThread(thread.copy(updatedAt = Timestamp(System.currentTimeMillis())))
                    }

                    responseBuilder.clear()
                    currentModelMessageId = null
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
                
                // STT IconButton with dual handler
                TalkBackAwareIconButton(
                    onClick = {
                        if (isVoskInitialized && globalSttHelper != null) {
                            if (isRecording) {
                                globalSttHelper.stopRecording()
                                isRecording = false
                                Log.d("ChatScreen", "Stopped STT recording.")
                            } else {
                                globalSttHelper.startRecording()
                                isRecording = true
                                Log.d("ChatScreen", "Started STT recording.")
                            }
                        }
                    },
                    enabled = !isGenerating && isVoskInitialized && globalSttHelper != null,
                    isTalkBackEnabled = isTalkBackEnabled.value,
                    contentDescription = if (isRecording) "Stop Recording" else "Record Audio"
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Default.Mic,
                        contentDescription = null // Remove duplicate description
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Send Button with dual handler
                TalkBackAwareButton(
                    onClick = sendMessage,
                    enabled = !isGenerating && inputText.text.isNotBlank(),
                    isTalkBackEnabled = isTalkBackEnabled.value,
                    contentDescription = "Send Message"
                ) {
                    Icon(Icons.Default.Send, contentDescription = null) // Remove duplicate description
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
            // Page title and description
            item("chat_title") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .semantics {
                            contentDescription = "Chat View. Type messages to chat with the AI about your captured image. Use the microphone button to record voice input, or type directly in the text field."
                        }
                ) {
                    Text(
                        text = "Chat View",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .semantics {
                                contentDescription = "Chat View page title. This is the conversation interface for chatting with AI about your captured images."
                            }
                    )
                }
            }
            
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
                        // Handle retry logic for model messages
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
fun MessageBubble(message: ChatMessage, onRetry: () -> Unit) {
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

@Composable
private fun TalkBackAwareIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isTalkBackEnabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Create fresh interaction source based on TalkBack state to avoid conflicts
    val interactionSource = remember(isTalkBackEnabled) { MutableInteractionSource() }
    
    if (isTalkBackEnabled) {
        // TalkBack is enabled - use semantic touch handling
        Box(
            modifier = modifier
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    if (enabled) {
                        onClick(label = null, action = { onClick(); true })
                    }
                }
                .size(48.dp), // Standard IconButton size
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        // TalkBack is disabled - use normal IconButton
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.semantics {
                this.contentDescription = contentDescription
            }
        ) {
            content()
        }
    }
}

@Composable
private fun TalkBackAwareButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isTalkBackEnabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // Create fresh interaction source based on TalkBack state to avoid conflicts
    val interactionSource = remember(isTalkBackEnabled) { MutableInteractionSource() }
    
    if (isTalkBackEnabled) {
        // TalkBack is enabled - use semantic touch handling
        Button(
            onClick = { }, // Disable Button's onClick
            enabled = enabled,
            modifier = modifier
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    if (enabled) {
                        onClick(label = null, action = { onClick(); true })
                    }
                }
        ) {
            content()
        }
    } else {
        // TalkBack is disabled - use normal Button
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.semantics {
                this.contentDescription = contentDescription
            }
        ) {
            content()
        }
    }
} 