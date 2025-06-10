package com.example.mnn_llm_test.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mnn_llm_test.MnnLlmJni
import com.example.mnn_llm_test.ui.cameraview.CameraScreen
import com.example.mnn_llm_test.ui.chatview.ChatScreen
import com.example.mnn_llm_test.ui.chathistory.ChatHistoryScreen
import com.example.mnn_llm_test.ui.WelcomeScreen
import com.example.mnntest.ChatApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.Log
import com.example.mnn_llm_test.utils.HuggingFaceDownloader


@Composable
fun AppNavigator(
    navController: NavHostController = rememberNavController(),
    chatSessionState: MnnLlmJni.ChatSession?, // Pass the loaded chat session
    isModelLoading: Boolean // Pass the loading state
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as ChatApplication).repository
    
    // Track if there are any chat threads for enabling/disabling buttons
    val allChatThreads by repository.getAllChatThreads().collectAsState(initial = emptyList())
    val hasChatHistory = allChatThreads.isNotEmpty()
    
    // Get current route for bottom navigation highlighting
    val currentRoute = navController.currentDestination?.route ?: Screen.CameraView.route
    
    if (isModelLoading) {
         Scaffold(
             modifier = Modifier.fillMaxSize().systemBarsPadding(),
             containerColor = Color.White
         ) { innerPadding ->
            WelcomeScreen(
                LocalContext.current,
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            )
        }
    } else {
        NavHost(navController = navController, startDestination = Screen.CameraView.route) {
            composable(Screen.CameraView.route) {
                CameraScreen(
                    navController = navController,
                    currentScreen = "camera_view",
                    hasChatHistory = hasChatHistory,
                    onNavigateToChat = {
                        if (hasChatHistory) {
                            // Navigate to the most recent chat
                            val latestThread = allChatThreads.firstOrNull()
                            latestThread?.let {
                                navController.navigate(Screen.ChatView.routeWithArgs(threadId = it.id))
                            }
                        }
                    },
                    onNavigateToHistory = {
                        if (hasChatHistory) {
                            navController.navigate(Screen.ChatHistory.route)
                        }
                    }
                )
            }
            
            composable(
                route = Screen.ChatView.chatViewRouteDefinition,
                arguments = listOf(navArgument(Screen.ChatView.threadIdArg) {
                    type = NavType.IntType
                })
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getInt(Screen.ChatView.threadIdArg)
                if (threadId != null) {
                    ChatScreen(
                        navController = navController,
                        chatSession = chatSessionState,
                        threadId = threadId,
                        currentScreen = "chat_view",
                        hasChatHistory = hasChatHistory,
                        onNavigateToCamera = {
                            navController.navigate(Screen.CameraView.route) {
                                popUpTo(Screen.CameraView.route) { inclusive = true }
                            }
                        },
                        onNavigateToHistory = {
                            if (hasChatHistory) {
                                navController.navigate(Screen.ChatHistory.route)
                            }
                        }
                    )
                } else {
                    Log.e("AppNavigator", "threadId is null for ChatScreen, navigating back.")
                    navController.popBackStack()
                }
            }
            
            composable(Screen.ChatHistory.route) {
                ChatHistoryScreen(
                    navController = navController,
                    currentScreen = "chat_history",
                    hasChatHistory = hasChatHistory,
                    onNavigateToCamera = {
                        navController.navigate(Screen.CameraView.route) {
                            popUpTo(Screen.CameraView.route) { inclusive = true }
                        }
                    },
                    onNavigateToChat = {
                        if (hasChatHistory) {
                            // Navigate to the most recent chat
                            val latestThread = allChatThreads.firstOrNull()
                            latestThread?.let {
                                navController.navigate(Screen.ChatView.routeWithArgs(threadId = it.id))
                            }
                        }
                    }
                )
            }
        }
    }
} 