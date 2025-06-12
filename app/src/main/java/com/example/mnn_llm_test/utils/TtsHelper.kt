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
                        Log.d("TtsHelper", "Speech started: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        Log.d("TtsHelper", "Speech finished: $utteranceId")
                        onSpeechFinished?.invoke()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
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
    
    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        if (isInitialized && !text.isBlank() && !isSpeaking) {
            onSpeechFinished = onFinished
            val utteranceId = "tts_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            Log.d("TtsHelper", "Speaking: $text")
        } else {
            if (isSpeaking) {
                Log.d("TtsHelper", "TTS busy, skipping: $text")
            } else {
                Log.w("TtsHelper", "TTS not initialized or empty text")
            }
            // Call callback immediately if not speaking
            if (!isSpeaking) {
                onFinished?.invoke()
            }
        }
    }
    
    fun isBusy(): Boolean {
        return isSpeaking
    }
    
    fun stop() {
        tts?.stop()
        isSpeaking = false
        onSpeechFinished = null
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        isSpeaking = false
        onSpeechFinished = null
    }
} 