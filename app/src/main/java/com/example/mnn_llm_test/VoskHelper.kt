package com.example.mnn_llm_test

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoskHelper(
    private val context: Context,
    private var onFinalTranscription: ((String) -> Unit)? = null, // Callback to handle recognition results
    private var onError: ((String) -> Unit)? = null   // Callback to handle errors
) : RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    
    // Allow updating callbacks for global instance usage
    fun setCallbacks(
        onFinalTranscription: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        this.onFinalTranscription = onFinalTranscription
        this.onError = onError
        Log.d("VoskHelper", "📞 STT callbacks updated - finalTranscription: ${onFinalTranscription != null}, error: ${onError != null}")
    }
    
    // Safe method to clear callbacks only if they match (to prevent race conditions)
    fun clearCallbacksIfMatching(
        expectedFinalTranscription: ((String) -> Unit)?,
        expectedError: ((String) -> Unit)?
    ) {
        if (this.onFinalTranscription == expectedFinalTranscription && this.onError == expectedError) {
            this.onFinalTranscription = null
            this.onError = null
            Log.d("VoskHelper", "🧹 STT callbacks safely cleared - callbacks matched expected")
        } else {
            Log.d("VoskHelper", "⚠️ STT callback clear skipped - callbacks don't match (probably already replaced by another screen)")
        }
    }
    
    // Check if the model is ready for use
    fun isModelReady(): Boolean = model != null

    fun initModel() {
        StorageService.unpack(context, "vosk-model-small-en-in-0.4", "model",
            { model ->
                this.model = model
            },
            { exception ->
                onError?.invoke("Failed to unpack the model: ${exception.message}")
            }
        )
    }

    fun startRecording() {
        if (model == null) {
            onError?.invoke("Model is not ready")
            return
        }

        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: IOException) {
            onError?.invoke("Error starting recording: ${e.message}")
        }
    }

    fun stopRecording() {
        speechService?.stop()
        speechService = null
    }

    override fun onResult(hypothesis: String?) {
//        try {
//            // Check if the hypothesis is null or empty
//            if (hypothesis.isNullOrEmpty()) {
//                onError("Empty or null hypothesis received")
//                return
//            }
//
//            // Pass the result to the callback
//            onResultCallback(hypothesis)
//        } catch (e: Exception) {
//            // Handle any unexpected exceptions
//            Log.e("VoskHelper", "Error in onResult: ${e.message}", e)
//            onError("Error processing result: ${e.message}")
//        }
    }

    override fun onPartialResult(hypothesis: String?) {
//        hypothesis?.let { result ->
//            try {
//                // Parse the JSON string
//                val json = org.json.JSONObject(result)
//                val partialResult = json.optString("partial", "")
//
//                // Limit the size of the result (e.g., keep only the last 1000 characters)
//                val truncatedResult = if (partialResult.length > 1000) {
//                    partialResult.takeLast(1000)
//                } else {
//                    partialResult
//                }
//
//                // Pass the truncated result to the callback
//                if (truncatedResult.isNotEmpty()) {
//                    onResultCallback(truncatedResult)
//                }
//            } catch (e: org.json.JSONException) {
//                Log.e("VoskHelper", "Failed to parse partial result: $result", e)
//                onError("Failed to parse partial result")
//            }
//        }
    }

    override fun onFinalResult(hypothesis: String?) {
        hypothesis?.let { result ->
            try {
                // Parse the JSON string to extract the final transcription
                val json = JSONObject(result)
                val finalTranscription = json.optString("text", "")

                // Pass the final transcription to the callback
                if (finalTranscription.isNotEmpty()) {
                    onFinalTranscription?.invoke(finalTranscription)
                }
            } catch (e: Exception) {
                Log.e("VoskHelper", "Failed to parse final result: $result", e)
                onError?.invoke("Failed to parse final result")
            }
        }
    }

    override fun onError(exception: Exception?) {
        exception?.let { e ->
            Log.e("VoskHelper", "Recognition error: ${e.message}")
            onError?.invoke("Error: ${e.message}")
            stopRecording() // Stop the recognition process
        }
    }

    override fun onTimeout() {
        stopRecording()
    }
}