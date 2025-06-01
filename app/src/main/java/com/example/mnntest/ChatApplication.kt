package com.example.mnntest

import android.app.Application
import com.example.mnntest.data.AppDatabase
import com.example.mnntest.data.ChatRepository

class ChatApplication : Application() {

    // Using by lazy so the database and repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ChatRepository(database.chatDao()) }

    override fun onCreate() {
        super.onCreate()
        // You can perform other application-wide initializations here if needed
    }
} 