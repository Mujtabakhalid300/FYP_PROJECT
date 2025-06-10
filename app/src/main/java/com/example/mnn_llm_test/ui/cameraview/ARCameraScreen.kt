package com.example.mnn_llm_test.ui.cameraview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import android.view.ViewGroup
import com.example.mnn_llm_test.arcore.BoundingBoxOverlay
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import com.example.mnn_llm_test.navigation.Screen
import com.example.mnntest.ChatApplication
import com.example.mnntest.data.ChatThread
import com.example.mnntest.data.ChatThreadImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

// ARCore and rendering imports
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.example.mnn_llm_test.arcore.common.helpers.*
import com.example.mnn_llm_test.arcore.common.samplerender.SampleRender
import com.example.mnn_llm_test.arcore.ARCameraRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARCameraScreen(
    navController: NavHostController
) {
    // Wrap everything in lifecycle-aware camera management
    LifecycleAwareCameraView(navController = navController) { isActiveCamera ->
        ARCameraContent(
            navController = navController,
            isActiveCamera = isActiveCamera
        )
    }
}

@Composable
private fun ARCameraContent(
    navController: NavHostController,
    isActiveCamera: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = (context.applicationContext as ChatApplication).repository
    
    // ARCore session lifecycle helper - only create when camera is active
    var arCoreSessionHelper by remember { mutableStateOf<ARCoreSessionLifecycleHelper?>(null) }
    var renderer by remember { mutableStateOf<ARCameraRenderer?>(null) }

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

    // Handle ARCore lifecycle based on camera active state
    LaunchedEffect(isActiveCamera) {
        if (isActiveCamera) {
            Log.d("ARCameraScreen", "🟢 Camera activated - initializing ARCore")
        } else {
            Log.d("ARCameraScreen", "🔴 Camera deactivated - cleaning up ARCore")
            // Cleanup happens automatically through lifecycle observers
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (hasCameraPermission) {
            if (isActiveCamera) {
                Box(modifier = Modifier.weight(1f)) {
                    ARCameraView(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        onCapture = { bitmap ->
                            if (bitmap != null) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    // Save bitmap to file
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    val filename = "ARCapture_$timestamp.jpg"
                                    val imageFile = File(context.filesDir, filename)
                                    
                                    try {
                                        FileOutputStream(imageFile).use { outputStream ->
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                                        }
                                        
                                        val filePath = imageFile.absolutePath
                                        Log.d("ARCameraScreen", "Photo captured: $filePath")
                                        
                                        // Create chat thread and associate image
                                        val newChatThread = ChatThread(
                                            title = "AR Chat Session - ${System.currentTimeMillis()}",
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
                                    } catch (e: Exception) {
                                        Log.e("ARCameraScreen", "Error saving image", e)
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            } else {
                                Log.e("ARCameraScreen", "Failed to capture image")
                            }
                        },
                        onArCoreSessionCreated = { helper, rendererInstance ->
                            arCoreSessionHelper = helper
                            renderer = rendererInstance
                        }
                    )
                }
                // Instructions overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = "Tap the screen to capture with AR depth & object detection",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Camera is not active - show placeholder
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Camera paused",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
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

@Composable
fun ARCameraView(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onCapture: (Bitmap?) -> Unit,
    onArCoreSessionCreated: (ARCoreSessionLifecycleHelper, ARCameraRenderer) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            // Create a FrameLayout to hold both GLSurfaceView and BoundingBoxOverlay
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Create the BoundingBoxOverlay
                val boundingBoxOverlay = BoundingBoxOverlay(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                
                // Create the GLSurfaceView
                val glSurfaceView = GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                
                // Initialize ARCore components with overlay callback
                val arCoreSessionHelper = ARCoreSessionLifecycleHelper(context as androidx.activity.ComponentActivity)
                val renderer = ARCameraRenderer(
                    context as androidx.activity.ComponentActivity, 
                    onCapture
                ) { detections ->
                    // Update overlay on main thread
                    boundingBoxOverlay.post {
                        boundingBoxOverlay.updateDetections(detections)
                    }
                }
                
                // Connect renderer to session helper
                renderer.arCoreSessionHelper = arCoreSessionHelper
                
                // Setup ARCore session configuration
                arCoreSessionHelper.exceptionCallback = { exception ->
                    val message = when (exception) {
                        is UnavailableUserDeclinedInstallationException ->
                            "Please install Google Play Services for AR"
                        is UnavailableApkTooOldException -> "Please update ARCore"
                        is UnavailableSdkTooOldException -> "Please update this app"
                        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
                        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
                        else -> "Failed to create AR session: $exception"
                    }
                    Log.e("ARCameraView", "ARCore threw an exception", exception)
                }
                
                // Configure session features including depth estimation and autofocus
                arCoreSessionHelper.beforeSessionResume = { session ->
                    session.configure(
                        session.config.apply {
                            depthMode = Config.DepthMode.AUTOMATIC
                            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                            focusMode = Config.FocusMode.AUTO
                        }
                    )
                }
                
                lifecycleOwner.lifecycle.addObserver(arCoreSessionHelper)
                lifecycleOwner.lifecycle.addObserver(renderer)
                
                // Setup SampleRender and initialize renderer
                val sampleRender = SampleRender(glSurfaceView, renderer, context.assets)
                
                glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glSurfaceView.preserveEGLContextOnPause = true
                
                // Setup touch listener for capture on the overlay (so it doesn't interfere with GLSurfaceView)
                boundingBoxOverlay.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        renderer.requestCapture()
                        true
                    } else {
                        false
                    }
                }
                
                // Add both views to the FrameLayout
                addView(glSurfaceView)
                addView(boundingBoxOverlay)
                
                // Notify parent about ARCore session creation
                onArCoreSessionCreated(arCoreSessionHelper, renderer)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
} 