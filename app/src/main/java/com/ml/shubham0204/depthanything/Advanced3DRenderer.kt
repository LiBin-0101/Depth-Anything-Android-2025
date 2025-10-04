package com.ml.shubham0204.depthanything

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * 高级3D渲染器
 * 1. 深度感知的透视校正
 * 2. 自适应相机控制
 * 3. 平滑的3D空间导航
 * 4. 内容自动适配和占满
 */
class Advanced3DRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    private var meshData: MeshGenerator.MeshData? = null
    private var textureId: Int = 0
    private var programId: Int = 0
    private var imageAspectRatio: Float = 1f
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    
    // 变换矩阵
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    
    // 相机参数
    var cameraX = 0f
    var cameraY = 0f
    var cameraZ = 2f
    var rotationX = 0f
    var rotationY = 0f
    var scale = 1f
    
    // 3D空间参数
    private var depthRange = 1f // 深度范围
    private var nearPlane = 0.1f
    private var farPlane = 100f
    private var fov = 45f // 视野角度
    
    // 自适应参数
    private var autoFitScale = 1f
    private var contentBounds = FloatArray(6) // minX, maxX, minY, maxY, minZ, maxZ
    
    // 顶点和索引缓冲区
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: IntBuffer? = null
    
    // 简化Shader程序
    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec2 texCoord;
        uniform mat4 mvpMatrix;
        varying vec2 vTexCoord;
        
        void main() {
            gl_Position = mvpMatrix * position;
            vTexCoord = texCoord;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D texture;
        varying vec2 vTexCoord;
        
        void main() {
            gl_FragColor = texture2D(texture, vTexCoord);
        }
    """.trimIndent()
    
    fun setMeshData(meshData: MeshGenerator.MeshData, texture: Bitmap) {
        this.meshData = meshData
        this.texture = texture
        this.imageAspectRatio = texture.width.toFloat() / texture.height.toFloat()
        this.imageWidth = texture.width
        this.imageHeight = texture.height
        prepareBuffers()
        calculateContentBounds()
        calculateAutoFitScale()
        
        // 如果OpenGL上下文已经创建，立即加载纹理
        if (programId != 0) {
            loadTexture(texture)
        }
    }
    
    private var texture: Bitmap? = null
    
    fun updateCamera(x: Float, y: Float, z: Float) {
        cameraX = x
        cameraY = y
        cameraZ = z
    }
    
    fun updateRotation(x: Float, y: Float) {
        rotationX = x
        rotationY = y
    }
    
    fun updateScale(newScale: Float) {
        this.scale = newScale
    }
    
    fun resetView() {
        cameraX = 0f
        cameraY = 0f
        cameraZ = 2f
        rotationX = 0f
        rotationY = 0f
        scale = autoFitScale
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景色
        GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1.0f)
        
        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        
        // 启用面剔除
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        
        // 启用混合（用于平滑边缘）
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        // 创建shader程序
        programId = createProgram(vertexShaderCode, fragmentShaderCode)
        
        // 检查shader编译是否成功
        if (programId == 0) {
            android.util.Log.e("Advanced3DRenderer", "Failed to create shader program")
        }
        
        // 如果纹理已经设置，现在加载它
        texture?.let { loadTexture(it) }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        // 计算自适应投影矩阵，确保正确的宽高比
        val screenRatio = width.toFloat() / height.toFloat()
        val imageRatio = imageAspectRatio
        
        // 调试日志
        android.util.Log.d("Advanced3DRenderer", "屏幕尺寸: ${width}x${height}, 屏幕宽高比: $screenRatio")
        android.util.Log.d("Advanced3DRenderer", "图像宽高比: $imageRatio")

        val aspect = screenRatio
        
        android.util.Log.d("Advanced3DRenderer", "投影矩阵aspect: $aspect")
        
        // 使用透视投影，使用屏幕宽高比
        Matrix.perspectiveM(projectionMatrix, 0, fov, aspect, nearPlane, farPlane)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val mesh = meshData ?: return
        
        // 计算动态相机位置，实现平滑的3D空间导航
        val dynamicCameraZ = cameraZ * autoFitScale
        val dynamicCameraX = cameraX * autoFitScale
        val dynamicCameraY = cameraY * autoFitScale
        
        // 设置相机位置，支持360度旋转（修正上下视角）
        Matrix.setLookAtM(
            viewMatrix, 0,
            dynamicCameraX, dynamicCameraY, dynamicCameraZ,
            0f, 0f, 0f,
            0f, -1f, 0f
        )
        
        // 设置模型矩阵
        Matrix.setIdentityM(modelMatrix, 0)

        val imageAspectRatio = this.imageAspectRatio
        Matrix.scaleM(modelMatrix, 0, imageAspectRatio, 1f, 1f)
        
        // 初始旋转180度，让正面面向相机（从下往上看）
        Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
        
        // 应用用户旋转（限制在±7度）
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        
        // 应用缩放
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
        
        // 计算MVP矩阵
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        
        // 使用shader程序
        GLES20.glUseProgram(programId)
        
        // 设置uniform变量
        val positionHandle = GLES20.glGetAttribLocation(programId, "position")
        val texCoordHandle = GLES20.glGetAttribLocation(programId, "texCoord")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "mvpMatrix")
        val textureHandle = GLES20.glGetUniformLocation(programId, "texture")
        
        // 设置uniform值
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        // 启用顶点属性
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        
        // 设置顶点数据（5元素格式：x, y, z, u, v）
        vertexBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, buffer)
            
            buffer.position(3)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, buffer)
        }
        
        // 设置纹理
        if (textureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(textureHandle, 0)
        }
        
        // 绘制网格
        indexBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, buffer.remaining(), GLES20.GL_UNSIGNED_INT, buffer)
        }
        
        // 禁用顶点属性
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
    
    private fun calculateContentBounds() {
        val mesh = meshData ?: return
        
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = Float.MIN_VALUE
        
        // 遍历所有顶点计算边界（原始网格数据步长为5：x, y, z, u, v）
        for (i in 0 until mesh.vertices.size step 5) {
            val x = mesh.vertices[i]
            val y = mesh.vertices[i + 1]
            val z = mesh.vertices[i + 2]
            
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
            minZ = minOf(minZ, z)
            maxZ = maxOf(maxZ, z)
        }
        
        contentBounds[0] = minX
        contentBounds[1] = maxX
        contentBounds[2] = minY
        contentBounds[3] = maxY
        contentBounds[4] = minZ
        contentBounds[5] = maxZ
        
        // 调试日志
        val meshWidth = maxX - minX
        val meshHeight = maxY - minY
        val meshAspectRatio = meshWidth / meshHeight
        
        // 获取网格的实际尺寸（顶点数量）
        val actualMeshWidth = mesh.width
        val actualMeshHeight = mesh.height
        val actualMeshAspectRatio = actualMeshWidth.toFloat() / actualMeshHeight.toFloat()
        
        android.util.Log.d("Advanced3DRenderer", "网格边界: X[${minX}, ${maxX}], Y[${minY}, ${maxY}], Z[${minZ}, ${maxZ}]")
        android.util.Log.d("Advanced3DRenderer", "坐标范围尺寸: ${meshWidth}x${meshHeight}, 坐标宽高比: $meshAspectRatio")
        android.util.Log.d("Advanced3DRenderer", "网格实际尺寸: ${actualMeshWidth}x${actualMeshHeight}, 网格宽高比: $actualMeshAspectRatio")
        
        // 计算深度范围，确保有最小深度范围
        depthRange = maxOf(maxZ - minZ, 0.1f)
    }
    
    private fun calculateAutoFitScale() {
        val contentWidth = contentBounds[1] - contentBounds[0]
        val contentHeight = contentBounds[3] - contentBounds[2]
        val contentDepth = contentBounds[5] - contentBounds[4]
        
        // 计算适合屏幕的缩放比例，考虑图像宽高比
        val maxDimension = maxOf(contentWidth, contentHeight)
        val depthScale = if (contentDepth > 0) 1f / contentDepth else 1f
        
        // 基础缩放基于最大尺寸，深度缩放单独处理
        val baseScale = if (maxDimension > 0) 1.8f / maxDimension else 1f
        autoFitScale = baseScale
        
        // 设置初始缩放
        scale = autoFitScale
    }
    
    private fun createProgram(vertexShaderCode: String, fragmentShaderCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        return program
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // 检查编译错误
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            android.util.Log.e("Advanced3DRenderer", "Shader compilation error: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    private fun loadTexture(bitmap: Bitmap) {
        if (textureId != 0) {
            val textureIds = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textureIds, 0)
        }
        
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        
        // 设置高质量纹理参数，减少锯齿
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        
        // 启用各向异性过滤（如果支持）
        val maxAnisotropy = FloatArray(1)
        GLES20.glGetFloatv(0x84FF, maxAnisotropy, 0) // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
        if (maxAnisotropy[0] > 0) {
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, 0x84FE, minOf(4f, maxAnisotropy[0])) // GL_TEXTURE_MAX_ANISOTROPY_EXT
        }
        
        // 加载纹理数据
        val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                buffer.put((pixel shr 16 and 0xFF).toByte()) // R
                buffer.put((pixel shr 8 and 0xFF).toByte())  // G
                buffer.put((pixel and 0xFF).toByte())        // B
                buffer.put((pixel shr 24 and 0xFF).toByte()) // A
            }
        }
        buffer.position(0)
        
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            bitmap.width, bitmap.height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        
        // 生成Mipmap
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    }
    
    private fun prepareBuffers() {
        val mesh = meshData ?: return
        
        // 准备顶点缓冲区（使用原始5元素格式：x, y, z, u, v）
        val vertexBuffer = ByteBuffer.allocateDirect(mesh.vertices.size * 4)
        vertexBuffer.order(ByteOrder.nativeOrder())
        this.vertexBuffer = vertexBuffer.asFloatBuffer()
        this.vertexBuffer?.put(mesh.vertices)
        this.vertexBuffer?.position(0)
        
        // 准备索引缓冲区
        val indexBuffer = ByteBuffer.allocateDirect(mesh.indices.size * 4)
        indexBuffer.order(ByteOrder.nativeOrder())
        this.indexBuffer = indexBuffer.asIntBuffer()
        this.indexBuffer?.put(mesh.indices)
        this.indexBuffer?.position(0)
    }
}