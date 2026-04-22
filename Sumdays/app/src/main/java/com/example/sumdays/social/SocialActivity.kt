package com.example.sumdays.social

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import android.widget.EditText
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
import com.example.sumdays.social.reqeust.AddFriendDialog
import com.example.sumdays.social.reqeust.FriendRequestDialog
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts

class SocialActivity : AppCompatActivity() {
    private lateinit var navBarController: NavBarController
    private lateinit var recyclerSocial: RecyclerView
    private lateinit var etSearchSocial: EditText
    private lateinit var socialAdapter: SocialAdapter

    private lateinit var tvSocialRequests: TextView
    private lateinit var btnAddSocial: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnUpdate: ImageButton
    private lateinit var tvEmpty: TextView
    private lateinit var tvError: TextView

    private lateinit var viewModel: SocialViewModel
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val deletedFriendId = result.data?.getIntExtra("deletedFriendId", -1) ?: -1
                if (deletedFriendId != -1) {
                    viewModel.removeFriendLocally(deletedFriendId)
                }
            }
        }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 기본 설정
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social)
        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.PROFILE)


        initViewModel()
        initView()
        setupClickListeners()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        viewModel.loadFriends()
    }
    private fun initViewModel() {
        val repository = SocialRepository()
        val factory = SocialViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[SocialViewModel::class.java]
    }
    private fun initView() {
        btnBack = findViewById(R.id.btnBack)
        recyclerSocial = findViewById(R.id.recyclerSocial)
        etSearchSocial = findViewById(R.id.etSearchSocial)
        tvSocialRequests = findViewById(R.id.tvSocialRequests)
        btnAddSocial = findViewById(R.id.btnAddSocial)
        btnUpdate = findViewById(R.id.btnUpdate)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvError = findViewById(R.id.tvError)

    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        tvSocialRequests.setOnClickListener {
            val dialog = FriendRequestDialog()
            dialog.show(supportFragmentManager, "FriendRequestDialog")
        }

        btnAddSocial.setOnClickListener {
            val dialog = AddFriendDialog()
            dialog.show(supportFragmentManager, "AddFriendDialog")
        }
        btnUpdate.setOnClickListener{
            viewModel.loadFriends()
        }

    }
    private fun setupRecyclerView() {
        socialAdapter = SocialAdapter(
            friendList = mutableListOf(),
            onItemClick = { friendInfo ->
                val intent = Intent(this, SocialDetailActivity::class.java)
                intent.putExtra("friendInfo", friendInfo)
                detailLauncher.launch(intent)
            },
            onButtonClick = { friendInfo ->
                Toast.makeText(
                    this,
                    "${friendInfo.nickname} 버튼 클릭",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        recyclerSocial.layoutManager = LinearLayoutManager(this)
        recyclerSocial.adapter = socialAdapter
    }
    private fun setupSearch() {
        etSearchSocial.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchQuery(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SocialUiState.Idle -> {
                        recyclerSocial.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        tvError.visibility = View.GONE
                    }

                    is SocialUiState.Loading -> {
                        recyclerSocial.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        tvError.visibility = View.GONE
                    }

                    is SocialUiState.Success -> {
                        tvError.visibility = View.GONE

                        if (state.friends.isEmpty()) {
                            recyclerSocial.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            recyclerSocial.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                        }
                    }

                    is SocialUiState.Error -> {
                        recyclerSocial.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.filteredFriends.collect { friends ->
                socialAdapter.updateList(friends)
            }
        }
    }
}

