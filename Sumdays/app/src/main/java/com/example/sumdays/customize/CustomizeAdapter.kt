package com.example.sumdays.customize

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.shop.ThemeShopItem
import com.google.android.material.card.MaterialCardView

class CustomizeAdapter(
    private val items: List<ThemeShopItem>,
    private var appliedTheme: String,
    private val onClick: (ThemeShopItem) -> Unit
) : RecyclerView.Adapter<CustomizeAdapter.ViewHolder>() {

    fun setAppliedTheme(themeName: String) {
        appliedTheme = themeName
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val card: MaterialCardView = view.findViewById(R.id.cardTheme)
        val imgTheme: ImageView = view.findViewById(R.id.imgTheme)
        val tvThemeName: TextView = view.findViewById(R.id.tvThemeName)
        val tvApplied: TextView = view.findViewById(R.id.tvApplied)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customize, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = items[position]

        holder.tvThemeName.text = item.name

        // 원하는 이미지로 변경
        holder.imgTheme.setImageResource(R.drawable.dailyread_fox_face_level_1)

        val selected = item.name == appliedTheme

        holder.tvApplied.visibility =
            if (selected) View.VISIBLE else View.GONE

        if (selected) {
            holder.card.strokeColor =
                ContextCompat.getColor(holder.itemView.context, R.color.foxrange)
            holder.card.strokeWidth = 5
        } else {
            holder.card.strokeColor =
                ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray)
            holder.card.strokeWidth = 2
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}