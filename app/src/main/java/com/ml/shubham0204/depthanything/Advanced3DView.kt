package com.ml.shubham0204.depthanything

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * 高级3D视图
 * 1. 360度自由旋转
 * 2. 平滑的缩放和导航
 * 3. 惯性滚动
 * 4. 自动内容适配
 */
class Advanced3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GLSurfaceView(context, attrs) {
    
    private val renderer = Advanced3DRenderer(context)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    
    // 触摸状态
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isRotating = false
    private var isDragging = false
    
    // 惯性滚动
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastUpdateTime = 0L
    private val friction = 0.95f
    private val maxVelocity = 10f
    
    // 相机控制
    private var targetRotationX = 0f
    private var targetRotationY = 0f
    private var targetScale = 1f
    private var targetCameraX = 0f
    private var targetCameraY = 0f
    private var targetCameraZ = 2f
    
    // 动画插值
    private val animationSpeed = 0.1f
    
    // 动画处理器
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            updateAnimation()
            animationHandler.postDelayed(this, 16) // 60fps
        }
    }
    
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        
        // 设置初始状态
        resetView()
        
        // 启动动画循环
        animationHandler.post(animationRunnable)
    }
    
    fun setMeshData(meshData: MeshGenerator.MeshData, texture: android.graphics.Bitmap) {
        renderer.setMeshData(meshData, texture)
    }
    
    fun resetView() {
        targetRotationX = 0f
        targetRotationY = 0f
        targetScale = 1f
        targetCameraX = 0f
        targetCameraY = 0f
        targetCameraZ = 2f
        
        velocityX = 0f
        velocityY = 0f
        
        renderer.resetView()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isRotating = true
                isDragging = false
                velocityX = 0f
                velocityY = 0f
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isRotating && event.pointerCount == 1) {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    
                    // 计算旋转增量
                    val rotationSensitivity = 0.5f
                    val deltaRotationX = deltaY * rotationSensitivity
                    val deltaRotationY = deltaX * rotationSensitivity
                    
                    // 更新目标旋转角度（限制在±7度）
                    targetRotationX = (targetRotationX + deltaRotationX).coerceIn(-7f, 7f)
                    targetRotationY = (targetRotationY + deltaRotationY).coerceIn(-7f, 7f)
                    
                    // 计算速度（用于惯性滚动）
                    val currentTime = System.currentTimeMillis()
                    if (lastUpdateTime > 0) {
                        val deltaTime = (currentTime - lastUpdateTime) / 1000f
                        if (deltaTime > 0) {
                            velocityX = (deltaRotationY / deltaTime).coerceIn(-maxVelocity, maxVelocity)
                            velocityY = (deltaRotationX / deltaTime).coerceIn(-maxVelocity, maxVelocity)
                        }
                    }
                    lastUpdateTime = currentTime
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (isDragging && event.pointerCount == 1) {
                    // 相机平移
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    
                    val panSensitivity = 0.01f
                    targetCameraX += deltaX * panSensitivity
                    targetCameraY -= deltaY * panSensitivity
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            
            MotionEvent.ACTION_UP -> {
                isRotating = false
                isDragging = false
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 双指操作开始
                isRotating = false
                isDragging = true
            }
            
            MotionEvent.ACTION_POINTER_UP -> {
                isDragging = false
            }
        }
        
        return true
    }
    
    // 动画更新方法，在触摸事件中调用
    private fun updateAnimation() {
        // 应用惯性滚动（限制在±7度）
        if (!isRotating && !isDragging) {
            if (abs(velocityX) > 0.1f || abs(velocityY) > 0.1f) {
                targetRotationX = (targetRotationX + velocityY).coerceIn(-7f, 7f)
                targetRotationY = (targetRotationY + velocityX).coerceIn(-7f, 7f)
                
                // 应用摩擦力
                velocityX *= friction
                velocityY *= friction
            }
        }
        
        // 平滑插值到目标值
        val currentRotationX = renderer.rotationX
        val currentRotationY = renderer.rotationY
        val currentScale = renderer.scale
        val currentCameraX = renderer.cameraX
        val currentCameraY = renderer.cameraY
        val currentCameraZ = renderer.cameraZ
        
        val newRotationX = lerp(currentRotationX, targetRotationX, animationSpeed).coerceIn(-7f, 7f)
        val newRotationY = lerp(currentRotationY, targetRotationY, animationSpeed).coerceIn(-7f, 7f)
        val newScale = lerp(currentScale, targetScale, animationSpeed)
        val newCameraX = lerp(currentCameraX, targetCameraX, animationSpeed)
        val newCameraY = lerp(currentCameraY, targetCameraY, animationSpeed)
        val newCameraZ = lerp(currentCameraZ, targetCameraZ, animationSpeed)
        
        // 更新渲染器
        renderer.updateRotation(newRotationX, newRotationY)
        renderer.updateScale(newScale)
        renderer.updateCamera(newCameraX, newCameraY, newCameraZ)
    }
    
    private fun lerp(start: Float, end: Float, factor: Float): Float {
        return start + (end - start) * factor
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            targetScale = (targetScale * scaleFactor).coerceIn(0.1f, 10f)
            return true
        }
    }
    
    // 公共方法用于外部控制
    fun setRotation(x: Float, y: Float) {
        targetRotationX = x
        targetRotationY = y
    }
    
    fun setScale(scale: Float) {
        targetScale = scale.coerceIn(0.1f, 10f)
    }
    
    fun setCameraPosition(x: Float, y: Float, z: Float) {
        targetCameraX = x
        targetCameraY = y
        targetCameraZ = z
    }
    
    fun getCurrentRotation(): Pair<Float, Float> {
        return Pair(renderer.rotationX, renderer.rotationY)
    }
    
    fun getCurrentScale(): Float {
        return renderer.scale
    }
    
    fun getCurrentCameraPosition(): Triple<Float, Float, Float> {
        return Triple(renderer.cameraX, renderer.cameraY, renderer.cameraZ)
    }
    
    // 清理资源
    fun cleanup() {
        animationHandler.removeCallbacks(animationRunnable)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}
