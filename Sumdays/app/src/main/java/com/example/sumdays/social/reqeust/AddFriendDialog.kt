package com.example.sumdays.social.reqeust

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.databinding.DialogAddFriendBinding
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.RequestFriendBody
import com.example.sumdays.social.SocialViewModel
import kotlinx.coroutines.launch

class AddFriendDialog() : DialogFragment() {

    private var _binding: DialogAddFriendBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SocialViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddFriendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[SocialViewModel::class.java]

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnConfirm.setOnClickListener {
            val friendEmail = binding.etFriendId.text.toString().trim()
            if (friendEmail.isNotEmpty()) {
                sendRequest(friendEmail) // 부모 액티비티에 ID
            } else {
                Toast.makeText(context, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 다이얼로그 너비를 화면에 맞게 조정
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sendRequest(email: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.socialApi.requestFriend(
                    RequestFriendBody(receiverEmail = email)
                )

                val body = response.body()

                if (response.isSuccessful && body?.success == true) {
                    if (body.code == "AUTO_ACCEPTED" && body.data != null) {
                        viewModel.addFriendLocally(body.data)
                    }
                    Toast.makeText(requireContext(), body.message, Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    val errorMessage = body?.message ?: "요청 실패"
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "요청 실패", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "친구 요청 실패", e)
            }
        }
    }
}