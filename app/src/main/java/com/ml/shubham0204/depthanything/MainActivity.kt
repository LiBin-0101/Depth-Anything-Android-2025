package com.ml.shubham0204.depthanything

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import com.ml.shubham0204.depthanything.ui.theme.DepthAnythingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

class MainActivity : ComponentActivity() {

    private var depthImageState = mutableStateOf<Bitmap?>(null)
    private var originalImageState = mutableStateOf<Bitmap?>(null)
    private var meshDataState = mutableStateOf<MeshGenerator.MeshData?>(null)
    private var inferenceTimeState = mutableLongStateOf(0)
    private var progressState = mutableStateOf(false)
    private var show3DViewState = mutableStateOf(false)
    private lateinit var depthAnything: DepthAnything
    private lateinit var meshGenerator: MeshGenerator
    private var selectedModelState = mutableStateOf("model.onnx")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        depthAnything = DepthAnything(this, selectedModelState.value)
        meshGenerator = MeshGenerator()

        setContent { ActivityUI() }
    }

    @Composable
    private fun ActivityUI() {
        DepthAnythingTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val depthImage by remember { depthImageState }
                val originalImage by remember { originalImageState }
                val show3DView by remember { show3DViewState }
                ProgressDialog()
                if (show3DView && originalImage != null && meshDataState.value != null) {
                    ThreeDViewUI(originalImage = originalImage!!, meshData = meshDataState.value!!)
                } else if (depthImage != null) {
                    DepthImageUI(depthImage = depthImage!!)
                } else {
                    ImageSelectionUI()
                }
            }
        }
    }

    @Composable
    private fun ImageSelectionUI() {
        val pickMediaLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) {
                if (it != null) {
                    progressState.value = true
                    val bitmap = getFixedBitmap(it)
                    originalImageState.value = bitmap
                    CoroutineScope(Dispatchers.Default).launch {
                        val (depthMap, inferenceTime) = depthAnything.predict(bitmap)
                        depthImageState.value = colormapInferno(depthMap)
                        inferenceTimeState.longValue = inferenceTime
                        
                        // 生成3D网格（使用深度感知映射）
                        val meshData = meshGenerator.generateSimplifiedMesh(depthMap, bitmap, 8000, 0.3f)
                        withContext(Dispatchers.Main) { 
                            meshDataState.value = meshData
                            progressState.value = false 
                        }
                    }
                }
            }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // 主标题卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
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
                        text = "🎯 ${getString(R.string.model_name)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = getString(R.string.model_description),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 模型选择卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "📱 选择模型",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    var expanded by remember { mutableStateOf(false) }
                    val models = remember { listModelsInAssets() }

                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedModelState.value)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        selectedModelState.value = model
                                        expanded = false
                                        depthAnything = DepthAnything(this@MainActivity, model)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 图片选择按钮
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🖼️ 选择图片",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Button(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "📁 从相册选择图片",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // 视频处理按钮
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎬 视频处理",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Button(
                        onClick = {
                            startActivity(Intent(this@MainActivity, VideoProcessingActivity::class.java))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text(
                            text = "🎥 处理视频文件",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // 链接卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔗 相关链接",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Hyperlink-style text
                    val annotatedString = buildAnnotatedString {
                        pushStringAnnotation(
                            tag = "paper",
                            annotation = getString(R.string.model_paper_url)
                        )
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("📄 查看论文")
                        }
                        pop()
                        append("   ")
                        pushStringAnnotation(
                            tag = "github",
                            annotation = getString(R.string.model_github_url)
                        )
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("💻 GitHub")
                        }
                        pop()
                    }
                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium,
                        onClick = { offset ->
                            annotatedString
                                .getStringAnnotations(tag = "paper", start = offset, end = offset)
                                .firstOrNull()
                                ?.let {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(it.item)).apply {
                                        startActivity(this)
                                    }
                                }
                            annotatedString
                                .getStringAnnotations(tag = "github", start = offset, end = offset)
                                .firstOrNull()
                                ?.let {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(it.item)).apply {
                                        startActivity(this)
                                    }
                                }
                        }
                    )
                }
            }
        }
    }

    private fun listModelsInAssets(): List<String> {
        return assets.list("")?.filter { it.endsWith(".onnx") } ?: emptyList()
    }

    @Composable
    private fun DepthImageUI(depthImage: Bitmap) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部控制栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎯 深度图像",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "推理时间: ${inferenceTimeState.longValue} ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "模型: ${depthAnything.modelName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { 
                            depthImageState.value = null
                            originalImageState.value = null
                            meshDataState.value = null
                            show3DViewState.value = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = "❌ 关闭")
                    }
                }
            }
            
            // 深度图像显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Image(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(depthImage.width.toFloat() / depthImage.height.toFloat())
                        .zoomable(rememberZoomState()),
                    bitmap = depthImage.asImageBitmap(),
                    contentDescription = "Depth Image"
                )
            }
            
            // 底部操作按钮
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🔧 操作选项",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { show3DViewState.value = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(text = "🎮 3D视图")
                        }
                        
                        Button(
                            onClick = { 
                                depthImageState.value = null
                                originalImageState.value = null
                                meshDataState.value = null
                                show3DViewState.value = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(text = "🔄 重新选择")
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ThreeDViewUI(originalImage: Bitmap, meshData: MeshGenerator.MeshData) {
        var advanced3DView: Advanced3DView? by remember { mutableStateOf(null) }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部控制栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎮 3D立体视图",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "单指拖拽旋转（±7度），双指缩放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { show3DViewState.value = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = "❌ 返回")
                    }
                }
            }
            
            // 高级3D视图 - 全屏显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        Advanced3DView(context).apply {
                            setMeshData(meshData, originalImage)
                            advanced3DView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // 底部控制按钮
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🎛️ 视图控制",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                advanced3DView?.resetView()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(text = "🔄 重置视图")
                        }
                        
                        Button(
                            onClick = { 
                                // 自动适配视图
                                advanced3DView?.resetView()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(text = "🎯 自动适配")
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProgressDialog() {
        val isShowingProgress by remember { progressState }
        if (isShowingProgress) {
            BasicAlertDialog(onDismissRequest = { /* ProgressDialog is not cancellable */}) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "🔄 正在处理图片...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请稍候，正在生成深度图和3D网格",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }

    private fun getFixedBitmap(imageFileUri: Uri): Bitmap {
        var imageBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(imageFileUri))
        val exifInterface = ExifInterface(contentResolver.openInputStream(imageFileUri)!!)
        imageBitmap =
            when (
                exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(imageBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(imageBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(imageBitmap, 270f)
                else -> imageBitmap
            }
        return imageBitmap
    }

}
