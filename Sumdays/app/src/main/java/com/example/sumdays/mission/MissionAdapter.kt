package com.example.sumdays.mission

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R

class MissionAdapter(
    private val onClaimClick: (Mission) -> Unit,
) : ListAdapter<MissionListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is MissionListItem.Section -> VIEW_TYPE_SECTION
        is MissionListItem.Card -> VIEW_TYPE_MISSION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION -> SectionViewHolder(
                inflater.inflate(R.layout.item_mission_section, parent, false)
            )
            else -> MissionViewHolder(
                inflater.inflate(R.layout.item_mission, parent, false),
                onClaimClick,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MissionListItem.Section -> (holder as SectionViewHolder).bind(item)
            is MissionListItem.Card -> (holder as MissionViewHolder).bind(item.mission)
        }
    }

    private class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvMissionSectionTitle)

        fun bind(item: MissionListItem.Section) {
            title.text = item.category.title
        }
    }

    private class MissionViewHolder(
        itemView: View,
        private val onClaimClick: (Mission) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvMissionTitle)
        private val description: TextView = itemView.findViewById(R.id.tvMissionDescription)
        private val progressText: TextView = itemView.findViewById(R.id.tvMissionProgress)
        private val reward: TextView = itemView.findViewById(R.id.tvMissionReward)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressMission)
        private val action: Button = itemView.findViewById(R.id.btnMissionAction)

        fun bind(mission: Mission) {
            val definition = mission.definition
            title.text = definition.title
            description.text = definition.description
            progressText.text = "${mission.currentCount} / ${definition.targetCount}"
            reward.text = "+%,dP".format(definition.rewardPoints)
            progressBar.max = definition.targetCount
            progressBar.progress = mission.currentCount

            when (mission.status) {
                MissionStatus.IN_PROGRESS -> setActionStyle(
                    text = "진행 중",
                    enabled = false,
                    color = ContextCompat.getColor(itemView.context, R.color.mission_button_disabled),
                )
                MissionStatus.CLAIMABLE -> setActionStyle(
                    text = "수령",
                    enabled = true,
                    color = ContextCompat.getColor(itemView.context, R.color.foxrange),
                )
                MissionStatus.CLAIMED -> setActionStyle(
                    text = "수령 완료",
                    enabled = false,
                    color = ContextCompat.getColor(itemView.context, R.color.mission_button_claimed),
                )
            }

            action.setOnClickListener { onClaimClick(mission) }
        }

        private fun setActionStyle(text: String, enabled: Boolean, color: Int) {
            action.text = text
            action.isEnabled = enabled
            action.backgroundTintList = ColorStateList.valueOf(color)
            action.setTextColor(Color.WHITE)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MissionListItem>() {
        override fun areItemsTheSame(oldItem: MissionListItem, newItem: MissionListItem): Boolean =
            when {
                oldItem is MissionListItem.Section && newItem is MissionListItem.Section ->
                    oldItem.category == newItem.category
                oldItem is MissionListItem.Card && newItem is MissionListItem.Card ->
                    oldItem.mission.definition.code == newItem.mission.definition.code
                else -> false
            }

        override fun areContentsTheSame(oldItem: MissionListItem, newItem: MissionListItem): Boolean =
            oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_MISSION = 1
    }
}

