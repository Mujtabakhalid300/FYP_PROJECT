package com.example.mnn_llm_test.model

// Using String for URI for now, can be platform specific URI later
data class CapturedImage(
    val uri: String, // Path to the image file
    val description: String? = null // Optional description from VLM or user
) 