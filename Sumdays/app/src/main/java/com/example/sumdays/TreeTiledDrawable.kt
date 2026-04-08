// TreeTiledDrawable.kt
package com.example.sumdays.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

class TreeTiledDrawable(
    private var bitmap: Bitmap
) : Drawable() {

    private val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@TreeTiledDrawable.shader }
    private val darkPaint = Paint().apply { color = Color.BLACK }
    private val localMatrix = Matrix()

    private var totalScrollY: Float = 0f

    fun setScroll(totalY: Float, viewWidth: Int = bounds.width()) {
        totalScrollY = totalY

        // 화면 폭에 맞게 스케일
        val scale = viewWidth.toFloat() / bitmap.width.toFloat()

        // '캔버스(px)' 단위로 모듈러
        val tileHCanvas = bitmap.height * scale
        val offsetCanvas = ((totalScrollY % tileHCanvas) + tileHCanvas) % tileHCanvas

        localMatrix.reset()
        localMatrix.setScale(scale, scale)
        // 3) 캔버스 단위로 그대로 이동
        localMatrix.postTranslate(0f, -offsetCanvas)

        shader.setLocalMatrix(localMatrix)
        invalidateSelf()
    }



    /** 특정 지점에서 배경 타일 교체 */
    fun swapTile(newBitmap: Bitmap) {
        bitmap = newBitmap
        // 새 Bitmap으로 새로운 Shader 재구성
        val newShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        newShader.setLocalMatrix(localMatrix)
        paint.shader = newShader
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        // 타일 그리기
        canvas.drawRect(bounds, paint)
    }
    override fun setAlpha(alpha: Int) { /* not used */ }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
