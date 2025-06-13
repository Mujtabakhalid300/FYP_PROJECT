package com.example.mnn_llm_test

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import android.view.WindowManager
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
import com.example.mnn_llm_test.utils.TtsHelper
import com.example.mnn_llm_test.arcore.ml.LiteRTYoloDetector
import com.example.mnn_llm_test.VoskHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File


class MainActivity : ComponentActivity() {
    
    companion object {
        // 🌍 Global YOLO detector - initialized once, used throughout app lifecycle
        @Volatile
        private var _globalYoloDetector: LiteRTYoloDetector? = null
        
        val globalYoloDetector: LiteRTYoloDetector?
            get() = _globalYoloDetector
            
        internal fun setGlobalYoloDetector(detector: LiteRTYoloDetector) {
            _globalYoloDetector = detector
        }
        
        // 🎤 Global TTS helper - initialized once, used throughout app lifecycle
        @Volatile
        private var _globalTtsHelper: TtsHelper? = null
        
        val globalTtsHelper: TtsHelper?
            get() = _globalTtsHelper
            
        internal fun setGlobalTtsHelper(ttsHelper: TtsHelper) {
            _globalTtsHelper = ttsHelper
        }
        
        // 🎙️ Global STT (VoskHelper) - initialized once, used throughout app lifecycle
        @Volatile
        private var _globalSttHelper: VoskHelper? = null
        
        val globalSttHelper: VoskHelper?
            get() = _globalSttHelper
            
        internal fun setGlobalSttHelper(sttHelper: VoskHelper) {
            _globalSttHelper = sttHelper
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MnnLlmJni // Initialize JNI if needed
        enableEdgeToEdge()
        
        // 🎨 Set system bars to use app theme colors
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            var isModelLoading by remember { mutableStateOf(true) }
            var chatSession by remember { mutableStateOf<MnnLlmJni.ChatSession?>(null) }
            var isYoloReady by remember { mutableStateOf(false) }
            var isTtsReady by remember { mutableStateOf(false) }
            var isSttReady by remember { mutableStateOf(false) }

            MnnllmtestTheme {
                AppNavigator(chatSessionState = chatSession, isModelLoading = isModelLoading)
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    // 🚀 Initialize YOLO detector and TTS (parallel with VLM setup)
                    val yoloInitJob = launch {
                        try {
                            Log.d("YoloLoading", "🎯 Initializing global YOLO detector...")
                            val yoloDetector = LiteRTYoloDetector(this@MainActivity)
                            val success = yoloDetector.initialize()
                            
                            if (success) {
                                setGlobalYoloDetector(yoloDetector)
                                isYoloReady = true
                                Log.d("YoloLoading", "✅ Global YOLO detector ready!")
                            } else {
                                Log.e("YoloLoading", "❌ Failed to initialize global YOLO detector")
                            }
                        } catch (e: Exception) {
                            Log.e("YoloLoading", "❌ Error initializing global YOLO detector", e)
                        }
                    }
                    
                    val ttsInitJob = launch {
                        try {
                            Log.d("TtsLoading", "🎤 Initializing global TTS helper...")
                            val ttsHelper = TtsHelper(this@MainActivity)
                            
                            // TTS initialization is async, but we can set it immediately
                            // The actual TTS engine will initialize in the background
                            setGlobalTtsHelper(ttsHelper)
                            isTtsReady = true
                            Log.d("TtsLoading", "✅ Global TTS helper ready!")
                        } catch (e: Exception) {
                            Log.e("TtsLoading", "❌ Error initializing global TTS helper", e)
                        }
                    }
                    
                    val sttInitJob = launch {
                        try {
                            Log.d("SttLoading", "🎙️ Initializing global STT helper...")
                            val sttHelper = VoskHelper(
                                context = this@MainActivity,
                                onFinalTranscription = null, // Will be set by individual screens
                                onError = { error ->
                                    Log.e("SttLoading", "Global STT error: $error")
                                }
                            )
                            
                            // Initialize the Vosk model
                            sttHelper.initModel()
                            
                            // Wait a bit for model to be fully loaded before marking as ready
                            kotlinx.coroutines.delay(2000)
                            
                            setGlobalSttHelper(sttHelper)
                            
                            // Check if model is actually ready
                            if (sttHelper.isModelReady()) {
                                isSttReady = true
                                Log.d("SttLoading", "✅ Global STT helper ready!")
                            } else {
                                Log.w("SttLoading", "⚠️ STT helper created but model not ready yet")
                                // Still set as ready since model initialization is async
                                isSttReady = true
                            }
                        } catch (e: Exception) {
                            Log.e("SttLoading", "❌ Error initializing global STT helper", e)
                            isSttReady = false // Ensure we don't proceed without STT
                        }
                    }
                    
                    // 🧠 Initialize VLM (existing code)
                    val repoName = "taobao-mnn/Qwen2-VL-2B-Instruct-MNN"
                    val commitSha = "38f5d45ae192dc56e92c609c6447b7e7232bda53"
                    val localFolderName = "Qwen2-VL-2B-Instruct-MNN-$commitSha"

                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "ignored/taobao-mnn/$localFolderName"
                    )

                    // 🔧 Smart model loading - offline-first approach
                    try {
                        val downloader = HuggingFaceDownloader(applicationContext)
                        downloader.downloadModelFiles(
                            repo = repoName,
                            commitSha = commitSha,
                            outputDirName = "ignored",
                            onProgress = { downloaded, total, currentFile ->
                                Log.d("DownloadProgress", "Downloading $currentFile ($downloaded/$total)")
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("ModelLoading", "❌ Download error: ${e.message}")
                        // Continue to check if model files exist anyway (might be a network issue but files are there)
                    }

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

                        // Wait for YOLO, TTS, and STT to be ready before completing initialization
                        yoloInitJob.join()
                        ttsInitJob.join()
                        sttInitJob.join()
                        
                        withContext(Dispatchers.Main) {
                            chatSession = session
                            isModelLoading = false
                        }
                        Log.d("ModelLoading", "✅ Model initialized successfully.")
                        Log.d("AppInit", "🎉 App fully initialized - VLM: ✅ YOLO: ${if (isYoloReady) "✅" else "❌"} TTS: ${if (isTtsReady) "✅" else "❌"} STT: ${if (isSttReady) "✅" else "❌"}")
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
    
    override fun onPause() {
        super.onPause()
        // 🔇 Stop TTS immediately when app goes to background
        globalTtsHelper?.forceStop()
        Log.d("MainActivity", "🔇 TTS stopped - app paused/backgrounded")
    }
    
    override fun onStop() {
        super.onStop()
        // 🔇 Also stop TTS when app is no longer visible (additional safety)
        globalTtsHelper?.forceStop()
        Log.d("MainActivity", "🔇 TTS stopped - app stopped/hidden")
    }
    
    override fun onBackPressed() {
        // 🔇 Stop TTS on back button press
        globalTtsHelper?.forceStop()
        Log.d("MainActivity", "🔇 TTS stopped - back button pressed")
        super.onBackPressed()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup global resources
        globalTtsHelper?.shutdown()
        globalYoloDetector?.close()
        globalSttHelper?.stopRecording()
        Log.d("MainActivity", "🧹 Global resources cleaned up")
    }
}



