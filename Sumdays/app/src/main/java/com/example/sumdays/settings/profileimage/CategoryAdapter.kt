package com.example.sumdays.settings.profileimage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R

class CategoryAdapter(
    private val categories: List<ProfileImageCategory>,
    private val selectedIdsMap: Map<ProfileImageItemType, Int>,
    private val onPartClick: (ProfileImageItem) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.categoryTitle)
        val rvItems: RecyclerView = view.findViewById(R.id.rvHorizontalItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.title.text = category.title

        // 가로 스크롤 설정,
        holder.rvItems.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvItems.adapter = ItemAdapter(
            items = category.parts,
            selectedId = selectedIdsMap[category.type] ?: -1,
            onItemSelected = onPartClick
        )
    }

    override fun getItemCount() = categories.size
}