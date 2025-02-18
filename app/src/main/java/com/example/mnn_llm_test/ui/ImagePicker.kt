package com.example.mnn_llm_test.ui


import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.mnn_llm_test.utils.getFilePathFromUri

@Composable
fun ImagePicker(context: Context, isImageEnabled: Boolean, onImagePicked: (Uri) -> Unit) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            val filePath = getFilePathFromUri(context, uri)
            if (filePath != null) {
                Log.d("ImagePicker", "Selected image file path: $filePath")
                onImagePicked(Uri.parse(filePath))
            } else {
                Log.e("ImagePicker", "Failed to resolve file path from URI")
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Selected Image",
                modifier = Modifier.size(200.dp).clip(CircleShape)
            )
        }
        Button(enabled = isImageEnabled, onClick = { launcher.launch("image/*") }) {
            Text(text = "Pick Image")
        }
    }
}
