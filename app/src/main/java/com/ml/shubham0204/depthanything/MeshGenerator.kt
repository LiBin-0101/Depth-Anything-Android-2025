package com.ml.shubham0204.depthanything

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import kotlin.math.*

/**
 * 3D网格生成器，将深度图转换为3D网格顶点
 */
class MeshGenerator {
    
    data class Vertex3D(
        val x: Float,
        val y: Float,
        val z: Float,
        val u: Float, // 纹理坐标U
        val v: Float  // 纹理坐标V
    )
    
    data class MeshData(
        val vertices: FloatArray,
        val indices: IntArray,
        val width: Int,
        val height: Int
    )
    
    /**
     * 从深度图和原始图像生成3D网格
     * @param depthMap 深度图（灰度图）
     * @param originalImage 原始彩色图像
     * @param depthScale 深度缩放因子，控制3D效果的强度
     * @param gridDensity 网格密度，值越小网格越密集
     * @return 3D网格数据
     */
    fun generateMesh(
        depthMap: Bitmap,
        originalImage: Bitmap,
        depthScale: Float = 0.1f,
        gridDensity: Int = 2
    ): MeshData {
        // 使用原始图像的尺寸和宽高比，而不是深度图的尺寸
        val originalWidth = originalImage.width
        val originalHeight = originalImage.height
        val depthWidth = depthMap.width
        val depthHeight = depthMap.height
        
        // 调试日志
        android.util.Log.d("MeshGenerator", "原始图像尺寸: ${originalWidth}x${originalHeight}, 宽高比: ${originalWidth.toFloat() / originalHeight.toFloat()}")
        android.util.Log.d("MeshGenerator", "深度图尺寸: ${depthWidth}x${depthHeight}, 宽高比: ${depthWidth.toFloat() / depthHeight.toFloat()}")
        
        val step = maxOf(1, gridDensity)
        android.util.Log.d("MeshGenerator", "网格步长: $step")
        
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        
        // 计算原始图像的宽高比，确保网格比例正确
        val imageAspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        
        // 计算顶点，使用原始图像的网格尺寸
        // 确保包含边界顶点
        val maxX = originalWidth - 1
        val maxY = originalHeight - 1
        
        // 生成网格点，确保包含边界顶点
        val xPoints = mutableListOf<Int>()
        val yPoints = mutableListOf<Int>()
        
        // 生成X坐标点
        for (x in 0..maxX step step) {
            xPoints.add(x)
        }
        if (xPoints.last() != maxX) {
            xPoints.add(maxX) // 确保包含右边界
        }
        
        // 生成Y坐标点
        for (y in 0..maxY step step) {
            yPoints.add(y)
        }
        if (yPoints.last() != maxY) {
            yPoints.add(maxY) // 确保包含上边界
        }
        
        android.util.Log.d("MeshGenerator", "X坐标点数量: ${xPoints.size}, 范围: ${xPoints.first()}..${xPoints.last()}")
        android.util.Log.d("MeshGenerator", "Y坐标点数量: ${yPoints.size}, 范围: ${yPoints.first()}..${yPoints.last()}")
        
        for (y in yPoints) {
            for (x in xPoints) {
                val actualX = x
                val actualY = y
                
                // 将原始图像坐标映射到深度图坐标
                val depthX = (actualX.toFloat() / originalWidth * depthWidth).toInt().coerceIn(0, depthWidth - 1)
                val depthY = (actualY.toFloat() / originalHeight * depthHeight).toInt().coerceIn(0, depthHeight - 1)
                
                // 获取深度值（0-255）- 从alpha通道获取（ALPHA_8格式）
                val depthValue = Color.alpha(depthMap.getPixel(depthX, depthY))
                
                // 应用深度平滑算法减少毛刺
                val smoothedDepth = smoothDepthValue(depthMap, depthX, depthY, depthWidth, depthHeight)
                
                // 将深度值转换为Z坐标，反转深度值使近处物体更突出
                val z = ((255 - smoothedDepth) / 255f) * depthScale
                
                // 归一化坐标到[-1, 1]范围，保持原始图像的宽高比
                val normalizedX = (actualX.toFloat() / maxX) * 2f - 1f
                val normalizedY = (actualY.toFloat() / maxY) * 2f - 1f
                
                // 调试日志（记录边界顶点）
                if (actualX == 0 && actualY == 0) {
                    android.util.Log.d("MeshGenerator", "左下顶点: ($actualX, $actualY) -> ($normalizedX, $normalizedY)")
                }
                if (actualX == maxX && actualY == 0) {
                    android.util.Log.d("MeshGenerator", "右下顶点: ($actualX, $actualY) -> ($normalizedX, $normalizedY)")
                }
                if (actualX == 0 && actualY == maxY) {
                    android.util.Log.d("MeshGenerator", "左上顶点: ($actualX, $actualY) -> ($normalizedX, $normalizedY)")
                }
                if (actualX == maxX && actualY == maxY) {
                    android.util.Log.d("MeshGenerator", "右上顶点: ($actualX, $actualY) -> ($normalizedX, $normalizedY)")
                }
                
                // 纹理坐标，确保精确映射到原始图像
                val u = actualX.toFloat() / maxX
                val v = actualY.toFloat() / maxY
                
                // 添加顶点：位置(x,y,z) + 纹理坐标(u,v)
                vertices.addAll(listOf(normalizedX, normalizedY, z, u, v))
            }
        }
        
        // 计算索引（三角形），使用实际的网格点数量
        val gridWidth = xPoints.size
        val gridHeight = yPoints.size
        
        for (y in 0 until gridHeight - 1) {
            for (x in 0 until gridWidth - 1) {
                val topLeft = y * gridWidth + x
                val topRight = topLeft + 1
                val bottomLeft = (y + 1) * gridWidth + x
                val bottomRight = bottomLeft + 1
                
                // 第一个三角形
                indices.addAll(listOf(topLeft, bottomLeft, topRight))
                // 第二个三角形
                indices.addAll(listOf(topRight, bottomLeft, bottomRight))
            }
        }
        
        val meshData = MeshData(
            vertices = vertices.toFloatArray(),
            indices = indices.toIntArray(),
            width = gridWidth,
            height = gridHeight
        )
        
        // 调试日志
        android.util.Log.d("MeshGenerator", "网格尺寸: ${gridWidth}x${gridHeight}, 顶点数: ${vertices.size / 5}")
        
        // 应用拉普拉斯平滑算法减少网格毛刺
        return applyLaplacianSmoothing(meshData, 2)
    }
    
