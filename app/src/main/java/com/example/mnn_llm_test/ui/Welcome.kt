package com.example.mnn_llm_test.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(context: Context, modifier: Modifier) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).semantics(mergeDescendants = true) { contentDescription = "Welcome to Ibelong"  },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,


    ) {
        Text(text = "Welcome", fontSize = 70.sp , color = Color.Black,style = MaterialTheme.typography.headlineLarge)
        Text(text = "to", fontSize = 70.sp, color = Color.Black ,style = MaterialTheme.typography.headlineLarge)
        Text(text = "Ibelong", fontSize = 70.sp, color = Color(0xFF0047FF), style = MaterialTheme.typography.headlineLarge)




    }
}


