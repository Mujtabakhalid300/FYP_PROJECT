package com.example.mnn_llm_test.utils


import android.content.Context
import android.net.Uri
import android.provider.MediaStore

fun getFilePathFromUri(context: Context, uri: Uri): String? {
    var filePath: String? = null
    if (uri.scheme.equals("content")) {
        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                if (columnIndex != -1) {
                    filePath = it.getString(columnIndex)
                }
            }
        }
    } else if (uri.scheme.equals("file")) {
        filePath = uri.path
    }
    return filePath
}
