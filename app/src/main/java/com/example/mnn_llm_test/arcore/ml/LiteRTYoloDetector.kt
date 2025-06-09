/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.mnn_llm_test.arcore.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

// TensorFlow Lite imports (bridge to LiteRT)
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

/**
 * LiteRT-based YOLO Object Detection with improved buffer management
 * This version fixes the buffer corruption issue through proper memory management
 * Uses CPU-only execution for maximum stability and compatibility
 */
class LiteRTYoloDetector(private val context: Context) {
    companion object {
        private const val TAG = "LiteRTYoloDetector"
        private const val MODEL_FILENAME = "yolo11s_float32.tflite"
        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 80
        private const val NUM_THREADS = 4
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val IOU_THRESHOLD = 0.5f
        
        // Buffer refresh interval to prevent corruption (more frequent for larger models)
        private const val BUFFER_REFRESH_INTERVAL = 25
        
        // COCO class names
        val COCO_CLASSES = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isModelInitialized = false
    private var inferenceCount = 0
    
    // Improved buffer management
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Buffer sizes
    private val inputBufferSize = 4 * INPUT_SIZE * INPUT_SIZE * 3
    private var outputBufferSize = 0
    
    // Enhanced logging for GPU/CPU tracking
    private var isUsingGpu = false
    private var backendInfo = "Unknown"
    
    // Coordinate transformation parameters for letterboxing
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var offsetX: Float = 0.0f
    private var offsetY: Float = 0.0f
    
    data class Detection(
        val classId: Int,
        val className: String,
        val confidence: Float,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelExists = try {
                context.assets.open(MODEL_FILENAME).use { true }
            } catch (e: IOException) {
                false
            }
            
            if (!modelExists) {
                Log.w(TAG, "YOLO model file '$MODEL_FILENAME' not found in assets.")
                return@withContext false
            }
            
            val modelFile = FileUtil.loadMappedFile(context, MODEL_FILENAME)
            
            // Initialize GPU delegate with proper compatibility checking
            val compatList = CompatibilityList()
            val options = Interpreter.Options().apply {
                setNumThreads(NUM_THREADS)
                
                if (compatList.isDelegateSupportedOnThisDevice) {
                    // GPU acceleration available - use the best options for this device
                    try {
                        val delegateOptions = compatList.bestOptionsForThisDevice
                        gpuDelegate = GpuDelegate(delegateOptions)
                        addDelegate(gpuDelegate!!)
                        Log.d(TAG, "GPU delegate added successfully - hardware acceleration enabled")
                        isUsingGpu = true
                        backendInfo = "GPU"
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to initialize GPU delegate, falling back to CPU: ${e.message}")
                        gpuDelegate?.close()
                        gpuDelegate = null
                        isUsingGpu = false
                        backendInfo = "CPU"
                        // CPU fallback is automatic if GPU delegate fails
                    }
                } else {
                    // GPU not supported - use multi-threaded CPU execution
                    Log.d(TAG, "GPU delegate not supported on this device, using CPU with $NUM_THREADS threads")
                    isUsingGpu = false
                    backendInfo = "CPU"
                }
                
                // Disable NNAPI as it's deprecated and can cause issues
                setUseNNAPI(false)
            }
            
            interpreter = Interpreter(modelFile, options)
            initializeBuffers()
            
            isModelInitialized = true
            Log.d(TAG, "LiteRT YOLO model initialized successfully")
            
            // Log final backend configuration
            Log.i(TAG, "🚀 YOLO Detector Ready - Backend: $backendInfo | GPU Enabled: $isUsingGpu")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT YOLO model", e)
            cleanup()
            false
        }
    }
    
    private fun initializeBuffers() {
        val outputShape = interpreter!!.getOutputTensor(0).shape()
        outputBufferSize = 4 * outputShape[1] * outputShape[2]
        
        inputBuffer = ByteBuffer.allocateDirect(inputBufferSize).apply {
            order(ByteOrder.nativeOrder())
        }
        
        outputBuffer = ByteBuffer.allocateDirect(outputBufferSize).apply {
            order(ByteOrder.nativeOrder())
        }
    }
    
