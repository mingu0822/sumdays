package com.example.sumdays.alchemy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.shop.FoxShopItem

class AlchemyItemAdapter(

    private val onItemClick: (FoxShopItem) -> Unit

) : RecyclerView.Adapter<AlchemyItemAdapter.AlchemyViewHolder>() {

    private val items = mutableListOf<FoxShopItem>()

    fun submitList(list: List<FoxShopItem>) {

        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class AlchemyViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvItemName: TextView =
            itemView.findViewById(R.id.tvItemName)

        private val imgItem: ImageView =
            itemView.findViewById(R.id.imgItem)

        private val tvCount: TextView =
            itemView.findViewById(R.id.tvCount)

        fun bind(item: FoxShopItem) {

            tvItemName.text = item.name

            imgItem.setImageResource(item.imageRes)

            tvCount.text = "x${item.count}"

            val selected =
                AlchemySelectionManager.isSelected(item)

            // 선택된 아이템은 반투명하게 하지 않고 강조
            if (selected) {

                itemView.alpha = 1f

                itemView.background =
                    ContextCompat.getDrawable(
                        itemView.context,
                        R.drawable.bg_alchemy_selected
                    )

            } else {

                itemView.background = null

                // 재고가 없으면 흐리게
                itemView.alpha =
                    if (item.count <= 0) 0.35f else 1f
            }

            itemView.setOnClickListener {

                // 재고가 없고 선택도 안 된 아이템은 클릭 불가
                if (item.count <= 0 && !selected)
                    return@setOnClickListener

                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AlchemyViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_alchemy_inventory,
                    parent,
                    false
                )

        return AlchemyViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: AlchemyViewHolder,
        position: Int
    ) {

        holder.bind(items[position])
    }

    override fun getItemCount(): Int {

        return items.size
    }
}