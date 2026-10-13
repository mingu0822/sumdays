package com.example.sumdays.stickers

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class StickerEditorDialog(
    activity: AppCompatActivity,
    diaryText: String,
    existing: List<DiarySticker>,
    private var draft: DiarySticker,
    isNew: Boolean,
    private val onSave: (StickerGeometry, StickerEditorDialog) -> Unit
) : Dialog(activity, R.style.Theme_First) {
    private val canvas: StickerCanvasView
    private val save: Button
    private val cancel: Button
    private val size: SeekBar
    private val rotation: SeekBar

    init {
        setContentView(R.layout.dialog_sticker_editor)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val root = findViewById<View>(R.id.stickerEditorTitle).parent as View
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<TextView>(R.id.stickerEditorTitle).text = if (isNew) "스티커 붙이기" else "스티커 옮기기"
        findViewById<TextView>(R.id.stickerEditorHint).text = if (isNew) "붙이면 스티커 1개를 사용해요." else "옮길 때는 스티커가 추가로 사용되지 않아요."
        findViewById<TextView>(R.id.stickerEditorDiary).text = diaryText
        canvas = findViewById(R.id.stickerEditorCanvas)
        canvas.stickers = existing.filter { it.id != draft.id } + draft
        canvas.selectedId = draft.id
        canvas.onGeometryChanged = { draft = draft.withGeometry(it) }
        size = findViewById(R.id.stickerSize)
        rotation = findViewById(R.id.stickerRotation)
        size.progress = (draft.size * 100).roundToInt() - 8
        rotation.progress = draft.rotation.roundToInt() + 180
        fun adjust() {
            draft = draft.withGeometry(draft.geometry().copy(size = (size.progress + 8) / 100f,
                rotation = (rotation.progress - 180).toFloat()))
            canvas.stickers = canvas.stickers.map { if (it.id == draft.id) draft else it }
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) adjust() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        size.setOnSeekBarChangeListener(listener)
        rotation.setOnSeekBarChangeListener(listener)
        cancel = findViewById(R.id.stickerCancel)
        save = findViewById(R.id.stickerConfirm)
        save.text = if (isNew) "붙이기" else "저장"
        cancel.setOnClickListener { dismiss() }
        save.setOnClickListener { onSave(draft.geometry(), this) }
        setOnShowListener {
            canvas.post {
                val bounds = StickerBounds.calculate(canvas.width, canvas.height, draft.geometry())
                val scroll = findViewById<ScrollView>(R.id.stickerEditorScroll)
                scroll.scrollTo(0, (bounds.centerY - scroll.height / 2).toInt().coerceAtLeast(0))
            }
        }
    }

    fun saving() {
        save.isEnabled = false; cancel.isEnabled = false
        size.isEnabled = false; rotation.isEnabled = false
        canvas.isEnabled = false
        save.text = "저장 중…"
        setCancelable(false)
    }
}
