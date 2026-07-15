package com.example.sumdays.customize

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.google.android.material.card.MaterialCardView

class FoxAdapter(
    private val items: List<CompleteFox>,
    private var appliedFox: String,
    private val onClick: (CompleteFox) -> Unit
) : RecyclerView.Adapter<FoxAdapter.ViewHolder>() {

    fun setAppliedFox(foxName: String) {
        appliedFox = foxName
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val card: MaterialCardView =
            view.findViewById(R.id.cardTheme)

        val image: ImageView =
            view.findViewById(R.id.imgTheme)

        val tvName: TextView =
            view.findViewById(R.id.tvThemeName)

        val tvApplied: TextView =
            view.findViewById(R.id.tvApplied)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customize, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val fox = items[position]

        holder.tvName.text = fox.name

        // CompleteFox에 저장된 미리보기 이미지
        holder.image.setImageResource(fox.previewImage)

        val selected = fox.name == appliedFox

        holder.tvApplied.visibility =
            if (selected) View.VISIBLE else View.GONE

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

        holder.itemView.setOnClickListener {
            onClick(fox)
        }
    }
}