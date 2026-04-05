package com.example.sumdays

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.shop.AllFoxMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.shop.PointPrefs
import com.example.sumdays.shop.ThemeShopItem
import com.example.sumdays.theme.ThemePrefs

class ShopActivity : AppCompatActivity() {

    private lateinit var btnShopClose: ImageButton
    private lateinit var btnEarnPoint: Button
    private lateinit var btnPurchase: Button

    private lateinit var tvCurrencyValue: TextView
    private lateinit var tvSelectedItemName: TextView
    private lateinit var tvSelectedItemDesc: TextView
    private lateinit var tvSelectedItemPrice: TextView

    private lateinit var chipAll: TextView
    private lateinit var chipTheme: TextView
    private lateinit var chipFox: TextView
    private lateinit var chipSticker: TextView

    private lateinit var btnReset: TextView

    private lateinit var rvShopItems: RecyclerView

    private lateinit var shopAdapter: ShopAdapter
    private val allItems = mutableListOf<ShopItem>()
    private val filteredItems = mutableListOf<ShopItem>()

    private var selectedItem: ShopItem? = null
    private var currentPoint: Int = 0
    private var selectedCategory: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        currentPoint = PointPrefs.getPoint(this)

        initViews()
        setupRecyclerView()
        loadItems()
        setupCategoryChips()
        bindBasicActions()
        updatePointUI()
        filterItems("all")
        updateSelectedItemUI()
    }

    private fun initViews() {
        btnShopClose = findViewById(R.id.btnShopClose)
        btnEarnPoint = findViewById(R.id.btnEarnPoint)
        btnPurchase = findViewById(R.id.btnPurchase)

        tvCurrencyValue = findViewById(R.id.tvCurrencyValue)
        tvSelectedItemName = findViewById(R.id.tvSelectedItemName)
        tvSelectedItemDesc = findViewById(R.id.tvSelectedItemDesc)
        tvSelectedItemPrice = findViewById(R.id.tvSelectedItemPrice)

        chipAll = findViewById(R.id.chipAll)
        chipTheme = findViewById(R.id.chipTheme)
        chipFox = findViewById(R.id.chipFox)
        chipSticker = findViewById(R.id.chipSticker)

        btnReset = findViewById(R.id.btn_reset)

        rvShopItems = findViewById(R.id.rvShopItems)
    }

    private fun setupRecyclerView() {
        shopAdapter = ShopAdapter(
            items = filteredItems,
            onItemClick = { item ->
                selectedItem = item
                updateSelectedItemUI()
            },
            onActionClick = { item ->
                if (item.isOwned) {
                    selectedItem = item
                    updateSelectedItemUI()
                    applyItem(item)
                } else {
                    tryPurchaseItem(item)
                }
            }
        )

        rvShopItems.layoutManager = LinearLayoutManager(this)
        rvShopItems.adapter = shopAdapter
    }

    private fun resetShop() {
        val defaultThemeKey = "default"
        val defaultFoxKey = "default"

        // 1) 포인트 초기화
        currentPoint = 1240
        PointPrefs.savePoint(this, currentPoint)

        // 2) 구매 상태 저장소 초기화
        OwnedPrefs.clear(this)

        // 3) 테마 구매 상태 초기화
        for ((name, theme) in AllThemeMap.allThemeMap) {
            theme.isOwned = (name == defaultThemeKey)
        }

        // 4) 여우 구매 상태 초기화
        for ((name, fox) in AllFoxMap.allFoxMap) {
            fox.isOwned = (name == defaultFoxKey)
        }

        // 5) 기본 테마/여우 다시 적용
        ThemePrefs.saveTheme(this, defaultThemeKey)
        ThemePrefs.saveFox(this, defaultFoxKey)

        // 6) 기본 아이템은 다시 구매 상태로 저장
        OwnedPrefs.saveOwned(this, defaultThemeKey)
        OwnedPrefs.saveOwned(this, defaultFoxKey)

        // 7) 화면 데이터 다시 불러오기
        loadItems()
        filterItems(selectedCategory)

        updatePointUI()
        updateSelectedItemUI()
        shopAdapter.notifyDataSetChanged()

        Toast.makeText(this, "상점이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun loadItems() {
        allItems.clear()

        val allThemeMap = AllThemeMap.allThemeMap
        for ((name, theme) in allThemeMap) {

            val owned = OwnedPrefs.isOwned(this, name)

            allItems.add(
                ThemeShopItem(
                    id = theme.id,
                    name = name,
                    description = theme.description,
                    price = theme.price,
                    isOwned = owned,
                    theme = theme,
                )
            )
        }

        val allFoxMap = AllFoxMap.allFoxMap
        for ((name, fox) in allFoxMap) {

            val owned = OwnedPrefs.isOwned(this, name)

            allItems.add(
                FoxShopItem(
                    id = fox.id,
                    name = name,
                    description = fox.description,
                    price = fox.price,
                    isOwned = owned,
                    fox = fox,
                )
            )
        }
    }

    private fun setupCategoryChips() {
        chipAll.setOnClickListener { filterItems("all") }
        chipTheme.setOnClickListener { filterItems("theme") }
        chipFox.setOnClickListener { filterItems("fox") }
        chipSticker.setOnClickListener { filterItems("sticker") }

        btnReset.setOnClickListener {
            resetShop()
        }
    }

    private fun filterItems(category: String) {
        selectedCategory = category

        filteredItems.clear()
        if (category == "all") {
            filteredItems.addAll(allItems)
        } else {
            filteredItems.addAll(allItems.filter { it.category == category })
        }

        updateChipStyle()

        if (filteredItems.isNotEmpty()) {
            if (selectedItem == null || !filteredItems.contains(selectedItem)) {
                selectedItem = filteredItems[0]
            }
        } else {
            selectedItem = null
        }

        shopAdapter.notifyDataSetChanged()
        updateSelectedItemUI()
    }

    private fun updateChipStyle() {
        val selectedTextColor = getColor(android.R.color.white)
        val normalTextColor = getColor(android.R.color.black)

        val selectedBg = getColor(R.color.foxrange)
        val normalBg = getColor(android.R.color.transparent)

        listOf(chipAll, chipTheme, chipFox, chipSticker).forEach {
            it.setBackgroundColor(normalBg)
            it.setTextColor(normalTextColor)
        }

        when (selectedCategory) {
            "all" -> {
                chipAll.setBackgroundColor(selectedBg)
                chipAll.setTextColor(selectedTextColor)
            }
            "theme" -> {
                chipTheme.setBackgroundColor(selectedBg)
                chipTheme.setTextColor(selectedTextColor)
            }
            "fox" -> {
                chipFox.setBackgroundColor(selectedBg)
                chipFox.setTextColor(selectedTextColor)
            }
            "sticker" -> {
                chipSticker.setBackgroundColor(selectedBg)
                chipSticker.setTextColor(selectedTextColor)
            }
        }
    }

    private fun bindBasicActions() {
        btnShopClose.setOnClickListener {
            finish()
        }

        btnEarnPoint.setOnClickListener {
            Toast.makeText(this, "포인트 모으기 기능은 아직 준비 중입니다", Toast.LENGTH_SHORT).show()
        }

        btnPurchase.setOnClickListener {
            val item = selectedItem ?: return@setOnClickListener

            if (item.isOwned) {
                Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
            } else {
                tryPurchaseItem(item)
            }
        }
    }

    private fun applyItem(item: ShopItem) {
        when (item) {
            is ThemeShopItem -> {
                ThemePrefs.saveTheme(this, item.name)
                Toast.makeText(this, "${item.name} 테마 적용", Toast.LENGTH_SHORT).show()
                recreate()
            }

            is FoxShopItem -> {
                ThemePrefs.saveFox(this, item.name)
                Toast.makeText(this, "${item.name} 여우 적용", Toast.LENGTH_SHORT).show()
                recreate()
            }

            else -> {
                Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun tryPurchaseItem(item: ShopItem) {

        if (currentPoint < item.price) {
            Toast.makeText(this, "포인트가 부족합니다", Toast.LENGTH_SHORT).show()
            return
        }

        currentPoint -= item.price
        PointPrefs.savePoint(this, currentPoint)

        item.isOwned = true

        OwnedPrefs.saveOwned(this, item.name)

        updatePointUI()
        updateSelectedItemUI()
        shopAdapter.notifyDataSetChanged()

        Toast.makeText(this, "${item.name} 구매 완료", Toast.LENGTH_SHORT).show()
    }

    private fun updatePointUI() {
        tvCurrencyValue.text = currentPoint.toString()
    }

    private fun updateSelectedItemUI() {
        val item = selectedItem

        if (item == null) {
            tvSelectedItemName.text = "선택된 상품 없음"
            tvSelectedItemDesc.text = "카테고리를 선택해 상품을 골라보세요"
            tvSelectedItemPrice.text = "-"
            btnPurchase.text = "구매하기"
            btnPurchase.isEnabled = false
            return
        }

        tvSelectedItemName.text = item.name
        tvSelectedItemDesc.text = item.description
        tvSelectedItemPrice.text = if (item.isOwned) "보유중" else "${item.price}P"
        btnPurchase.text = if (item.isOwned) "적용하기" else "구매하기"
        btnPurchase.isEnabled = true
    }
}