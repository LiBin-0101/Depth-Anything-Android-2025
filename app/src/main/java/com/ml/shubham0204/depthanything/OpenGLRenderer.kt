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

/**
 * OpenGL ES 2.0 渲染器，用于渲染3D网格
 */
class OpenGLRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    private var meshData: MeshGenerator.MeshData? = null
    private var textureId: Int = 0
    private var programId: Int = 0
    private var imageAspectRatio: Float = 1f
    
    // 变换矩阵
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    
    // 相机参数
    private var cameraX = 0f
    private var cameraY = 0f
    private var cameraZ = 2f
    var rotationX = 0f
    var rotationY = 0f
    var scale = 1f
    
    // 顶点和索引缓冲区
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: IntBuffer? = null
    
    // Shader程序
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
        prepareBuffers()
        
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
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景色
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        
        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // 启用面剔除（可选，提高性能）
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        
        // 创建shader程序
        programId = createProgram(vertexShaderCode, fragmentShaderCode)
        
        // 检查shader编译是否成功
        if (programId == 0) {
            android.util.Log.e("OpenGLRenderer", "Failed to create shader program")
        }
        
        // 如果纹理已经设置，现在加载它
        texture?.let { loadTexture(it) }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        // 设置投影矩阵，使用图像宽高比
        val screenRatio = width.toFloat() / height.toFloat()
        val imageRatio = imageAspectRatio
        
        if (imageRatio > screenRatio) {
            // 图像更宽，以宽度为准
            val scale = screenRatio / imageRatio
            Matrix.frustumM(projectionMatrix, 0, -screenRatio, screenRatio, -scale, scale, 1f, 10f)
        } else {
            // 图像更高，以高度为准
            val scale = imageRatio / screenRatio
            Matrix.frustumM(projectionMatrix, 0, -scale, scale, -1f, 1f, 1f, 10f)
        }
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val mesh = meshData ?: return
        
        // 设置相机位置，从下方看向网格以翻转视图
        Matrix.setLookAtM(viewMatrix, 0, cameraX, -cameraY, cameraZ, 0f, 0f, 0f, 0f, -1f, 0f)
        
        // 设置模型矩阵（旋转和缩放）
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -1f)
        // 初始旋转180度，让正面面向相机
        Matrix.rotateM(modelMatrix, 0, 180f, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
        
        // 计算MVP矩阵
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        
        // 使用shader程序
        GLES20.glUseProgram(programId)
        
        // 设置顶点属性
        val positionHandle = GLES20.glGetAttribLocation(programId, "position")
        val texCoordHandle = GLES20.glGetAttribLocation(programId, "texCoord")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "mvpMatrix")
        val textureHandle = GLES20.glGetUniformLocation(programId, "texture")
        
        // 启用顶点属性
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        
        // 设置顶点数据
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
        } else {
            android.util.Log.e("OpenGLRenderer", "Texture ID is 0, texture not loaded properly")
            // 如果纹理没有加载，显示白色
            GLES20.glUniform4f(GLES20.glGetUniformLocation(programId, "color"), 1.0f, 1.0f, 1.0f, 1.0f)
        }
        
        // 设置MVP矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        // 绘制网格
        indexBuffer?.let { buffer ->
            buffer.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, buffer.remaining(), GLES20.GL_UNSIGNED_INT, buffer)
        }
        
        // 禁用顶点属性
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
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
            android.util.Log.e("OpenGLRenderer", "Shader compilation error: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    private fun loadTexture(bitmap: Bitmap) {
        if (textureId != 0) {
            // 如果纹理已经加载，先删除
            val textureIds = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textureIds, 0)
        }
        
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]
        
        android.util.Log.d("OpenGLRenderer", "Loading texture: ${bitmap.width}x${bitmap.height}, format: ${bitmap.config}")
        
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        
        // 设置纹理参数
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        
        // 加载纹理数据
        val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                // Android Bitmap.getPixel() 返回 ARGB 格式，需要转换为 RGBA
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
        
        android.util.Log.d("OpenGLRenderer", "Texture loaded successfully, ID: $textureId")
    }
    
    private fun prepareBuffers() {
        val mesh = meshData ?: return
        
        // 准备顶点缓冲区
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
