package com.example.sumdays

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.ThemeShopItem
import com.example.sumdays.theme.ThemePrefs

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

    inner class ShopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val ivShopItemImage: ImageView =
            itemView.findViewById(R.id.ivShopItemImage)

        private val tvShopItemName: TextView =
            itemView.findViewById(R.id.tvShopItemName)

        private val tvShopItemDescription: TextView =
            itemView.findViewById(R.id.tvShopItemDescription)

        private val tvShopItemPrice: TextView =
            itemView.findViewById(R.id.tvShopItemPrice)

        private val tvPurchasedBadge: TextView =
            itemView.findViewById(R.id.tvPurchasedBadge)

        private val btnShopItemAction: Button =
            itemView.findViewById(R.id.btnShopItemAction)

        fun bind(item: ShopItem) {

            val context = itemView.context

            // 공통 UI
            tvShopItemName.text = item.name
            tvShopItemDescription.text = item.description

            when (item) {

                // ============================
                // 테마
                // ============================
                is ThemeShopItem -> {

                    val isApplied =
                        ThemePrefs.getTheme(context) == item.name

                    tvShopItemPrice.text =
                        if (item.isOwned) "보유중"
                        else "${item.price}P"

                    when {

                        isApplied -> {

                            tvPurchasedBadge.visibility = View.VISIBLE
                            tvPurchasedBadge.text = "적용중"
                            tvPurchasedBadge.setBackgroundResource(
                                R.drawable.bg_label_applied
                            )
                            tvPurchasedBadge.setTextColor(
                                context.getColor(android.R.color.black)
                            )

                            btnShopItemAction.text = "적용 중"
                            btnShopItemAction.isEnabled = false
                        }

                        item.isOwned -> {

                            tvPurchasedBadge.visibility = View.VISIBLE
                            tvPurchasedBadge.text = "보유중"
                            tvPurchasedBadge.setBackgroundResource(
                                R.drawable.bg_label_owned
                            )
                            tvPurchasedBadge.setTextColor(
                                context.getColor(android.R.color.white)
                            )

                            btnShopItemAction.text = "적용"
                            btnShopItemAction.isEnabled = true
                        }

                        else -> {

                            tvPurchasedBadge.visibility = View.GONE

                            btnShopItemAction.text = "구매"
                            btnShopItemAction.isEnabled = true
                        }
                    }
                }

                // ============================
                // 아이템
                // ============================
                is FoxShopItem -> {

                    val count = ItemPrefs.getCount(
                        context,
                        item.name
                    )

                    tvShopItemPrice.text = "${item.price}P"

                    if (count > 0) {

                        tvPurchasedBadge.visibility = View.VISIBLE
                        tvPurchasedBadge.text = "보유 ${count}개"

                        // 적용중과 같은 노란 배지
                        tvPurchasedBadge.setBackgroundResource(
                            R.drawable.bg_label_applied
                        )

                        tvPurchasedBadge.setTextColor(
                            context.getColor(android.R.color.black)
                        )

                    } else {

                        tvPurchasedBadge.visibility = View.GONE
                    }

                    btnShopItemAction.text = "구매"
                    btnShopItemAction.isEnabled = true
                }
            }

            ivShopItemImage.setImageResource(
                android.R.drawable.ic_menu_gallery
            )

            btnShopItemAction.setOnClickListener {
                onActionClick(item)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}