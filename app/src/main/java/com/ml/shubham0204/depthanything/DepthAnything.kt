package com.ml.shubham0204.depthanything

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class DepthAnything(context: Context, val modelName: String) {

    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession =
        ortEnvironment.createSession(context.assets.open(modelName).readBytes())
    private val inputName = ortSession.inputNames.iterator().next()

    private val inputDim = 256
    private val outputDim = 252

    private val rotateTransform = Matrix().apply { 
        postRotate(90f)
        postScale(-1f, 1f) // 水平翻转
    }

    suspend fun predict(inputImage: Bitmap): Pair<Bitmap, Long> =
        withContext(Dispatchers.Default) {
            // 保存原始图像尺寸和宽高比
            val originalWidth = inputImage.width
            val originalHeight = inputImage.height
            val originalAspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
            
            // 调试日志
            android.util.Log.d("DepthAnything", "原始图像尺寸: ${originalWidth}x${originalHeight}, 宽高比: $originalAspectRatio")
            
            // 如果图像分辨率超过1024，先按比例缩放
            val maxDimension = 1024
            val scaledImage = if (inputImage.width > maxDimension || inputImage.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(inputImage.width, inputImage.height)
                val newWidth = (inputImage.width * scale).toInt()
                val newHeight = (inputImage.height * scale).toInt()
                Bitmap.createScaledBitmap(inputImage, newWidth, newHeight, true)
            } else {
                inputImage
            }
            
            // 记录缩放后的尺寸
            val scaledWidth = scaledImage.width
            val scaledHeight = scaledImage.height
            val scaledAspectRatio = scaledWidth.toFloat() / scaledHeight.toFloat()
            
            val resizedImage = Bitmap.createScaledBitmap(
                scaledImage,
                inputDim,
                inputDim,
                true
            )
            val imagePixels = convert(resizedImage)
            val inputTensor =
                OnnxTensor.createTensor(
                    ortEnvironment,
                    imagePixels,
                    longArrayOf(1, inputDim.toLong(), inputDim.toLong(), 3),
                    OnnxJavaType.UINT8
                )
            val t1 = System.currentTimeMillis()
            val outputs = ortSession.run(mapOf(inputName to inputTensor))
            val inferenceTime = System.currentTimeMillis() - t1
            val outputTensor = outputs[0] as OnnxTensor
            var depthMap = Bitmap.createBitmap(outputDim, outputDim, Bitmap.Config.ALPHA_8)
            depthMap.copyPixelsFromBuffer(outputTensor.byteBuffer)
            depthMap = Bitmap.createBitmap(depthMap, 0, 0, outputDim, outputDim, rotateTransform, false)
            
            // 将深度图缩放回原始图像尺寸，而不是scaledImage尺寸
            depthMap = Bitmap.createScaledBitmap(depthMap, originalWidth, originalHeight, true)
            
            // 调试日志
            android.util.Log.d("DepthAnything", "最终深度图尺寸: ${depthMap.width}x${depthMap.height}, 宽高比: ${depthMap.width.toFloat() / depthMap.height.toFloat()}")
            
            return@withContext Pair(depthMap, inferenceTime)
        }

    private fun convert(bitmap: Bitmap): ByteBuffer {
        val imgData = ByteBuffer.allocate(1 * bitmap.width * bitmap.height * 3)
        imgData.rewind()
        for (i in 0 until bitmap.width) {
            for (j in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(i, j)
                imgData.put(Color.red(pixel).toByte())
                imgData.put(Color.blue(pixel).toByte())
                imgData.put(Color.green(pixel).toByte())
            }
        }
        imgData.rewind()
        return imgData
    }
}