package com.example.mnn_llm_test.model

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String, // e.g., "user" or "model"
    val timestamp: Long
) 