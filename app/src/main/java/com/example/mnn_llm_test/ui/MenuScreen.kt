package com.example.mnn_llm_test.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MenuScreen(
    onCameraClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onCameraClick,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(200.dp)
        ) {
            Text(text = "Camera")
        }
        
        Button(
            onClick = onChatClick,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(200.dp)
        ) {
            Text(text = "Chat")
        }
        
        Button(
            onClick = onSettingsClick,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(200.dp)
        ) {
            Text(text = "Settings")
        }
    }
} 