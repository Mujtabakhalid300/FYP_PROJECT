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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import android.widget.FrameLayout
import android.view.ViewGroup
import com.example.mnn_llm_test.arcore.BoundingBoxOverlay
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.mnn_llm_test.navigation.Screen
import com.example.mnntest.ChatApplication
import com.example.mnn_llm_test.MainActivity
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
    // 🎯 Simple navigation detection - no complex binary manager needed
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isCameraActive = currentRoute == Screen.CameraView.route
    
    ARCameraContent(
        navController = navController,
        isCameraActive = isCameraActive
    )
}

@Composable
private fun ARCameraContent(
    navController: NavHostController,
    isCameraActive: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // ✅ Initialize with actual permission status to prevent popup flash
    var hasCameraPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = (context.applicationContext as ChatApplication).repository
    
    // ARCore session lifecycle helper - only create when camera is active
    var arCoreSessionHelper by remember { mutableStateOf<ARCoreSessionLifecycleHelper?>(null) }
    var renderer by remember { mutableStateOf<ARCameraRenderer?>(null) }
    
    // State for camera control buttons
    var isRealTimeAnnouncementEnabled by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    // Only request permission if we don't already have it
    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 🎯 Simple camera state management based on navigation
    LaunchedEffect(isCameraActive, renderer) {
        renderer?.let { 
            Log.d("ARCameraScreen", "📡 Setting camera active state to: $isCameraActive")
            it.setCameraActive(isCameraActive) 
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera view section
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                if (isCameraActive) {
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
                                        val currentTime = System.currentTimeMillis()
                                        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                        val formattedDate = dateFormat.format(Date(currentTime))
                                        
                                        val newChatThread = ChatThread(
                                            title = formattedDate,
                                            systemPrompt = null,
                                            createdAt = Timestamp(currentTime),
                                            updatedAt = Timestamp(currentTime)
                                        )
                                        val threadId = chatRepository.insertChatThread(newChatThread).toInt()

                                        val newChatImage = ChatThreadImage(
                                            threadId = threadId,
                                            imagePath = filePath,
                                            createdAt = Timestamp(System.currentTimeMillis())
                                        )
                                        chatRepository.insertChatThreadImage(newChatImage)
                                        
                                        launch(Dispatchers.Main) {
                                            // 🔇 Stop TTS before navigation
                                            MainActivity.globalTtsHelper?.forceStop()
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
                            
                            // Set the initial camera state based on current isCameraActive value
                            Log.d("ARCameraScreen", "🎯 Renderer created, setting initial camera active state: $isCameraActive")
                            rendererInstance.setCameraActive(isCameraActive)
                            
                            // Initialize the toggle state with the renderer
                            rendererInstance.setRealTimeAnnouncementEnabled(isRealTimeAnnouncementEnabled)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Camera is not active - show placeholder
                    Text(
                        text = "Camera paused",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera permission is required. Please grant permission.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        }
        
        // Camera control buttons
        if (hasCameraPermission && isCameraActive) {
            CameraControlButtons(
                isRealTimeAnnouncementEnabled = isRealTimeAnnouncementEnabled,
                onToggleRealTimeAnnouncement = { enabled ->
                    isRealTimeAnnouncementEnabled = enabled
                    renderer?.setRealTimeAnnouncementEnabled(enabled)
                },
                onDescribeScene = {
                    renderer?.describeCurrentScene()
                },
                onCaptureAndChat = {
                    renderer?.requestCapture()
                }
            )
        }
    }
}

@Composable
fun ARCameraView(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onCapture: (Bitmap?) -> Unit,
    onArCoreSessionCreated: (ARCoreSessionLifecycleHelper, ARCameraRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Camera preview
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
                
                // Initialize ARCore components without overlay callback
                val arCoreSessionHelper = ARCoreSessionLifecycleHelper(context as androidx.activity.ComponentActivity)
                
                // Create renderer with bounding box overlay callback
                val renderer = ARCameraRenderer(
                    context as androidx.activity.ComponentActivity, 
                    onCapture,
                    onDetectionUpdate = { detections ->
                        // Update overlay on main thread
                        boundingBoxOverlay.post {
                            boundingBoxOverlay.updateDetections(detections)
                        }
                    }
                )
                
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
                
                // Give renderer access to GLSurfaceView for render mode control
                renderer.setGLSurfaceView(glSurfaceView)
                
                // Touch handling removed - camera interactions now handled by buttons
                
                // Add both views to the FrameLayout
                addView(glSurfaceView)
                addView(boundingBoxOverlay)
                
                // Notify parent about ARCore session creation
                onArCoreSessionCreated(arCoreSessionHelper, renderer)
            }
        },
        modifier = Modifier.fillMaxSize()
        )
        
        // ✨ Overlay text positioned directly on top of camera preview
        var overlayText by remember { mutableStateOf("Use buttons below camera to interact") }
        var overlayVisible by remember { mutableStateOf(true) }
        var overlayAlpha by remember { mutableStateOf(0f) }
        
        // Initial fade in, then fade out after delay
        LaunchedEffect(Unit) {
            // Fade in
            androidx.compose.animation.core.animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(500)
            ) { value, _ ->
                overlayAlpha = value
            }
            
            // Stay visible for 5 seconds (longer since text is more detailed)
            kotlinx.coroutines.delay(5000)
            
            // Fade out
            androidx.compose.animation.core.animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = tween(500)
            ) { value, _ ->
                overlayAlpha = value
            }
            
            overlayVisible = false
        }
        
        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = overlayText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = overlayAlpha),
                    modifier = Modifier
                        .drawBehind {
                            // Semi-transparent background like HelloAR
                            drawRoundRect(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f * overlayAlpha),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                            )
                        }
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun CameraControlButtons(
    isRealTimeAnnouncementEnabled: Boolean,
    onToggleRealTimeAnnouncement: (Boolean) -> Unit,
    onDescribeScene: () -> Unit,
    onCaptureAndChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle real-time announcements
            Button(
                onClick = { onToggleRealTimeAnnouncement(!isRealTimeAnnouncementEnabled) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .semantics {
                        contentDescription = if (isRealTimeAnnouncementEnabled) {
                            "Real time announcements enabled, tap to disable"
                        } else {
                            "Real time announcements disabled, tap to enable"
                        }
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRealTimeAnnouncementEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Icon(
                    imageVector = if (isRealTimeAnnouncementEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isRealTimeAnnouncementEnabled) "Live On" else "Live Off",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            // Describe current scene
            Button(
                onClick = onDescribeScene,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .semantics {
                        contentDescription = "Describe what the camera sees around you"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Describe",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            // Capture and start chat
            Button(
                onClick = onCaptureAndChat,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .semantics {
                        contentDescription = "Capture current image and start chat conversation"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
} 