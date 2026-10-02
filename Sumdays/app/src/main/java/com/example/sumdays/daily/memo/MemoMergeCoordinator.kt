package com.example.sumdays.daily.memo

import com.example.sumdays.data.Memo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Screen-local merge state. All list changes and callbacks run on Main.
 * The gateway is injectable so failures/partial responses need no real server.
 */
class MemoMergeCoordinator(
    private val memos: MutableList<Memo>,
    private val scope: CoroutineScope,
    private val gateway: MemoMergeGateway,
    private val changed: (Int?) -> Unit,
    private val allMerged: () -> Unit,
    private val failed: (String) -> Unit,
    private val marbleChanged: () -> Unit,
) {
    private data class Undo(val memos: List<Memo>)
    private val undo = ArrayDeque<Undo>()
    private val busy = AtomicBoolean(false)
    val isMerging: Boolean get() = busy.get()
    var lastMood: String? = null
        private set

    fun merge(from: Int, to: Int) {
        if (!scope.isActive) return
        if (from == to || from !in memos.indices || to !in memos.indices) return
        if (!busy.compareAndSet(false, true)) return
        val previous = Undo(memos.toList())
        val target = if (from < to) to - 1 else to
        val payload = listOf(
            MemoPayload(content = memos[to].content, order = 0),
            MemoPayload(content = memos[from].content, order = 1),
        )
        scope.launch(Dispatchers.Main.immediate) {
            try {
                memos.removeAt(from)
                changed(null)
                val result = gateway.merge(payload, false) { partial ->
                    withContext(Dispatchers.Main.immediate) {
                        memos[target] = memos[target].copy(content = partial)
                        changed(target)
                    }
                }
                memos[target] = memos[target].copy(content = result.text)
                undo.addLast(previous)
                changed(target)
                if (memos.size <= 1) allMerged()
            } catch (error: CancellationException) {
                withContext(NonCancellable + Dispatchers.Main.immediate) { restore(previous) }
                throw error
            } catch (error: Exception) {
                restore(previous)
                failed(error.message ?: "메모 합치기에 실패했어요.")
            } finally {
                busy.set(false)
                withContext(NonCancellable + Dispatchers.Main.immediate) { marbleChanged() }
            }
        }
    }

    private fun restore(snapshot: Undo) {
        memos.clear()
        memos.addAll(snapshot.memos)
        changed(null)
    }

    fun undoLastMerge(): Boolean {
        if (isMerging) return false
        val previous = undo.removeLastOrNull() ?: return false
        restore(previous)
        return true
    }

    suspend fun mergeAll(): String {
        if (!busy.compareAndSet(false, true)) throw MergeException.RequestRejected("합치기를 처리 중이에요.")
        try {
            val payload = withContext(Dispatchers.Main.immediate) {
                memos.mapIndexed { index, memo -> MemoPayload(content = memo.content, order = index) }
            }
            val result = gateway.merge(payload, true)
            lastMood = result.mood
            return result.text
        } finally {
            busy.set(false)
            withContext(NonCancellable + Dispatchers.Main.immediate) { marbleChanged() }
        }
    }

    suspend fun generateMood(diary: String): String? = gateway.generateMood(diary)
}
