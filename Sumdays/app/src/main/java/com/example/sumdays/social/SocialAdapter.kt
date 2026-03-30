package com.example.sumdays.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R

class SocialAdapter(
    private val socialUserList: List<SocialUser>
) : RecyclerView.Adapter<SocialAdapter.SocialViewHolder>() {

    class SocialViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProfileEmoji: TextView = itemView.findViewById(R.id.tvProfileEmoji)
        val tvSocialName: TextView = itemView.findViewById(R.id.tvSocialName)
        val tvSocialSummary: TextView = itemView.findViewById(R.id.tvSocialSummary)
        val tvEmotion: TextView = itemView.findViewById(R.id.tvEmotion)
        val btnOpenSocial: ImageButton = itemView.findViewById(R.id.btnOpenSocial)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocialViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_social, parent, false)

        return SocialViewHolder(view)
    }

    override fun onBindViewHolder(holder: SocialViewHolder, position: Int) {
        val socialUser = socialUserList[position]

        holder.tvProfileEmoji.text = socialUser.profileEmoji
        holder.tvSocialName.text = socialUser.name
        holder.tvSocialSummary.text = socialUser.summary
        holder.tvEmotion.text = socialUser.emotion
    }

    override fun getItemCount(): Int {
        return socialUserList.size
    }
}