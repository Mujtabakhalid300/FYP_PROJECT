/*
 * Copyright 2021 Google LLC
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
package com.example.mnn_llm_test.arcore

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.mnn_llm_test.arcore.ml.LiteRTYoloDetector

/**
 * Custom view for drawing LiteRT YOLO bounding boxes with object names and distances
 */
class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detections: List<ARCameraRenderer.DetectionWithDepth> = emptyList()
    private val boundingBoxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.GREEN
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 0, 128, 0) // Semi-transparent green
        isAntiAlias = true
    }

    /**
     * Update the detections to be displayed
     */
    fun updateDetections(newDetections: List<ARCameraRenderer.DetectionWithDepth>) {
        detections = newDetections
        invalidate() // Trigger redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth <= 0 || viewHeight <= 0) return

        // Draw each detection
        for (detectionWithDepth in detections) {
            val detection = detectionWithDepth.detection
            val distance = detectionWithDepth.distance
            
            // Convert normalized coordinates [0,1] to screen coordinates
            val left = detection.x * viewWidth
            val top = detection.y * viewHeight
            val right = (detection.x + detection.width) * viewWidth
            val bottom = (detection.y + detection.height) * viewHeight
            
            // Draw bounding box
            canvas.drawRect(left, top, right, bottom, boundingBoxPaint)
            
            // Prepare label text
            val distanceText = if (distance > 0) {
                if (distance < 1000) "${distance}mm" else "${String.format("%.1f", distance / 1000f)}m"
            } else ""
            
            val labelText = if (distanceText.isNotEmpty()) {
                "${detection.className} - $distanceText"
            } else {
                detection.className
            }
            val confidence = String.format("%.1f%%", detection.confidence * 100)
            
            // Measure text dimensions
            val labelBounds = Rect()
            textPaint.getTextBounds(labelText, 0, labelText.length, labelBounds)
            val confBounds = Rect()
            textPaint.getTextBounds(confidence, 0, confidence.length, confBounds)
            
            val textWidth = maxOf(labelBounds.width(), confBounds.width()) + 16f
            val textHeight = labelBounds.height() + confBounds.height() + 24f
            
            // Position label above the bounding box, or inside if not enough space
            val labelX = left
            val labelY = if (top >= textHeight + 8f) {
                top - 8f
            } else {
                top + 8f
            }
            
            // Draw text background
            canvas.drawRect(
                labelX - 8f,
                labelY - textHeight,
                labelX + textWidth,
                labelY + 8f,
                textBackgroundPaint
            )
            
            // Draw label text
            canvas.drawText(
                labelText,
                labelX,
                labelY - confBounds.height() - 8f,
                textPaint
            )
            
            // Draw confidence text
            canvas.drawText(
                confidence,
                labelX,
                labelY - 4f,
                textPaint
            )
        }
    }
} 