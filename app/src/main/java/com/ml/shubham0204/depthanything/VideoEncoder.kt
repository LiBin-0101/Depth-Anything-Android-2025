package com.ml.shubham0204.depthanything

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * 使用Android原生MediaCodec和MediaMuxer将图片序列合成为视频
 */
class VideoEncoder {
    
    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = "video/avc" // H.264
        private const val FRAME_RATE = 2 // 每秒2帧(因为我们每0.5秒提取一帧)
        private const val I_FRAME_INTERVAL = 1
        private const val TIMEOUT_USEC = 10000L
    }
    
    /**
     * 将图片序列合成为MP4视频
     * 
     * @param inputDir 包含depth_XXXX.jpg图片的目录
     * @param outputVideoPath 输出视频文件路径
     * @param onProgress 进度回调 (当前帧, 总帧数)
     * @return 是否成功
     */
    suspend fun encodeImagesToVideo(
        inputDir: File,
        outputVideoPath: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            // 获取所有图片文件
            val imageFiles = inputDir.listFiles { file ->
                file.name.startsWith("depth_") && file.name.endsWith(".jpg")
            }?.sortedBy { it.name } ?: emptyList()
            
            if (imageFiles.isEmpty()) {
                Log.e(TAG, "没有找到图片文件")
                return@withContext false
            }
            
            Log.d(TAG, "找到 ${imageFiles.size} 张图片")
            
            // 读取第一张图片获取尺寸
            val firstBitmap = BitmapFactory.decodeFile(imageFiles[0].absolutePath)
            val width = firstBitmap.width
            val height = firstBitmap.height
            firstBitmap.recycle()
            
            // 确保宽高是16的倍数(MediaCodec要求)
            val encoderWidth = (width / 16) * 16
            val encoderHeight = (height / 16) * 16
            
            Log.d(TAG, "视频尺寸: ${encoderWidth}x${encoderHeight}")
            
            // 创建MediaCodec编码器
            val format = MediaFormat.createVideoFormat(MIME_TYPE, encoderWidth, encoderHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, 2000000) // 2Mbps
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }
            
            val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            
            // 创建MediaMuxer
            val muxer = MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            
            val bufferInfo = MediaCodec.BufferInfo()
            var frameIndex = 0
            var inputDone = false
            var outputDone = false
            
            while (!outputDone) {
                // 输入帧
                if (!inputDone) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inputBufferIndex >= 0) {
                        if (frameIndex < imageFiles.size) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        
                        if (inputBuffer != null) {
                            // 加载并转换图片
                            val bitmap = BitmapFactory.decodeFile(imageFiles[frameIndex].absolutePath)
                            val scaledBitmap = if (bitmap.width != encoderWidth || bitmap.height != encoderHeight) {
                                Bitmap.createScaledBitmap(bitmap, encoderWidth, encoderHeight, true)
                            } else {
                                bitmap
                            }
                            
                            // 转换为YUV420
                            val yuvData = bitmapToNV12(scaledBitmap, encoderWidth, encoderHeight)
                            inputBuffer.clear()
                            inputBuffer.put(yuvData)
                            
                            val presentationTimeUs = computePresentationTime(frameIndex, FRAME_RATE)
                            encoder.queueInputBuffer(inputBufferIndex, 0, yuvData.size, presentationTimeUs, 0)
                            
                            scaledBitmap.recycle()
                            if (scaledBitmap != bitmap) bitmap.recycle()
                            
                            frameIndex++
                            onProgress(frameIndex, imageFiles.size)
                            Log.d(TAG, "编码帧 $frameIndex/${imageFiles.size}")
                        }
                        } else {
                            // 发送EOS标记
                            encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            Log.d(TAG, "发送EOS标记")
                        }
                    }
                }
                
                // 输出编码数据
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = encoder.outputFormat
                        trackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                        Log.d(TAG, "Muxer已启动")
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            if (muxerStarted) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                            }
                        }
                        
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            
            // 清理资源
            encoder.stop()
            encoder.release()
            
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            
            Log.d(TAG, "视频合成完成: $outputVideoPath")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "视频合成失败", e)
            return@withContext false
        }
    }
    
    /**
     * 将Bitmap转换为NV12格式 (YUV420SemiPlanar)
     */
    private fun bitmapToNV12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val yuvSize = width * height * 3 / 2
        val yuv = ByteArray(yuvSize)
        
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        
        var yIndex = 0
        var uvIndex = width * height
        
        for (j in 0 until height) {
            for (i in 0 until width) {
                val r = (argb[j * width + i] shr 16) and 0xff
                val g = (argb[j * width + i] shr 8) and 0xff
                val b = argb[j * width + i] and 0xff
                
                // RGB转Y
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                
                // UV交错存储 (NV12格式)
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        
        return yuv
    }
    
    /**
     * 计算presentation time
     */
    private fun computePresentationTime(frameIndex: Int, frameRate: Int): Long {
        return 1000000L * frameIndex / frameRate
    }
}

