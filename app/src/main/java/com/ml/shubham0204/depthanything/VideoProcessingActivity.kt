package com.ml.shubham0204.depthanything

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.depthanything.ui.theme.DepthAnythingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoProcessingActivity : ComponentActivity() {

    private lateinit var depthAnything: DepthAnything
    private lateinit var videoProcessor: VideoDepthProcessor
    private val videoEncoder = VideoEncoder()
    
    private var selectedVideoUriState = mutableStateOf<Uri?>(null)
    private var videoFileNameState = mutableStateOf<String>("")
    private var isProcessingState = mutableStateOf(false)
    private var progressCurrentState = mutableIntStateOf(0)
    private var progressTotalState = mutableIntStateOf(0)
    private var statusTextState = mutableStateOf("请选择视频文件")
    private var outputVideoPathState = mutableStateOf<String?>(null)
    private var isEncodingState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 初始化DepthAnything模型
            android.util.Log.d("VideoProcessing", "初始化DepthAnything模型...")
            depthAnything = DepthAnything(this, "model.onnx")
            videoProcessor = VideoDepthProcessor(this, depthAnything)
            android.util.Log.d("VideoProcessing", "模型初始化成功")
        } catch (e: Exception) {
            android.util.Log.e("VideoProcessing", "模型初始化失败", e)
            statusTextState.value = "❌ 模型加载失败: ${e.message}"
        }
        
        setContent { VideoProcessingUI() }
    }

    @Composable
    private fun VideoProcessingUI() {
        DepthAnythingTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 顶部标题卡片
                    TopHeaderCard()
                    
                    // 视频选择卡片
                    VideoSelectionCard()
                    
                    // 处理控制卡片
                    ProcessingControlCard()
                    
                    // 进度显示卡片
                    ProgressCard()
                    
                    // 结果卡片
                    ResultCard()
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 返回主界面按钮
                    Button(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("← 返回主界面")
                    }
                }
            }
        }
    }

    @Composable
    private fun TopHeaderCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎬 视频深度处理",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "将视频逐帧转换为深度图视频",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun VideoSelectionCard() {
        val selectedVideoUri by remember { selectedVideoUriState }
        val videoFileName by remember { videoFileNameState }
        
        val pickVideoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                selectedVideoUriState.value = it
                videoFileNameState.value = getFileName(it)
                statusTextState.value = "已选择: ${videoFileName}"
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "📁 选择视频文件",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Button(
                    onClick = { pickVideoLauncher.launch("video/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isProcessingState.value
                ) {
                    Text(
                        text = if (selectedVideoUri != null) "✓ ${videoFileName}" else "选择视频",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                if (selectedVideoUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ 视频已选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun ProcessingControlCard() {
        val selectedVideoUri by remember { selectedVideoUriState }
        val isProcessing by remember { isProcessingState }
        val scope = rememberCoroutineScope()
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "⚙️ 处理控制",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Button(
                    onClick = {
                        selectedVideoUri?.let { uri ->
                            scope.launch {
                                startVideoProcessing(uri)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = selectedVideoUri != null && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isProcessing) "⏳ 处理中..." else "▶️ 开始处理",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    @Composable
    private fun ProgressCard() {
        val isProcessing by remember { isProcessingState }
        val currentFrame by remember { progressCurrentState }
        val totalFrames by remember { progressTotalState }
        val statusText by remember { statusTextState }
        val outputPath by remember { outputVideoPathState }
        
        if (isProcessing || currentFrame > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "📊 处理进度",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    if (totalFrames > 0) {
                        LinearProgressIndicator(
                            progress = currentFrame.toFloat() / totalFrames,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "$currentFrame / $totalFrames 帧",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    @Composable
    private fun ResultCard() {
        val outputPath by remember { outputVideoPathState }
        
        if (outputPath != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "✅ 深度视频已生成",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = "深度估计视频已合成完毕",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "文件: ${File(outputPath!!).name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { playVideo(outputPath!!) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("▶️ 播放")
                        }
                        
                        Button(
                            onClick = { shareVideo(outputPath!!) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("📤 分享")
                        }
                    }
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = "video.mp4"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
        return result
    }

    private suspend fun startVideoProcessing(videoUri: Uri) {
        isProcessingState.value = true
        progressCurrentState.intValue = 0
        progressTotalState.intValue = 0
        statusTextState.value = "正在准备..."
        
        try {
            android.util.Log.d("VideoProcessing", "开始处理视频URI: $videoUri")
            
            // 将URI复制到临时文件
            val inputFile = File(cacheDir, "input_video.mp4")
            android.util.Log.d("VideoProcessing", "复制视频到临时文件: ${inputFile.absolutePath}")
            
            contentResolver.openInputStream(videoUri)?.use { input ->
                inputFile.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    android.util.Log.d("VideoProcessing", "复制了 $bytes 字节")
                }
            }
            
            if (!inputFile.exists() || inputFile.length() == 0L) {
                throw Exception("视频文件复制失败")
            }
            
            // 设置输出目录(保存图片序列)
            val outputDir = File(cacheDir, "depth_frames_${System.currentTimeMillis()}")
            outputDir.mkdirs()
            android.util.Log.d("VideoProcessing", "输出目录: ${outputDir.absolutePath}")
            
            // 开始处理
            android.util.Log.d("VideoProcessing", "调用videoProcessor.processVideo...")
            val frameCount = videoProcessor.processVideo(
                inputVideoPath = inputFile.absolutePath,
                outputDir = outputDir,
                onProgress = { current, total, status ->
                    android.util.Log.d("VideoProcessing", "进度: $current/$total - $status")
                    progressCurrentState.intValue = current
                    progressTotalState.intValue = total
                    statusTextState.value = status
                }
            )
            
            android.util.Log.d("VideoProcessing", "处理完成,帧数: $frameCount")
            
            if (frameCount > 0) {
                // 合成视频
                withContext(Dispatchers.Main) {
                    isEncodingState.value = true
                    statusTextState.value = "正在合成视频..."
                }
                
                val outputVideoFile = File(getExternalFilesDir(null), "depth_video_${System.currentTimeMillis()}.mp4")
                android.util.Log.d("VideoProcessing", "开始合成视频: ${outputVideoFile.absolutePath}")
                
                val encodeSuccess = videoEncoder.encodeImagesToVideo(
                    inputDir = outputDir,
                    outputVideoPath = outputVideoFile.absolutePath,
                    onProgress = { current, total ->
                        kotlinx.coroutines.runBlocking(Dispatchers.Main) {
                            statusTextState.value = "正在合成视频: $current/$total 帧"
                        }
                    }
                )
                
                withContext(Dispatchers.Main) {
                    isEncodingState.value = false
                    if (encodeSuccess) {
                        android.util.Log.d("VideoProcessing", "视频合成成功: ${outputVideoFile.absolutePath}")
                        outputVideoPathState.value = outputVideoFile.absolutePath
                        statusTextState.value = "✅ 处理完成!\n深度视频已生成"
                    } else {
                        statusTextState.value = "❌ 视频合成失败"
                    }
                }
                
                // 清理临时图片目录
                outputDir.listFiles()?.forEach { it.delete() }
                outputDir.delete()
            } else {
                withContext(Dispatchers.Main) {
                    statusTextState.value = "❌ 处理失败"
                }
            }
            
            // 清理临时文件
            inputFile.delete()
            
        } catch (e: Exception) {
            android.util.Log.e("VideoProcessing", "处理视频出错", e)
            statusTextState.value = "❌ 错误: ${e.message}"
        } finally {
            isProcessingState.value = false
        }
    }

    private fun playVideo(videoPath: String) {
        try {
            val videoFile = File(videoPath)
            val videoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                videoFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(videoUri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "选择播放器"))
        } catch (e: Exception) {
            android.util.Log.e("VideoProcessing", "无法播放视频", e)
            android.widget.Toast.makeText(
                this,
                "无法打开视频播放器: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun shareVideo(videoPath: String) {
        try {
            val videoFile = File(videoPath)
            val videoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                videoFile
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享视频"))
        } catch (e: Exception) {
            android.util.Log.e("VideoProcessing", "无法分享视频", e)
            android.widget.Toast.makeText(
                this,
                "无法分享视频: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}

