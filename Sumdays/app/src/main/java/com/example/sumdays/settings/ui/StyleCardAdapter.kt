package com.example.sumdays.settings.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.data.style.UserStyle
import com.example.sumdays.databinding.ItemStyleAddCardBinding
import com.example.sumdays.databinding.ItemStyleCardBinding
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository

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

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_ADD else TYPE_STYLE
    }

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
        if (holder is StyleVH) {
            holder.bind(items[position])
        } else {
            (holder as AddVH).bind()
        }
    }

    inner class StyleVH(private val b: ItemStyleCardBinding) : RecyclerView.ViewHolder(b.root) {
        private var flipped = false

        fun bind(style: UserStyle) {
            val context = b.root.context

            val themeKey = ThemePrefs.getTheme(context)
            val theme = ThemeRepository.ownedThemes[themeKey] ?: return

            val primaryColor = ContextCompat.getColor(context, theme.textPrimaryColor)
            val blockColor = ContextCompat.getColor(context, theme.blockColor)
            val buttonColor = ContextCompat.getColor(context, theme.buttonColor)

            val isDefault = style.styleId <= DEFAULT_STYLE_MAX_ID
            val isActive = style.styleId == activeId

            b.moreButton.visibility = if (isDefault) View.INVISIBLE else View.VISIBLE

            b.front.visibility = View.VISIBLE
            b.back.visibility = View.GONE
            flipped = false

            b.cardRoot.setCardBackgroundColor(blockColor)
            b.cardRoot.strokeWidth =
                if (isActive) (4 * b.root.resources.displayMetrics.density).toInt() else 0
            b.cardRoot.strokeColor = primaryColor

            b.styleName.setTextColor(primaryColor)
            b.sampleDiary.setTextColor(primaryColor)
            b.promptBody.setTextColor(primaryColor)

            b.styleName.text = style.styleName
            b.sampleDiary.text = style.sampleDiary.ifBlank { "샘플 생성 중..." }

            val p = style.stylePrompt
            b.promptBody.text = buildString {
                p.character_concept.let { append("• 스타일 컨셉: $it\n") }
                p.sentence_endings.takeIf { it.isNotEmpty() }
                    ?.let { append("• 종결 어미: ${it.joinToString(", ")}\n") }
                p.punctuation_style.let { append("• 문장부호 스타일: $it\n") }
                p.special_syntax.let { append("• 특수 문법/밈: $it\n") }
                p.lexical_choice.let { append("• 어휘 선택: $it\n") }
            }

            b.flipContainer.setOnClickListener {
                flipped = !flipped
                b.front.visibility = if (flipped) View.GONE else View.VISIBLE
                b.back.visibility = if (flipped) View.VISIBLE else View.GONE
            }

            b.moreButton.setColorFilter(primaryColor)

            b.moreButton.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    menuInflater.inflate(R.menu.settings_menu_style_card, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_rename -> {
                                showRenameDialog(style)
                                true
                            }
                            R.id.action_delete -> {
                                onDelete(style)
                                true
                            }
                            else -> false
                        }
                    }
                }.show()
            }

            b.cardRoot.setOnClickListener {
                onSelect(style)
            }
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
                    val newName = input.text.toString().ifBlank { defaultName(adapterPosition) }
                    onRename(style, newName)
                }
                .setNegativeButton("취소", null)
                .show()
        }

        private fun defaultName(idx: Int): String = "스타일 ${idx + 1}"
    }

    inner class AddVH(private val b: ItemStyleAddCardBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind() {
            val context = b.root.context
            val themeKey = ThemePrefs.getTheme(context)
            val theme = ThemeRepository.ownedThemes[themeKey] ?: return

            val primaryColor = ContextCompat.getColor(context, theme.textPrimaryColor)
            val blockColor = ContextCompat.getColor(context, theme.blockColor)

            b.addRoot.setCardBackgroundColor(blockColor)

            b.addRoot.setOnClickListener { onAdd() }
        }
    }

    fun styleAt(position: Int): UserStyle? {
        return if (position in 0 until items.size) items[position] else null
    }

    fun updateActiveStyleId(activeId: Long?) {
        this.activeId = activeId
        notifyDataSetChanged()
    }
}