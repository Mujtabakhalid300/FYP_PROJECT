package com.example.mnn_llm_test.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import android.Manifest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.unit.sp
import com.example.mnn_llm_test.MnnLlmJni
import com.example.mnn_llm_test.tts.TTSManager
import com.example.mnn_llm_test.VoskHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(UnstableApi::class)
@Composable
fun ChatUI(
    context: Context,
    modifier: Modifier = Modifier,
    chatSession: MnnLlmJni.ChatSession?,
    coroutineScope: CoroutineScope,
    tts: TTSManager,
    isSpeaking: Boolean
) {
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<String>("") }
    var isImageEnabled by remember { mutableStateOf(true) }
    var isRecording by remember { mutableStateOf(false) }
    val responseBuilder = remember { StringBuilder() }





    // Define the ProgressListener
    val progressListener = remember {
        object : MnnLlmJni.ProgressListener {
            override fun onProgress(progress: String): Boolean {
                coroutineScope.launch(Dispatchers.Main) {
                    responseBuilder.append(progress)
                    responseText = responseBuilder.toString()
                }
                return !isGenerating // Continue streaming
            }
        }
    }

    // Vosk Helper
    val voskHelper = remember {
        VoskHelper(
            context = context,
            onFinalTranscription = { result ->
                inputText += result // Append transcribed text to inputText
            },
            onError = { error ->
                responseText = error
            }
        )
    }

    // Request audio permission
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voskHelper.initModel()
        } else {
            responseText = "Audio permission denied"
        }
    }

    // Check and request permission on launch
    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            voskHelper.initModel()
        }
    }

    // Send to model function
    val sendToModel = {
        chatSession?.let { session ->
            coroutineScope.launch {
                val formattedPrompt = if (isImageEnabled && imageUri.isNotEmpty()) {
                    String.format("<img>%s</img>%s", imageUri, inputText)
                } else {
                    inputText
                }

                withContext(Dispatchers.Main) {
                    isGenerating = true
                    responseBuilder.clear()
                    responseText = ""
                    tts.stop() // Stop any ongoing speech
                }
                try {
                    session.generate(formattedPrompt, progressListener) // Use the progressListener here
                    inputText = ""
                    withContext(Dispatchers.Main) {
                        tts.speak(responseText)
                    }
                } catch (e: Exception) {
                    responseText = "Error: ${e.message}"
                } finally {
                    isGenerating = false
                }
            }
        } ?: Log.e("ChatSession", "Model session not initialized yet.")
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp).verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle switch for image
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Include Image", color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = isImageEnabled, onCheckedChange = { isImageEnabled = it })
        }

        // Input field
        BasicTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray)
                .padding(16.dp),
            textStyle = TextStyle(color = Color.Black)
        )

        // Image picker
        ImagePicker(context, isImageEnabled) { selectedUri ->

            imageUri = selectedUri
            // Here you have access to the image's Uri
            // You can do whatever you want with this URI, like load the image, save it, etc.
            Log.d("ChatUI", "Image URI: $selectedUri")
            // Example: Use this URI to load the image or store it somewhere
        }

        // Record button
        Button(
            onClick = {
                if (isRecording) {
                    voskHelper.stopRecording()
                } else {
                    voskHelper.startRecording()
                }
                isRecording = !isRecording
            },
            enabled = !isGenerating,
            shape = CircleShape, // Makes it circular
            modifier = Modifier.size(200.dp) // Adjust size as needed
            , colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Microphone",
                tint = Color.White // Adjust color if needed
            )
        }

        // Send button
        Button(
            onClick = { sendToModel() },
            enabled = !isGenerating
        ) {
            Text(text = if (isGenerating) "Generating..." else "Send")
        }

        Text(text = "TTS", fontSize = 30.sp,color = Color.White)
        // TTS control button
        Button(
            onClick = {
                if (isSpeaking) {
                    tts.stop()
                } else {
                    tts.speak(responseText)
                }
            },
            enabled = responseText.isNotEmpty()
        ) {
            Text(text = if (isSpeaking) "Stop TTS" else "Play TTS")
        }

        // Response text
        Text(text = responseText, color = Color.White)
    }
}