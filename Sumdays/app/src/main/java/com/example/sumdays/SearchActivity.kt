package com.example.sumdays


import DailySearchViewModel
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.ViewModelProvider
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.repository.DailyEntryRepository
import com.example.sumdays.search.DailyEntrySearchAdapter
import com.example.sumdays.search.DailySearchViewModelFactory


class SearchActivity : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var btnBack: ImageButton

    private lateinit var adapter: DailyEntrySearchAdapter
    private lateinit var viewModel: DailySearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etQuery = findViewById(R.id.etQuery)
        rvResults = findViewById(R.id.rvResults)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)

        setButtonClickListener()

        // Room DB/DAO 가져오기
        val db = AppDatabase.getDatabase(this)
        val repo = DailyEntryRepository(db.dailyEntryDao())
        viewModel = ViewModelProvider(this, DailySearchViewModelFactory(repo))
            .get(DailySearchViewModel::class.java)

        adapter = DailyEntrySearchAdapter { entry ->
             startActivity(Intent(this, DailyReadActivity::class.java).putExtra("date", entry.date))
        }

        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        etQuery.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewModel.results.observe(this) { list ->
            adapter.submitList(list)
            tvEmpty.isVisible = list.isEmpty() && etQuery.text.isNotBlank()
        }
    }

    private fun setButtonClickListener(){
        btnBack.setOnClickListener {
            finish()
        }
    }
}
