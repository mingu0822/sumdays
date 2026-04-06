package com.example.sumdays.daily.memo

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

// RecyclerView의 드래그 앤 드롭 및 스와이프 동작을 처리하는 콜백 클래스
class MemoDragAndDropCallback(
    private val adapter: MemoAdapter,
    private val onMove: (fromPosition: Int, toPosition: Int) -> Unit,
    private val onDelete: (position: Int) -> Unit,
    private val onDragStart: () -> Unit, // 드래그 시작 콜백
    private val onDragEnd: () -> Unit // 드래그 종료 콜백
) : ItemTouchHelper.Callback() {

    // 드래그 방향 및 스와이프 방향 설정
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN // 위아래 드래그 허용
        val swipeFlags = 0 // 좌우 스와이프 비허용
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    // 아이템이 드래그되어 이동했을 때 호출
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        // 어댑터의 데이터 리스트를 직접 변경
        adapter.moveItem(fromPosition, toPosition)
        // 실제 데이터 변경을 ViewModel에 알리기 위해 람다 함수 호출
        onMove(fromPosition, toPosition)
        return true
    }

    // 아이템이 스와이프되었을 때 호출 (삭제 기능)
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        // 데이터 삭제를 ViewModel에 알리기 위해 람다 함수 호출
        onDelete(position)
    }

    // 드래그 중인 아이템의 배경색 변경 및 쓰레기통 아이콘 가시성 제어
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            onDragStart()
            viewHolder?.itemView?.alpha = 0.5f
        } else {
            onDragEnd()
            viewHolder?.itemView?.alpha = 1.0f
        }
    }

    // 드래그가 끝났을 때 원래대로 복구
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f
    }

    // 드래그 중인 뷰를 그리는 방식 커스터마이징
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // 드래그 중인 뷰가 손을 떼지 않아도 원래 위치로 돌아가지 않도록
            viewHolder.itemView.translationY = dY
            viewHolder.itemView.translationX = dX
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }


}