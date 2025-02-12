package com.example.mnn_llm_test

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import coil.compose.rememberAsyncImagePainter
import com.example.mnn_llm_test.MnnLlmJni.submitNative
import com.example.mnn_llm_test.ui.theme.MnnllmtestTheme
import kotlinx.coroutines.*

import java.io.File

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MnnLlmJni // Initialize JNI if needed
        enableEdgeToEdge() // Optional full-screen support

        setContent {
            MnnllmtestTheme {
                var isLoading by remember { mutableStateOf(true) }
                var progressText by remember { mutableStateOf("Loading Model...") }
                var chatSession by remember { mutableStateOf<MnnLlmJni.ChatSession?>(null) }
                val coroutineScope = rememberCoroutineScope()

                // Show a loading dialog while the model loads
                if (isLoading) {
                    Dialog(onDismissRequest = { }) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = progressText)
                            }
                        }
                    }
                }

                // Load model in the background
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        val modelDir = filesDir.absolutePath + "/models/Qwen2.5-0.5B-Instruct-MNN/"
                        val modelDirFile = File(modelDir)

                        // Ensure directory exists
                        if (!modelDirFile.exists()) modelDirFile.mkdirs()

                        Log.d("ModelLoading", "Copying model files from assets to internal storage...")
                        copyAssetFolderToInternalStorage(applicationContext, "models/Qwen2.5-0.5B-Instruct-MNN", modelDir)
//                        val copiedFiles = File(modelDir).listFiles()
//                        copiedFiles?.forEach { file ->
//                            Log.d("ModelCopy", "Copied file: ${file.name}, Path: ${file.absolutePath}")
//                        }
                        val modelFile = File(modelDir, "llm.mnn")
                        if (modelFile.exists()) {
                            Log.d("ModelLoading", "Model file exists at: ${modelFile.absolutePath}")
                        } else {
                            Log.e("ModelLoading", "Model file does not exist at: ${modelFile.absolutePath}")
                        }

                        val modelConfigPath = File(modelDir, "config.json").absolutePath
                        Log.d("ModelLoading", "Model config file: $modelConfigPath")
                        Log.d("PRINTING SIZES", "------------------------------")
                        printAssetFileSizes(applicationContext,"models/Qwen2.5-0.5B-Instruct-MNN")
                        Log.d("PRINTING SIZES", "------------------------------")
                        try {
                            Log.d("ModelLoading", "Initializing the model...")
                            val session = MnnLlmJni.ChatSession(
                                sessionId = "12345",
                                configPath = modelConfigPath,
                                useTmpPath = true,
                                savedHistory = null,
                                isDiffusion = false
                            )

                            withContext(Dispatchers.Main) {
                                chatSession = session
                                isLoading = false
                            }
                            Log.d("ModelLoading", "Model initialized successfully.")






                        } catch (e: Exception) {
                            Log.e("ModelLoading", "Error initializing the model", e)
                        }
                    }
                }

                // Main UI content after model loading
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatUI(
                        modifier = Modifier.padding(innerPadding),
                        chatSession = chatSession,
                        coroutineScope = coroutineScope
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ChatUI(
    modifier: Modifier = Modifier,
    chatSession: MnnLlmJni.ChatSession?,
    coroutineScope: CoroutineScope
) {
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isImageEnabled by remember { mutableStateOf(false) } // Toggle switch state
    val responseBuilder = remember { StringBuilder() }

    // Define the ProgressListener to handle streamed responses
    val progressListener = remember {
        object : MnnLlmJni.ProgressListener {
            override fun onProgress(progress: String): Boolean {
                coroutineScope.launch(Dispatchers.Main) {
                    responseBuilder.append(progress)
                    responseText = responseBuilder.toString()
                }
                return !isGenerating
            }
        }
    }

    val sendToModel = {
        chatSession?.let { session ->
            coroutineScope.launch {
                val formattedPrompt = if (isImageEnabled && imageUri != null) {
                    String.format("<img>%s</img>%s", imageUri.toString(), inputText)
                } else {
                    inputText
                }

                withContext(Dispatchers.Main) {
                    isGenerating = true
                    responseBuilder.clear()
                    responseText = ""
                }
                try {
                    Log.d("FINAL_PROMPT", formattedPrompt)
                    session.generate(formattedPrompt, progressListener)
                    inputText = ""
                } catch (e: Exception) {
                    Log.e("ChatUI", "Error generating response", e)
                    withContext(Dispatchers.Main) {
                        responseText = "Error: ${e.message}"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isGenerating = false
                    }
                }
            }
        } ?: Log.e("ChatSession", "Model session not initialized yet.")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle switch to enable/disable image in prompt
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Include Image", color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = isImageEnabled, onCheckedChange = { isImageEnabled = it })
        }

        // Input field for user text
        BasicTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray)
                .padding(16.dp),
            textStyle = TextStyle(color = Color.White)
        )

        // Image picker with callback to update imageUri
        ImagePicker(isImageEnabled) {  selectedUri ->
            imageUri = selectedUri
        }

        // Send button
        Button(
            onClick = { sendToModel() },
            enabled = !isGenerating
        ) {
            Text(text = if (isGenerating) "Generating..." else "Send")
        }

        // Response text
        Text(text = responseText, color = Color.White)
    }
}

@Composable
fun ImagePicker(isImageEnabled:Boolean ,onImagePicked: (Uri) -> Unit) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            onImagePicked(it) // Pass the selected image URI to the parent function
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(enabled = isImageEnabled,onClick = { launcher.launch("image/*") }) {
            Text(text = "Pick Image")

        }
    }
}
