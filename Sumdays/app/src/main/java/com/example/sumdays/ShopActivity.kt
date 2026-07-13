package com.example.sumdays

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.shop.*
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.button.MaterialButton

class ShopActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton

    private lateinit var btnEarnPoint: Button

    private lateinit var tvCurrencyValue: TextView
    private lateinit var chipTheme: TextView
    private lateinit var chipItem: TextView

    private lateinit var rvShopItems: RecyclerView
    private lateinit var shopAdapter: ShopAdapter

    private lateinit var rootLayout: ConstraintLayout

    private lateinit var navBarController: NavBarController

    private lateinit var indicator: View

    private val allItems = mutableListOf<ShopItem>()
    private val filteredItems = mutableListOf<ShopItem>()

    private var selectedItem: ShopItem? = null
    private var currentPoint = 0
    private var selectedCategory = "theme"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        currentPoint = PointPrefs.getPoint(this)

        initViews()
        setupRecyclerView()
        loadItems()
        setupCategoryChips()
        bindBasicActions()

        rootLayout = findViewById(R.id.shopRoot)
        setupEdgeToEdge(rootLayout)

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.SHOP)

        updatePointUI()
        filterItems("theme")
    }

    private fun initViews() {

        backButton = findViewById(R.id.backButton)

        btnEarnPoint = findViewById(R.id.btnEarnPoint)
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue)

        chipTheme = findViewById(R.id.chipTheme)
        chipItem = findViewById(R.id.chipItem)

        rvShopItems = findViewById(R.id.rvShopItems)

        indicator = findViewById(R.id.tabIndicator)
    }

    private fun setupRecyclerView() {

        shopAdapter = ShopAdapter(
            items = filteredItems,

            onItemClick = {
                selectedItem = it
            },

            onActionClick = { item ->

                selectedItem = item

                when(item){

                    is ThemeShopItem -> {

                        if(item.isOwned)
                            applyItem(item)
                        else
                            tryPurchaseItem(item)
                    }

                    is FoxShopItem -> {
                        tryPurchaseItem(item)
                    }
                }
            }
        )

        rvShopItems.layoutManager = LinearLayoutManager(this)
        rvShopItems.adapter = shopAdapter
    }

    private fun loadItems() {

        allItems.clear()

        AllThemeMap.allThemeMap.forEach { (key, theme) ->

            theme.isOwned = OwnedPrefs.isOwned(this, key)

            allItems.add(
                ThemeShopItem(
                    id = theme.id,
                    name = key,
                    description = theme.description,
                    price = theme.price,
                    isOwned = theme.isOwned,
                    theme = theme
                )
            )
        }

        AllItemMap.allItemMap.forEach { (key, fox) ->

            fox.isOwned = OwnedPrefs.isOwned(this, key)

            allItems.add(
                FoxShopItem(
                    id = fox.id,
                    name = key,
                    description = fox.description,
                    price = fox.price,
                    isOwned = fox.isOwned
                )
            )
        }

        ThemeRepository.updateOwned()
    }

    private fun setupCategoryChips() {

        chipTheme.setOnClickListener {
            filterItems("theme")
        }

        chipItem.setOnClickListener {
            filterItems("item")
        }
    }

    private fun filterItems(category: String) {

        selectedCategory = category

        filteredItems.clear()

        when (category) {

            "theme" ->
                filteredItems.addAll(allItems.filterIsInstance<ThemeShopItem>())

            "item" ->
                filteredItems.addAll(allItems.filterIsInstance<FoxShopItem>())

            else ->
                filteredItems.addAll(allItems)
        }

        updateChipStyle()

        if (selectedItem !in filteredItems)
            selectedItem = filteredItems.firstOrNull()

        shopAdapter.notifyDataSetChanged()
    }

    private fun updateChipStyle() {

        chipTheme.post {

            val width = chipTheme.width

            val params = indicator.layoutParams
            params.width = width
            indicator.layoutParams = params

            if (selectedCategory == "theme") {

                chipTheme.setTextColor(getColor(R.color.foxrange))
                chipItem.setTextColor(getColor(android.R.color.black))

                indicator.animate()
                    .translationX(0f)
                    .setDuration(200)
                    .start()

            } else {

                chipTheme.setTextColor(getColor(android.R.color.black))
                chipItem.setTextColor(getColor(R.color.foxrange))

                indicator.animate()
                    .translationX(width.toFloat())
                    .setDuration(200)
                    .start()
            }
        }
    }

    private fun bindBasicActions() {

        backButton.setOnClickListener { finish() }

        btnEarnPoint.setOnClickListener {

            Toast.makeText(
                this,
                "포인트 기능 준비중",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applyItem(item: ShopItem) {

        when (item) {

            is ThemeShopItem -> {
                ThemePrefs.saveTheme(this, item.name)
                Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
            }

            is FoxShopItem -> {
                ThemePrefs.saveFoxItem(this, item.name)
                Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
            }
        }

        shopAdapter.notifyDataSetChanged()
    }

    private fun tryPurchaseItem(item: ShopItem){

        if(currentPoint < item.price){
            Toast.makeText(this,"포인트가 부족합니다.",Toast.LENGTH_SHORT).show()
            return
        }

        currentPoint -= item.price
        PointPrefs.savePoint(this,currentPoint)

        when(item){

            is ThemeShopItem -> {

                item.isOwned = true
                OwnedPrefs.saveOwned(this,item.name)
            }

            is FoxShopItem -> {

                ItemPrefs.addItem(this,item.name)
            }
        }

        updatePointUI()
        shopAdapter.notifyDataSetChanged()

        Toast.makeText(this,"${item.name} 구매 완료",Toast.LENGTH_SHORT).show()
    }

    private fun updatePointUI() {

        tvCurrencyValue.text = "%,d".format(currentPoint)
    }
}