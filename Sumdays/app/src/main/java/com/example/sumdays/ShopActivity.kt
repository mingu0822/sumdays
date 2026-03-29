package com.example.sumdays

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.ThemeShopItem

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

    private lateinit var rvShopItems: RecyclerView

    private lateinit var shopAdapter: ShopAdapter
    private val allItems = mutableListOf<ShopItem>()
    private val filteredItems = mutableListOf<ShopItem>()

    private var selectedItem: ShopItem? = null
    private var currentPoint: Int = 1240
    private var selectedCategory: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

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
                    Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
                } else {
                    tryPurchaseItem(item)
                }
            }
        )

        rvShopItems.layoutManager = LinearLayoutManager(this)
        rvShopItems.adapter = shopAdapter
    }

    private fun loadItems() {
        val allThemeMap = AllThemeMap.allThemeMap

        allItems.clear()
        for ((name, theme) in allThemeMap) {
            allItems.add(
                ThemeShopItem(
                    id = theme.id,
                    name = name,
                    description = theme.description,
                    price = theme.price,
                    isOwned = theme.isOwned,
                    theme = theme,
                )
            )
        }
    }

    private fun setupCategoryChips() {
        chipAll.setOnClickListener { filterItems("all") }
        chipTheme.setOnClickListener { filterItems("theme") }
        chipFox.setOnClickListener { filterItems("sticker") }
        chipSticker.setOnClickListener { filterItems("sticker") }
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

        val selectedBg = getColor(R.color.btn_violet)
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

    private fun tryPurchaseItem(item: ShopItem) {
        if (currentPoint < item.price) {
            Toast.makeText(this, "포인트가 부족합니다", Toast.LENGTH_SHORT).show()
            return
        }

        currentPoint -= item.price
        item.isOwned = true

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