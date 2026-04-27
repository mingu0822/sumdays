package com.example.sumdays.social.reqeust

import FriendRequestAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.sumdays.databinding.DialogFriendRequestBinding
import com.example.sumdays.network.apiService.SocialApiService
import com.google.android.material.tabs.TabLayout
import com.example.sumdays.network.ApiClient
import kotlin.collections.emptyList
import com.example.sumdays.network.apiService.FriendRequest

class FriendRequestDialog : DialogFragment() {

    private var _binding: DialogFriendRequestBinding? = null
    private val binding get() = _binding!!
    private lateinit var requestAdapter :FriendRequestAdapter

    // 친구 요청 list
    private var receivedRequestList: List<FriendRequest> = emptyList()
    private var sentRequestList: List<FriendRequest> = emptyList()
    private var ReequestsLoadFailed = false


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFriendRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
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

    // adapter 초기화 (버튼 action 등)
    private fun setupAdapter() {
        // 1. requestAdapter 초기호
        requestAdapter = FriendRequestAdapter(
            onAccept = { request -> handleAccept(request) },
            onReject = { request -> handleReject(request) },
            onCancel = { request -> handleCancel(request) }
        )

        binding.rvRequests.adapter = requestAdapter
    }
    private fun handleAccept(request: FriendRequest) {
        lifecycleScope.launch {
            try {
                // 직접 ApiService 호출
                val response = ApiClient.socialApi.handleRequest(request.id, mapOf("action" to "ACCEPT"))
                if (response.isSuccessful) {
                    receivedRequestList = receivedRequestList.filter { it.id != request.id }
                    showList(binding.tabLayout.selectedTabPosition)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "수락 실패.", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "수락 실패", e)
            }
        }
    }
    private fun handleReject(request: FriendRequest) {
        lifecycleScope.launch {
            try {
                // 직접 ApiService 호출
                val response = ApiClient.socialApi.handleRequest(request.id, mapOf("action" to "REJECT"))
                if (response.isSuccessful) {
                    receivedRequestList = receivedRequestList.filter { it.id != request.id }
                    showList(binding.tabLayout.selectedTabPosition)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "거절 실패.", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "거절 실패", e)
            }
        }
    }
    private fun handleCancel(request: FriendRequest) {
        lifecycleScope.launch {
            try {
                // 직접 ApiService 호출
                val response = ApiClient.socialApi.cancelRequest(mapOf("receiverId" to request.id))
                if (response.isSuccessful) {
                    sentRequestList = sentRequestList.filter { it.id != request.id }
                    showList(binding.tabLayout.selectedTabPosition)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "취소 실패.", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "취소 실패", e)
            }
        }
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
                val response = ApiClient.socialApi.getFriendRequests()
                if (response.isSuccessful) {
                    val body = response.body()
                    receivedRequestList = body?.received ?: emptyList()
                    receivedRequestList.forEach {
                        Log.d("FriendRequest", "id: ${it.id}, nickname: ${it.nickname}")
                    }
                    sentRequestList = body?.sent ?: emptyList()
                    ReequestsLoadFailed = false
                } else {
                    receivedRequestList = emptyList()
                    sentRequestList = emptyList()
                    ReequestsLoadFailed = true
                }

                // ✅ fetch 끝난 뒤 현재 탭 다시 렌더링
                showList(binding.tabLayout.selectedTabPosition)

            } catch (e: Exception) {
                receivedRequestList = emptyList()
                sentRequestList = emptyList()
                ReequestsLoadFailed = true
                e.printStackTrace()

                // ✅ 실패해도 현재 탭 다시 렌더링
                showList(binding.tabLayout.selectedTabPosition)
            }
        }
    }
    private fun showList(position: Int) {

        // ✅ 1. 무조건 먼저 타입 동기화
        requestAdapter.updateType(if (position == 0) "received" else "sent")

        // 2. 현재 보여줄 리스트
        val currentList = if (position == 0) receivedRequestList else sentRequestList
        val emptyText = if (position == 0) "받은 친구 요청이 없습니다." else "보낸 친구 요청이 없습니다."

        // 3. 상태 처리
        when {
            ReequestsLoadFailed -> {
                binding.tvEmptyMessage.text = "데이터를 불러오지 못했습니다."
                binding.tvEmptyMessage.visibility = View.VISIBLE
                requestAdapter.submitList(emptyList())
            }

            currentList.isEmpty() -> {
                binding.tvEmptyMessage.text = emptyText
                binding.tvEmptyMessage.visibility = View.VISIBLE
                requestAdapter.submitList(emptyList())
            }

            else -> {
                binding.tvEmptyMessage.visibility = View.GONE
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