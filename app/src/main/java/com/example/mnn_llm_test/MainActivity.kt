package com.example.mnn_llm_test

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.mnn_llm_test.navigation.AppNavigator
import com.example.mnn_llm_test.ui.theme.MnnllmtestTheme
import com.example.mnn_llm_test.utils.HuggingFaceDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MnnLlmJni // Initialize JNI if needed
        enableEdgeToEdge()

        setContent {
            var isModelLoading by remember { mutableStateOf(true) }
            var chatSession by remember { mutableStateOf<MnnLlmJni.ChatSession?>(null) }

            MnnllmtestTheme {
                AppNavigator(chatSessionState = chatSession, isModelLoading = isModelLoading)
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val repoName = "taobao-mnn/Qwen2-VL-2B-Instruct-MNN"
                    val commitSha = "38f5d45ae192dc56e92c609c6447b7e7232bda53"
                    val localFolderName = "Qwen2-VL-2B-Instruct-MNN-$commitSha"

                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "ignored/taobao-mnn/$localFolderName"
                    )

                    val downloader = HuggingFaceDownloader(applicationContext)
                    downloader.downloadModelFiles(
                        repo = repoName,
                        commitSha = commitSha,
                        outputDirName = "ignored",
                        onProgress = { downloaded, total, currentFile ->
                            Log.d("DownloadProgress", "Downloading $currentFile ($downloaded/$total)")
                        }
                    )

                    val modelFile = File(downloadsDir, "llm.mnn")
                    if (modelFile.exists()) {
                        Log.d("ModelLoading", "✅ Model file exists at: ${modelFile.absolutePath}")
                    } else {
                        Log.e("ModelLoading", "❌ Model file does not exist at: ${modelFile.absolutePath}")
                        isModelLoading = false
                        return@withContext
                    }

                    val modelConfigPath = File(downloadsDir, "config.json").absolutePath
                    Log.d("ModelLoading", "Model config file: $modelConfigPath")

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
                            isModelLoading = false
                        }
                        Log.d("ModelLoading", "✅ Model initialized successfully.")
                    } catch (e: Exception) {
                        Log.e("ModelLoading", "❌ Error initializing the model", e)
                        withContext(Dispatchers.Main) {
                            isModelLoading = false
                        }
                    }
                }
            }
        }
    }
}



