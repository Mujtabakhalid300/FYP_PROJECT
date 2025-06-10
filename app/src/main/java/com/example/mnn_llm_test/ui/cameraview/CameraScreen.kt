package com.example.mnn_llm_test.ui.cameraview

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/**
 * Main Camera Screen - now using ARCore with depth estimation and object detection
 * This replaces the original CameraX implementation with ARCore functionality
 */
@Composable
fun CameraScreen(
    navController: NavHostController,
    currentScreen: String,
    hasChatHistory: Boolean,
    onNavigateToChat: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    // Delegate to the new ARCore-based camera screen
    ARCameraScreen(
        navController = navController,
        currentScreen = currentScreen,
        hasChatHistory = hasChatHistory,
        onNavigateToChat = onNavigateToChat,
        onNavigateToHistory = onNavigateToHistory
    )
} 