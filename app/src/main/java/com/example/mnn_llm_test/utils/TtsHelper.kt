package com.example.mnn_llm_test.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TtsHelper(private val context: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    private var onSpeechFinished: (() -> Unit)? = null
    private var currentPriority = Priority.NORMAL
    
    enum class Priority {
        LOW,        // Background announcements (scene changes) - interruptible
        NORMAL,     // Default priority
        HIGH        // User-triggered announcements (taps) - uninterruptible
    }
    
    init {
        initializeTts()
    }
    
    private fun initializeTts() {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && 
                           result != TextToSpeech.LANG_NOT_SUPPORTED
            
            if (isInitialized) {
                // Set speech rate for accessibility (slightly slower)
                tts?.setSpeechRate(0.8f)
                
                // Ensure TTS runs locally only - disable network synthesis
                tts?.setEngineByPackageName("com.google.android.tts")
                
                // Set up utterance progress listener to track when speech finishes
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        Log.d("TtsHelper", "Speech started: $utteranceId (Priority: $currentPriority)")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        currentPriority = Priority.NORMAL // Reset priority
                        Log.d("TtsHelper", "Speech finished: $utteranceId")
                        onSpeechFinished?.invoke()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        currentPriority = Priority.NORMAL // Reset priority
                        Log.e("TtsHelper", "Speech error: $utteranceId")
                        onSpeechFinished?.invoke()
                    }
                })
                
                Log.d("TtsHelper", "TTS initialized successfully (local only)")
            } else {
                Log.e("TtsHelper", "TTS language not supported")
            }
        } else {
            Log.e("TtsHelper", "TTS initialization failed")
        }
    }
    
    /**
     * Speak with priority control
     * @param text Text to speak
     * @param priority Priority level (LOW = interruptible, HIGH = uninterruptible)
     * @param onFinished Callback when speech finishes
     */
    fun speak(text: String, priority: Priority = Priority.NORMAL, onFinished: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            Log.w("TtsHelper", "TTS not initialized or empty text")
            onFinished?.invoke()
            return
        }
        
        // Priority-based interruption logic
        if (isSpeaking) {
            when {
                priority == Priority.HIGH && currentPriority == Priority.HIGH -> {
                    // HIGH priority interrupting another HIGH priority: stop current but don't start new one
                    Log.d("TtsHelper", "🔴 HIGH priority interrupting another HIGH priority - stopping without replacement")
                    stop()
                    onFinished?.invoke() // Call callback immediately
                    return // Don't start new speech
                }
                priority == Priority.HIGH -> {
                    // HIGH priority: interrupt any other priority speech
                    Log.d("TtsHelper", "🔴 HIGH priority speech interrupting current TTS")
                    stop()
                }
                priority == Priority.NORMAL && currentPriority == Priority.LOW -> {
                    // NORMAL priority: interrupt LOW priority speech
                    Log.d("TtsHelper", "🟡 NORMAL priority speech interrupting LOW priority TTS")
                    stop()
                }
                priority == Priority.LOW && currentPriority >= Priority.NORMAL -> {
                    // LOW priority: skip if NORMAL/HIGH priority is speaking
                    Log.d("TtsHelper", "🟢 LOW priority speech skipped - higher priority TTS active")
                    onFinished?.invoke()
                    return
                }
                priority == Priority.NORMAL && currentPriority >= Priority.NORMAL -> {
                    // NORMAL priority: skip if NORMAL/HIGH priority is speaking
                    Log.d("TtsHelper", "🟡 NORMAL priority speech skipped - equal/higher priority TTS active")
                    onFinished?.invoke()
                    return
                }
                priority == Priority.LOW && currentPriority == Priority.LOW -> {
                    // LOW priority: skip if another LOW priority is speaking (completion pending)
                    Log.d("TtsHelper", "🟢 LOW priority speech skipped - another LOW priority TTS active (completion pending)")
                    onFinished?.invoke()
                    return
                }
            }
        }
        
        currentPriority = priority
        onSpeechFinished = onFinished
        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d("TtsHelper", "🎤 Speaking [$priority]: $text")
    }
    
    /**
     * Legacy speak method with default NORMAL priority
     */
    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        speak(text, Priority.NORMAL, onFinished)
    }
    
    /**
     * Interrupt any speech regardless of priority (emergency stop)
     */
    fun forceStop() {
        Log.d("TtsHelper", "🛑 Force stopping all TTS")
        stop()
    }
    
    fun isBusy(): Boolean {
        return isSpeaking
    }
    
    fun getCurrentPriority(): Priority {
        return currentPriority
    }
    
    fun stop() {
        tts?.stop()
        isSpeaking = false
        currentPriority = Priority.NORMAL
        onSpeechFinished = null
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        isSpeaking = false
        currentPriority = Priority.NORMAL
        onSpeechFinished = null
    }
} 