    suspend fun detectObjects(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        if (!isModelInitialized || interpreter == null) {
            return@withContext emptyList<Detection>()
        }
        
        try {
            inferenceCount++
            
            if (inferenceCount % BUFFER_REFRESH_INTERVAL == 0) {
                refreshBuffers()
            }
            
            // Check if buffers are still available after refresh
            if (inputBuffer == null || outputBuffer == null) {
                Log.w(TAG, "❌ Buffers not properly initialized after refresh")
                return@withContext emptyList<Detection>()
            }
            
            preprocessImage(bitmap)
            runInference()
            return@withContext parseResults()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during detection", e)
            return@withContext emptyList()
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap) {
        // Check if buffers are properly initialized
        if (inputBuffer == null) {
            Log.w(TAG, "❌ Input buffer is null, skipping preprocessing")
            return
        }
        
        Log.d(TAG, "📸 Preprocessing frame $inferenceCount - Input: ${bitmap.width}x${bitmap.height} -> Output: ${INPUT_SIZE}x${INPUT_SIZE}")
        
        // Create letterboxed image with proper aspect ratio
        val letterboxedBitmap = createLetterboxedBitmap(bitmap)
        
        // CRITICAL: Clear buffer properly to prevent corruption
        inputBuffer!!.clear()
        inputBuffer!!.position(0)
        
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        letterboxedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f  
            val b = (pixel and 0xFF) / 255.0f
            
            inputBuffer!!.putFloat(r)
            inputBuffer!!.putFloat(g)
            inputBuffer!!.putFloat(b)
        }
        
        inputBuffer!!.position(0)
        letterboxedBitmap.recycle()
    }
    
    private fun createLetterboxedBitmap(originalBitmap: Bitmap): Bitmap {
        val originalWidth = originalBitmap.width.toFloat()
        val originalHeight = originalBitmap.height.toFloat()
        
        // Calculate scaling factors to maintain aspect ratio
        val scale = minOf(INPUT_SIZE.toFloat() / originalWidth, INPUT_SIZE.toFloat() / originalHeight)
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        // Store transformation parameters for coordinate conversion later
        scaleX = scaledWidth.toFloat() / INPUT_SIZE
        scaleY = scaledHeight.toFloat() / INPUT_SIZE
        offsetX = (INPUT_SIZE - scaledWidth) / 2.0f / INPUT_SIZE
        offsetY = (INPUT_SIZE - scaledHeight) / 2.0f / INPUT_SIZE
        
        Log.d(TAG, "🔧 Letterboxing: scale=$scale, offset=($offsetX,$offsetY), scaled=${scaledWidth}x${scaledHeight}")
        
        // Create letterboxed image with proper aspect ratio
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
        val letterboxedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        
        // Fill with black background (standard for YOLO models)
        val canvas = Canvas(letterboxedBitmap)
        canvas.drawColor(Color.BLACK) // Black padding is standard for YOLO
        
        // Draw scaled image centered
        val left = (INPUT_SIZE - scaledWidth) / 2
        val top = (INPUT_SIZE - scaledHeight) / 2
        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
        
        // Log aspect ratio info
        val originalAspectRatio = originalWidth / originalHeight
        val targetAspectRatio = 1.0f // Square (640x640)
        if (kotlin.math.abs(originalAspectRatio - targetAspectRatio) > 0.1f) {
            Log.i(TAG, "✅ Letterboxing applied! Original aspect: ${String.format("%.2f", originalAspectRatio)}, preserved with padding")
        }
        
        scaledBitmap.recycle()
        
        return letterboxedBitmap
    }
    
    private fun runInference() {
        outputBuffer!!.clear()
        outputBuffer!!.position(0)
        
        interpreter!!.run(inputBuffer!!, outputBuffer!!)
        
        outputBuffer!!.position(0)
    }
    
    private fun refreshBuffers() {
        Log.d(TAG, "Refreshing buffers (inference: $inferenceCount)")
        
        // Log performance status periodically
        if (inferenceCount % 50 == 0) {
            Log.i(TAG, "📊 Performance Status - Inference #$inferenceCount | Backend: $backendInfo | GPU: $isUsingGpu")
        }
        
        inputBuffer = ByteBuffer.allocateDirect(inputBufferSize).apply {
            order(ByteOrder.nativeOrder())
        }
        
        outputBuffer = ByteBuffer.allocateDirect(outputBufferSize).apply {
            order(ByteOrder.nativeOrder())
        }
    }
    