    /**
     * 深度值平滑算法，减少深度图中的噪声和毛刺
     */
    private fun smoothDepthValue(depthMap: Bitmap, x: Int, y: Int, width: Int, height: Int): Int {
        val kernelSize = 3
        val halfKernel = kernelSize / 2
        var totalWeight = 0f
        var weightedSum = 0f
        
        // 3x3高斯核
        val gaussianKernel = arrayOf(
            floatArrayOf(1f, 2f, 1f),
            floatArrayOf(2f, 4f, 2f),
            floatArrayOf(1f, 2f, 1f)
        )
        
        for (ky in -halfKernel..halfKernel) {
            for (kx in -halfKernel..halfKernel) {
                val sampleX = (x + kx).coerceIn(0, width - 1)
                val sampleY = (y + ky).coerceIn(0, height - 1)
                val depthValue = Color.alpha(depthMap.getPixel(sampleX, sampleY))
                val weight = gaussianKernel[ky + halfKernel][kx + halfKernel]
                
                weightedSum += depthValue * weight
                totalWeight += weight
            }
        }
        
        return (weightedSum / totalWeight).toInt()
    }
    
    /**
     * 拉普拉斯平滑算法，减少网格表面的毛刺和锯齿
     */
    private fun applyLaplacianSmoothing(meshData: MeshData, iterations: Int): MeshData {
        val vertices = meshData.vertices.clone()
        val width = meshData.width
        val height = meshData.height
        
        repeat(iterations) {
            val smoothedVertices = vertices.clone()
            
            // 对内部顶点应用拉普拉斯平滑
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = (y * width + x) * 5
                    
                    // 获取相邻顶点
                    val neighbors = listOf(
                        (y - 1) * width + x,      // 上
                        (y + 1) * width + x,      // 下
                        y * width + (x - 1),      // 左
                        y * width + (x + 1)       // 右
                    )
                    
                    var avgX = 0f
                    var avgY = 0f
                    var avgZ = 0f
                    
                    neighbors.forEach { neighborIndex ->
                        val neighborVertexIndex = neighborIndex * 5
                        avgX += vertices[neighborVertexIndex]
                        avgY += vertices[neighborVertexIndex + 1]
                        avgZ += vertices[neighborVertexIndex + 2]
                    }
                    
                    // 应用平滑（只对Z坐标进行平滑，保持X、Y坐标不变）
                    smoothedVertices[index + 2] = avgZ / neighbors.size * 0.5f + vertices[index + 2] * 0.5f
                }
            }
            
