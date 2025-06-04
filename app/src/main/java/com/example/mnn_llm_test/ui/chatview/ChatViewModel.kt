package com.example.mnn_llm_test.ui.chatview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mnntest.data.ChatRepository
import com.example.mnntest.data.ChatMessage
import com.example.mnntest.data.ChatThread
import com.example.mnntest.data.ChatThreadImage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.sql.Timestamp

class ChatViewModel(private val repository: ChatRepository, val threadId: Int) : ViewModel() {

    val chatThread: StateFlow<ChatThread?> = repository.getChatThreadById(threadId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val chatImage: StateFlow<ChatThreadImage?> = repository.getChatThreadImage(threadId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allChatThreads: StateFlow<List<ChatThread>> = repository.getAllChatThreads()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val messages: StateFlow<List<com.example.mnn_llm_test.model.ChatMessage>> = repository.getMessagesForThread(threadId)
        .map { dbMessages ->
            dbMessages.map { dbMsg ->
                com.example.mnn_llm_test.model.ChatMessage(
                    id = dbMsg.id.toString(),
                    text = dbMsg.message ?: "",
                    sender = dbMsg.sender,
                    timestamp = dbMsg.timestamp.time
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addMessage(text: String?, sender: String): Long? {
        if (text.isNullOrBlank() && sender != SENDER_IMAGE) return null
        var newDbMessageId: Long? = null
        viewModelScope.launch {
            val newMessage = ChatMessage(
                threadId = threadId,
                message = text,
                sender = sender,
                timestamp = Timestamp(System.currentTimeMillis())
            )
            newDbMessageId = repository.insertChatMessage(newMessage)
        }
        return newDbMessageId
    }

    suspend fun addNewMessageAndGetId(text: String?, sender: String): Long? {
        if (text.isNullOrBlank() && sender != SENDER_IMAGE) return null
        val newMessage = ChatMessage(
            threadId = threadId,
            message = text,
            sender = sender,
            timestamp = Timestamp(System.currentTimeMillis())
        )
        return repository.insertChatMessage(newMessage)
    }

    fun updateMessage(messageIdToUpdate: Int, newText: String) {
        viewModelScope.launch {
            repository.updateChatMessageText(messageIdToUpdate, newText, Timestamp(System.currentTimeMillis()))
        }
    }

    /**
     * Deletes a chat thread by its ID. This will cascade to delete associated messages and images.
     * @param threadIdToDelete The ID of the thread to delete
     * @return Boolean indicating if the deletion was successful
     */
    suspend fun deleteThread(threadIdToDelete: Int): Boolean {
        return try {
            repository.deleteChatThreadById(threadIdToDelete)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the next available thread ID after deleting the current one.
     * Returns null if no threads exist.
     */
    suspend fun getNextAvailableThreadId(excludeThreadId: Int): Int? {
        val allThreads = repository.getAllChatThreads().firstOrNull() ?: return null
        return allThreads.filter { it.id != excludeThreadId }
            .sortedByDescending { it.updatedAt }
            .firstOrNull()?.id
    }
}

class ChatViewModelFactory(private val repository: ChatRepository, private val threadId: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository, threadId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 