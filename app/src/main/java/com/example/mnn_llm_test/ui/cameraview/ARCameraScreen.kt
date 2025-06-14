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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import android.widget.FrameLayout
import android.view.ViewGroup
import com.example.mnn_llm_test.arcore.BoundingBoxOverlay
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
        // Page title and description
        if (hasCameraPermission && isCameraActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Camera View. Point your camera at objects to detect them. Use Live On/Off to toggle real-time announcements, Describe to get scene description, or Chat to capture and start conversation."
                    }
            ) {
                Text(
                    text = "Camera View",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        
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
                    // Camera is not active - show nothing during transitions
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
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp) // Match navigation bar height
            .border(
                width = 1.dp,
                color = borderColor
            ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toggle real-time announcements
            CameraControlSection(
                icon = if (isRealTimeAnnouncementEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                label = if (isRealTimeAnnouncementEnabled) "Live On" else "Live Off",
                isEnabled = true,
                isSelected = isRealTimeAnnouncementEnabled,
                onClick = { onToggleRealTimeAnnouncement(!isRealTimeAnnouncementEnabled) },
                accessibilityDescription = if (isRealTimeAnnouncementEnabled) {
                    "Real time announcements enabled"
                } else {
                    "Real time announcements disabled"
                },
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = borderColor
                    )
            )
            
            // Describe current scene
            CameraControlSection(
                icon = Icons.Default.RecordVoiceOver,
                label = "Describe",
                isEnabled = true,
                isSelected = false,
                onClick = onDescribeScene,
                accessibilityDescription = "Describe what the camera sees around you",
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = borderColor
                    )
            )
            
            // Capture and start chat
            CameraControlSection(
                icon = Icons.Default.Chat,
                label = "Chat",
                isEnabled = true,
                isSelected = false,
                onClick = onCaptureAndChat,
                accessibilityDescription = "Capture current image and start chat conversation",
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = borderColor
                    )
            )
        }
    }
}

@Composable
private fun CameraControlSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isEnabled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    accessibilityDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    
    // Detect TalkBack state
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
    
    // Create fresh interaction source based on TalkBack state to avoid conflicts
    val interactionSource = remember(isTalkBackEnabled.value) { MutableInteractionSource() }
    
    // Apply different touch handling strategies based on TalkBack state
    val columnModifier = if (isTalkBackEnabled.value) {
        // TalkBack is enabled - use semantic touch handling
        modifier
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = accessibilityDescription
                if (isEnabled) {
                    onClick(label = null, action = { onClick(); true })
                }
            }
    } else {
        // TalkBack is disabled - use normal clickable only
        modifier
            .fillMaxHeight()
            .semantics {
                contentDescription = accessibilityDescription
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                enabled = isEnabled,
                onClick = onClick
            )
    }
    
    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Remove duplicate description
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = iconColor
        )
    }
} 