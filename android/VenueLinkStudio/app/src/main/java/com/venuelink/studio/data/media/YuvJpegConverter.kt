package com.venuelink.studio.data.media

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.File

object YuvJpegConverter {
    fun saveImageAsJpeg(image: Image, target: File, quality: Int = 92) {
        require(image.format == ImageFormat.YUV_420_888) { "Only YUV_420_888 images can be converted." }
        val nv21 = imageToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        target.outputStream().use { output ->
            yuv.compressToJpeg(Rect(0, 0, image.width, image.height), quality.coerceIn(1, 100), output)
        }
    }

    fun imageToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height * 3 / 2)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer.duplicate()
        var outputOffset = 0

        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            yBuffer.position(rowStart)
            yBuffer.get(output, outputOffset, width)
            outputOffset += width
        }

        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                output[outputOffset++] = vBuffer.get(vIndex)
                output[outputOffset++] = uBuffer.get(uIndex)
            }
        }
        return output
    }
}