            // 更新顶点数组
            System.arraycopy(smoothedVertices, 0, vertices, 0, vertices.size)
        }
        
        return MeshData(vertices, meshData.indices, meshData.width, meshData.height)
    }
    
    /**
     * 生成简化的网格（用于性能优化）
     */
    fun generateSimplifiedMesh(
        depthMap: Bitmap,
        originalImage: Bitmap,
        targetVertices: Int = 15000,
        depthScale: Float = 0.15f
    ): MeshData {
        val originalWidth = originalImage.width
        val originalHeight = originalImage.height
        
        // 计算合适的步长以达到目标顶点数，保持宽高比
        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        val totalPixels = originalWidth * originalHeight
        val baseStep = maxOf(1, sqrt(totalPixels.toFloat() / targetVertices).toInt())
        
        // 根据宽高比调整步长，确保网格保持正确的宽高比
        val stepX = if (aspectRatio > 1f) {
            baseStep
        } else {
            (baseStep * aspectRatio).toInt().coerceAtLeast(1)
        }
        
        val stepY = if (aspectRatio < 1f) {
            baseStep
        } else {
            (baseStep / aspectRatio).toInt().coerceAtLeast(1)
        }
        
        // 使用较小的步长以确保质量
        val step = minOf(stepX, stepY)
        
        android.util.Log.d("MeshGenerator", "步长计算: 基础步长=$baseStep, stepX=$stepX, stepY=$stepY, 最终步长=$step")
        
        return generateMesh(depthMap, originalImage, depthScale, step)
    }
    
    /**
     * 生成高质量网格（更多顶点，更平滑的表面）
     */
    fun generateHighQualityMesh(
        depthMap: Bitmap,
        originalImage: Bitmap,
        depthScale: Float = 0.2f
    ): MeshData {
        // 使用更小的步长获得更密集的网格
        val step = 1
        return generateMesh(depthMap, originalImage, depthScale, step)
    }
    
    /**
     * 从深度图生成法线贴图（用于光照计算）
     */
    fun generateNormalMap(depthMap: Bitmap): Bitmap {
        val width = depthMap.width
        val height = depthMap.height
        val normalMap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 获取周围像素的深度值
                val depth = Color.alpha(depthMap.getPixel(x, y)) / 255f
                val depthLeft = if (x > 0) Color.alpha(depthMap.getPixel(x - 1, y)) / 255f else depth
                val depthRight = if (x < width - 1) Color.alpha(depthMap.getPixel(x + 1, y)) / 255f else depth
                val depthUp = if (y > 0) Color.alpha(depthMap.getPixel(x, y - 1)) / 255f else depth
                val depthDown = if (y < height - 1) Color.alpha(depthMap.getPixel(x, y + 1)) / 255f else depth
                
                // 计算梯度
                val dx = (depthRight - depthLeft) * 0.5f
                val dy = (depthDown - depthUp) * 0.5f
                
                // 计算法线向量
                val normalX = -dx
                val normalY = -dy
                val normalZ = 1f
                
                // 归一化
                val length = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
                val normalizedX = (normalX / length + 1f) * 0.5f
                val normalizedY = (normalY / length + 1f) * 0.5f
                val normalizedZ = (normalZ / length + 1f) * 0.5f
                
                // 转换为颜色
                val r = (normalizedX * 255).toInt().coerceIn(0, 255)
                val g = (normalizedY * 255).toInt().coerceIn(0, 255)
                val b = (normalizedZ * 255).toInt().coerceIn(0, 255)
                
                normalMap.setPixel(x, y, Color.argb(255, r, g, b))
            }
        }
        
        return normalMap
    }
}
