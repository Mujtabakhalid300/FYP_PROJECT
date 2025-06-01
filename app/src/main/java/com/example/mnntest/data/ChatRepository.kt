package com.example.mnntest.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    // ChatThread methods
    fun getAllChatThreads(): Flow<List<ChatThread>> = chatDao.getAllChatThreads()

    fun getChatThreadById(threadId: Int): Flow<ChatThread?> = chatDao.getChatThreadById(threadId)

    suspend fun insertChatThread(chatThread: ChatThread): Long {
        return chatDao.insertChatThread(chatThread)
    }

    suspend fun updateChatThread(chatThread: ChatThread) {
        chatDao.updateChatThread(chatThread)
    }

    suspend fun deleteChatThreadById(threadId: Int) {
        chatDao.deleteChatThreadById(threadId)
        // Also delete related images and messages
        chatDao.deleteChatThreadImageByThreadId(threadId)
        chatDao.deleteMessagesForThread(threadId)
    }

    // ChatThreadImage methods
    fun getChatThreadImage(threadId: Int): Flow<ChatThreadImage?> = chatDao.getChatThreadImage(threadId)

    suspend fun insertChatThreadImage(image: ChatThreadImage): Long {
        return chatDao.insertChatThreadImage(image)
    }
    
    suspend fun deleteChatThreadImageByThreadId(threadId: Int) {
        chatDao.deleteChatThreadImageByThreadId(threadId)
    }

    // ChatMessage methods
    fun getMessagesForThread(threadId: Int): Flow<List<ChatMessage>> = chatDao.getMessagesForThread(threadId)

    suspend fun insertChatMessage(message: ChatMessage): Long {
        return chatDao.insertChatMessage(message)
    }

    suspend fun updateChatMessageText(messageId: Int, newMessageText: String, newTimestamp: java.sql.Timestamp) {
        chatDao.updateChatMessageText(messageId, newMessageText, newTimestamp)
    }

    suspend fun deleteMessagesForThread(threadId: Int) {
        chatDao.deleteMessagesForThread(threadId)
    }
} 