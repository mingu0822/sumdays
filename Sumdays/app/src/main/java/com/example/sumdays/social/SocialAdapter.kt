package com.example.sumdays.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R

class SocialAdapter(
    private val socialUserList: List<SocialUser>,
    private val onItemClick : (SocialUser) -> Unit,
    private val onButtonClick : (SocialUser) -> Unit
) : RecyclerView.Adapter<SocialAdapter.SocialViewHolder>() {

    class SocialViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProfile: TextView = itemView.findViewById(R.id.tvProfile)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvUserInfo: TextView = itemView.findViewById(R.id.tvUserInfo)
        val btnFriend: ImageButton = itemView.findViewById(R.id.btnFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocialViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_social, parent, false)

        return SocialViewHolder(view)
    }

    override fun onBindViewHolder(holder: SocialViewHolder, position: Int) {
        val socialUser = socialUserList[position]

        holder.tvProfile.text = socialUser.profileEmoji
        holder.tvUserName.text = socialUser.name
        holder.itemView.setOnClickListener {
            onItemClick(socialUser)
        }
        holder.btnFriend.setOnClickListener {
            onButtonClick(socialUser)
        }
    }

    override fun getItemCount(): Int {
        return socialUserList.size
    }
}