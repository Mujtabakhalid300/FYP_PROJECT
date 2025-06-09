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
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer

/** AR Camera Renderer that integrates depth estimation and object detection */
class ARCameraRenderer(
    private val activity: androidx.activity.ComponentActivity,
    private val onCaptureCallback: (Bitmap?) -> Unit
) : SampleRender.Renderer, DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "ARCameraRenderer"
        private const val Z_NEAR = 0.1f
        private const val Z_FAR = 100f
    }

    lateinit var render: SampleRender
    lateinit var backgroundRenderer: BackgroundRenderer
    private var hasSetTextureNames = false

    // Object detection components
    private lateinit var liteRTDetector: LiteRTYoloDetector
    private lateinit var frameProcessor: FrameProcessor
    private var isDetectorInitialized = false
    private var frameCounter = 0
    private val detectionInterval = 10 // Run detection every N frames
    private var detectorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Depth data for object detection
    private var currentDepthData: DepthData? = null

    // Detection results
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentDetectionResults: List<DetectionWithDepth>? = null
    
    // Capture state
    private var shouldCapture = false

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

    // Session reference through activity
    var arCoreSessionHelper: com.example.mnn_llm_test.arcore.common.helpers.ARCoreSessionLifecycleHelper? = null
    val session get() = arCoreSessionHelper?.session
    val displayRotationHelper = DisplayRotationHelper(activity)
    val trackingStateHelper = TrackingStateHelper(activity)

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        hasSetTextureNames = false
        
        // Clear any stale detection results
        currentDetectionResults = null
        frameCounter = 0
        
        Log.d(TAG, "🚀 AR Camera resumed - initializing detector")
        
        // Initialize object detector
        initializeLiteRTDetector()
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
        
        // Clear detection state
        currentDetectionResults = null
        currentDepthData = null
        
        // Clean up detector
        detectorScope.cancel()
        if (::liteRTDetector.isInitialized) {
            liteRTDetector.close()
            isDetectorInitialized = false
        }
        
        Log.d(TAG, "⏸️ AR Camera paused - detector cleanup complete")
    }

    /**
     * Initialize LiteRT object detector
     */
    private fun initializeLiteRTDetector() {
        try {
            if (detectorScope.isActive) {
                detectorScope.cancel()
            }
            
            val newDetectorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            
            liteRTDetector = LiteRTYoloDetector(activity)
            frameProcessor = FrameProcessor()
            
            Log.d(TAG, "Starting YOLO detector initialization...")
            
            newDetectorScope.launch {
                try {
                    isDetectorInitialized = liteRTDetector.initialize()
                    if (isDetectorInitialized) {
                        Log.d(TAG, "✅ LiteRT detector initialized successfully")
                    } else {
                        Log.w(TAG, "❌ Failed to initialize LiteRT detector")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error during detector initialization", e)
                    isDetectorInitialized = false
                }
            }
            
            this.detectorScope = newDetectorScope
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up LiteRT detector", e)
            isDetectorInitialized = false
        }
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

            // Run object detection
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
     * Process object detection using LiteRT
     */
    private fun processObjectDetection(frame: Frame) {
        frameCounter++
        if (frameCounter % detectionInterval != 0 || !isDetectorInitialized) {
            return
        }

        val depthDataSnapshot = currentDepthData

        detectorScope.launch {
            try {
                val bitmap = frameProcessor.frameToBitmap(frame)
                if (bitmap != null) {
                    val detections = liteRTDetector.detectObjects(bitmap)
                    
                    if (detections.isNotEmpty()) {
                        val depthInfo = getDepthInfoForDetections(detections, depthDataSnapshot)
                        
                        val detectionsWithDepth = detections.mapIndexed { index, detection ->
                            val depth = depthInfo?.getOrNull(index) ?: 0
                            DetectionWithDepth(detection, depth)
                        }
                        
                        withContext(Dispatchers.Main) {
                            currentDetectionResults = detectionsWithDepth
                            
                            // Enhanced logging for debugging
                            Log.d(TAG, "🎯 Detected ${detections.size} objects with depth info:")
                            detectionsWithDepth.forEachIndexed { index, detectionWithDepth ->
                                val detection = detectionWithDepth.detection
                                val distance = detectionWithDepth.distance
                                Log.d(TAG, "  [$index] Object: ${detection.className} (${String.format("%.2f", detection.confidence * 100)}%) " +
                                          "at distance: ${distance}mm (${String.format("%.1f", distance / 1000.0)}m) " +
                                          "bbox: [${detection.x.toInt()}, ${detection.y.toInt()}, ${detection.width.toInt()}, ${detection.height.toInt()}]")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            currentDetectionResults = null
                            // Log when no objects detected (less frequently to avoid spam)
                            if (frameCounter % 60 == 0) {
                                Log.d(TAG, "🔍 No objects detected in current frame")
                            }
                        }
                    }
                    
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detection error: ${e.message}")
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
     * Capture current frame as bitmap
     */
    private fun captureFrame() {
        try {
            // Get surface dimensions from OpenGL viewport
            val viewport = IntArray(4)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
            val width = viewport[2]
            val height = viewport[3]
            
            val buffer = ByteBuffer.allocateDirect(width * height * 4)
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            
            val pixelArray = IntArray(width * height)
            buffer.rewind()
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = buffer.get().toInt() and 0xFF
                    val g = buffer.get().toInt() and 0xFF
                    val b = buffer.get().toInt() and 0xFF
                    val a = buffer.get().toInt() and 0xFF
                    
                    val flippedY = height - 1 - y
                    pixelArray[flippedY * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            val bitmap = Bitmap.createBitmap(pixelArray, width, height, Bitmap.Config.ARGB_8888)
            
            mainHandler.post {
                onCaptureCallback(bitmap)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame", e)
            mainHandler.post {
                onCaptureCallback(null)
            }
        }
    }
} 