package com.example.sumdays.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.network.apiService.FriendInfo
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.image.ProfileRenderer


class SocialAdapter(
    private val friendList: MutableList<FriendInfo>,
    private val onItemClick : (FriendInfo) -> Unit,
    private val onButtonClick : (FriendInfo) -> Unit
) : RecyclerView.Adapter<SocialAdapter.SocialViewHolder>() {

    // 원소 1개
    class SocialViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: CardView = itemView.findViewById(R.id.cardSocialRoot)
        val friendProfile: View = itemView.findViewById(R.id.friendProfile)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvUserInfo: TextView = itemView.findViewById(R.id.tvUserInfo)
        val btnFriend: ImageButton = itemView.findViewById(R.id.btnFriend)
    }

    // ui 뼈대
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocialViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_social, parent, false)

        return SocialViewHolder(view)
    }

    // ui 알맹이
    override fun onBindViewHolder(holder: SocialViewHolder, position: Int) {
        val friendInfo = friendList[position]
        val context = holder.itemView.context
        ThemeRepository.updateOwned()
        val themeKey = ThemePrefs.getTheme(context)
        val currentTheme = ThemeRepository.ownedThemes[themeKey]
            ?: ThemeRepository.allThemeMap[themeKey]

        currentTheme?.let { theme ->
            holder.cardRoot.setBackgroundResource(theme.blockStyleA)
            holder.friendProfile.setBackgroundResource(theme.blockStyleA)
            holder.tvUserName.setTextColor(ContextCompat.getColor(context, theme.themeTextColorBasic))
            holder.tvUserInfo.setTextColor(ContextCompat.getColor(context, theme.themeColorD))
            holder.btnFriend.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, theme.themeColorD)
                )
        }

        ProfileRenderer.render(
            root = holder.friendProfile,
            profile = friendInfo.profile,
            isMine = false
        )

        holder.tvUserName.text = friendInfo.nickname
        holder.tvUserInfo.text = "🔥 ${friendInfo.streak}  🍃 ${
            friendInfo.countWeeklySummaries}   🍇 ${friendInfo.countWeeklySummaries / 5}"
        holder.itemView.setOnClickListener {
            onItemClick(friendInfo)
        }
        holder.btnFriend.setOnClickListener {
            onButtonClick(friendInfo)
        }
    }

    override fun getItemCount(): Int {
        return friendList.size
    }

    fun updateList(newList: List<FriendInfo>) {
        friendList.clear()
        friendList.addAll(newList)
        notifyDataSetChanged()
    }
}
