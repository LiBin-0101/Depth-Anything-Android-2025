package com.ml.shubham0204.depthanything

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 视频深度处理器 - 使用Android原生API实现
 * 
 * 工作流程:
 * 1. 使用MediaExtractor逐帧解码视频
 * 2. 将每帧转为Bitmap并使用DepthAnything处理
 * 3. 将处理后的帧保存为图片
 * 4. 使用外部工具或简化方案合成视频
 */
class VideoDepthProcessor(
    private val context: Context,
    private val depthAnything: DepthAnything
) {
    private val tempDir = File(context.cacheDir, "video_frames").apply {
        if (!exists()) mkdirs()
    }
    
    companion object {
        private const val TAG = "VideoDepthProcessor"
    }
    
    /**
     * 处理视频文件 - 简化版本
     * 使用MediaMetadataRetriever逐帧提取并处理
     * 
     * @param inputVideoPath 输入视频文件路径
     * @param outputDir 输出图片序列目录
     * @param onProgress 进度回调 (当前帧, 总帧数, 状态信息)
     * @return 处理的帧数
     */
    suspend fun processVideo(
        inputVideoPath: String,
        outputDir: File,
        onProgress: (current: Int, total: Int, status: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始处理视频: $inputVideoPath")
            Log.d(TAG, "输出目录: ${outputDir.absolutePath}")
            
            // 清理输出目录
            if (!outputDir.exists()) {
                val created = outputDir.mkdirs()
                Log.d(TAG, "创建输出目录: $created")
            }
            
            val retriever = MediaMetadataRetriever()
            Log.d(TAG, "设置数据源...")
            retriever.setDataSource(inputVideoPath)
            Log.d(TAG, "数据源设置成功")
            
            // 获取视频信息
            onProgress(0, 0, "正在分析视频...")
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSec = durationMs / 1000.0
            
            // 估算帧数 (假设30fps)
            val fps = 30
            val estimatedFrames = (durationSec * fps).toInt()
            
            Log.d(TAG, "视频时长: ${durationSec}秒, 估算帧数: $estimatedFrames")
            
            // 按时间间隔提取帧 (每秒提取2帧以加快处理速度)
            val frameInterval = 500_000L // 500ms = 0.5秒
            var currentTime = 0L
            var frameIndex = 0
            
            while (currentTime < durationMs * 1000) { // 转为微秒
                try {
                    // 提取指定时间的帧
                    val bitmap = retriever.getFrameAtTime(
                        currentTime,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    
                    if (bitmap != null) {
                        frameIndex++
                        
                        // 使用DepthAnything处理
                        val (depthMap, inferenceTime) = depthAnything.predict(bitmap)
                        
                        // 应用颜色映射
                        val colorDepthMap = colormapInferno(depthMap)
                        
                        // 保存处理后的帧
                        val outputFile = File(outputDir, "depth_%04d.jpg".format(frameIndex))
                        FileOutputStream(outputFile).use { output ->
                            colorDepthMap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                        }
                        
                        // 更新进度
                        val progress = (currentTime.toFloat() / (durationMs * 1000)).coerceIn(0f, 1f)
                        val currentFrame = (progress * estimatedFrames).toInt()
                        
                        withContext(Dispatchers.Main) {
                            onProgress(
                                currentFrame,
                                estimatedFrames,
                                "处理中: 第${frameIndex}帧 (${inferenceTime}ms)"
                            )
                        }
                        
                        Log.d(TAG, "已处理帧 $frameIndex, 推理时间: ${inferenceTime}ms")
                        
                        // 释放资源
                        bitmap.recycle()
                        depthMap.recycle()
                        colorDepthMap.recycle()
                    }
                    
                    currentTime += frameInterval
                    
                } catch (e: Exception) {
                    Log.e(TAG, "处理帧失败: $e")
                    currentTime += frameInterval
                }
            }
            
            retriever.release()
            
            withContext(Dispatchers.Main) {
                onProgress(estimatedFrames, estimatedFrames, "完成! 共处理 $frameIndex 帧")
            }
            
            Log.d(TAG, "视频处理完成, 共处理 $frameIndex 帧")
            return@withContext frameIndex
            
        } catch (e: Exception) {
            Log.e(TAG, "处理视频时出错", e)
            withContext(Dispatchers.Main) {
                onProgress(0, 0, "错误: ${e.message}")
            }
            return@withContext 0
        }
    }
    
    /**
     * 获取视频元数据
     */
    fun getVideoInfo(videoPath: String): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            
            VideoInfo(
                width = width,
                height = height,
                durationMs = durationMs,
                frameRate = frameRate
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取视频信息失败", e)
            null
        } finally {
            retriever.release()
        }
    }
    
    /**
     * 清理临时目录
     */
    fun cleanTempDir() {
        tempDir.listFiles()?.forEach { file ->
            file.delete()
        }
        Log.d(TAG, "临时目录已清理")
    }
    
    /**
     * 视频信息
     */
    data class VideoInfo(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val frameRate: Float
    )
}
