package com.example.mnn_llm_test.ui.mainmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.mnn_llm_test.navigation.Screen
import com.example.mnntest.ChatApplication
import com.example.mnntest.data.ChatThread
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.sql.Timestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = (context.applicationContext as ChatApplication).repository

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Main Menu") })
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
            Text("Welcome!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.navigate(Screen.CameraView.route) }) {
                Text("Open Camera")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                coroutineScope.launch {
                    val latestThread = chatRepository.getAllChatThreads().firstOrNull()?.firstOrNull()
                    if (latestThread != null) {
                        navController.navigate(Screen.ChatView.routeWithArgs(threadId = latestThread.id))
                    } else {
                        // No existing chats, create a new one
                        val newChatThread = ChatThread(
                            title = "New Chat - ${System.currentTimeMillis()}", // Default title for new chat
                            systemPrompt = null,
                            createdAt = Timestamp(System.currentTimeMillis()),
                            updatedAt = Timestamp(System.currentTimeMillis())
                        )
                        val newThreadId = chatRepository.insertChatThread(newChatThread).toInt()
                        navController.navigate(Screen.ChatView.routeWithArgs(threadId = newThreadId))
                    }
                }
            }) {
                Text("Open Chat")
            }
        }
    }
} 