package com.example.sumdays.social

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
import com.example.sumdays.network.apiService.FriendRequest
import com.example.sumdays.network.apiService.HandleRequestBody
import com.example.sumdays.utils.getErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context
import com.example.sumdays.network.apiService.CancelRequestBody

class SocialViewModel(
    private val repository: SocialRepository
) : ViewModel() {

    // 친구 리스트 관련 변수
    private val _uiState = MutableStateFlow<SocialUiState>(SocialUiState.Idle)
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private val allFriendList = mutableListOf<FriendInfo>()

    private val _filteredFriends = MutableStateFlow<List<FriendInfo>>(emptyList())
    val filteredFriends: StateFlow<List<FriendInfo>> = _filteredFriends.asStateFlow()
    private var currentQuery: String = ""

    // 친구 요청 리스트  관련 변수
    private val _receivedRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val receivedRequests: StateFlow<List<FriendRequest>> = _receivedRequests.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val sentRequests: StateFlow<List<FriendRequest>> = _sentRequests.asStateFlow()
    private val _requestLoadFailed = MutableStateFlow(false)
    val requestLoadFailed: StateFlow<Boolean> = _requestLoadFailed.asStateFlow()



    fun loadSocialList() {
        loadFriends()
        loadFriendRequests()
    }
    /* ------ 친구 리스트 관련 함수 ------ */
    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = SocialUiState.Loading

            repository.getMyFriends()
                .onSuccess { friends ->
                    allFriendList.clear()
                    allFriendList.addAll(friends)

                    applyFilter(currentQuery)
                    _uiState.value = SocialUiState.Success(friends)
                }
                .onFailure { e ->
                    _uiState.value = SocialUiState.Error(
                        e.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
        }
    }
    fun updateSearchQuery(query: String) {
        currentQuery = query
        applyFilter(query)
    }
    fun removeFriendLocally(friendId: Int) {
        allFriendList.removeAll { it.id == friendId }
        applyFilter(currentQuery)

        _uiState.value = SocialUiState.Success(allFriendList.toList())
    }
    fun addFriendLocally(friend: FriendInfo) {
        Log.d("SocialVM", "addFriendLocally 호출")
        Log.d("SocialVM", "friend = $friend")
        // 중복 방지
        if (allFriendList.any { it.id == friend.id }) return

        allFriendList.add(friend)

        applyFilter(currentQuery)

        _uiState.value = SocialUiState.Success(allFriendList.toList())
    }

    fun addSentRequestLocally(friendRequest: FriendRequest) {
        Log.d("SocialVM", "addSentRequestLocally 호출: $friendRequest")

        // 중복 방지
        if (_sentRequests.value.any { it.userId == friendRequest.userId }) return

        // 리스트 결합 및 StateFlow 업데이트 (자동 렌더링 트리거)
        _sentRequests.value = _sentRequests.value + friendRequest
    }
    private fun applyFilter(query: String) {
        val keyword = query.trim()

        _filteredFriends.value =
            if (keyword.isEmpty()) {
                allFriendList.toList()
            } else {
                allFriendList.filter {
                    it.nickname.contains(keyword, ignoreCase = true)
                }.toList()
            }
    }
    /* ------ 친구 리스트 관련 함수 ------ */


    /* ------ 친구 요청 리스트 관련 함수 ------ */
    // 1. 서버에서 친구 요청 리스트 가져오는 함수
    fun loadFriendRequests() {
        viewModelScope.launch {
            try {
                val response = ApiClient.socialApi.getFriendRequests()
                if (response.isSuccessful) {
                    val body = response.body()
                    // 받은 요청과 보낸 요청을 각각의 StateFlow에 업데이트
                    _receivedRequests.value = body?.data?.received ?: emptyList()
                    _sentRequests.value = body?.data?.sent ?: emptyList()
                    _requestLoadFailed.value = false
                } else {
                    _receivedRequests.value = emptyList()
                    _sentRequests.value = emptyList()
                    _requestLoadFailed.value = true
                }
            } catch (e: Exception) {
                _receivedRequests.value = emptyList()
                _sentRequests.value = emptyList()
                _requestLoadFailed.value = true
                Log.e("SocialViewModel", "친구 요청 목록 조회 실패", e)
            }
        }
    }

    // 2. 친구 요청 수락 함수 (onComplete 콜백 제거 버전)
    fun acceptFriendRequest(context: Context, request: FriendRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.socialApi.handleRequest(
                    HandleRequestBody(
                        requesterId = request.userId,
                        action = "ACCEPT"
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()

                    // 🌟 [추가] 서버에서 응답한 body 전체 구조 로그로 찍기
                    Log.d("API_RESPONSE_BODY", "수락 응답 전체: $body")
                    Log.d("API_RESPONSE_DATA", "수락 응답 data 필드: ${body?.data}")

                    Log.d("API_SUCCESS", "친구 요청 수락 성공 - 상대방 ID: ${request.userId}, 닉네임: ${request.nickname}, 메시지: ${body?.message}")

                    // 🌟 로컬 리스트 필터링 후 StateFlow 업데이트 (collect하고 있던 다이얼로그가 자동 리렌더링 트리거)
                    _receivedRequests.value = _receivedRequests.value.filter { it.userId != request.userId }

                    body?.data?.let { friendInfo ->
                        addFriendLocally(friendInfo)
                    }

                    Toast.makeText(context, body?.message, Toast.LENGTH_SHORT).show()

                } else {
                    val errorMessage = response.getErrorMessage("수락 실패")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "수락 실패.", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "수락 실패", e)
            }
        }
    }

    // 3. 친구 요청 거절 함수 (onComplete 콜백 제거 버전)
    fun rejectFriendRequest(context: Context, request: FriendRequest) {
        viewModelScope.launch {
            try {
                val response = ApiClient.socialApi.handleRequest(
                    HandleRequestBody(
                        requesterId = request.userId,
                        action = "REJECT"
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()

                    // 🌟 로컬 리스트 필터링 후 StateFlow 업데이트 (자동 리렌더링)
                    _receivedRequests.value = _receivedRequests.value.filter { it.userId != request.userId }

                    Toast.makeText(context, body?.message, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.getErrorMessage("거절 실패")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "거절 실패.", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "거절 실패", e)
            }
        }
    }

    // 4. 친구 요청 취소 함수 (onComplete 콜백 제거 버전)
    fun cancelSentFriendRequest(context: Context, request: FriendRequest) {
        viewModelScope.launch {
            try {
                Log.d("handleCancel", "userId: ${request.userId}, nickname: ${request.nickname}")
                val response = ApiClient.socialApi.cancelRequest(
                    CancelRequestBody(receiverId = request.userId)
                )

                if (response.isSuccessful || response.code() == 404) {
                    val body = response.body()

                    // 🌟 로컬 리스트 필터링 후 StateFlow 업데이트 (자동 리렌더링)
                    _sentRequests.value = _sentRequests.value.filter { it.userId != request.userId }

                    val message = body?.message ?: "이미 취소된 요청입니다."
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                } else {
                    val errorMessage = response.getErrorMessage("취소 실패")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "취소 실패.", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "취소 실패", e)
            }
        }
    }/* ------ 친구 요청 리스트 관련 함수 ------ */


}

class SocialViewModelFactory(
    private val repository: SocialRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SocialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}