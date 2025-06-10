package com.example.mnn_llm_test.navigation

sealed class Screen(val route: String) {
    object CameraView : Screen("camera_view")
    object ChatView : Screen("chat_view") {
        fun routeWithArgs(threadId: Int): String {
            return "chat_view?threadId=$threadId"
        }
        const val threadIdArg = "threadId"
    }
    object ChatHistory : Screen("chat_history")

    // Static route definition for NavHost
    val chatViewRouteDefinition: String = "chat_view?threadId={${ChatView.threadIdArg}}"
} 