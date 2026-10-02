package com.example.sumdays

import android.content.Intent
import android.os.Bundle
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
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.MergePassShopItem
import com.example.sumdays.shop.MergeTicketShopItem
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.shop.PointPrefs
import com.example.sumdays.shop.PointWalletRepository
import com.example.sumdays.shop.ShopPurchaseResult
import com.example.sumdays.shop.ThemeShopItem
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch

class ShopActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton

    private lateinit var btnEarnPoint: Button

    private lateinit var tvCurrencyValue: TextView
    private lateinit var chipTheme: TextView
    private lateinit var chipItem: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setupCategoryChips()
        bindBasicActions()

        rootLayout = findViewById(R.id.shopRoot)
        setupEdgeToEdge(rootLayout)

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.SHOP)

        updatePointUI()
        filterItems(intent.getStringExtra(EXTRA_INITIAL_CATEGORY) ?: "theme")
    }

    private fun initViews() {

        backButton = findViewById(R.id.backButton)

        btnEarnPoint = findViewById(R.id.btnEarnPoint)
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue)

        chipTheme = findViewById(R.id.chipTheme)
        chipItem = findViewById(R.id.chipItem)
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

            fox.isOwned = OwnedPrefs.isOwned(this, id)

            allItems.add(
                FoxShopItem(
                    id = fox.id,
                    name = fox.name,
                    description = fox.description,
                    price = serverProduct?.price ?: fox.price,
                    isOwned = fox.isOwned,
                    count = serverProduct?.quantity ?: ItemPrefs.getCount(this, id),
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

        ThemeRepository.updateOwned()
    }

    private fun setupCategoryChips() {

        chipTheme.setOnClickListener {
            filterItems("theme")
        }

        chipItem.setOnClickListener {
            filterItems("item")
        }

        chipMergeTicket.setOnClickListener {
            filterItems("merge_ticket")
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

            "merge_ticket" ->
                filteredItems.addAll(
                    allItems.filter { it is MergePassShopItem || it is MergeTicketShopItem }
                )

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

            when (selectedCategory) {
                "theme" -> {
                    chipTheme.setTextColor(getColor(R.color.foxrange))
                    chipItem.setTextColor(getColor(android.R.color.black))
                    chipMergeTicket.setTextColor(getColor(android.R.color.black))
                    indicator.animate().translationX(0f).setDuration(200).start()
                }

                "item" -> {
                    chipTheme.setTextColor(getColor(android.R.color.black))
                    chipItem.setTextColor(getColor(R.color.foxrange))
                    chipMergeTicket.setTextColor(getColor(android.R.color.black))
                    indicator.animate().translationX(width.toFloat()).setDuration(200).start()
                }

                else -> {
                    chipTheme.setTextColor(getColor(android.R.color.black))
                    chipItem.setTextColor(getColor(android.R.color.black))
                    chipMergeTicket.setTextColor(getColor(R.color.foxrange))
                    indicator.animate().translationX((width * 2).toFloat()).setDuration(200).start()
                }
            }
        }
    }

    private fun bindBasicActions() {

        backButton.setOnClickListener { finish() }

        btnEarnPoint.setOnClickListener {
            startActivity(Intent(this, MissionActivity::class.java))
        }
    }

    private fun applyItem(item: ShopItem) {

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
