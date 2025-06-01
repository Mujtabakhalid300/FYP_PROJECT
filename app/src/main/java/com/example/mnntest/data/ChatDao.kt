package com.example.mnntest.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.sql.Timestamp
// Explicitly import entities, just in case
import com.example.mnntest.data.ChatThread
import com.example.mnntest.data.ChatThreadImage
import com.example.mnntest.data.ChatMessage

@Dao
interface ChatDao {

    // ChatThread operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatThread(chatThread: ChatThread): Long

    @Update
    suspend fun updateChatThread(chatThread: ChatThread)

    @Query("SELECT * FROM chat_thread ORDER BY updated_at DESC")
    fun getAllChatThreads(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_thread WHERE id = :threadId")
    fun getChatThreadById(threadId: Int): Flow<ChatThread?>

    @Query("DELETE FROM chat_thread WHERE id = :threadId")
    suspend fun deleteChatThreadById(threadId: Int)

    // ChatThreadImage operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatThreadImage(image: ChatThreadImage): Long

    @Query("SELECT * FROM chat_thread_image WHERE thread_id = :threadId LIMIT 1")
    fun getChatThreadImage(threadId: Int): Flow<ChatThreadImage?>
    
    @Query("DELETE FROM chat_thread_image WHERE thread_id = :threadId")
    suspend fun deleteChatThreadImageByThreadId(threadId: Int)

    // ChatMessage operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage): Long

    @Query("SELECT * FROM chat_message WHERE thread_id = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: Int): Flow<List<ChatMessage>>

    @Query("UPDATE chat_message SET message = :newMessageText, timestamp = :newTimestamp WHERE id = :messageId")
    suspend fun updateChatMessageText(messageId: Int, newMessageText: String, newTimestamp: Timestamp)

    @Query("DELETE FROM chat_message WHERE thread_id = :threadId")
    suspend fun deleteMessagesForThread(threadId: Int)
} 