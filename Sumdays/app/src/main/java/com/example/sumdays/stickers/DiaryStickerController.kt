package com.example.sumdays.stickers

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.auth.InventoryOwner
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.shop.StickerCatalog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shared reader behavior; each asynchronous result is tied to its original account and diary. */
class DiaryStickerController(
    private val activity: AppCompatActivity,
    private val inventoryOwner: InventoryOwner,
    private val canvas: StickerCanvasView,
    private val button: Button,
    private val visibilityButton: Button,
    remote: StickerRemote? = null,
    private val onAccessDenied: () -> Unit = {}
) : DefaultLifecycleObserver {
    private val actor = SessionManager.getUserId()
    private val inventory = StickerInventoryStore(activity)
    private val repository = if (actor > 0) {
        (remote ?: SessionManager.getToken()?.let(::RetrofitStickerRemote))?.let {
            StickerRepository(inventory, it) { inventoryOwner.isCurrent }
        }
    } else null
    private var owner = -1
    private var entry: DailyEntry? = null
    private var generation = 0
    private var ready = false
    private var editing = false
    private var stickersHidden = activity.savedStateRegistry
        .consumeRestoredStateForKey("diary_sticker_visibility")?.getBoolean("hidden") ?: false
    private var busy = false
    private var loadJob: Job? = null
    private var picker: Dialog? = null
    private var editor: StickerEditorDialog? = null

    init {
        activity.savedStateRegistry.registerSavedStateProvider("diary_sticker_visibility") {
            Bundle().apply { putBoolean("hidden", stickersHidden) }
        }
        activity.lifecycle.addObserver(this)
        button.visibility = View.GONE
        button.setOnClickListener {
            if (!inventoryOwner.requireCurrent(activity)) return@setOnClickListener
            if (repository == null) { message("로그인 후 스티커를 붙일 수 있어요."); return@setOnClickListener }
            if (ready) showPicker() else refresh(openPicker = true)
        }
        visibilityButton.visibility = View.GONE
        visibilityButton.setOnClickListener {
            if (!inventoryOwner.requireCurrent(activity)) return@setOnClickListener
            stickersHidden = !stickersHidden
            updateButton()
        }
        canvas.onStickerClick = { if (!busy && ready) manage(it) }
    }

    fun bind(ownerId: Int, diary: DailyEntry?) {
        val changed = owner != ownerId || entry?.date != diary?.date || entry?.diary != diary?.diary ||
            entry?.isDeleted != diary?.isDeleted || entry?.isAllowed != diary?.isAllowed
        owner = ownerId
        entry = diary
        if (changed) {
            generation++
            loadJob?.cancel()
            loadJob = null
            picker?.dismiss(); editor?.dismiss()
            canvas.stickers = emptyList()
            ready = false
            refresh()
        }
        updateButton()
    }

    fun setEditing(value: Boolean) {
        editing = value
        if (value) { picker?.dismiss(); editor?.dismiss() }
        updateButton()
    }

    private fun hasDiary() = entry?.let { !it.diary.isNullOrBlank() && !it.isDeleted &&
        (owner == actor || it.isAllowed == true) } == true

    private fun updateButton() {
        canvas.visibility = if (editing || stickersHidden) View.GONE else View.VISIBLE
        visibilityButton.visibility = if (hasDiary() && !editing) View.VISIBLE else View.GONE
        visibilityButton.text = if (stickersHidden) "스티커 보이기" else "스티커 가리기"
        button.visibility = if (hasDiary() && !editing) View.VISIBLE else View.GONE
        button.isEnabled = !busy && loadJob?.isActive != true
        button.text = when {
            busy -> "저장 중…"
            loadJob?.isActive == true -> "불러오는 중…"
            !ready && repository != null -> "스티커 새로고침"
            owner == actor -> "스티커 꾸미기"
            else -> "장난치기!"
        }
    }

    private fun current(version: Int) = generation == version && inventoryOwner.isCurrent &&
        !activity.isFinishing && !activity.isDestroyed

    fun refresh(openPicker: Boolean = false) {
        if (!hasDiary() || repository == null || busy || loadJob?.isActive == true ||
            !inventoryOwner.requireCurrent(activity)) return
        val version = generation
        val target = owner
        val date = entry!!.date
        loadJob = activity.lifecycleScope.launch {
            try {
                val stickers = repository.load(target, date)
                if (current(version)) {
                    canvas.stickers = stickers
                    ready = true
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (current(version)) {
                    // An unsynced own diary will be uploaded when its first sticker is confirmed.
                    if (target == actor && error is StickerRequestException && error.code == "STICKER_DIARY_MISSING") {
                        canvas.stickers = emptyList()
                        ready = true
                    } else {
                        ready = false
                        handleError(error)
                    }
                }
            } finally {
                if (current(version)) {
                    loadJob = null
                    updateButton()
                    if (openPicker && ready) showPicker()
                }
            }
        }
        updateButton()
    }

    private fun showPicker() {
        if (!hasDiary() || !ready || busy || editing || !inventoryOwner.requireCurrent(activity)) return
        picker?.dismiss()
        val dialog = BottomSheetDialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        content.addView(label("내 스티커", 20f).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
        content.addView(label("보유한 스티커를 골라 일기에 붙여 보세요.", 13f).apply { setPadding(0, dp(8), 0, dp(12)) })
        val grid = GridLayout(activity).apply { columnCount = 3 }
        StickerCatalog.items.forEachIndexed { index, product ->
            val count = inventory.count(product.id)
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(6), dp(12), dp(6), dp(12))
                setBackgroundResource(R.drawable.bg_shop_item_image)
                isEnabled = count > 0
                alpha = if (count > 0) 1f else 0.4f
                contentDescription = "${product.name}, 보유 ${count}개"
                addView(ImageView(activity).apply { setImageResource(product.imageRes) }, LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(label(product.name, 12f).apply { gravity = Gravity.CENTER })
                addView(label("보유 ${count}개", 12f).apply { gravity = Gravity.CENTER })
                setOnClickListener {
                    if (count > 0 && inventoryOwner.requireCurrent(activity)) {
                        dialog.dismiss()
                        val geometry = StickerGeometry()
                        openEditor(DiarySticker("draft", owner, actor, "나", entry!!.date, product.id,
                            geometry.x, geometry.y, geometry.size, geometry.rotation), isNew = true)
                    }
                }
            }
            grid.addView(cell, GridLayout.LayoutParams(GridLayout.spec(index / 3), GridLayout.spec(index % 3, 1f)).apply {
                width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT; setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        content.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (canvas.stickers.isNotEmpty()) {
            content.addView(Button(activity).apply {
                text = "붙인 스티커 관리 (${canvas.stickers.size})"
                setOnClickListener {
                    dialog.dismiss()
                    val stickers = canvas.stickers
                    AlertDialog.Builder(activity).setTitle("붙인 스티커").setItems(stickers.map {
                        "${StickerCatalog.items.find { product -> product.id == it.stickerId }?.name ?: "스티커"} · ${it.authorName}"
                    }.toTypedArray()) { _, index -> manage(stickers[index]) }.setNegativeButton("닫기", null).show()
                }
            })
        }
        content.addView(Button(activity).apply {
            text = "상점에서 더 보기"
            setOnClickListener {
                if (inventoryOwner.requireCurrent(activity)) {
                    dialog.dismiss()
                    activity.startActivity(Intent(activity, ShopActivity::class.java).putExtra("shop_category", "sticker"))
                }
            }
        })
        dialog.setContentView(ScrollView(activity).apply { addView(content) })
        picker = dialog
        dialog.show()
    }

    private fun openEditor(sticker: DiarySticker, isNew: Boolean) {
        if (!ready || busy || !inventoryOwner.requireCurrent(activity)) return
        val snapshot = entry ?: return
        val version = generation
        editor = StickerEditorDialog(activity, snapshot.diary.orEmpty(), canvas.stickers, sticker, isNew) { geometry, dialog ->
            if (!current(version) || busy || !inventoryOwner.requireCurrent(activity)) { dialog.dismiss(); return@StickerEditorDialog }
            dialog.saving()
            busy = true; updateButton()
            activity.lifecycleScope.launch {
                try {
                    val result = if (isNew) repository!!.place(sticker.ownerId, sticker.date, sticker.stickerId,
                        geometry, snapshot.takeIf { sticker.ownerId == actor }) else repository!!.update(sticker, geometry)
                    if (current(version)) {
                        canvas.stickers = canvas.stickers.filter { it.id != result.id } + listOf(result).filter { it.active }
                        message(if (isNew) "스티커를 붙였어요." else "스티커 위치를 저장했어요.")
                    }
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) {
                    if (current(version)) { ready = false; handleError(error) }
                } finally {
                    dialog.dismiss()
                    finishMutation(version)
                }
            }
        }.also { it.show() }
    }

    private fun manage(sticker: DiarySticker) {
        if (!ready || busy || loadJob?.isActive == true || !inventoryOwner.requireCurrent(activity)) return
        val version = generation
        val choices = mutableListOf<String>()
        if (sticker.authorId == actor) choices += "위치·크기·회전 바꾸기"
        if (sticker.authorId == actor || owner == actor) choices += "삭제"
        if (choices.isEmpty()) { message("${sticker.authorName}님이 붙인 스티커예요."); return }
        AlertDialog.Builder(activity).setTitle("${sticker.authorName}님의 스티커")
            .setItems(choices.toTypedArray()) { _, index ->
                if (!current(version)) return@setItems
                if (choices[index] == "삭제") {
                    AlertDialog.Builder(activity).setTitle("스티커 삭제")
                        .setMessage("일기에서 스티커를 삭제할까요? 사용한 스티커는 돌려받지 않아요.")
                        .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                            if (!current(version) || busy || !inventoryOwner.requireCurrent(activity)) return@setPositiveButton
                            busy = true; updateButton()
                            activity.lifecycleScope.launch {
                                try {
                                    repository!!.remove(sticker)
                                    if (current(version)) { canvas.stickers = canvas.stickers.filter { it.id != sticker.id }; message("스티커를 삭제했어요.") }
                                } catch (error: CancellationException) { throw error
                                } catch (error: Exception) { if (current(version)) { ready = false; handleError(error) }
                                } finally { finishMutation(version) }
                            }
                        }.show()
                } else openEditor(sticker, isNew = false)
            }.setNegativeButton("닫기", null).show()
    }

    private fun finishMutation(version: Int) {
        busy = false
        if (current(version)) updateButton()
        else if (inventoryOwner.isCurrent && !activity.isFinishing && !activity.isDestroyed) {
            // A date change during a save postponed its load until the original request settled.
            refresh()
            updateButton()
        }
    }

    private fun handleError(error: Exception) {
        if (owner != actor && error is StickerRequestException &&
            error.code in setOf("STICKER_FORBIDDEN", "STICKER_DIARY_MISSING")) {
            canvas.stickers = emptyList()
            message(error.message.orEmpty())
            onAccessDenied()
        } else if (inventory.pending().isNotEmpty()) {
            message("저장 결과를 확인하지 못했어요. 스티커 새로고침을 눌러 다시 확인해 주세요.")
        } else message(error.message ?: "스티커를 저장하지 못했어요. 다시 시도해 주세요.")
    }

    private fun label(value: String, size: Float) = TextView(activity).apply { text = value; textSize = size }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun message(value: String) { Toast.makeText(activity, value, Toast.LENGTH_LONG).show() }
    override fun onResume(owner: LifecycleOwner) { if (inventoryOwner.requireCurrent(activity)) refresh() }
    override fun onDestroy(owner: LifecycleOwner) {
        generation++; loadJob?.cancel(); picker?.dismiss(); editor?.dismiss()
        canvas.onStickerClick = null
    }
}
