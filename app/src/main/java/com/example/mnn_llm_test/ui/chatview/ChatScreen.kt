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
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.mnn_llm_test.MnnLlmJni
import com.example.mnn_llm_test.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

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
    imagePath: String?
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var imageAlreadySentInConversation by remember { mutableStateOf(false) }
    var initialImageProcessed by remember { mutableStateOf(false) }

    LaunchedEffect(imagePath) {
        imageAlreadySentInConversation = false
        initialImageProcessed = false // Reset for new image
        chatMessages.removeAll { it.sender == SENDER_IMAGE } // Remove old image display message
        if (imagePath != null) {
            // Add a placeholder message for the image to be displayed in the LazyColumn
            chatMessages.add(0, ChatMessage(
                id = UUID.randomUUID().toString(),
                text = imagePath, // Store imagePath here
                sender = SENDER_IMAGE,
                timestamp = System.currentTimeMillis() - 1 // Ensure it's slightly older for sorting if needed
            ))
            initialImageProcessed = true
        }
    }

    val progressListener = remember {
        object : ChatProgressListener {
            private var currentModelMessageId: String? = null
            private val responseBuilder = StringBuilder()

            override fun onProgress(progress: String): Boolean {
                coroutineScope.launch(Dispatchers.Main) {
                    responseBuilder.append(progress)
                    val fullResponse = responseBuilder.toString()

                    if (currentModelMessageId == null) {
                        val newMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = fullResponse,
                            sender = SENDER_MODEL,
                            timestamp = System.currentTimeMillis()
                        )
                        chatMessages.add(newMessage)
                        currentModelMessageId = newMessage.id
                    } else {
                        val existingMessageIndex = chatMessages.indexOfFirst { it.id == currentModelMessageId }
                        if (existingMessageIndex != -1) {
                            chatMessages[existingMessageIndex] = chatMessages[existingMessageIndex].copy(text = fullResponse)
                        } else {
                            val newMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = fullResponse,
                                sender = SENDER_MODEL,
                                timestamp = System.currentTimeMillis()
                            )
                            chatMessages.add(newMessage)
                            currentModelMessageId = newMessage.id
                        }
                    }
                    coroutineScope.launch {
                        if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.size - 1)
                    }
                }
                return !isGenerating
            }

            override fun finalizeMessage() {
                currentModelMessageId = null
                responseBuilder.clear()
            }
        }
    }

    val sendMessage = {
        if (inputText.text.isNotBlank() && chatSession != null && !isGenerating) {
            val userMessageText = inputText.text
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = userMessageText,
                sender = SENDER_USER,
                timestamp = System.currentTimeMillis()
            )
            chatMessages.add(userMessage)
            inputText = TextFieldValue("")

            coroutineScope.launch {
                if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.size - 1)
            }

            isGenerating = true
            progressListener.finalizeMessage()

            // Use imagePath directly from Composable state, not from a potentially stale message item
            val currentImagePath = imagePath 

            val formattedPrompt = if (currentImagePath != null && !imageAlreadySentInConversation) {
                imageAlreadySentInConversation = true
                "<img>${currentImagePath}</img>$userMessageText"
            } else {
                userMessageText
            }

            Log.d("ChatScreen", "Sending to LLM: Prompt: '$formattedPrompt'")

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    chatSession.generate(formattedPrompt, progressListener)
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error during VLM generation", e)
                    coroutineScope.launch(Dispatchers.Main) {
                        chatMessages.add(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                text = "Error: ${e.message}",
                                sender = SENDER_MODEL,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } finally {
                    coroutineScope.launch(Dispatchers.Main) {
                        isGenerating = false
                        progressListener.finalizeMessage()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (imagePath != null) "Chat with Image" else "Chat") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            // Image display is now part of the LazyColumn
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp) // Apply padding for system bars here if needed, or rely on Scaffold's contentPadding
                    .navigationBarsPadding(), // Add padding for the navigation bar
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
    ) { contentPadding -> // contentPadding from Scaffold accounts for TopAppBar and BottomAppBar
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding) // Apply Scaffold's padding
                // .navigationBarsPadding() // Apply here if bottomBar doesn't use it and items go behind nav bar
                .padding(horizontal = 8.dp), // Additional horizontal padding for messages
            reverseLayout = false
        ) {
            // The image is now added directly to chatMessages list with SENDER_IMAGE type
            items(chatMessages) { message ->
                if (message.sender == SENDER_IMAGE) {
                    ImageBubble(imagePath = message.text) // Pass imagePath from message text
                } else {
                    MessageBubble(message)
                }
            }
        }
    }
}

@Composable
fun ImageBubble(imagePath: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp) // Consistent padding
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), // Neutral background
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current).data(data = File(imagePath)).apply(block = fun ImageRequest.Builder.() {
                    crossfade(true)
                    // placeholder(R.drawable.ic_launcher_foreground) // Replace with actual placeholder
                    // error(R.drawable.ic_launcher_background) // Replace with actual error drawable
                }).build()
            ),
            contentDescription = "Captured Image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp) // Max height for the image bubble
                .clip(RoundedCornerShape(12.dp)), // Clip image itself too
            contentScale = ContentScale.Fit // Fit the image within bounds
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
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