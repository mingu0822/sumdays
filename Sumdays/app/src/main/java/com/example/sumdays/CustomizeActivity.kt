package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.customize.FoxAdapter
import com.example.sumdays.customize.ThemeAdapter
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge

class CustomizeActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnShop: ImageButton
    private lateinit var btnAlchemy: ImageButton

    private lateinit var imgPreview: ImageView

    private lateinit var themeAdapter: ThemeAdapter
    private lateinit var foxAdapter: FoxAdapter

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var navBarController: NavBarController

    private lateinit var rvTheme: RecyclerView
    private lateinit var rvFox: RecyclerView

    private val themeList = mutableListOf<Theme>()
    private val foxList = mutableListOf<CompleteFox>()

    private var selectedTheme: Theme? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customize)

        initViews()

        loadThemes()
        loadFoxItems()
        setupRecycler()

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.CUSTOMIZE)

        rootLayout = findViewById(R.id.customRoot)
        setupEdgeToEdge(rootLayout)

        btnBack.setOnClickListener {
            finish()
        }

        btnAlchemy.setOnClickListener {

            startActivity(
                Intent(this, FoxAlchemyActivity::class.java)
            )

            overridePendingTransition(0, 0)
        }
    }

    private fun initViews() {

        btnBack = findViewById(R.id.btnBack)
        btnShop = findViewById(R.id.btnShop)
        btnAlchemy = findViewById(R.id.btnAlchemy)

        rvTheme = findViewById(R.id.rvTheme)
        rvFox = findViewById(R.id.rvFox)

        imgPreview = findViewById(R.id.imgPreview)
    }

    /**
     * 보유한 테마 불러오기
     */
    private fun loadThemes() {

        themeList.clear()

        AllThemeMap.allThemeMap.forEach { (id, theme) ->

            if (OwnedPrefs.isOwned(this, id)) {
                themeList.add(theme)
            }
        }

        // 기본 테마는 항상 존재하도록
        if (themeList.none { it.id == 1 }) {

            AllThemeMap.allThemeMap[1]?.let {
                themeList.add(0, it)
            }
        }
    }

    /**
     * 여우 목록 불러오기
     */
    private fun loadFoxItems() {

        foxList.clear()

        AllFoxMap.allFoxMap.values.forEach { fox ->
            foxList.add(fox)
        }
    }

    /**
     * RecyclerView 설정
     */
    private fun setupRecycler() {

        rvTheme.layoutManager =
            LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
            )

        themeAdapter = ThemeAdapter(

            items = themeList,

            appliedTheme = ThemePrefs.getTheme(this),

            onClick = { theme ->

                ThemePrefs.saveTheme(
                    this,
                    theme.id
                )

                themeAdapter.setAppliedTheme(
                    theme.id
                )

                selectedTheme = theme

                updatePreviewTheme(theme)
            }
        )

        rvTheme.adapter = themeAdapter

        rvFox.layoutManager =
            LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
            )

        foxAdapter = FoxAdapter(

            items = foxList,

            appliedFox = ThemePrefs.getFoxItem(this),

            onClick = { fox ->

                ThemePrefs.saveFoxItem(
                    this,
                    fox.id
                )

                imgPreview.setImageResource(
                    fox.previewImage
                )

                foxAdapter.setAppliedFox(
                    fox.id
                )
            }
        )

        rvFox.adapter = foxAdapter
    }

    /**
     * 테마 미리보기 갱신
     */
    private fun updatePreviewTheme(theme: Theme) {

        // 현재 선택된 여우 유지
        val currentFox = foxList.firstOrNull {
            it.id == ThemePrefs.getFoxItem(this)
        }

        if (currentFox != null) {
            imgPreview.setImageResource(currentFox.previewImage)
        } else {
            imgPreview.setImageResource(theme.previewImage)
        }
    }

    override fun onResume() {
        super.onResume()

        // 상점/연금술에서 돌아왔을 때 목록 갱신
        loadThemes()
        loadFoxItems()

        themeAdapter.notifyDataSetChanged()
        foxAdapter.notifyDataSetChanged()

        // 적용된 여우 미리보기 표시
        foxList.firstOrNull {
            it.id == ThemePrefs.getFoxItem(this)
        }?.let {
            imgPreview.setImageResource(it.previewImage)
        }

        // 선택 상태 동기화
        themeAdapter.setAppliedTheme(
            ThemePrefs.getTheme(this)
        )

        foxAdapter.setAppliedFox(
            ThemePrefs.getFoxItem(this)
        )
    }
}