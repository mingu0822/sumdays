import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.databinding.ItemFriendRequestBinding // 패키지명 확인 필요

data class FriendRequest(
    val id: Int,
    val name: String,
    val isCloseFriend: Boolean
)

class FriendRequestAdapter : ListAdapter<FriendRequest, FriendRequestAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(request: FriendRequest) {
            binding.tvName.text = request.name
            // 버튼 클릭 리스너는 나중에 로직 추가 시 여기에 작성
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FriendRequest>() {
        override fun areItemsTheSame(oldItem: FriendRequest, newItem: FriendRequest) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FriendRequest, newItem: FriendRequest) = oldItem == newItem
    }
}