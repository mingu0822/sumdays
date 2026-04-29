package com.example.sumdays.social.reqeust

import FriendRequestAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.sumdays.databinding.DialogFriendRequestBinding
import com.example.sumdays.network.apiService.SocialApiService
import com.google.android.material.tabs.TabLayout
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.CancelRequestBody
import com.example.sumdays.network.apiService.FriendInfo
import kotlin.collections.emptyList
import com.example.sumdays.network.apiService.FriendRequest
import com.example.sumdays.network.apiService.HandleRequestBody
import com.example.sumdays.social.SocialActivity
import com.example.sumdays.social.SocialViewModel
import com.example.sumdays.utils.getErrorMessage

class FriendRequestDialog : DialogFragment() {

    private var _binding: DialogFriendRequestBinding? = null
    private val binding get() = _binding!!
    private lateinit var requestAdapter :FriendRequestAdapter

    // 친구 요청 list
    private var receivedRequestList: List<FriendRequest> = emptyList()
    private var sentRequestList: List<FriendRequest> = emptyList()
    private var ReequestsLoadFailed = false
    private lateinit var viewModel: SocialViewModel



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFriendRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[SocialViewModel::class.java]
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
                val response = ApiClient.socialApi.handleRequest(
                    HandleRequestBody(
                        requesterId = request.userId,
                        action = "ACCEPT"
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    // 🔥 받은 요청 리스트에서 제거
                    receivedRequestList.filter { it.userId != request.userId }
                    showList(binding.tabLayout.selectedTabPosition)

                    body?.data?.let { friendInfo ->
                        viewModel.addFriendLocally(friendInfo)
                    }

                    Toast.makeText(context, body?.message, Toast.LENGTH_SHORT).show()

                } else {
                    val errorMessage = response.getErrorMessage("수락 실패")
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
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
                val response = ApiClient.socialApi.handleRequest(
                    HandleRequestBody(
                        requesterId = request.userId,
                        action = "REJECT"
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    receivedRequestList.filter { it.userId != request.userId }
                    showList(binding.tabLayout.selectedTabPosition)
                    Toast.makeText(context, body?.message, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.getErrorMessage("거절 실패")
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
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
                Log.d("handleCancel", "userId: ${request.userId}, nickname: ${request.nickname}")
                val response = ApiClient.socialApi.cancelRequest(
                    CancelRequestBody(receiverId = request.userId)
                )


                if (response.isSuccessful || response.code() == 404) {
                    val body = response.body()
                    sentRequestList = sentRequestList.filter { it.userId != request.userId }
                    showList(binding.tabLayout.selectedTabPosition)

                    val message = body?.message ?: "이미 취소된 요청입니다."
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                } else {
                    val errorMessage = response.getErrorMessage("취소 실패")
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
                    receivedRequestList = body?.data?.received ?: emptyList()
                    sentRequestList = body?.data?.sent ?: emptyList()

                    receivedRequestList.forEach {
                        Log.d("getFriendRequests", "recevied - id: ${it.userId}, nickname: ${it.nickname}")
                    }
                    sentRequestList.forEach {
                        Log.d("getFriendRequests", "sent - id: ${it.userId}, nickname: ${it.nickname}")
                    }


                    ReequestsLoadFailed = false
                } else {
                    receivedRequestList = emptyList()
                    sentRequestList = emptyList()
                    ReequestsLoadFailed = true

                    val errorMessage = response.getErrorMessage("조회 실패")
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                showList(binding.tabLayout.selectedTabPosition)

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                receivedRequestList = emptyList()
                sentRequestList = emptyList()
                ReequestsLoadFailed = true

                Log.e("API_ERROR", "친구 요청 목록 조회 실패", e)

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