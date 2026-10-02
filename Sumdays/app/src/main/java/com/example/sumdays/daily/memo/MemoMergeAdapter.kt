package com.example.sumdays.daily.memo

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.DragShadowBuilder
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.data.Memo
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.settings.prefs.UserStatsPrefs
import kotlinx.coroutines.CoroutineScope

class MemoMergeAdapter(
    private val context: Context, // toast 띄우기 위해서 추가
    private val memoList: MutableList<Memo>,
    private val scope: CoroutineScope,
    private val onAllMergesDone: () -> Unit,
    private val useStableIds: Boolean = true,
    private val userStatsPrefs: UserStatsPrefs,
    private val userStyleDao: UserStyleDao,
    private val onMarbleChanged: () -> Unit = {},
) : RecyclerView.Adapter<MemoMergeAdapter.VH>() {

    private val coordinator = MemoMergeCoordinator(
        memos = memoList,
        scope = scope,
        gateway = MemoMergeRepository(context, userStatsPrefs, userStyleDao),
        changed = { index ->
            if (index == null) notifyDataSetChanged()
            else notifyItemChanged(index, memoList[index].content)
        },
        allMerged = onAllMergesDone,
        failed = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        marbleChanged = onMarbleChanged,
    )
    val isMerging: Boolean get() = coordinator.isMerging
    val lastMood: String? get() = coordinator.lastMood

    init {
        if (useStableIds) {
            try {
                setHasStableIds(true)
            } catch (_: Throwable) {
                // 테스트 환경에서의 NPE 방지
            }
        }
    }

    override fun getItemId(position: Int): Long =
        memoList[position].id.toLong()

    fun getMemoContent(index: Int): String {
        return memoList[index].content
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memo, parent, false)

        val memoText = v.findViewById<TextView>(R.id.memo_text)
        val params = memoText.layoutParams
        params.width = dp(parent.context, 190)
        memoText.minHeight = dp(parent.context, 40)
        memoText.layoutParams = params

        return VH(v)
    }

    private fun dp(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.itemView.alpha = 1.0f
        holder.itemView.scaleX = 1.0f
        holder.itemView.scaleY = 1.0f
        val memo = memoList[position]
        holder.bind(memo)

        holder.itemView.isLongClickable = true
        holder.itemView.setOnLongClickListener { v ->
            if (isMerging) return@setOnLongClickListener false
            val fromIndex = holder.adapterPosition
            if (fromIndex == RecyclerView.NO_POSITION) return@setOnLongClickListener false

            val data = ClipData.newPlainText("memo", "drag")
            val shadow = DragShadowBuilder(v)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, shadow, fromIndex, 0)
            } else {
                @Suppress("DEPRECATION")
                v.startDrag(data, shadow, fromIndex, 0)
            }
            v.alpha = 0.6f
            true
        }

        holder.itemView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true

                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.scaleX = 1.03f
                    view.scaleY = 1.03f
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    view.scaleX = 1f
                    view.scaleY = 1f
                    true
                }

                DragEvent.ACTION_DROP -> {
                    if (isMerging) return@setOnDragListener false
                    view.scaleX = 1f
                    view.scaleY = 1f

                    val fromIndex = event.localState as? Int ?: return@setOnDragListener false
                    if (fromIndex !in memoList.indices) return@setOnDragListener false
                    val toIndex = holder.adapterPosition
                    if (toIndex == RecyclerView.NO_POSITION) return@setOnDragListener false

                    if (fromIndex != toIndex) {
                        coordinator.merge(fromIndex, toIndex)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    holder.itemView.alpha = 1f
                    true
                }

                else -> false
            }
        }
    }

    override fun getItemCount(): Int = memoList.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val content: TextView = itemView.findViewById(R.id.memo_text)
        private val timestamp: TextView = itemView.findViewById(R.id.memo_time)

        fun bind(m: Memo) {
            content.text = m.content
            timestamp.text = m.timestamp
        }

        fun updateTextOnly(text: String) {
            content.text = text
        }
    }

    fun undoLastMerge(): Boolean = coordinator.undoLastMerge()

    suspend fun mergeAllMemo(): String = coordinator.mergeAll()

    suspend fun generateMood(diary: String): String? = coordinator.generateMood(diary)

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val partialText = payloads.last() as String
            holder.updateTextOnly(partialText)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}
