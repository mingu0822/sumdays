package com.example.sumdays.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sumdays.R
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class SocialAdapter(
    private val friendList: MutableList<FriendInfo>,
    private val onItemClick : (FriendInfo) -> Unit,
    private val onButtonClick : (FriendInfo) -> Unit
) : RecyclerView.Adapter<SocialAdapter.SocialViewHolder>() {

    // 원소 1개
    class SocialViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
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

        val fullUrl = "${ApiClient.BASE_URL.removeSuffix("/")}${friendInfo.profileImageUrl}"
        Glide.with(holder.itemView.context)
            .load(fullUrl)
            .placeholder(R.drawable.loading_animation) // 로딩 중에 보여줄 이미지
            .error(R.drawable.ic_account_circle)             // 로드 실패 시 보여줄 이미지
            .circleCrop()                                   // 사진을 동그랗게 깎아줌! (꿀팁)
            .transition(DrawableTransitionOptions.withCrossFade()) // 부드럽게 나타나게
            .into(holder.ivProfile) // ImageView에 꽂아넣기

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