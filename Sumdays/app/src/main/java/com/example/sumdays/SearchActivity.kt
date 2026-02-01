package com.example.sumdays


import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import android.text.Editable
import android.text.TextWatcher
import com.example.sumdays.search.SearchItem
import com.example.sumdays.search.SearchResultAdapter

class SearchActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var etQuery: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: SearchResultAdapter

    // TODO: 실제 데이터(일기/메모)로 교체
    private val allItems: List<SearchItem> = listOf(
        SearchItem(id = 1, title = "청소", preview = "방 정리하고 빨래도 했다…"),
        SearchItem(id = 2, title = "냥냥", preview = "고양이가 창밖을 보면서…")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search) // 너가 올린 XML 파일명

        bindViews()
        setupRecycler()
        setupActions()

        // 처음에는 전체(혹은 빈 화면) 중 취향대로
        showResults(allItems)   // 전체 보여주기
        // showResults(emptyList()) // 처음엔 아무것도 안 보이게 하고 싶으면 이걸로
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        btnClear = findViewById(R.id.btnClear)
        etQuery = findViewById(R.id.etQuery)
        rvResults = findViewById(R.id.rvResults)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupRecycler() {
        adapter = SearchResultAdapter { item ->
            // TODO: 클릭 시 상세 화면으로 이동
            // startActivity(Intent(this, DailyReadActivity::class.java).putExtra("id", item.id))
        }
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter
    }

    private fun setupActions() {
        btnBack.setOnClickListener { finish() }

        btnClear.setOnClickListener {
            etQuery.setText("")
            // 지우면 전체 다시 보여주거나 빈 화면으로 처리
            showResults(allItems)
        }

        // 엔터(검색) 누르면 검색 실행
        etQuery.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isSearchAction || isEnterKey) {
                performSearch(etQuery.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        // 입력 중이면 X 버튼 보이게 / 없으면 숨김
        etQuery.addTextChangedListenerSimple { text ->
            btnClear.isVisible = !text.isNullOrBlank()
        }

        // 초기 상태: 비어있으면 숨김
        btnClear.isVisible = !etQuery.text.isNullOrBlank()
    }

    private fun performSearch(queryRaw: String) {
        val query = queryRaw.trim()
        if (query.isEmpty()) {
            showResults(allItems)
            return
        }

        val filtered = allItems.filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                    item.preview.contains(query, ignoreCase = true)
        }
        showResults(filtered)
    }

    private fun showResults(list: List<SearchItem>) {
        adapter.submitList(list)
        val isEmpty = list.isEmpty()
        tvEmpty.isVisible = isEmpty
        rvResults.isVisible = !isEmpty
    }

    fun EditText.addTextChangedListenerSimple(onChanged: (CharSequence?) -> Unit) {
        this.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
