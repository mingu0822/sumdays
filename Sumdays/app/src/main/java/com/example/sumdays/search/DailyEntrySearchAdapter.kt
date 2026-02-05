package com.example.sumdays.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.data.DailyEntry

class DailyEntrySearchAdapter(
    private val onClick: (DailyEntry) -> Unit
) : ListAdapter<DailyEntry, DailyEntrySearchAdapter.VH>(diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_entry_search, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onClick: (DailyEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvIcons: TextView = itemView.findViewById(R.id.tvIcons)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvPreview)
        private val tvKeywords: TextView = itemView.findViewById(R.id.tvKeywords)

        private var current: DailyEntry? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(entry: DailyEntry) {
            current = entry

            tvDate.text = entry.date

            // 아이콘은 null일 수 있으니까 안전하게
            val emotion = entry.emotionIcon.orEmpty()
            val theme = entry.themeIcon.orEmpty()
            tvIcons.text = listOf(emotion, theme)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            // 미리보기(일기 없으면 AI 코멘트라도 보여주기)
            val preview = when {
                !entry.diary.isNullOrBlank() -> entry.diary!!.trim()
                !entry.aiComment.isNullOrBlank() -> entry.aiComment!!.trim()
                else -> "(내용 없음)"
            }
            tvPreview.text = preview

            // keywords는 ';'로 저장된다고 했으니 그대로 보여주거나 예쁘게 바꾸기
            tvKeywords.text = if (entry.keywords.isNullOrBlank()) {
                ""
            } else {
                "키워드: ${entry.keywords}"
            }
            tvKeywords.visibility = if (tvKeywords.text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private val diff = object : DiffUtil.ItemCallback<DailyEntry>() {
            override fun areItemsTheSame(oldItem: DailyEntry, newItem: DailyEntry): Boolean {
                // date가 PK라서 이게 id 역할
                return oldItem.date == newItem.date
            }

            override fun areContentsTheSame(oldItem: DailyEntry, newItem: DailyEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
