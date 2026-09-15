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
import com.example.sumdays.customize.FoxBitmapRenderer
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.customize.ThemeAdapter
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.example.sumdays.alchemy.AlchemyRecipeManager
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.ui.component.setupBackToCalendar

class CustomizeActivity : AppCompatActivity() {

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

        btnAlchemy.setOnClickListener {

            startActivity(
                Intent(this, FoxAlchemyActivity::class.java)
            )

            overridePendingTransition(0, 0)
        }
        setupBackToCalendar()
    }

    private fun initViews() {

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
     * 저장된 여우 미리보기 표시
     */
    private fun showFoxPreview(
        fox: CompleteFox
    ) {

        // 1. 저장된 PNG가 있으면 사용
        val bitmap = FoxBitmapRenderer.loadPreview(
            fox.previewPath
        )

        if (bitmap != null) {

            FoxBitmapRenderer.displayPreview(imgPreview, bitmap, fox)

            return
        }

        // 2. PNG가 없으면 기존 방식으로 생성
        val generatedBitmap =
            FoxBitmapRenderer.createPreview(
                this,
                fox
            )

        FoxBitmapRenderer.displayPreview(imgPreview, generatedBitmap, fox)
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

                val bitmap =
                    FoxBitmapRenderer.createPreview(
                        this,
                        fox
                    )

                if (bitmap != null) {
                    FoxBitmapRenderer.displayPreview(imgPreview, bitmap, fox)
                } else {
                    imgPreview.setImageResource(
                        fox.previewImage
                    )
                }

                foxAdapter.setAppliedFox(
                    fox.id
                )
            },

            onDelete = { fox ->
                showDeleteFoxDialog(fox)
            }
        )

        rvFox.adapter = foxAdapter
    }

    private fun showDeleteFoxDialog(
        fox: CompleteFox
    ) {

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("여우 삭제")
            .setMessage(
                "\"${fox.name}\"을(를) 정말 삭제하시겠습니까?"
            )
            .setNegativeButton(
                "취소",
                null
            )
            .setPositiveButton(
                "삭제"
            ) { _, _ ->

                deleteFox(fox)
            }
            .show()
    }

    private fun deleteFox(
        fox: CompleteFox
    ) {

        // 현재 적용 중인 여우라면 기본 여우로 변경
        if (ThemePrefs.getFoxItem(this) == fox.id) {

            // 기본 여우 ID가 1이라는 현재 구조 기준
            ThemePrefs.saveFoxItem(
                this,
                1
            )
        }

        // 저장 데이터에서 삭제
        FoxPrefs.delete(
            this,
            fox.id
        )

        // 현재 리스트에서도 삭제
        foxList.removeAll {
            it.id == fox.id
        }

        // RecyclerView 갱신
        foxAdapter.notifyDataSetChanged()

        // 삭제된 여우가 현재 미리보기였다면 기본 여우 표시
        val currentFox = foxList.firstOrNull {
            it.id == ThemePrefs.getFoxItem(this)
        }

        if (currentFox != null) {

            val bitmap =
                FoxBitmapRenderer.createPreview(
                    this,
                    currentFox
                )

            if (bitmap != null) {
                FoxBitmapRenderer.displayPreview(imgPreview, bitmap, currentFox)
            } else {
                imgPreview.setImageResource(
                    currentFox.previewImage
                )
            }

        } else {

            imgPreview.setImageResource(
                R.drawable.dailyread_fox_face_level_3
            )
        }
    }

    /**
     * 테마 미리보기 갱신
     */
    private fun updatePreviewTheme(
        theme: Theme
    ) {

        // 현재 선택된 여우 유지
        val currentFox =
            foxList.firstOrNull {
                it.id == ThemePrefs.getFoxItem(this)
            }

        if (currentFox != null) {

            // 여우가 있으면 저장된 미리보기 사용
            showFoxPreview(currentFox)

        } else {

            // 여우가 없으면 테마 미리보기
            imgPreview.setImageResource(
                theme.previewImage
            )
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
        val appliedFox =
            foxList.firstOrNull {
                it.id == ThemePrefs.getFoxItem(this)
            }

        if (appliedFox != null) {

            showFoxPreview(appliedFox)

        } else {

            // 적용된 여우가 없으면 테마 미리보기
            val currentTheme =
                themeList.firstOrNull {
                    it.id == ThemePrefs.getTheme(this)
                }

            currentTheme?.let {
                imgPreview.setImageResource(
                    it.previewImage
                )
            }
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
