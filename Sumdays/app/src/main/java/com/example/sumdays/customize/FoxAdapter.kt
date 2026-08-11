package com.example.sumdays.customize

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.google.android.material.card.MaterialCardView

class FoxAdapter(
    private val items: MutableList<CompleteFox>,
    private var appliedFox: Int,
    private val onClick: (CompleteFox) -> Unit,
    private val onDelete: (CompleteFox) -> Unit
) : RecyclerView.Adapter<FoxAdapter.ViewHolder>() {

    fun setAppliedFox(foxId: Int) {
        appliedFox = foxId
        notifyDataSetChanged()
    }

    /**
     * 여우 삭제
     */
    fun deleteFox(foxId: Int) {

        val position =
            items.indexOfFirst { it.id == foxId }

        if (position != -1) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val card: MaterialCardView =
            view.findViewById(R.id.cardTheme)

        val image: ImageView =
            view.findViewById(R.id.imgPreview)

        val tvName: TextView =
            view.findViewById(R.id.tvFoxName)

        val tvApplied: TextView =
            view.findViewById(R.id.tvApplied)

        val btnDelete: ImageButton =
            view.findViewById(R.id.btnDeleteFox)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_customize_fox,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun getItemCount(): Int =
        items.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val fox = items[position]

        // -------------------------
        // 이름
        // -------------------------

        holder.tvName.text = fox.name


        // -------------------------
        // 여우 이미지
        // -------------------------

        val bitmap =
            FoxBitmapRenderer.createPreview(
                holder.itemView.context,
                fox
            )

        holder.image.setImageBitmap(bitmap)


        // -------------------------
        // 적용 여부
        // -------------------------

        val selected =
            fox.id == appliedFox

        holder.tvApplied.visibility =
            if (selected) {
                View.VISIBLE
            } else {
                View.GONE
            }


        // -------------------------
        // 카드 테두리
        // -------------------------

        if (selected) {

            holder.card.strokeColor =
                ContextCompat.getColor(
                    holder.itemView.context,
                    R.color.foxrange
                )

            holder.card.strokeWidth = 5

        } else {

            holder.card.strokeColor =
                ContextCompat.getColor(
                    holder.itemView.context,
                    android.R.color.darker_gray
                )

            holder.card.strokeWidth = 2
        }


        // -------------------------
        // 삭제 버튼
        // -------------------------

        if (fox.id == 1) {

            // 기본 여우는 삭제 불가능
            holder.btnDelete.visibility =
                View.GONE

        } else {

            holder.btnDelete.visibility =
                View.VISIBLE

            holder.btnDelete.setOnClickListener {

                onDelete(fox)
            }
        }


        // -------------------------
        // 카드 클릭
        // -------------------------

        holder.itemView.setOnClickListener {

            onClick(fox)
        }
    }
}