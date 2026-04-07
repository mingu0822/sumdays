package com.example.sumdays.social.reqeust


import FriendRequest
import FriendRequestAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.sumdays.databinding.DialogFriendRequestBinding
import com.example.sumdays.network.apiService.SocialApiService
import com.google.android.material.tabs.TabLayout
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendRequestResponse
import kotlinx.coroutines.async
import kotlin.collections.emptyList

class FriendRequestDialog : DialogFragment() {

    private var _binding: DialogFriendRequestBinding? = null
    private val binding get() = _binding!!
    private val requestAdapter = FriendRequestAdapter()

    // 친구 요청 list
    private var receivedRequestList: List<FriendRequest>? = emptyList()
    private var sentRequestList: List<FriendRequest>? = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFriendRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabLayout()

        // X 버튼 누르면 닫기
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // 서버에서 데이터 받아오기
        fetchRequestsFromServer()

        // 초기 데이터 로드 (첫 번째 탭: 일반 친구 요청)
        showList(0)
    }

    private fun setupRecyclerView() {
        binding.rvRequests.adapter = requestAdapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showList(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }


    private fun fetchRequestsFromServer() {
        lifecycleScope.launch {
            try {
                // 1. 두 요청을 동시에 던집니다 (Parallel Start)
                val receivedDeferred = async { ApiClient.socialApi.getPendingRequests("received") }
                val sentDeferred = async { ApiClient.socialApi.getPendingRequests("sent") }

                // 2. 두 결과가 모두 올 때까지 기다립니다 (Wait for both)
                val receivedRes = receivedDeferred.await()
                val sentRes = sentDeferred.await()

                // 3. 각각 결과 처리
                receivedRequestList = if (receivedRes.isSuccessful) receivedRes.body()?.map { it.toDomain() } else null
                sentRequestList = if (sentRes.isSuccessful) sentRes.body()?.map { it.toDomain() } else null
            } catch (e: Exception) {
                e.printStackTrace()
                receivedRequestList = null
                sentRequestList = null
            }
        }
    }
    private fun showList(position: Int) {
        // 1. 현재 보여줄 리스트와 에러 메시지 설정
        val currentList = if (position == 0) receivedRequestList else sentRequestList
        val emptyText = if (position == 0) "받은 친구 요청이 없습니다." else "보낸 친구 요청이 없습니다."

        // 2. 상태에 따른 분기 처리
        when {
            // (A) 데이터를 불러오는 데 실패한 경우 (null)
            currentList == null -> {
                binding.tvEmptyMessage.text = "데이터를 불러오지 못했습니다."
                binding.tvEmptyMessage.visibility = View.VISIBLE
                requestAdapter.submitList(emptyList<FriendRequest>()) // 리스트는 비워줌
            }

            // (B) 리스트가 비어있는 경우
            currentList.isEmpty() -> {
                binding.tvEmptyMessage.text = emptyText
                binding.tvEmptyMessage.visibility = View.VISIBLE
                requestAdapter.submitList(emptyList<FriendRequest>())
            }

            // (C) 데이터가 정상적으로 있는 경우
            else -> {
                binding.tvEmptyMessage.visibility = View.GONE // 오타 수정!
                requestAdapter.submitList(currentList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 다이얼로그 크기를 화면에 맞게 조정
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
fun FriendRequestResponse.toDomain(): FriendRequest {
    return FriendRequest(
        id = this.id,
        nickname = this.nickname ?: "이름 없음", // Null 방어 로직
    )
}