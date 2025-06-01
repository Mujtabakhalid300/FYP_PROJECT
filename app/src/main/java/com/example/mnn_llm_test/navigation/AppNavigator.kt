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
import com.example.mnn_llm_test.ui.mainmenu.MainMenuScreen
import com.example.mnn_llm_test.ui.cameraview.CameraScreen
// import com.example.mnn_llm_test.ui.cameraview.CameraViewModel // Will add later
import com.example.mnn_llm_test.ui.chatview.ChatScreen
// import com.example.mnn_llm_test.ui.chatview.ChatViewModel // Will add later
import com.example.mnn_llm_test.ui.WelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
         Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White) { innerPadding ->
            WelcomeScreen(LocalContext.current, modifier = Modifier.padding(innerPadding))
        }
    } else {
        NavHost(navController = navController, startDestination = Screen.MainMenu.route) {
            composable(Screen.MainMenu.route) {
                MainMenuScreen(navController = navController)
            }
            composable(Screen.CameraView.route) {
                // val cameraViewModel: CameraViewModel = viewModel() // Initialize when ViewModel is created
                CameraScreen(navController = navController /*, viewModel = cameraViewModel */)
            }
            composable(
                route = Screen.MainMenu.chatViewRouteDefinition, // Use the definition from Screen object
                arguments = listOf(navArgument(Screen.ChatView.imagePathArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) {
                backStackEntry ->
                val imagePathEncoded = backStackEntry.arguments?.getString(Screen.ChatView.imagePathArg)
                val imagePathDecoded = imagePathEncoded?.let { Uri.decode(it) }
                ChatScreen(
                    navController = navController,
                    chatSession = chatSessionState,
                    imagePath = imagePathDecoded
                )
            }
        }
    }
} 