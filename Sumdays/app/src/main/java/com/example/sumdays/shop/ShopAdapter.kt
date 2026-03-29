package com.example.sumdays

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.room.util.query
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemeRepository

class ShopAdapter(
    private val items: List<ShopItem>,
    private val onItemClick: (ShopItem) -> Unit,
    private val onActionClick: (ShopItem) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shop, parent, false)
        return ShopViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    private fun purchaseTheme(themeName: String) {
        val themeRepo = ThemeRepository

        val theme = AllThemeMap.allThemeMap[themeName] ?: return

        theme.isOwned = true
        themeRepo.updateOwned()
    }

    inner class ShopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivShopItemImage: ImageView = itemView.findViewById(R.id.ivShopItemImage)
        private val tvShopItemCategory: TextView = itemView.findViewById(R.id.tvShopItemCategory)
        private val tvShopItemName: TextView = itemView.findViewById(R.id.tvShopItemName)
        private val tvShopItemDescription: TextView = itemView.findViewById(R.id.tvShopItemDescription)
        private val tvShopItemPrice: TextView = itemView.findViewById(R.id.tvShopItemPrice)
        private val tvPurchasedBadge: TextView = itemView.findViewById(R.id.tvPurchasedBadge)
        private val btnShopItemAction: Button = itemView.findViewById(R.id.btnShopItemAction)

        fun bind(item: ShopItem) {
            tvShopItemCategory.text = item.category
            tvShopItemName.text = item.name
            tvShopItemDescription.text = item.description
            tvShopItemPrice.text = if (item.isOwned) "보유중" else "${item.price}P"

            tvPurchasedBadge.visibility = if (item.isOwned) View.VISIBLE else View.GONE
            btnShopItemAction.text = if (item.isOwned) "적용" else "구매"

            ivShopItemImage.setImageResource(android.R.drawable.ic_menu_gallery)

            itemView.setOnClickListener {
                onItemClick(item)
            }

            btnShopItemAction.setOnClickListener {
                onActionClick(item)
            }
        }
    }
}