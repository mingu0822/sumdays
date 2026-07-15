package com.example.sumdays

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
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.shop.ThemeShopItem
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge

class CustomizeActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnShop: ImageButton

    private lateinit var imgPreview: ImageView

    private lateinit var themeAdapter: ThemeAdapter
    private lateinit var foxAdapter: FoxAdapter

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var navBarController: NavBarController

    private lateinit var rvTheme: RecyclerView
    private lateinit var rvFox: RecyclerView

    private val themeList = mutableListOf<ThemeShopItem>()
    private val foxList = mutableListOf<CompleteFox>()

    private var selectedTheme: ThemeShopItem? = null

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
    }

    private fun initViews() {

        btnBack = findViewById(R.id.btnBack)
        btnShop = findViewById(R.id.btnShop)

        rvTheme = findViewById(R.id.rvTheme)
        rvFox = findViewById(R.id.rvFox)

        imgPreview = findViewById(R.id.imgPreview)
    }
    private fun loadThemes() {

        themeList.clear()

        AllThemeMap.allThemeMap.forEach { (name, theme) ->

            val owned = OwnedPrefs.isOwned(this, name)

            if (owned) {
                themeList.add(
                    ThemeShopItem(
                        id = theme.id,
                        name = name,
                        description = theme.description,
                        price = theme.price,
                        isOwned = true,
                        theme = theme
                    )
                )
            }
        }
    }

    private fun loadFoxItems() {

        foxList.clear()

        AllFoxMap.allFoxMap.forEach { (_, fox) ->
            foxList.add(fox)
        }
    }

    private fun setupRecycler() {

        val currentTheme = ThemePrefs.getTheme(this)

        rvTheme.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        themeAdapter = ThemeAdapter(
            themeList,
            currentTheme
        ) { theme ->

            ThemePrefs.saveTheme(this, theme.name)

            themeAdapter.setAppliedTheme(theme.name)

            selectedTheme = theme

            updatePreviewTheme(theme)
        }

        rvTheme.adapter = themeAdapter

        rvFox.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        foxAdapter = FoxAdapter(
            foxList,
            ThemePrefs.getFoxItem(this)
        ) { fox ->

            ThemePrefs.saveFoxItem(this, fox.name)

            imgPreview.setImageResource(fox.previewImage)

            foxAdapter.setAppliedFox(fox.name)
        }

        rvFox.adapter = foxAdapter
    }

    private fun updatePreviewTheme(theme: ThemeShopItem) {

        imgPreview.setImageResource(theme.theme.previewImage)
    }
}