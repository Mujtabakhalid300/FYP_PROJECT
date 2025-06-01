package com.example.mnn_llm_test.navigation

sealed class Screen(val route: String) {
    object MainMenu : Screen("main_menu")
    object CameraView : Screen("camera_view")
    object ChatView : Screen("chat_view") {
        fun routeWithArgs(imagePath: String? = null): String {
            return if (imagePath != null) {
                "chat_view?imagePath=$imagePath"
            } else {
                "chat_view"
            }
        }
        const val imagePathArg = "imagePath" // For NavType argument name
    }

    // Static route definition for NavHost
    val chatViewRouteDefinition: String = "chat_view?imagePath={${ChatView.imagePathArg}}"
} 