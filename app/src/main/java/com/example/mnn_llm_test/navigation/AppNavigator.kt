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
import com.example.mnn_llm_test.navigation.BottomNavigationBar
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
        Scaffold(
            bottomBar = {
                BottomNavigationBar(navController = navController)
            }
        ) { paddingValues ->
            NavHost(
                navController = navController, 
                startDestination = Screen.CameraView.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.CameraView.route) {
                    CameraScreen(navController = navController)
                }
                composable(Screen.ChatHistory.route) {
                    ChatHistoryScreen(navController = navController)
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
                            threadId = threadId
                        )
                    } else {
                        Log.e("AppNavigator", "threadId is null for ChatScreen, navigating back.")
                        navController.popBackStack()
                    }
                }
            }
        }
    }
} 