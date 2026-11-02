package com.example.sumdays.stickers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.content.res.AppCompatResources
import com.example.sumdays.shop.StickerCatalog
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class StickerBounds(val centerX: Float, val centerY: Float, val side: Float, val extent: Float) {
    companion object {
        fun calculate(width: Int, height: Int, geometry: StickerGeometry): StickerBounds {
            val radians = Math.toRadians(geometry.rotation.toDouble())
            val rotationFactor = (abs(cos(radians)) + abs(sin(radians))).toFloat()
            val side = (width * geometry.size).coerceAtMost(height / rotationFactor)
            val extent = side * rotationFactor
            return StickerBounds(extent / 2 + (width - extent) * geometry.x,
                extent / 2 + (height - extent) * geometry.y, side, extent)
        }
    }
}

class StickerCanvasView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var stickers: List<DiarySticker> = emptyList()
        set(value) { field = value; contentDescription = "일기에 붙은 스티커 ${value.size}개"; invalidate() }
    var selectedId: String? = null
        set(value) { field = value; invalidate() }
    var onStickerClick: ((DiarySticker) -> Unit)? = null
    var onGeometryChanged: ((StickerGeometry) -> Unit)? = null
    private val images = mutableMapOf<Int, Drawable>()
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(219, 84, 52); style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private var touched: DiarySticker? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (sticker in stickers) {
            if (!sticker.active || !sticker.geometry().isValid()) continue
            val resource = StickerCatalog.items.find { it.id == sticker.stickerId }?.imageRes ?: continue
            val drawable = images.getOrPut(resource) { AppCompatResources.getDrawable(context, resource)!!.mutate() }
            val bounds = StickerBounds.calculate(width, height, sticker.geometry())
            canvas.save()
            canvas.translate(bounds.centerX, bounds.centerY)
            canvas.rotate(sticker.rotation)
            val half = bounds.side / 2
            drawable.setBounds((-half).toInt(), (-half).toInt(), half.toInt(), half.toInt())
            drawable.draw(canvas)
            if (selectedId == sticker.id) canvas.drawRoundRect(-half, -half, half, half, 8f, 8f, outline)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touched = stickers.asReversed().firstOrNull { sticker ->
                    (selectedId == null || selectedId == sticker.id) && hit(sticker, event.x, event.y)
                } ?: return false
                downX = event.x; downY = event.y; moved = false
                if (selectedId != null) parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val sticker = touched ?: return false
                if (abs(event.x - downX) + abs(event.y - downY) > slop) moved = true
                if (selectedId == sticker.id && moved) {
                    val bounds = StickerBounds.calculate(width, height, sticker.geometry())
                    val next = sticker.geometry().copy(
                        x = ((bounds.centerX + event.x - downX - bounds.extent / 2) /
                            (width - bounds.extent).coerceAtLeast(1f)).coerceIn(0f, 1f),
                        y = ((bounds.centerY + event.y - downY - bounds.extent / 2) /
                            (height - bounds.extent).coerceAtLeast(1f)).coerceIn(0f, 1f))
                    stickers = stickers.map { if (it.id == sticker.id) it.withGeometry(next) else it }
                    onGeometryChanged?.invoke(next)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val sticker = touched ?: return false
                if (!moved) {
                    performClick()
                    if (selectedId == null) onStickerClick?.invoke(sticker)
                }
                touched = null
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touched = null
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    private fun hit(sticker: DiarySticker, x: Float, y: Float): Boolean {
        if (!sticker.active || !sticker.geometry().isValid()) return false
        val bounds = StickerBounds.calculate(width, height, sticker.geometry())
        val radians = Math.toRadians(-sticker.rotation.toDouble())
        val dx = x - bounds.centerX; val dy = y - bounds.centerY
        val localX = dx * cos(radians) - dy * sin(radians)
        val localY = dx * sin(radians) + dy * cos(radians)
        val half = bounds.side / 2 + 6 * resources.displayMetrics.density
        return abs(localX) <= half && abs(localY) <= half
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
