package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import com.example.sumdays.auth.AccountInventory
import com.example.sumdays.auth.InventoryOwner
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.billing.GooglePlayMergePassBillingManager
import com.example.sumdays.daily.memo.MergeMarbleRepository
import com.example.sumdays.daily.memo.MergeMarbleState
import com.example.sumdays.daily.memo.MergeTicketProduct
import com.example.sumdays.mission.MissionConfig
import com.example.sumdays.mission.ShopProductResponse
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.MergePassShopItem
import com.example.sumdays.shop.MergeTicketShopItem
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.shop.PointPrefs
import com.example.sumdays.shop.PointWalletRepository
import com.example.sumdays.shop.ShopPurchaseResult
import com.example.sumdays.shop.StickerCatalog
import com.example.sumdays.shop.StickerPrefs
import com.example.sumdays.shop.StickerShopItem
import com.example.sumdays.shop.ThemeShopItem
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.sumdays.ui.component.setupBackToCalendar

class ShopActivity : AppCompatActivity() {
    private lateinit var inventoryOwner: InventoryOwner

    private lateinit var backButton: ImageButton

    private lateinit var btnEarnPoint: Button

    private lateinit var tvCurrencyValue: TextView
    private lateinit var chipTheme: TextView
    private lateinit var chipItem: TextView
    private lateinit var chipSticker: TextView
    private lateinit var chipMergeTicket: TextView

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
    private lateinit var pointWalletRepository: PointWalletRepository
    private lateinit var mergeMarbleRepository: MergeMarbleRepository
    private lateinit var mergePassBilling: GooglePlayMergePassBillingManager
    private var purchaseInProgress = false
    private var shopDataReady = !MissionConfig.USE_SERVER_MISSIONS
    private var ticketState: MergeMarbleState? = null
    private val pendingPassTokens = mutableSetOf<String>()
    private var selectedItemCategory: ItemCategory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        inventoryOwner = InventoryOwner(savedInstanceState)
        super.onCreate(savedInstanceState)
        if (!inventoryOwner.requireCurrent(this)) return
        setContentView(R.layout.activity_shop)

