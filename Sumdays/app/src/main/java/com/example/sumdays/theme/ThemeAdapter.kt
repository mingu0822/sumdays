package com.example.sumdays.theme

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.databinding.ItemThemeBinding

class ThemeAdapter(
    private val themes: Map<String, Theme>,
    private var selectedThemeKey: String,
    private val onThemeClick: (String, Theme) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    private val themeItems = themes.entries.toList()

    inner class ThemeViewHolder(val binding: ItemThemeBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemThemeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThemeViewHolder(binding)
    }

    override fun getItemCount(): Int = themeItems.size

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val (themeKey, theme) = themeItems[position]
        val binding = holder.binding

        binding.themePreview.setImageResource(theme.themePreviewImage)
        binding.themeName.text = theme.name

        if (themeKey == selectedThemeKey) {
            binding.root.alpha = 1.0f
            binding.root.setBackgroundResource(R.drawable.bg_theme_selected)
        } else {
            binding.root.alpha = 1.0f
            binding.root.setBackgroundResource(R.drawable.bg_theme_item)
        }

        binding.root.setOnClickListener {
            selectedThemeKey = themeKey
            notifyDataSetChanged()
            onThemeClick(themeKey, theme)
        }
    }

    fun updateSelectedTheme(themeKey: String) {
        selectedThemeKey = themeKey
        notifyDataSetChanged()
    }
}