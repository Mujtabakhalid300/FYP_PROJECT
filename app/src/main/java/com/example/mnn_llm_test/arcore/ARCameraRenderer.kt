package com.example.mnn_llm_test.arcore

import android.graphics.Bitmap
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.example.mnn_llm_test.arcore.common.helpers.DisplayRotationHelper
import com.example.mnn_llm_test.arcore.common.helpers.TrackingStateHelper
import com.example.mnn_llm_test.arcore.common.samplerender.SampleRender
import com.example.mnn_llm_test.arcore.common.samplerender.arcore.BackgroundRenderer
import com.example.mnn_llm_test.arcore.ml.LiteRTYoloDetector
import com.example.mnn_llm_test.arcore.ml.FrameProcessor
import com.example.mnn_llm_test.MainActivity
import com.example.mnn_llm_test.utils.TtsHelper
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.os.Environment

/** AR Camera Renderer that integrates depth estimation and object detection */
class ARCameraRenderer(
    private val activity: androidx.activity.ComponentActivity,
    private val onCaptureCallback: (Bitmap?) -> Unit,
    private val onDetectionUpdate: ((List<DetectionWithDepth>) -> Unit)? = null
) : DefaultLifecycleObserver, SampleRender.Renderer {
    
    companion object {
        private const val TAG = "ARCameraRenderer"
        private const val Z_NEAR = 0.1f
        private const val Z_FAR = 100f
    }

    // Core rendering components
    lateinit var render: SampleRender
    lateinit var backgroundRenderer: BackgroundRenderer
    private var hasSetTextureNames = false

    // Object detection
    private lateinit var frameProcessor: FrameProcessor
    private var frameCounter = 0
    private val detectionInterval = 10 // Run detection every N frames
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Session reference through activity
    var arCoreSessionHelper: com.example.mnn_llm_test.arcore.common.helpers.ARCoreSessionLifecycleHelper? = null
    val session get() = arCoreSessionHelper?.session
    val displayRotationHelper = DisplayRotationHelper(activity)
    val trackingStateHelper = TrackingStateHelper(activity)
    
    // Depth data and detection results
    private var currentDepthData: DepthData? = null
    var currentDetectionResults: List<DetectionWithDepth>? = null
    private var currentFrame: Frame? = null
    
    // State management
    private var shouldCapture = false
    private var isCameraActiveForRendering = true
    private var glSurfaceView: android.opengl.GLSurfaceView? = null
    
    // UI and accessibility
    private val mainHandler = Handler(Looper.getMainLooper())
    // TTS helper accessed globally - no local instance needed
    
    // Scene change detection
    private var lastDetectionCount = 0

    data class DepthData(
        val buffer: ByteArray,
        val width: Int,
        val height: Int,
        val pixelStride: Int,
        val rowStride: Int
    )

    data class DetectionWithDepth(
        val detection: LiteRTYoloDetector.Detection,
        val distance: Int // Distance in millimeters
    )

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        hasSetTextureNames = false
        
        // Clear any stale detection results
        currentDetectionResults = null
        frameCounter = 0
        
        Log.d(TAG, "🚀 AR Camera resumed")
        
        // 🎯 Simple initialization - just create frame processor
        frameProcessor = FrameProcessor()
        
        // Check if global YOLO detector is ready
        val globalDetector = MainActivity.globalYoloDetector
        if (globalDetector != null) {
            Log.d(TAG, "✅ Global YOLO detector available and ready")
        } else {
            Log.w(TAG, "⚠️ Global YOLO detector not yet available")
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
        
        Log.d(TAG, "⏸️ AR Camera pausing")
        
        // 🎯 Simple cleanup - no complex detector management needed
        currentDetectionResults = null
        currentDepthData = null
        currentFrame = null
        
        // Clear overlay
        mainHandler.post {
            onDetectionUpdate?.invoke(emptyList())
        }
        
        Log.d(TAG, "✅ AR Camera paused - simple cleanup complete")
    }

    // SampleRender.Renderer interface implementation
    override fun onSurfaceCreated(render: SampleRender) {
        this.render = render
        try {
            backgroundRenderer = BackgroundRenderer(render)
            backgroundRenderer.setUseDepthVisualization(render, false)
            Log.d(TAG, "OpenGL surface created and initialized")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read a required asset file", e)
        }
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        // Early exit if camera is not active for rendering
        if (!isCameraActiveForRendering) {
            Log.d(TAG, "⚡ onDrawFrame: Camera inactive, skipping frame")
            return
        }
        
        val session = session ?: return

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
            hasSetTextureNames = true
        }

        displayRotationHelper.updateSessionIfNeeded(session)

        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during onDrawFrame", e)
            return
        } catch (e: com.google.ar.core.exceptions.SessionPausedException) {
            Log.d(TAG, "Session is paused, skipping frame update")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during session update", e)
            return
        }

        // Store current frame for capture
        currentFrame = frame

        val camera = frame.camera

        backgroundRenderer.updateDisplayGeometry(frame)
        trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

        if (camera.trackingState == TrackingState.TRACKING) {
            // Process depth information
            try {
                val depthImage = frame.acquireDepthImage16Bits()
                val width = depthImage.width
                val height = depthImage.height
                
                val plane = depthImage.planes[0]
                val buffer = plane.buffer
                val depthArray = ByteArray(buffer.remaining())
                buffer.rewind()
                buffer.get(depthArray)
                
                currentDepthData = DepthData(depthArray, width, height, plane.pixelStride, plane.rowStride)
                
                // Enhanced depth logging every 30 frames to avoid spam
                if (frameCounter % 30 == 0) {
                    Log.d(TAG, "📏 Depth data updated: ${width}x${height}, stride: ${plane.rowStride}, pixel stride: ${plane.pixelStride}")
                }
                depthImage.close()
            } catch (e: NotYetAvailableException) {
                currentDepthData = null
            }

            // 🎯 Simplified object detection
            processObjectDetection(frame)

            // Handle capture request
            if (shouldCapture) {
                captureFrame()
                shouldCapture = false
            }
        }

        // Draw background
        if (frame.timestamp != 0L) {
            backgroundRenderer.drawBackground(render)
        }

        if (camera.trackingState == TrackingState.PAUSED) {
            return
        }
    }

    /**
     * 🎯 Simplified object detection using global YOLO detector
     */
    private fun processObjectDetection(frame: Frame) {
        frameCounter++
        
        // Skip if not time for detection or camera not active
        if (frameCounter % detectionInterval != 0 || !isCameraActiveForRendering) {
            return
        }

        // Check if global detector is available
        val globalDetector = MainActivity.globalYoloDetector
        if (globalDetector == null) {
            Log.d(TAG, "🔄 Global YOLO detector not ready yet, skipping detection")
            return
        }

        val depthDataSnapshot = currentDepthData

        // 🚀 Simple async detection - no complex scope management
        detectionScope.launch {
            try {
                // Single activity check at start
                if (!isActive || !isCameraActiveForRendering) return@launch
                
                val bitmap = frameProcessor.frameToBitmap(frame)
                if (bitmap != null) {
                    
                    // 🎯 Use global detector - no initialization needed!
                    val detections = globalDetector.detectObjects(bitmap)
                    
                    if (detections.isNotEmpty()) {
                        val depthInfo = getDepthInfoForDetections(detections, depthDataSnapshot)
                        
                        val detectionsWithDepth = detections.mapIndexed { index, detection ->
                            val depth = depthInfo?.getOrNull(index) ?: 0
                            DetectionWithDepth(detection, depth)
                        }
                        
                        withContext(Dispatchers.Main) {
                            // Final check before UI update
                            if (isCameraActiveForRendering) {
                                currentDetectionResults = detectionsWithDepth
                                onDetectionUpdate?.invoke(detectionsWithDepth)
                                
                                // Check for scene changes and announce
                                checkSceneChange(detectionsWithDepth)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (isCameraActiveForRendering) {
                                currentDetectionResults = emptyList()
                                onDetectionUpdate?.invoke(emptyList())
                                
                                // Check for scene changes (no detections)
                                checkSceneChange(emptyList())
                                
                                // Log when no objects detected (less frequently to avoid spam)
                                if (frameCounter % 60 == 0) {
                                    Log.d(TAG, "🔍 No objects detected in current frame")
                                }
                            }
                        }
                    }
                    
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                // Only log if it's not a cancellation (which is expected during cleanup)
                if (e !is kotlinx.coroutines.CancellationException && isActive) {
                    Log.e(TAG, "Detection error: ${e.message}")
                }
            }
        }
    }

    /**
     * Get depth information for each detection
     */
    private fun getDepthInfoForDetections(
        detections: List<LiteRTYoloDetector.Detection>, 
        depthDataSnapshot: DepthData?
    ): List<Int>? {
        if (depthDataSnapshot == null) {
            return null
        }
        
        return try {
            detections.map { detection ->
                val centerX = detection.x + detection.width / 2
                val centerY = detection.y + detection.height / 2
                
                val depthPixelX = (centerX * depthDataSnapshot.width).toInt()
                    .coerceIn(0, depthDataSnapshot.width - 1)
                val depthPixelY = (centerY * depthDataSnapshot.height).toInt()
                    .coerceIn(0, depthDataSnapshot.height - 1)
                
                getMillimetersDepth(depthDataSnapshot, depthPixelX, depthPixelY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting depth info", e)
            null
        }
    }

    /**
     * Get depth in millimeters at specific pixel coordinates
     */
    private fun getMillimetersDepth(depthData: DepthData, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= depthData.width || y >= depthData.height) {
            return 0
        }
        
        val depthByteIndex = y * depthData.rowStride + x * depthData.pixelStride
        if (depthByteIndex + 1 >= depthData.buffer.size) {
            return 0
        }
        
        // Depth is stored as 16-bit values in millimeters
        val depthSample = ((depthData.buffer[depthByteIndex + 1].toInt() and 0xFF) shl 8) or
                         (depthData.buffer[depthByteIndex].toInt() and 0xFF)
        
        return if (depthSample == 0) 0 else depthSample
    }

    /**
     * Trigger capture on next frame
     */
    fun requestCapture() {
        shouldCapture = true
    }

    /**
     * Capture current frame as bitmap using ARCore camera frame
     */
    private fun captureFrame() {
        try {
            Log.d(TAG, "📸 Capturing ARCore camera frame...")
            
            // Use the current frame that's being processed
            currentFrame?.let { frame ->
                val bitmap = frameProcessor.frameToBitmap(frame)
                if (bitmap != null) {
                    Log.d(TAG, "✅ Successfully captured camera frame: ${bitmap.width}x${bitmap.height}")
                    mainHandler.post {
                        onCaptureCallback(bitmap)
                    }
                } else {
                    Log.e(TAG, "❌ Failed to convert frame to bitmap")
                    mainHandler.post {
                        onCaptureCallback(null)
                    }
                }
            } ?: run {
                Log.e(TAG, "❌ No current frame available for capture")
                mainHandler.post {
                    onCaptureCallback(null)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturing frame", e)
            mainHandler.post {
                onCaptureCallback(null)
            }
        }
    }

    /**
     * Save YOLO input image to device storage for debugging
     */
    private fun saveYoloInputImage(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val filename = "YOLO_input_$timestamp.jpg"
            
            // Create NewYOLODebug directory in external storage
            val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NewYOLODebug")
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            
            val imageFile = File(picturesDir, filename)
            
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            
            // Make image visible in gallery by scanning it
            MediaScannerConnection.scanFile(
                activity,
                arrayOf(imageFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            
            Log.d(TAG, "💾 Saved YOLO input image: ${imageFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving YOLO input image", e)
        }
    }
    
    /**
     * Set GLSurfaceView reference for render mode control
     */
    fun setGLSurfaceView(surfaceView: android.opengl.GLSurfaceView) {
        this.glSurfaceView = surfaceView
    }
    
    /**
     * Set camera active state for immediate control over rendering
     */
    fun setCameraActive(active: Boolean) {
        Log.d(TAG, if (active) "🟢 Renderer: Camera activated" else "🔴 Renderer: Camera deactivated")
        isCameraActiveForRendering = active
        
        if (!active) {
            // Immediately stop all detection processing
            currentDetectionResults = null
            currentDepthData = null
            currentFrame = null
            
            // Stop any ongoing TTS announcements
            MainActivity.globalTtsHelper?.stop()
            
            // 🎯 No complex scope cancellation needed - just stop processing
            Log.d(TAG, "🛑 Detection processing stopped")
            
            // Clear overlay
            mainHandler.post {
                onDetectionUpdate?.invoke(emptyList())
            }
            
            // Change GLSurfaceView to render on demand to reduce frame calls
            glSurfaceView?.let { surfaceView ->
                activity.runOnUiThread {
                    surfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    Log.d(TAG, "🛑 GLSurfaceView set to RENDERMODE_WHEN_DIRTY")
                }
            }
            
            Log.d(TAG, "🛑 Camera renderer fully stopped - no more frame processing")
        } else {
            // Resume continuous rendering
            glSurfaceView?.let { surfaceView ->
                activity.runOnUiThread {
                    surfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    Log.d(TAG, "🚀 GLSurfaceView set to RENDERMODE_CONTINUOUSLY")
                }
            }
            
            Log.d(TAG, "🚀 Camera renderer activated - resuming frame processing")
        }
    }
    
    /**
     * Check for scene changes and announce detection count
     */
    private fun checkSceneChange(currentDetections: List<DetectionWithDepth>) {
        val currentCount = currentDetections.size
        
        if (currentCount != lastDetectionCount && isCameraActiveForRendering) {
            val announcement = if (currentCount == 0) {
                "No detections"
            } else if (currentCount == 1) {
                "1 detection"
            } else {
                "$currentCount detections"
            }
            
            Log.d(TAG, "Scene change: $lastDetectionCount -> $currentCount objects")
            
            // Update count immediately to prevent duplicate announcements
            lastDetectionCount = currentCount
            
            // 🟢 LOW priority - these background announcements have completion pending
            // (won't interrupt each other, will skip if another LOW priority is active)
            MainActivity.globalTtsHelper?.speak(announcement, TtsHelper.Priority.LOW) {
                Log.d(TAG, "Background TTS finished, ready for next scene change detection")
            }
        }
    }
    
    /**
     * Announce all objects organized by left and right side of screen
     */
    fun announceObjectsOnSide(tapX: Float, screenWidth: Float, detections: List<DetectionWithDepth>) {
        // 🔴 HIGH priority - user-triggered tap announcements
        // Will interrupt any ongoing speech, but if another HIGH priority is playing, 
        // will stop it without starting new one (handled by TtsHelper logic)
        
        Log.d(TAG, "🎤 Single tap detected - requesting HIGH priority TTS announcement")
        
        if (detections.isEmpty()) {
            MainActivity.globalTtsHelper?.speak("No objects of interest present in the scenery. Try moving your camera around to explore different areas.", TtsHelper.Priority.HIGH)
            return
        }
        
        // Split detections into left and right sides - simple center point method
        val leftDetections = detections.filter { detection ->
            val bbox = detection.detection
            val centerX = bbox.x + bbox.width / 2
            centerX < 0.5f  // Left half of screen
        }.sortedBy { it.distance }
        
        val rightDetections = detections.filter { detection ->
            val bbox = detection.detection
            val centerX = bbox.x + bbox.width / 2
            centerX >= 0.5f  // Right half of screen
        }.sortedBy { it.distance }
        
        val announcement = buildString {
            // Announce left side objects first
            if (leftDetections.isNotEmpty()) {
                append("Objects to your left: ")
                leftDetections.forEachIndexed { index, detection ->
                    if (index > 0) append(", ")
                    
                    val distanceText = if (detection.distance > 0) {
                        if (detection.distance < 1000) {
                            "${detection.distance} millimeters"
                        } else {
                            val meters = detection.distance / 1000f
                            "${String.format("%.1f", meters)} meters"
                        }
                    } else {
                        "unknown distance"
                    }
                    
                    append("${detection.detection.className} at $distanceText")
                }
            }
            
            // Add separator if both sides have objects
            if (leftDetections.isNotEmpty() && rightDetections.isNotEmpty()) {
                append(". ")
            }
            
            // Announce right side objects
            if (rightDetections.isNotEmpty()) {
                append("Objects to your right: ")
                rightDetections.forEachIndexed { index, detection ->
                    if (index > 0) append(", ")
                    
                    val distanceText = if (detection.distance > 0) {
                        if (detection.distance < 1000) {
                            "${detection.distance} millimeters"
                        } else {
                            val meters = detection.distance / 1000f
                            "${String.format("%.1f", meters)} meters"
                        }
                    } else {
                        "unknown distance"
                    }
                    
                    append("${detection.detection.className} at $distanceText")
                }
            }
            
            // Handle case where all objects are on one side
            if (leftDetections.isEmpty() && rightDetections.isNotEmpty()) {
                // Already handled above
            } else if (rightDetections.isEmpty() && leftDetections.isNotEmpty()) {
                // Already handled above
            }
        }
        
        Log.d(TAG, "🎤 HIGH priority announcement from tap: $announcement")
        MainActivity.globalTtsHelper?.speak(announcement, TtsHelper.Priority.HIGH)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // TTS is now global - don't shutdown here, will be handled by MainActivity
    }
} 