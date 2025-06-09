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
package com.example.mnn_llm_test.arcore.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import com.google.ar.core.Frame
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Utility class for processing ARCore camera frames for YOLO object detection
 */
class FrameProcessor {
    companion object {
        private const val TAG = "FrameProcessor"
    }
    
    /**
     * Convert ARCore Frame to Bitmap for YOLO processing with proper rotation
     */
    fun frameToBitmap(frame: Frame): Bitmap? {
        return try {
            val cameraImage = frame.acquireCameraImage()
            var bitmap = imageToBitmap(cameraImage)
            cameraImage.close()
            
            if (bitmap != null) {
                // Apply rotation to match display orientation (typically 90 degrees for portrait)
                bitmap = rotateBitmap(bitmap, 90)
                Log.d(TAG, "Applied 90° rotation to camera frame: ${bitmap.width}x${bitmap.height}")
            }
            
            bitmap
        } catch (e: com.google.ar.core.exceptions.DeadlineExceededException) {
            // Session is pausing/stopping, this is expected during navigation
            Log.d(TAG, "Frame acquisition timed out - session likely pausing")
            null
        } catch (e: com.google.ar.core.exceptions.NotYetAvailableException) {
            // Frame not yet available, skip this frame
            Log.d(TAG, "Frame not yet available, skipping")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting frame to bitmap", e)
            null
        }
    }
    
    /**
     * Convert ARCore Image to Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        return when (image.format) {
            ImageFormat.YUV_420_888 -> yuvToBitmap(image)
            ImageFormat.NV21 -> nv21ToBitmap(image)
            else -> {
                Log.w(TAG, "Unsupported image format: ${image.format}")
                null
            }
        }
    }
    
    /**
     * Convert YUV_420_888 format to Bitmap with proper color channel handling
     */
    private fun yuvToBitmap(image: Image): Bitmap? {
        return try {
            Log.d(TAG, "Converting YUV image: ${image.width}x${image.height}, format: ${image.format}")
            
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            Log.d(TAG, "YUV plane sizes - Y: $ySize, U: $uSize, V: $vSize")
            
            // Create RGB bitmap directly from YUV data to avoid JPEG compression artifacts
            val width = image.width
            val height = image.height
            val rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Convert YUV to RGB pixel by pixel for better color accuracy
            val yPixelStride = yPlane.pixelStride
            val yRowStride = yPlane.rowStride
            val uvPixelStride = uPlane.pixelStride
            val uvRowStride = uPlane.rowStride
            
            val pixels = IntArray(width * height)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val yIndex = y * yRowStride + x * yPixelStride
                    val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride
                    
                    // Get YUV values
                    val yValue = (yBuffer.get(yIndex).toInt() and 0xFF)
                    val uValue = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                    val vValue = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128
                    
                    // Convert YUV to RGB using ITU-R BT.601 conversion matrix
                    var r = (yValue + 1.402 * vValue).toInt()
                    var g = (yValue - 0.344136 * uValue - 0.714136 * vValue).toInt()
                    var b = (yValue + 1.772 * uValue).toInt()
                    
                    // Clamp values to [0, 255]
                    r = r.coerceIn(0, 255)
                    g = g.coerceIn(0, 255)
                    b = b.coerceIn(0, 255)
                    
                    // Set pixel in ARGB format
                    pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            rgbBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            Log.d(TAG, "✅ Successfully converted YUV to RGB bitmap: ${rgbBitmap.width}x${rgbBitmap.height}")
            
            rgbBitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error converting YUV to RGB bitmap", e)
            
            // Fallback to original JPEG-based conversion
            try {
                Log.d(TAG, "Trying fallback JPEG conversion...")
                yuvToBitmapFallback(image)
            } catch (fallbackException: Exception) {
                Log.e(TAG, "❌ Fallback conversion also failed", fallbackException)
                null
            }
        }
    }
    
    /**
     * Fallback YUV to Bitmap conversion using JPEG compression (original method)
     */
    private fun yuvToBitmapFallback(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            
            val ySize = yPlane.buffer.remaining()
            val uSize = uPlane.buffer.remaining()
            val vSize = vPlane.buffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            // Copy Y plane
            yPlane.buffer.get(nv21, 0, ySize)
            
            // Interleave U and V planes for NV21 format
            val uvBuffer = ByteArray(uSize + vSize)
            vPlane.buffer.get(uvBuffer, 0, vSize)
            uPlane.buffer.get(uvBuffer, vSize, uSize)
            
            var uvIdx = 0
            var idx = ySize
            for (i in 0 until vSize) {
                nv21[idx++] = uvBuffer[uvIdx + vSize]
                nv21[idx++] = uvBuffer[uvIdx]
                uvIdx++
            }
            
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback YUV conversion", e)
            null
        }
    }
    
    /**
     * Convert NV21 format to Bitmap
     */
    private fun nv21ToBitmap(image: Image): Bitmap? {
        return try {
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            
            val yuvImage = YuvImage(data, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting NV21 to bitmap", e)
            null
        }
    }
    
    /**
     * Rotate bitmap to correct orientation if needed
     */
    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
    
    /**
     * Scale bitmap to target size while maintaining aspect ratio
     */
    fun scaleBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        
        val scaleX = targetWidth.toFloat() / originalWidth
        val scaleY = targetHeight.toFloat() / originalHeight
        val scale = minOf(scaleX, scaleY)
        
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }
} 