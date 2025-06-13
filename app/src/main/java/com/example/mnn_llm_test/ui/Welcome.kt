package com.example.mnn_llm_test.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(context: Context, modifier: Modifier) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).semantics(mergeDescendants = true) { 
            contentDescription = "Welcome to Ibelong - Loading AI models"  
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome", 
            fontSize = 70.sp, 
            color = Color.Black,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "to", 
            fontSize = 70.sp, 
            color = Color.Black,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "Ibelong", 
            fontSize = 70.sp, 
            color = Color(0xFF0047FF), 
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Loading indicator
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color(0xFF0047FF),
            strokeWidth = 4.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Loading message
        Text(
            text = "Initializing AI Models...",
            fontSize = 18.sp,
            color = Color.Gray,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Offline capability message
        Text(
            text = "Works offline once models are loaded",
            fontSize = 14.sp,
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { 
                contentDescription = "App works offline once AI models are initialized" 
            }
        )
    }
}


