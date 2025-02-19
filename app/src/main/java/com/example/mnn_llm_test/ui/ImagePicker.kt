package com.example.mnn_llm_test.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun ImagePicker(context: Context, isImageEnabled: Boolean, onImagePicked: (String) -> Unit) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var absolutePath by remember { mutableStateOf<String?>(null) }
    var cameraPermissionAllowed by remember { mutableStateOf<Boolean>(false) }

    // 🔥 Create image file and get its URI + absolute path
    val (fileUri, filePath) = remember {
        createImageFile(context).also { (uri, path) ->
            Log.d("ImagePicker", "Created FileProvider URI: $uri")
            Log.d("ImagePicker", "Absolute File Path: $path")
        }
    }

    // Launcher to capture an image using the camera
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageUri = fileUri
            absolutePath = filePath
            Log.d("ImagePicker", "Captured Image URI: $fileUri")
            Log.d("ImagePicker", "Captured Image Absolute Path: $filePath")
            onImagePicked(filePath) // ✅ Return absolute path
        } else {
            Log.e("ImagePicker", "Failed to capture image")
        }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraPermissionAllowed = permissions[Manifest.permission.CAMERA] == true
    }

// Request permissions inside LaunchedEffect
    LaunchedEffect(Unit) {
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)

        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            cameraPermissionAllowed = true
        }
    }


    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//
        Button(

            enabled = if (cameraPermissionAllowed) true else false,
            onClick = {
                Log.d("ImagePicker", "Launching camera with URI: $fileUri")
                takePictureLauncher.launch(fileUri)
            }
                    ,shape = CircleShape, // Makes it circular
            modifier = Modifier.aspectRatio(1f).fillMaxWidth() // Adjust size as needed
            , colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7700))
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Camera",
                tint = Color.White, // Adjust color if needed
                modifier = Modifier.fillMaxSize(),

            )
        }
    }
}

// 🛠 Function to Create Image File and Return (URI, Absolute Path)
private fun createImageFile(context: Context): Pair<Uri, String> {
    val storageDir = File(context.getExternalFilesDir(null), "images")
    if (!storageDir.exists()) {
        storageDir.mkdirs()
        Log.d("ImagePicker", "Storage Directory Created: ${storageDir.absolutePath}")
    }

    val imageFile = File(storageDir, "JPEG_${System.currentTimeMillis()}.jpg")
    Log.d("ImagePicker", "Image File Created: ${imageFile.absolutePath}")

    val fileUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )

    return Pair(fileUri, imageFile.absolutePath) // ✅ Return both URI and Absolute Path
}
