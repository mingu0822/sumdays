package com.example.sumdays.settings.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.data.style.UserStyle
import com.example.sumdays.databinding.ItemStyleAddCardBinding
import com.example.sumdays.databinding.ItemStyleCardBinding


class StyleCardAdapter(
    private val onSelect: (UserStyle?) -> Unit,
    private val onRename: (UserStyle, String) -> Unit,
    private val onDelete: (UserStyle) -> Unit,
    private val onAdd: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val items = mutableListOf<UserStyle>()
    private var activeId: Long? = null

    companion object {
        private const val TYPE_STYLE = 0
        private const val TYPE_ADD = 1
        private const val DEFAULT_STYLE_MAX_ID = 3L
    }

    fun submit(list: List<UserStyle>, activeStyleId: Long?) {
        items.clear()
        items.addAll(list)
        this.activeId = activeStyleId
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) =
        if (position == items.size) TYPE_ADD else TYPE_STYLE

    override fun getItemCount(): Int = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_STYLE) {
            StyleVH(ItemStyleCardBinding.inflate(inf, parent, false))
        } else {
            AddVH(ItemStyleAddCardBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is StyleVH) holder.bind(items[position])
        else (holder as AddVH).bind()
    }

    inner class StyleVH(private val b: ItemStyleCardBinding) : RecyclerView.ViewHolder(b.root) {
        private var flipped = false

        fun bind(style: UserStyle) {
            // 기본 스타일 메뉴 삭제
            val isDefault = style.styleId <= DEFAULT_STYLE_MAX_ID
            b.moreButton.visibility = if (isDefault) View.INVISIBLE else View.VISIBLE

            // 항상 초기 상태는 앞면
            b.front.visibility = View.VISIBLE
            b.back.visibility = View.GONE

            // 선택 상태에 따른 테두리 강조 효과
            if (style.styleId == activeId) {
                b.cardRoot.strokeWidth = (4 * b.root.resources.displayMetrics.density).toInt() // 2dp
            } else {
                b.cardRoot.strokeWidth = 0
            }

            // 기본 스타일 이름
            b.styleName.text = style.styleName

            // 샘플 일기 표시
            b.sampleDiary.text = style.sampleDiary.ifBlank { "샘플 생성 중..." }

            // 프롬프트
            val p = style.stylePrompt
            b.promptBody.text = buildString {
                // 0. 스타일 컨셉
                p.character_concept.let { append("• 스타일 컨셉: $it\n") }
                // 1. 기본 언어 구조
                // 2. 캐릭터 시그니처
                p.sentence_endings.takeIf { it.isNotEmpty() }?.let { append("• 종결 어미: ${it.joinToString(", ")}\n") }
                p.punctuation_style.let { append("• 문장부호 스타일: $it\n") }
                p.special_syntax.let { append("• 특수 문법/밈: $it\n") }
                // 3. 어휘
                p.lexical_choice.let { append("• 어휘 선택: $it\n") }
            }

            // flip
            b.flipContainer.setOnClickListener {
                flipped = !flipped
                b.front.visibility = if (flipped) View.GONE else View.VISIBLE
                b.back.visibility = if (flipped) View.VISIBLE else View.GONE
            }

            // ⋮ 메뉴
            b.moreButton.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    menuInflater.inflate(R.menu.settings_menu_style_card, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_rename -> {
                                showRenameDialog(style)
                                true
                            }
                            R.id.action_delete -> { onDelete(style); true }
                            else -> false
                        }
                    }
                }.show()
            }

            // 선택 상태(외부 버튼에서 처리하므로 카드에는 시각 피드백만)
            val isActive = (style.styleId == activeId)
            b.cardRoot.strokeWidth = if (isActive) (4 * b.root.resources.displayMetrics.density).toInt() else 0
            b.cardRoot.strokeColor = 0xFFd03b2f.toInt()
        }

        private fun showRenameDialog(style: UserStyle) {
            val ctx = b.root.context
            val inflater = LayoutInflater.from(ctx)
            val view = inflater.inflate(R.layout.dialog_rename_style, null)

            val input = view.findViewById<EditText>(R.id.edit_style_name_content).apply {
                setText(style.styleName)
                setSelection(text.length)
            }

            AlertDialog.Builder(ctx)
                .setView(view)
                .setPositiveButton("확인") { _, _ ->
                    val newName = input.text.toString()
                        .ifBlank { defaultName(adapterPosition) }
                    onRename(style, newName)
                }
                .setNegativeButton("취소", null)
                .show()
        }


        private fun defaultName(idx: Int) = "스타일 ${idx + 1}"
    }

    inner class AddVH(private val b: ItemStyleAddCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind() { b.addRoot.setOnClickListener { onAdd() } }
    }

    /** 외부에서 현재 스냅된 포지션의 스타일을 얻고 싶을 때 */
    fun styleAt(position: Int): UserStyle? =
        if (position in 0 until items.size) items[position] else null

    fun updateActiveStyleId(activeId: Long?) {
        this.activeId = activeId
        notifyDataSetChanged() // 즉시 UI 갱신
    }

}
