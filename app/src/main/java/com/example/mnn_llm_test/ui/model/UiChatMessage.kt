package com.example.mnn_llm_test.ui.model

data class UiChatMessage(
    val id: String,
    val text: String,
    val sender: String, // e.g., "user" or "model"
    val timestamp: Long
) 