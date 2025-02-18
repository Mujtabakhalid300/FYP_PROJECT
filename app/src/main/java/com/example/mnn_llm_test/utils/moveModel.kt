package com.example.mnn_llm_test.utils

import android.content.Context
import android.util.Log
import java.io.*

fun copyAssetFolderToInternalStorage(context: Context, assetFolderName: String, outputPath: String) {
    try {
        val assetManager = context.assets
        val outputDir = File(outputPath)

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val assetFiles = assetManager.list(assetFolderName) ?: emptyArray()

        for (fileName in assetFiles) {
            val assetFilePath = "$assetFolderName/$fileName"
            val outFile = File(outputDir, fileName)

            // Check if it's a directory inside assets
            if (assetManager.list(assetFilePath)?.isNotEmpty() == true) {
                // Recursively copy subdirectories
                copyAssetFolderToInternalStorage(context, assetFilePath, outFile.absolutePath)
            } else {
                if (outFile.exists()) {
                    Log.d("ModelCopy", "Skipping $fileName, already exists at ${outFile.absolutePath}")
                } else {
                    copyAssetFile(assetManager, assetFilePath, outFile)
                }

            }
        }

        Log.d("ModelCopy", "Model files copied successfully to $outputPath")
    } catch (e: IOException) {
        Log.e("ModelCopy", "Error copying asset folder: ${e.message}", e)
    }
}

private fun copyAssetFile(assetManager: android.content.res.AssetManager, assetFilePath: String, outFile: File) {
    try {
        assetManager.open(assetFilePath).use { inStream ->
            FileOutputStream(outFile).use { outStream ->
                val buffer = ByteArray(1024)
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                }
                outStream.flush()
            }
        }

        // Log the file size after copying
        val fileSize = outFile.length()
        Log.d("ModelCopy", "Copied $assetFilePath to ${outFile.absolutePath}, File size: ${formatFileSize(fileSize)}")
    } catch (e: IOException) {
        Log.e("ModelCopy", "Error copying file: $assetFilePath", e)
    }
}

// Helper function to format file size in a human-readable format (e.g., KB, MB)
private fun formatFileSize(sizeInBytes: Long): String {
    val kb = 1024
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        sizeInBytes >= gb -> String.format("%.2f GB", sizeInBytes / gb.toDouble())
        sizeInBytes >= mb -> String.format("%.2f MB", sizeInBytes / mb.toDouble())
        sizeInBytes >= kb -> String.format("%.2f KB", sizeInBytes / kb.toDouble())
        else -> "$sizeInBytes bytes"
    }
}


fun printAssetFileSizes(context: Context, assetFolderName: String) {
    try {
        val assetManager = context.assets
        val assetFiles = assetManager.list(assetFolderName) ?: emptyArray()

        // Iterate through files in the asset folder
        for (fileName in assetFiles) {
            val assetFilePath = "$assetFolderName/$fileName"

            // Check if it's a directory
            if (assetManager.list(assetFilePath)?.isNotEmpty() == true) {
                // Recursively call for subdirectories
                printAssetFileSizes(context, assetFilePath)
            } else {
                // Log file size without copying
                logFileSize(assetManager, assetFilePath)
            }
        }

    } catch (e: IOException) {
        Log.e("ModelCopy", "Error accessing asset folder: ${e.message}", e)
    }
}

private fun logFileSize(assetManager: android.content.res.AssetManager, assetFilePath: String) {
    try {
        // Open the asset file and get its size
        val inputStream = assetManager.open(assetFilePath)
        val sizeInBytes = inputStream.available()

        // Log the file size in bytes
        Log.d("ModelCopy", "File: $assetFilePath, Size: $sizeInBytes bytes")

        inputStream.close()
    } catch (e: IOException) {
        Log.e("ModelCopy", "Error accessing file: $assetFilePath", e)
    }
}
