package com.example.mnn_llm_test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.mnn_llm_test.tts.TTSManager
import com.example.mnn_llm_test.ui.ChatUI
import com.example.mnn_llm_test.ui.WelcomeScreen
import com.example.mnn_llm_test.ui.theme.MnnllmtestTheme
import com.example.mnn_llm_test.utils.copyAssetFolderToInternalStorage
import com.example.mnn_llm_test.utils.printAssetFileSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MnnLlmJni // Initialize JNI if needed
        enableEdgeToEdge() // Optional full-screen support

        setContent {

            val context = LocalContext.current
            MnnllmtestTheme {
                var isLoading by remember { mutableStateOf(true) }
                var progressText by remember { mutableStateOf("Loading Model...") }
                var chatSession by remember { mutableStateOf<MnnLlmJni.ChatSession?>(null) }
                val coroutineScope = rememberCoroutineScope()
                val tts = remember { TTSManager(context) }
                val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()

                // Show a loading dialog while the model loads
                if (isLoading) {
                    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White) { innerPadding -> WelcomeScreen(context, modifier = Modifier.padding(innerPadding))  }
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize(), containerColor= Color.White) { innerPadding ->
                        ChatUI(
                            context,
                            modifier = Modifier.padding(innerPadding),
                            chatSession = chatSession,
                            coroutineScope = coroutineScope,
                            tts = tts,
                            isSpeaking = isSpeaking
                        )
                    }
                }

                // Load model in the background
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        val modelDir = filesDir.absolutePath + "/models/Qwen2-VL-2B-Instruct-MNN/"
                        val modelDirFile = File(modelDir)

                        // Ensure directory exists
                        if (!modelDirFile.exists()) modelDirFile.mkdirs()

                        Log.d("ModelLoading", "Copying model files from assets to internal storage...")
                        copyAssetFolderToInternalStorage(applicationContext, "models/Qwen2-VL-2B-Instruct-MNN", modelDir)
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
                        printAssetFileSizes(applicationContext,"models/Qwen2-VL-2B-Instruct-MNN")
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


                // Clean up TTS when the composable is disposed
                DisposableEffect(Unit) {
                    onDispose {
                        tts.shutdown()
                    }
                }
            }
        }
    }
}



