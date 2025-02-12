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

    // Helper class to handle model session data
    class ChatSession(
        val sessionId: String,
        private val configPath: String,
        private val useTmpPath: Boolean,
        private val savedHistory: List<String>? = null,
        private val isDiffusion: Boolean = false
    ) {

        private var nativePtr: Long = 0
        private var mGenerating = false
        private var mReleaseRequeted = false
        private var keepHistory = true

        init {
            load()
        }

        private fun load() {
            val historyList = savedHistory ?: emptyList()
            nativePtr = initNative(configPath, useTmpPath, savedHistory, isDiffusion)
            Log.d("NativeLog", "Native Pointer: $nativePtr")
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
            if (nativePtr > 0) {
                releaseNative(nativePtr, isDiffusion)
                nativePtr = 0
            }
        }

        // Set whether to keep history
        fun setKeepHistory(keepHistory: Boolean) {
            this.keepHistory = keepHistory
        }

    }
}