        currentPoint = PointPrefs.getPoint(this)
        pointWalletRepository = PointWalletRepository(applicationContext)
        mergeMarbleRepository = MergeMarbleRepository(applicationContext)
        mergePassBilling = GooglePlayMergePassBillingManager(
            context = applicationContext,
            onPurchased = ::redeemMergePass,
            onPending = {
                Toast.makeText(this, "결제를 확인하고 있어요. 완료되면 패스가 활성화됩니다.", Toast.LENGTH_SHORT).show()
            },
            onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() },
        )

        initViews()
        setupRecyclerView()
        loadItems()
        selectedCategory = (savedInstanceState?.getString("shop_category")
            ?: intent.getStringExtra("shop_category"))?.takeIf { it in listOf("theme", "item", "sticker") } ?: "theme"
        selectedItemCategory = ItemCategory.values().find {
            it.name == savedInstanceState?.getString("item_category")
        }
        setupCategoryChips()
        bindBasicActions()

        rootLayout = findViewById(R.id.shopRoot)
        setupEdgeToEdge(rootLayout)

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.SHOP)

        updatePointUI()
        filterItems(intent.getStringExtra(EXTRA_INITIAL_CATEGORY) ?: "theme")
        setupBackToCalendar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        inventoryOwner.saveState(outState)
        super.onSaveInstanceState(outState)
        outState.putString("shop_category", selectedCategory)
        outState.putString("item_category", selectedItemCategory?.name)
    }

    private fun initViews() {


        btnEarnPoint = findViewById(R.id.btnEarnPoint)
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue)

        chipTheme = findViewById(R.id.chipTheme)
        chipItem = findViewById(R.id.chipItem)
        chipSticker = findViewById(R.id.chipSticker)
        chipMergeTicket = findViewById(R.id.chipMergeTicket)

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
                if (!inventoryOwner.requireCurrent(this)) return@ShopAdapter

                if (!shopDataReady) {
                    Toast.makeText(this, "상점 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
                } else {
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

                        is MergeTicketShopItem -> {
                            tryPurchaseMergeTicket(item.product)
                        }

                        is MergePassShopItem -> {
                            startMergePassPurchase()
                        }

                        // 서버 지갑에 스티커 상품이 생기기 전까지 어댑터에서 버튼을 비활성화한다.
                        is StickerShopItem -> Unit
                    }
                }
            }
        )

        rvShopItems.layoutManager = LinearLayoutManager(this)
        rvShopItems.adapter = shopAdapter
    }

    private fun loadItems(
        serverProducts: List<ShopProductResponse>? = null,
        mergeMarbleState: MergeMarbleState? = ticketState,
    ) {

        allItems.clear()
        AccountInventory.refreshOwnership(this)
        val serverProductMap = serverProducts
            ?.associateBy { "${it.productType}:${it.itemId}" }

        AllThemeMap.allThemeMap.forEach { (id, theme) ->

            val serverProduct = serverProductMap?.get("THEME:$id")
            if (serverProducts != null && id != 1 && serverProduct == null) {
                return@forEach
            }

            theme.isOwned = when {
                id == 1 -> true
                serverProduct != null -> serverProduct.owned
                else -> OwnedPrefs.isOwned(this, id)
            }

            allItems.add(
                ThemeShopItem(
                    id = theme.id,
                    name = theme.name,
                    description = theme.description,
                    price = serverProduct?.price ?: theme.price,
                    isOwned = theme.isOwned,
                    theme = theme,
                    imageRes = theme.previewImage
                )
            )
        }

        AllItemMap.allItemMap.forEach { (id, fox) ->

            val serverProduct = serverProductMap?.get("ITEM:$id")
            if (serverProducts != null && serverProduct == null) {
                return@forEach
            }

            fox.count = serverProduct?.quantity ?: ItemPrefs.getCount(this, id)
            fox.isOwned = fox.count > 0

            allItems.add(
                FoxShopItem(
                    id = fox.id,
                    name = fox.name,
                    description = fox.description,
                    price = serverProduct?.price ?: fox.price,
                    isOwned = fox.isOwned,
                    count = fox.count,
                    itemCategory = fox.itemCategory,
                    imageRes = fox.imageRes
                )
            )
        }

        mergeMarbleState?.let { state ->
            allItems.add(
                MergePassShopItem(
                    active = state.passActive,
                    expiresAt = state.passExpiresAt,
                )
            )
            state.ticketProducts.forEach { product ->
                allItems.add(
                    MergeTicketShopItem(
                        product = product,
                        ownedTicketCount = state.ticketBalance,
                        purchaseEnabled = !state.passActive,
                        disabledActionLabel = "패스 이용 중",
                    )
                )
            }
        }

        allItems.addAll(StickerCatalog.items.map { sticker ->
            sticker.copy(isOwned = StickerPrefs.getCount(this, sticker.id) > 0)
        })

        ThemeRepository.updateOwned()
    }

    private fun setupCategoryChips() {

        chipTheme.setOnClickListener {
            filterItems("theme")
        }

        chipItem.setOnClickListener {
            filterItems("item")
        }

        chipSticker.setOnClickListener {
            filterItems("sticker")
        }

        chipMergeTicket.setOnClickListener {
            filterItems("merge_ticket")
        }
        val group = findViewById<ChipGroup>(R.id.shopItemCategories)
        listOf(
            null to "전체", ItemCategory.FOXFACE to "표정",
            ItemCategory.GLASSES to "안경", ItemCategory.HAT to "모자",
            ItemCategory.SCARF to "목도리", ItemCategory.ACCESSORY to "악세서리"
        ).forEach { (category, label) ->
            group.addView(Chip(this).apply {
                id = View.generateViewId()
                tag = category
                text = label
                isCheckable = true
                isChecked = category == selectedItemCategory
                setOnClickListener {
                    selectedItemCategory = category
                    filterItems("item")
                }
            })
        }
    }

    private fun filterItems(category: String) {

        selectedCategory = category

        filteredItems.clear()

        when (category) {

            "theme" ->
                filteredItems.addAll(allItems.filterIsInstance<ThemeShopItem>())

            "item" ->
                filteredItems.addAll(allItems.filterIsInstance<FoxShopItem>().filter {
                    selectedItemCategory == null || it.itemCategory == selectedItemCategory
                })

            "merge_ticket" ->
                filteredItems.addAll(
                    allItems.filter { it is MergePassShopItem || it is MergeTicketShopItem }
                )

            "sticker" ->
                filteredItems.addAll(allItems.filterIsInstance<StickerShopItem>())

            else ->
                filteredItems.addAll(allItems)
        }

        updateChipStyle()
        findViewById<View>(R.id.shopItemCategoryScroll).visibility =
            if (category == "item") View.VISIBLE else View.GONE
        val group = findViewById<ChipGroup>(R.id.shopItemCategories)
        for (index in 0 until group.childCount) {
            (group.getChildAt(index) as Chip).apply { isChecked = tag == selectedItemCategory }
        }

        if (selectedItem !in filteredItems)
            selectedItem = filteredItems.firstOrNull()

        shopAdapter.notifyDataSetChanged()
    }

    private fun updateChipStyle() {
        val tabs = listOf(
            "theme" to chipTheme,
            "item" to chipItem,
            "sticker" to chipSticker,
            CATEGORY_MERGE_TICKET to chipMergeTicket,
        )
        tabs.forEach { (category, tab) ->
            tab.isSelected = selectedCategory == category
            tab.setTextColor(getColor(if (tab.isSelected) R.color.foxrange else android.R.color.black))
        }

        indicator.post {
            val selectedTab = tabs.firstOrNull { it.first == selectedCategory }?.second ?: return@post
            indicator.layoutParams = indicator.layoutParams.apply { width = selectedTab.width }
            indicator.animate()
                .translationX((selectedTab.left - indicator.left).toFloat())
                .setDuration(200)
                .start()
        }
    }

    private fun bindBasicActions() {
        btnEarnPoint.setOnClickListener {
            startActivity(Intent(this, MissionActivity::class.java))
        }
    }

    private fun applyItem(item: ShopItem) {
        if (!inventoryOwner.requireCurrent(this)) return

        when (item) {

            is ThemeShopItem -> {
                ThemePrefs.saveTheme(this, item.id)
                Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
            }

            is FoxShopItem -> {
                ThemePrefs.saveFoxItem(this, item.id)
                Toast.makeText(this, "${item.name} 적용", Toast.LENGTH_SHORT).show()
            }
        }

        shopAdapter.notifyDataSetChanged()
    }

    private fun tryPurchaseItem(item: ShopItem){
        if (!inventoryOwner.requireCurrent(this)) return
        if (purchaseInProgress) return
        purchaseInProgress = true
        lifecycleScope.launch {
            try {
                when (val result = pointWalletRepository.purchase(item)) {
                    is ShopPurchaseResult.InsufficientPoints -> {
                        result.pointBalance?.let {
                            currentPoint = it
                            updatePointUI()
                        }
                        Toast.makeText(this@ShopActivity,"포인트가 부족합니다.",Toast.LENGTH_SHORT).show()
                    }
                    is ShopPurchaseResult.Purchased -> {
                        currentPoint = result.pointBalance
                        when(item){
                            is ThemeShopItem -> {
                                item.isOwned = true
                            }
                            is FoxShopItem -> item.count = result.quantity
                        }
                        updatePointUI()
                        shopAdapter.notifyDataSetChanged()
                        AccountInventory.refreshOwnership(this@ShopActivity)
                        val message = if (result.alreadyOwned) {
                            "이미 보유한 상품입니다."
                        } else {
                            "${item.name} 구매 완료"
                        }
                        Toast.makeText(this@ShopActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this@ShopActivity,
                    "구매하지 못했습니다. 잠시 후 다시 시도해 주세요",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                purchaseInProgress = false
            }
        }
    }

    private fun tryPurchaseMergeTicket(product: MergeTicketProduct) {
        if (purchaseInProgress) return
        purchaseInProgress = true
        lifecycleScope.launch {
            try {
                val state = mergeMarbleRepository.purchaseTicket(product)
                ticketState = state
                currentPoint = state.pointBalance
                loadItems(mergeMarbleState = state)
                filterItems(selectedCategory)
                updatePointUI()
                Toast.makeText(
                    this@ShopActivity,
                    "합치기 횟수권 ${product.ticketCount}회를 추가했어요.",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: Exception) {
                Toast.makeText(
                    this@ShopActivity,
                    error.message ?: "횟수권을 구매하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                purchaseInProgress = false
            }
        }
    }

    private fun startMergePassPurchase() {
        if (ticketState?.passActive == true) return
        val userId = SessionManager.getUserId()
        if (userId < 0 || SessionManager.getToken() == null) {
            Toast.makeText(this, "무제한 패스는 로그인 후 이용할 수 있어요.", Toast.LENGTH_SHORT).show()
            return
        }
        mergePassBilling.launchPurchase(this, userId)
    }

    private fun redeemMergePass(productId: String, purchaseToken: String) {
        if (!pendingPassTokens.add(purchaseToken)) return
        lifecycleScope.launch {
            try {
                val state = mergeMarbleRepository.redeemPass(productId, purchaseToken)
                ticketState = state
                currentPoint = state.pointBalance
                loadItems(mergeMarbleState = state)
                filterItems(CATEGORY_MERGE_TICKET)
                updatePointUI()
                Toast.makeText(
                    this@ShopActivity,
                    if (state.duplicate) "무제한 패스를 확인했어요." else "무제한 패스가 활성화됐어요!",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: Exception) {
                Toast.makeText(
                    this@ShopActivity,
                    error.message ?: "무제한 패스 구매를 확인하지 못했습니다. 다시 열어 주세요.",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                pendingPassTokens.remove(purchaseToken)
            }
        }
    }

    private fun updatePointUI() {

        tvCurrencyValue.text = "%,d".format(currentPoint)
    }
    override fun onResume() {
        super.onResume()
        if (!inventoryOwner.requireCurrent(this)) return
        if (::mergePassBilling.isInitialized) mergePassBilling.refreshPurchases()
        if (::tvCurrencyValue.isInitialized) {
            currentPoint = PointPrefs.getPoint(this)
            updatePointUI()
            lifecycleScope.launch {
                runCatching {
                    val catalog = pointWalletRepository.refreshShopCatalog()
                    val marbles = mergeMarbleRepository.refresh()
                    catalog to marbles
                }.onSuccess { (catalog, marbles) ->
                        ticketState = marbles
                        if (catalog != null) {
                            currentPoint = catalog.pointBalance
                        } else {
                            currentPoint = marbles.pointBalance
                        }
                        loadItems(catalog?.products, marbles)
                        filterItems(selectedCategory)
                        shopDataReady = true
                        updatePointUI()
                    }
                    .onFailure {
                        shopDataReady = !MissionConfig.USE_SERVER_MISSIONS
                        if (MissionConfig.USE_SERVER_MISSIONS) {
                            Toast.makeText(
                                this@ShopActivity,
                                "상점 정보를 불러오지 못했습니다.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
            }
        }
    }

    override fun onDestroy() {
        if (::mergePassBilling.isInitialized) mergePassBilling.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INITIAL_CATEGORY = "initial_shop_category"
        const val CATEGORY_MERGE_TICKET = "merge_ticket"
    }
}
