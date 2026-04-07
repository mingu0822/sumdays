import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.databinding.ItemFriendRequestBinding // 패키지명 확인 필요

data class FriendRequest(
    val id: Int,
    val nickname: String
)
class FriendRequestAdapter(
    private val onAccept: (FriendRequest) -> Unit, // 수락 클릭 시 실행할 코드
    private val onReject: (FriendRequest) -> Unit, // 거절 클릭 시 실행할 코드
    private val onCancel: (FriendRequest) -> Unit  // 취소 클릭 시 실행할 코드
) : ListAdapter<FriendRequest, FriendRequestAdapter.ViewHolder>(DiffCallback()) {

    // 현재 어떤 탭인지 저장하는 변수 (기본값은 받은 요청)
    private var currentType: String = "received"

    // 탭이 바뀔 때 외부에서 호출해줄 함수
    fun updateType(type: String) {
        this.currentType = type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 데이터와 현재 탭 타입을 같이 넘겨줍니다.
        holder.bind(getItem(position), currentType, onAccept, onReject, onCancel)
    }

    class ViewHolder(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            request: FriendRequest,
            type: String, // "received" 또는 "sent"
            onAccept: (FriendRequest) -> Unit,
            onReject: (FriendRequest) -> Unit,
            onCancel: (FriendRequest) -> Unit
        ) {
            // 1. 닉네임 설정 (XML의 @id/tvNickname 반영)
            binding.tvNickname.text = request.nickname

            // 2. 탭 종류에 따른 버튼 가시성 제어
            if (type == "received") {
                // 받은 요청: [수락/거절] 보이고 [취소] 숨김
                binding.layoutReceivedActions.visibility = android.view.View.VISIBLE
                binding.btnCancel.visibility = android.view.View.GONE
            } else {
                // 보낸 요청: [수락/거절] 숨기고 [취소] 보임
                binding.layoutReceivedActions.visibility = android.view.View.GONE
                binding.btnCancel.visibility = android.view.View.VISIBLE
            }

            // 3. 클릭 리스너 연결
            binding.btnAccept.setOnClickListener { onAccept(request) }
            binding.btnReject.setOnClickListener { onReject(request) }
            binding.btnCancel.setOnClickListener { onCancel(request) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FriendRequest>() {
        override fun areItemsTheSame(oldItem: FriendRequest, newItem: FriendRequest) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FriendRequest, newItem: FriendRequest) = oldItem == newItem
    }
}