package com.example.sumdays.social.reqeust

import FriendRequest
import FriendRequestAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.sumdays.databinding.DialogFriendRequestBinding
import com.google.android.material.tabs.TabLayout

class FriendRequestDialog : DialogFragment() {

    private var _binding: DialogFriendRequestBinding? = null
    private val binding get() = _binding!!
    private val requestAdapter = FriendRequestAdapter()

    // 임시 데이터 (나중에 서버에서 받아오게 됨)
    private val mockData = listOf(
        FriendRequest(1, "김민수", false),
        FriendRequest(2, "이영희", false),
        FriendRequest(3, "박철수", true),
        FriendRequest(4, "최지우", true),
        FriendRequest(5, "강해린", false)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFriendRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabLayout()

        // [필수 요청사항] X 버튼 누르면 닫기
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // 초기 데이터 로드 (첫 번째 탭: 일반 친구 요청)
        filterList(0)
    }

    private fun setupRecyclerView() {
        binding.rvRequests.adapter = requestAdapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterList(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun filterList(position: Int) {
        val filteredList = if (position == 0) {
            mockData.filter { !it.isCloseFriend } // 일반 친구
        } else {
            mockData.filter { it.isCloseFriend } // 친한 친구
        }

        requestAdapter.submitList(filteredList)

        // 데이터가 없을 때 메시지 처리
        binding.tvEmptyMessage.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
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