    private fun parseResults(): List<Detection> {
        val detections = mutableListOf<Detection>()
        
        outputBuffer!!.position(0)
        
        val outputShape = interpreter!!.getOutputTensor(0).shape()
        val numFeatures = outputShape[1]
        val numDetections = outputShape[2]
        
        for (i in 0 until numDetections) {
            val centerX = outputBuffer!!.getFloat((0 * numDetections + i) * 4)
            val centerY = outputBuffer!!.getFloat((1 * numDetections + i) * 4)
            val width = outputBuffer!!.getFloat((2 * numDetections + i) * 4)
            val height = outputBuffer!!.getFloat((3 * numDetections + i) * 4)
            
            var maxClassScore = 0f
            var classId = 0
            
            for (j in 4 until numFeatures) {
                val classScore = outputBuffer!!.getFloat((j * numDetections + i) * 4)
                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    classId = j - 4
                }
            }
            
            if (maxClassScore >= CONFIDENCE_THRESHOLD && classId < NUM_CLASSES) {
                // Convert from letterboxed coordinates back to original image coordinates
                val originalCenterX = (centerX - offsetX) / scaleX
                val originalCenterY = (centerY - offsetY) / scaleY
                val originalWidth = width / scaleX
                val originalHeight = height / scaleY
                
                val x = originalCenterX - originalWidth / 2
                val y = originalCenterY - originalHeight / 2
                
                // Ensure coordinates are within bounds [0, 1]
                val clampedX = x.coerceIn(0f, 1f)
                val clampedY = y.coerceIn(0f, 1f)
                val clampedWidth = originalWidth.coerceIn(0f, 1f - clampedX)
                val clampedHeight = originalHeight.coerceIn(0f, 1f - clampedY)
                
                detections.add(
                    Detection(
                        classId = classId,
                        className = COCO_CLASSES[classId],
                        confidence = maxClassScore,
                        x = clampedX,
                        y = clampedY,
                        width = clampedWidth,
                        height = clampedHeight
                    )
                )
            }
        }
        
        Log.d(TAG, "🎯 Parsed ${detections.size} detections with letterbox coordinate conversion")
        return applyNMS(detections)
    }
    
    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val finalDetections = mutableListOf<Detection>()
        
        for (detection in sortedDetections) {
            var keep = true
            
            for (finalDetection in finalDetections) {
                if (detection.classId == finalDetection.classId) {
                    val iou = calculateIoU(detection, finalDetection)
                    if (iou > IOU_THRESHOLD) {
                        keep = false
                        break
                    }
                }
            }
            
            if (keep) {
                finalDetections.add(detection)
            }
        }
        
        return finalDetections
    }
    
    private fun calculateIoU(det1: Detection, det2: Detection): Float {
        val x1 = max(det1.x, det2.x)
        val y1 = max(det1.y, det2.y)
        val x2 = kotlin.math.min(det1.x + det1.width, det2.x + det2.width)
        val y2 = kotlin.math.min(det1.y + det1.height, det2.y + det2.height)
        
        val intersectionArea = max(0f, x2 - x1) * max(0f, y2 - y1)
        val area1 = det1.width * det1.height
        val area2 = det2.width * det2.height
        val unionArea = area1 + area2 - intersectionArea
        
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
    
    private fun cleanup() {
        inputBuffer?.clear()
        outputBuffer?.clear()
        inputBuffer = null
        outputBuffer = null
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        coroutineScope.cancel()
    }
    
    // Public method to get current backend status
    fun getBackendInfo(): String {
        return if (isModelInitialized) {
            "Backend: $backendInfo | GPU Acceleration: ${if (isUsingGpu) "✅ Enabled" else "❌ Disabled"}"
        } else {
            "Model not initialized"
        }
    }
    
    fun close() {
        cleanup()
        isModelInitialized = false
        Log.d(TAG, "LiteRT YOLO detector closed")
    }
} 