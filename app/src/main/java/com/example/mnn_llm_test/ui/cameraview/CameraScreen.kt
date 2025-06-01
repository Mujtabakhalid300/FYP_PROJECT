package com.example.mnn_llm_test.ui.cameraview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.mnn_llm_test.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import com.example.mnntest.ChatApplication
import com.example.mnntest.data.ChatThread
import com.example.mnntest.data.ChatThreadImage
import java.sql.Timestamp

// Filename format for captured images
private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    val imageCaptureUseCase = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = (context.applicationContext as ChatApplication).repository

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera View") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Menu")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasCameraPermission) {
                Box(modifier = Modifier.weight(1f)) {
                    CameraPreview(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        imageCaptureUseCase = imageCaptureUseCase,
                        cameraExecutor = cameraExecutor
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    takePhoto(context, imageCaptureUseCase, cameraExecutor, coroutineScope) {filePath ->
                        if (filePath != null) {
                            Log.d("CameraScreen", "Photo captured: $filePath")
                            coroutineScope.launch(Dispatchers.IO) {
                                val newChatThread = ChatThread(
                                    title = "Chat Session - ${System.currentTimeMillis()}",
                                    systemPrompt = null,
                                    createdAt = Timestamp(System.currentTimeMillis()),
                                    updatedAt = Timestamp(System.currentTimeMillis())
                                )
                                val threadId = chatRepository.insertChatThread(newChatThread).toInt()

                                val newChatImage = ChatThreadImage(
                                    threadId = threadId,
                                    imagePath = filePath,
                                    createdAt = Timestamp(System.currentTimeMillis())
                                )
                                chatRepository.insertChatThreadImage(newChatImage)
                                
                                launch(Dispatchers.Main) {
                                    navController.navigate(Screen.ChatView.routeWithArgs(threadId = threadId))
                                }
                            }
                        } else {
                            Log.e("CameraScreen", "Photo capture failed or file path is null")
                        }
                    }
                }) {
                    Text("Capture & Go to Chat")
                }
            } else {
                Text("Camera permission is required. Please grant permission.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCaptureUseCase: ImageCapture,
    cameraExecutor: ExecutorService
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val lastLoggedTime = remember { AtomicLong(0) }
    val logIntervalMs = 100

    AndroidView({ previewView }, modifier = Modifier.fillMaxSize()) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLoggedTime.get() >= logIntervalMs) {
                            Log.d("CameraScreen", "Frame received at: $currentTime, Res: ${imageProxy.width}x${imageProxy.height}")
                            lastLoggedTime.set(currentTime)
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCaptureUseCase
                )
            } catch (exc: Exception) {
                Log.e("CameraScreen", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onPhotoTaken: (String?) -> Unit
) {
    val photoFile = File(
        context.getExternalFilesDir(null),
        SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                coroutineScope.launch(Dispatchers.Main) {
                    onPhotoTaken(null)
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d("CameraScreen", "Photo capture succeeded: $savedUri, AbsolutePath: ${photoFile.absolutePath}")
                coroutineScope.launch(Dispatchers.Main) {
                    onPhotoTaken(photoFile.absolutePath)
                }
            }
        }
    )
} 