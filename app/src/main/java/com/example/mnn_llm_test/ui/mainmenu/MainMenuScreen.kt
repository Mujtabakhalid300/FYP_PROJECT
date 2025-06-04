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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    
    // Observe available chat threads to determine if "Open Chat" should be enabled
    val allChatThreads by chatRepository.getAllChatThreads().collectAsState(initial = emptyList())
    val hasExistingChats = allChatThreads.isNotEmpty()

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
            Button(
                onClick = {
                    if (hasExistingChats) {
                        coroutineScope.launch {
                            val latestThread = allChatThreads.firstOrNull()
                            if (latestThread != null) {
                                navController.navigate(Screen.ChatView.routeWithArgs(threadId = latestThread.id))
                            }
                        }
                    }
                    // If no existing chats, button is disabled, so this won't execute
                },
                enabled = hasExistingChats // Enable only if chats exist
            ) {
                Text(if (hasExistingChats) "Open Chat" else "No Chats Available")
            }
        }
    }
} 