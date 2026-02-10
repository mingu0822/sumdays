package com.example.sumdays

import DailySearchViewModel
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.repository.DailyEntryRepository
import com.example.sumdays.search.DailyEntrySearchAdapter
import com.example.sumdays.search.DailySearchViewModelFactory
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource

class SearchActivity : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var rvResults: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var searchBox: LinearLayout

    private lateinit var adapter: DailyEntrySearchAdapter
    private lateinit var viewModel: DailySearchViewModel
    private lateinit var navBarController: NavBarController

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etQuery = findViewById(R.id.etQuery)
        rvResults = findViewById(R.id.rvResults)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btnBack)
        btnClear = findViewById(R.id.btnClear)
        searchBox = findViewById(R.id.searchBox)

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.SEARCH)

        applyThemeModeSettings()

        // Room DB/DAO 가져오기
        val db = AppDatabase.getDatabase(this)
        val repo = DailyEntryRepository(db.dailyEntryDao())

        viewModel = ViewModelProvider(this, DailySearchViewModelFactory(repo))
            .get(DailySearchViewModel::class.java)

        adapter = DailyEntrySearchAdapter { entry ->
            startActivity(
                Intent(this, DailyReadActivity::class.java)
                    .putExtra("date", entry.date)
            )
        }

        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        setButtonClickListener()

        etQuery.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etQuery.addTextChangedListenerSimple { text ->
            btnClear.isVisible = !text.isNullOrBlank()
        }
        btnClear.isVisible = !etQuery.text.isNullOrBlank()

        viewModel.results.observe(this) { list ->
            adapter.submitList(list)
            val hasQuery = etQuery.text.isNotBlank()
            tvEmpty.isVisible = list.isEmpty() && hasQuery
            rvResults.isVisible = list.isNotEmpty() || !hasQuery
        }
    }

    private fun applyThemeModeSettings() {
        ThemeState.isDarkMode =
            (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode) {
            btnBack.setImageResource(R.drawable.ic_arrow_back_white)
            searchBox.setBackgroundResource(R.drawable.bg_search_round_white)
        } else {
            btnBack.setImageResource(R.drawable.ic_arrow_back_black)
            searchBox.setBackgroundResource(R.drawable.bg_search_round_gray)
        }
    }

    private fun setButtonClickListener() {
        btnBack.setOnClickListener {
            finish()
        }

        btnClear.setOnClickListener {
            etQuery.setText("")
            etQuery.requestFocus()
            etQuery.clearFocus()
            hideKeyboard()
            viewModel.setQuery("")
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etQuery.windowToken, 0)
    }

    private fun EditText.addTextChangedListenerSimple(onChanged: (CharSequence?) -> Unit) {
        this.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
