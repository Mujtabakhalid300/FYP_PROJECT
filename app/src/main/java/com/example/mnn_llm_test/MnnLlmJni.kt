package com.example.mnn_llm_test

import android.util.Log
import java.util.HashMap

object MnnLlmJni {
    init {
        System.loadLibrary("mnnllmapp")  // Ensure this matches the shared library built from C++
    }

    interface ProgressListener {
        fun onProgress(progress: String): Boolean
    }

    // Initializes the native model
    external fun initNative(
        modelDir: String,
        useTmpPath: Boolean,
        chatHistory: List<String>?,
        isDiffusion: Boolean
    ): Long

    // Submit the input for generation, with a progress listener
    external fun submitNative(
        llmPtr: Long,
        inputStr: String,
        keepHistory: Boolean,
        progressListener: ProgressListener
    ): HashMap<String, Any>

    // Submit diffusion input for generation, with a progress listener
    external fun submitDiffusionNative(
        instanceId: Long,
        input: String,
        outputPath: String,
        progressListener: ProgressListener
    ): HashMap<String, Long>

    // Reset the native session
    external fun resetNative(llmPtr: Long)

    // Release the native session
    external fun releaseNative(objecPtr: Long, isDiffusion: Boolean)

    // Sets a new chat history, resetting the previous one
    // Corresponds to JNI function: Java_com_example_mnn_1llm_1test_MnnLlmJni_setNewChatHistoryNative
    external fun setNewChatHistoryNative(
        llmPtr: Long,
        newChatHistory: List<String>?, // Nullable to represent an empty history for new chats
        isR1Session: Boolean
    )

    /**
     * Sets a new chat history for the given LLM instance, effectively resetting
     * the conversation to the provided history.
     *
     * This will clear the MNN engine's internal state (like KV cache) and then
     * re-prime the conversation with the new history.
     *
     * @param llmPtr Pointer to the native LLM instance.
     * @param newChatHistory A list of strings representing the new chat history.
     *                       Each string is a message. Assumes alternating user/assistant roles
     *                       starting with user if the list is populated.
     *                       Pass null or an empty list to start a truly fresh chat (only system prompt).
     * @param isR1Session A flag, usage determined by native implementation (currently logged).
     */
    fun setNewChatHistory(
        llmPtr: Long,
        newChatHistory: List<String>?,
        isR1Session: Boolean
    ) {
        if (llmPtr == 0L) {
            Log.w("MnnLlmJni", "LLM Pointer is 0, cannot set new chat history. Model not initialized or already released.")
            return
        }
        try {
            setNewChatHistoryNative(llmPtr, newChatHistory, isR1Session)
            Log.i("MnnLlmJni", "Successfully set new chat history. New history size: ${newChatHistory?.size ?: 0}, R1: $isR1Session")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("MnnLlmJni", "Failed to call setNewChatHistoryNative: UnsatisfiedLinkError. Ensure JNI function name matches C++.", e)
            // Potentially rethrow or handle as a critical error
            throw e
        } catch (e: Exception) {
            Log.e("MnnLlmJni", "Exception when calling setNewChatHistoryNative.", e)
            // Potentially rethrow or handle
            throw e
        }
    }

    // Helper class to handle model session data
    class ChatSession(
        val sessionId: String,
        private val configPath: String,
        private val useTmpPath: Boolean,
        private val savedHistory: List<String>? = null,
        private val isDiffusion: Boolean = false
    ) {

        private var _nativePtr: Long = 0
        val nativePtr: Long
            get() = _nativePtr

        private var mGenerating = false
        private var mReleaseRequeted = false
        private var keepHistory = true

        init {
            load()
        }

        private fun load() {
            val historyList = savedHistory ?: emptyList()
            _nativePtr = initNative(configPath, useTmpPath, savedHistory, isDiffusion)
            Log.d("NativeLog", "Native Pointer: $_nativePtr")
        }

        // Handle generation process
        fun generate(input: String, progressListener: ProgressListener): HashMap<String, Any> {
            synchronized(this) {
                Log.d("MNN_DEBUG", "submit: $input")
                mGenerating = true
                try {
                    val result = submitNative(nativePtr, input, keepHistory, progressListener)
                    mGenerating = false
                    if (mReleaseRequeted) {
                        releaseInner()
                    }
                    return result
                } catch (e: Exception) {
                    Log.e("MNN_DEBUG", "Error during native submission: ${e.message}")
                    mGenerating = false
                    throw e // Or handle gracefully
                }
            }
        }


        // Handle diffusion process
        fun generateDiffusion(input: String, outputPath: String, progressListener: ProgressListener): HashMap<String, Long> {
            synchronized(this) {
                mGenerating = true
                val result = submitDiffusionNative(nativePtr, input, outputPath, progressListener)
                mGenerating = false
                if (mReleaseRequeted) {
                    releaseInner()
                }
                return result
            }
        }

        // Reset the session
        fun reset() {
            synchronized(this) {
                resetNative(nativePtr)
            }
        }

        // Release the session and cleanup
        fun release() {
            synchronized(this) {
                if (!mGenerating) {
                    releaseInner()
                } else {
                    mReleaseRequeted = true
                }
            }
        }

        // Internal function to release the native session
        private fun releaseInner() {
            if (_nativePtr > 0) {
                releaseNative(_nativePtr, isDiffusion)
                _nativePtr = 0
            }
        }

        // Set whether to keep history
        fun setKeepHistory(keepHistory: Boolean) {
            this.keepHistory = keepHistory
        }

    }
}
