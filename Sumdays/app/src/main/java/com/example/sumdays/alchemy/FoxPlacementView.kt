package com.example.sumdays.alchemy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.customize.FoxComposition
import com.example.sumdays.customize.FoxItemPlacement
import kotlin.math.atan2
import kotlin.math.hypot

/** A fixed workspace prevents the fox from jumping or resizing during a drag. */
class FoxPlacementView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private lateinit var composition: FoxComposition
    private var workspace = RectF()
    private val viewport = Matrix()
    private val inverseViewport = Matrix()
    private var viewportScale = 1f
    private var layers = emptyList<FoxItemPlacement>()
    var selectedItemId: Int? = null
        private set
    val placements: List<FoxItemPlacement> get() = layers.toList()
    val selectedPlacement: FoxItemPlacement? get() = layers.find { it.itemId == selectedItemId }
    var onChanged: (() -> Unit)? = null
    private val undoStack = ArrayDeque<List<FoxItemPlacement>>()
    private val redoStack = ArrayDeque<List<FoxItemPlacement>>()
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    private var gestureStart: List<FoxItemPlacement>? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var lastAngle = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "여우 배치 캔버스. 아이템을 끌어서 이동하고 두 손가락으로 크기와 회전을 조절하세요."
    }

    fun setFox(fox: CompleteFox) {
        composition = FoxComposition(context, fox)
        layers = composition.placements
        workspace = composition.bounds(composition.defaults + layers).apply {
            inset(-composition.base.width * 0.3f, -composition.base.height * 0.3f)
            // Leave equal room on both sides of the face, including tall hats and wide scarves.
            val centerX = composition.base.width / 2f
            val centerY = composition.base.height / 2f
            val halfWidth = maxOf(centerX - left, right - centerX)
            val halfHeight = maxOf(centerY - top, bottom - centerY)
            set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
        }
        selectedItemId = layers.lastOrNull()?.itemId
        undoStack.clear()
        redoStack.clear()
        updateViewport()
        changed()
    }

    fun select(itemId: Int) {
        if (layers.any { it.itemId == itemId }) {
            selectedItemId = itemId
            changed()
        }
    }

    fun beginEdit() { gestureStart = layers.toList() }

    fun endEdit() {
        gestureStart?.takeIf { it != layers }?.let {
            undoStack.addLast(it)
            if (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
        gestureStart = null
        changed()
    }

    fun undo() {
        if (!canUndo) return
        redoStack.addLast(layers)
        layers = undoStack.removeLast()
        changed()
    }

    fun redo() {
        if (!canRedo) return
        undoStack.addLast(layers)
        layers = redoStack.removeLast()
        changed()
    }

    fun transformSelected(scale: Float? = null, rotation: Float? = null) {
        val current = selectedPlacement ?: return
        updateSelected(current.copy(scale = scale ?: current.scale, rotation = rotation ?: current.rotation))
    }

    fun moveLayer(forward: Boolean) {
        val index = layers.indexOfFirst { it.itemId == selectedItemId }
        val target = index + if (forward) 1 else -1
        if (index < 0 || target !in layers.indices) return
        beginEdit()
        layers = layers.toMutableList().apply { add(target, removeAt(index)) }
        endEdit()
    }

    fun resetSelected() {
        val original = composition.defaults.find { it.itemId == selectedItemId } ?: return
        beginEdit()
        updateSelected(original)
        endEdit()
    }

    private fun updateSelected(placement: FoxItemPlacement) {
        var adjusted = placement.copy(
            scale = placement.scale.coerceIn(0.25f, 2f),
            rotation = ((placement.rotation + 180f) % 360f + 360f) % 360f - 180f
        )
        var bounds = composition.itemBounds(adjusted)
        val fit = minOf(1f, workspace.width() / bounds.width(), workspace.height() / bounds.height())
        adjusted = adjusted.copy(scale = adjusted.scale * fit)
        bounds = composition.itemBounds(adjusted)
        val dx = when {
            bounds.left < workspace.left -> workspace.left - bounds.left
            bounds.right > workspace.right -> workspace.right - bounds.right
            else -> 0f
        }
        val dy = when {
            bounds.top < workspace.top -> workspace.top - bounds.top
            bounds.bottom > workspace.bottom -> workspace.bottom - bounds.bottom
            else -> 0f
        }
        adjusted = adjusted.copy(
            centerX = adjusted.centerX + dx / composition.base.width,
            centerY = adjusted.centerY + dy / composition.base.height
        )
        layers = layers.map { if (it.itemId == adjusted.itemId) adjusted else it }
        changed()
    }

    private fun changed() {
        invalidate()
        onChanged?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateViewport() }

    private fun updateViewport() {
        if (workspace.isEmpty || width == 0 || height == 0) return
        val padding = 12f * density
        viewportScale = minOf((width - padding * 2) / workspace.width(), (height - padding * 2) / workspace.height())
            .coerceAtLeast(0.001f)
        viewport.setScale(viewportScale, viewportScale)
        viewport.postTranslate(
            width / 2f - composition.base.width * viewportScale / 2f,
            height / 2f - composition.base.height * viewportScale / 2f
        )
        viewport.invert(inverseViewport)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::composition.isInitialized) return
        canvas.drawColor(Color.rgb(245, 241, 237))
        val saved = canvas.save()
        canvas.concat(viewport)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRect(workspace, paint)
        paint.color = Color.rgb(239, 233, 227)
        paint.strokeWidth = 1f / viewportScale
        val grid = composition.base.width / 8f
        var x = workspace.left
        while (x < workspace.right) {
            canvas.drawLine(x, workspace.top, x, workspace.bottom, paint)
            x += grid
        }
        var y = workspace.top
        while (y < workspace.bottom) {
            canvas.drawLine(workspace.left, y, workspace.right, y, paint)
            y += grid
        }
        composition.draw(canvas, layers)
        selectedPlacement?.let { selected ->
            val bitmap = composition.bitmaps.getValue(selected.itemId)
            val corners = floatArrayOf(0f, 0f, bitmap.width.toFloat(), 0f,
                bitmap.width.toFloat(), bitmap.height.toFloat(), 0f, bitmap.height.toFloat())
            composition.matrix(selected).mapPoints(corners)
            paint.color = Color.rgb(208, 59, 47)
            paint.strokeWidth = 2f * density / viewportScale
            for (index in 0..3) {
                val next = (index + 1) % 4
                canvas.drawLine(corners[index * 2], corners[index * 2 + 1], corners[next * 2], corners[next * 2 + 1], paint)
                canvas.drawCircle(corners[index * 2], corners[index * 2 + 1], 3f * density / viewportScale, paint)
            }
        }
        canvas.restoreToCount(saved)
    }

    private fun point(event: MotionEvent, index: Int = 0): FloatArray =
        floatArrayOf(event.getX(index), event.getY(index)).also(inverseViewport::mapPoints)

    private fun hit(x: Float, y: Float): Int? {
        // Transparent parts of a large hat must not steal touches from an item below it.
        for (layer in layers.asReversed()) {
            val local = floatArrayOf(x, y)
            val inverse = Matrix()
            composition.matrix(layer).invert(inverse)
            inverse.mapPoints(local)
            val bitmap = composition.bitmaps.getValue(layer.itemId)
            if (local[0] >= 0 && local[1] >= 0 && local[0] < bitmap.width && local[1] < bitmap.height &&
                Color.alpha(bitmap.getPixel(local[0].toInt(), local[1].toInt())) > 24) return layer.itemId
        }
        return selectedPlacement?.takeIf { composition.itemBounds(it).contains(x, y) }?.itemId
    }

    private fun trackPointers(event: MotionEvent) {
        val first = point(event)
        if (event.pointerCount >= 2) {
            val second = point(event, 1)
            lastX = (first[0] + second[0]) / 2f
            lastY = (first[1] + second[1]) / 2f
            lastSpan = hypot(second[0] - first[0], second[1] - first[1])
            lastAngle = Math.toDegrees(atan2(second[1] - first[1], second[0] - first[0]).toDouble()).toFloat()
        } else {
            lastX = first[0]
            lastY = first[1]
            lastSpan = 0f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::composition.isInitialized) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = point(event)
                selectedItemId = hit(point[0], point[1])
                beginEdit()
                trackPointers(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                changed()
            }
            MotionEvent.ACTION_POINTER_DOWN -> trackPointers(event)
            MotionEvent.ACTION_MOVE -> {
                val oldX = lastX
                val oldY = lastY
                val oldSpan = lastSpan
                val oldAngle = lastAngle
                trackPointers(event)
                selectedPlacement?.let { current ->
                    val multiTouch = event.pointerCount >= 2 && oldSpan > 0f
                    updateSelected(current.copy(
                        centerX = current.centerX + (lastX - oldX) / composition.base.width,
                        centerY = current.centerY + (lastY - oldY) / composition.base.height,
                        scale = current.scale * if (multiTouch) lastSpan / oldSpan else 1f,
                        rotation = current.rotation + if (multiTouch) lastAngle - oldAngle else 0f
                    ))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Anchor to a remaining pointer so lifting a finger cannot move the item.
                val remaining = if (event.actionIndex == 0) 1 else 0
                val point = point(event, remaining)
                lastX = point[0]
                lastY = point[1]
                lastSpan = 0f
            }
            MotionEvent.ACTION_UP -> {
                endEdit()
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureStart?.let { layers = it }
                gestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
                changed()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
