package com.example.mnn_llm_test.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class HuggingFaceDownloader(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun downloadModelFiles(
        repo: String,
        commitSha: String,
        outputDirName: String = "mnn_models",
        onProgress: ((downloadedFiles: Int, totalFiles: Int, currentFile: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {

        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$outputDirName/$repo-$commitSha"
        )

        // 🔧 OFFLINE-FIRST: Check for essential model files before making any network calls
        val essentialFiles = listOf("llm.mnn", "config.json")
        val allEssentialFilesExist = essentialFiles.all { fileName ->
            val file = File(downloadsDir, fileName)
            val exists = file.exists()
            Log.d("HFDownloader", "Checking essential file '$fileName': ${if (exists) "✅ EXISTS" else "❌ MISSING"}")
            exists
        }

        if (allEssentialFilesExist) {
            Log.d("HFDownloader", "🎉 All essential model files found locally. Working OFFLINE! No internet required.")
            return@withContext
        }

        Log.d("HFDownloader", "📥 Some essential files missing. Need to download from HuggingFace...")

        // Step 1: Get list of files from Hugging Face API (only if files are missing)
        val apiUrl = "https://huggingface.co/api/models/$repo/tree/$commitSha"
        val request = Request.Builder().url(apiUrl).build()
        
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e("HFDownloader", "❌ Network error - no internet connection: ${e.message}")
            throw Exception("No internet connection available and model files are missing. Please connect to internet for initial download.")
        }

        if (!response.isSuccessful) {
            Log.e("HFDownloader", "Failed to get file list: ${response.code}")
            throw Exception("Failed to fetch model file list from HuggingFace (HTTP ${response.code})")
        }

        val jsonString = response.body?.string() ?: run {
            Log.e("HFDownloader", "Empty response body")
            throw Exception("Empty response from HuggingFace API")
        }

        val jsonArray = JSONArray(jsonString)
        val fileList = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            fileList.add(item.getString("path"))
        }

        // Step 2: Check if all files exist in downloadsDir (full check now that we have the complete list)
        val allFilesExist = fileList.all { filePath ->
            File(downloadsDir, filePath).exists()
        }

        if (allFilesExist) {
            Log.d("HFDownloader", "✅ Complete model already exists in Downloads. Skipping download.")
            return@withContext
        }

        // Step 3: Create downloadsDir if not exists
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        Log.d("HFDownloader", "Found ${fileList.size} files. Starting download...")

        var downloadedFilesCount = 0

        // Step 4: Download files directly to downloadsDir
        for (filePath in fileList) {
            try {
                onProgress?.invoke(downloadedFilesCount, fileList.size, filePath)

                val fileUrl = "https://huggingface.co/$repo/resolve/$commitSha/$filePath"
                val fileRequest = Request.Builder().url(fileUrl).build()
                val fileResponse = client.newCall(fileRequest).execute()

                if (!fileResponse.isSuccessful) {
                    Log.e("HFDownloader", "Failed to download $filePath: ${fileResponse.code}")
                    continue
                }

                val outFile = File(downloadsDir, filePath)
                outFile.parentFile?.mkdirs()

                fileResponse.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }

                downloadedFilesCount++
                onProgress?.invoke(downloadedFilesCount, fileList.size, filePath)

                Log.d("HFDownloader", "Downloaded: $filePath")

            } catch (e: Exception) {
                Log.e("HFDownloader", "Error downloading $filePath", e)
            }
        }

        Log.d("HFDownloader", "✅ Done downloading all files.")
    }
}
