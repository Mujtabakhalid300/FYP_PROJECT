package com.example.mnn_llm_test.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NavigationManager : ViewModel() {
    private val _navigationStack = MutableStateFlow<List<String>>(emptyList())
    val navigationStack: StateFlow<List<String>> = _navigationStack.asStateFlow()
    
    private val _currentRoute = MutableStateFlow(Screen.CameraView.route)
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()
    
    fun navigateTo(route: String, fromRoute: String? = null) {
        _currentRoute.value = route
        
        // Update navigation stack for back button behavior
        val currentStack = _navigationStack.value.toMutableList()
        
        // Don't add duplicate consecutive routes
        if (currentStack.lastOrNull() != fromRoute && fromRoute != null) {
            currentStack.add(fromRoute)
        }
        
        // Limit stack size to prevent memory issues
        if (currentStack.size > 10) {
            currentStack.removeAt(0)
        }
        
        _navigationStack.value = currentStack
    }
    
    fun navigateBack(): String? {
        val currentStack = _navigationStack.value.toMutableList()
        return if (currentStack.isNotEmpty()) {
            val previousRoute = currentStack.removeLastOrNull()
            _navigationStack.value = currentStack
            previousRoute
        } else {
            // Default back behavior - go to camera
            Screen.CameraView.route
        }
    }
    
    fun clearStack() {
        _navigationStack.value = emptyList()
    }
}

@Composable
fun rememberNavigationManager(): NavigationManager {
    return viewModel<NavigationManager>()
} 