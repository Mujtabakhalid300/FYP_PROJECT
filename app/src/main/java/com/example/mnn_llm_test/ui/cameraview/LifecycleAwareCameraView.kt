package com.example.mnn_llm_test.ui.cameraview

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import android.util.Log
import com.example.mnn_llm_test.navigation.Screen

/**
 * Lifecycle-aware camera management that ensures proper resource cleanup
 * when navigating away from camera view.
 */
@Composable
fun LifecycleAwareCameraView(
    navController: NavHostController,
    content: @Composable (isActiveCamera: Boolean, onCameraActiveChanged: (Boolean) -> Unit) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Track if camera should be active based on current route
    val isCameraRoute = currentRoute == Screen.CameraView.route
    var isCameraActive by remember { mutableStateOf(isCameraRoute) }
    
    // Callback to immediately notify renderer of state changes
    val onCameraActiveChanged = remember {
        { active: Boolean ->
            if (isCameraActive != active) {
                Log.d("LifecycleAwareCameraView", if (active) "🟢 Camera activated" else "🔴 Camera deactivated")
                isCameraActive = active
            }
        }
    }
    
    // Lifecycle observer to handle pause/resume based on navigation
    DisposableEffect(currentRoute) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isCameraRoute) {
                        Log.d("LifecycleAwareCameraView", "🟢 Camera route active - enabling camera")
                        onCameraActiveChanged(true)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("LifecycleAwareCameraView", "⏸️ Lifecycle pause - checking camera state")
                    if (!isCameraRoute) {
                        Log.d("LifecycleAwareCameraView", "🔴 Camera route inactive - disabling camera")
                        onCameraActiveChanged(false)
                    }
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Immediate route change handling - this is the key for instant response
    LaunchedEffect(currentRoute) {
        when {
            isCameraRoute -> {
                Log.d("LifecycleAwareCameraView", "📷 Navigated to camera - activating immediately")
                onCameraActiveChanged(true)
            }
            else -> {
                Log.d("LifecycleAwareCameraView", "🚫 Navigated away from camera - deactivating immediately")
                onCameraActiveChanged(false)
            }
        }
    }
    
    // Provide the camera active state and callback to content
    content(isCameraActive, onCameraActiveChanged)
} 