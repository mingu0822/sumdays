package com.example.sumdays.settings.profileimage

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R

// 카테고리(눈/입/악세서리/얼굴형) 내에서 아이템을 가로스크롤로 보여주는 어댑터
class ItemAdapter(
    private val items: List<ProfileImageItem>,
    private var selectedId: Int,
    private val onItemSelected: (ProfileImageItem) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.itemImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_image_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // TODO: 에셋으로 바꾸기
        // holder.img.setImageResource(item.resId)

        if (item.id == selectedId) {
            holder.img.setPadding(4, 4, 4, 4)
            holder.img.setBackgroundResource(R.drawable.tmp_shape_selected_border) // 테두리 드로어블 필요
        } else {
            holder.img.setPadding(0, 0, 0, 0)
            holder.img.setBackgroundColor(Color.TRANSPARENT)
        }

        holder.itemView.setOnClickListener {
            // 선택 상태 갱신
            val oldSelectedId = selectedId
            selectedId = item.id

            notifyItemChanged(items.indexOfFirst { it.id == oldSelectedId })
            notifyItemChanged(position)

            onItemSelected(item)
        }
    }

    override fun getItemCount() = items.size
}