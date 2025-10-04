package com.ml.shubham0204.depthanything

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.*

/**
 * 自定义OpenGL视图，支持触摸交互
 */
class OpenGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GLSurfaceView(context, attrs) {
    
    private val renderer = OpenGLRenderer(context)
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isRotating = false
    private var isScaling = false
    private var lastDistance = 0f
    
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    
    fun setMeshData(meshData: MeshGenerator.MeshData, texture: android.graphics.Bitmap) {
        renderer.setMeshData(meshData, texture)
    }
    
    fun resetView() {
        renderer.updateCamera(0f, 0f, 2f)
        renderer.updateRotation(0f, 0f)
        renderer.updateScale(1f)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isRotating = true
                isScaling = false
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastDistance = getDistance(event)
                isScaling = true
                isRotating = false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isRotating && event.pointerCount == 1) {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    
                    // 获取当前旋转角度
                    val currentRotationX = renderer.rotationX
                    val currentRotationY = renderer.rotationY
                    
                    // 更新旋转角度，限制在7度以内
                    val newRotationX = (currentRotationX + deltaY * 0.5f).coerceIn(-7f, 7f)
                    val newRotationY = (currentRotationY + deltaX * 0.5f).coerceIn(-7f, 7f)
                    
                    renderer.updateRotation(newRotationX, newRotationY)
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (isScaling && event.pointerCount == 2) {
                    val currentDistance = getDistance(event)
                    if (lastDistance > 0) {
                        val scale = currentDistance / lastDistance
                        val currentScale = renderer.scale
                        val newScale = (currentScale * scale).coerceIn(0.1f, 5f)
                        renderer.updateScale(newScale)
                    }
                    lastDistance = currentDistance
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                isRotating = false
                isScaling = false
            }
        }
        
        return true
    }
    
    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }
}
