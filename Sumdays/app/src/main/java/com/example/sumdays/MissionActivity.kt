package com.example.sumdays

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.ads.PendingAdRewardStore
import com.example.sumdays.ads.RewardedAdController
import com.example.sumdays.ads.RewardedAdState
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.billing.GooglePlayPointBillingManager
import com.example.sumdays.mission.AdRewardResult
import com.example.sumdays.mission.GooglePlayPointRedeemRequest
import com.example.sumdays.mission.Mission
import com.example.sumdays.mission.MissionAdapter
import com.example.sumdays.mission.MissionCategory
import com.example.sumdays.mission.MissionListItem
import com.example.sumdays.mission.MissionRepository
import com.example.sumdays.mission.MissionRepositoryFactory
import com.example.sumdays.network.ApiClient
import com.example.sumdays.shop.PointPrefs
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch

class MissionActivity : AppCompatActivity() {
    private lateinit var repository: MissionRepository
    private lateinit var adapter: MissionAdapter
    private lateinit var adCard: View
    private lateinit var adDescription: TextView
    private lateinit var rewardedAdController: RewardedAdController
    private lateinit var questTab: TextView
    private lateinit var purchaseTab: TextView
    private lateinit var questIndicator: View
    private lateinit var purchaseIndicator: View
    private lateinit var purchaseContent: View
    private lateinit var missionList: RecyclerView
    private lateinit var pointBilling: GooglePlayPointBillingManager
    private var isAdRewardSubmitting = false
    private val pendingPointTokens = mutableSetOf<String>()
    private var isQuestTabSelected = true
    private var tabSelectedColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mission)

        val root = findViewById<android.view.View>(R.id.missionRoot)
        setupEdgeToEdge(root)
        repository = MissionRepositoryFactory.create(applicationContext)
        adCard = findViewById(R.id.cardAdMission)
        adDescription = findViewById(R.id.tvAdMissionDescription)
        questTab = findViewById(R.id.tabPointQuest)
        purchaseTab = findViewById(R.id.tabPointPurchase)
        questIndicator = findViewById(R.id.pointQuestIndicator)
        purchaseIndicator = findViewById(R.id.pointPurchaseIndicator)
        purchaseContent = findViewById(R.id.pointPurchaseContent)
        missionList = findViewById(R.id.rvMissions)
        pointBilling = GooglePlayPointBillingManager(
            context = applicationContext,
            onPurchased = ::redeemGooglePlayPointPurchase,
            onPending = {
                Toast.makeText(this, "결제를 확인하고 있어요. 완료되면 포인트가 자동으로 지급됩니다.", Toast.LENGTH_SHORT).show()
            },
            onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() },
        )
        rewardedAdController = RewardedAdController(
            activity = this,
            adUnitId = getString(R.string.admob_rewarded_ad_unit_id),
            onStateChanged = { updateAdBanner() },
        )

        adapter = MissionAdapter(::claimReward)
        missionList.apply {
            layoutManager = LinearLayoutManager(this@MissionActivity)
            adapter = this@MissionActivity.adapter
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        adCard.setOnClickListener { showRewardedAd() }
        questTab.setOnClickListener { showPointTab(isQuestTab = true) }
        purchaseTab.setOnClickListener {
            showPointTab(isQuestTab = false)
            // 탭을 열 때 맨 위(제목·광고 카드)부터 보이도록
            purchaseContent.post { purchaseContent.scrollTo(0, 0) }
        }
        findViewById<View>(R.id.cardPoint1000).setOnClickListener { startPointPurchase(POINT_PRODUCT_1000) }
        findViewById<View>(R.id.cardPoint3300).setOnClickListener { startPointPurchase(POINT_PRODUCT_3300) }
        findViewById<View>(R.id.cardPoint5800).setOnClickListener { startPointPurchase(POINT_PRODUCT_5800) }
        findViewById<View>(R.id.cardPoint12000).setOnClickListener { startPointPurchase(POINT_PRODUCT_12000) }
        showPointTab(isQuestTab = true)

        NavBarController(this).setNavigationBar(NavSource.SHOP)
    }

    private fun getCurrentThemeOrNull(): Theme? {
        ThemeRepository.updateOwned()
        val themeKey = ThemePrefs.getTheme(this)
        return ThemeRepository.ownedThemes[themeKey]
            ?: ThemeRepository.allThemeMap[themeKey]
    }

    private fun applyThemeModeSettings() {
        val currentTheme = getCurrentThemeOrNull() ?: return
        val pointColor = ContextCompat.getColor(this, currentTheme.themeColorA)
        val basicTextColor = ContextCompat.getColor(this, currentTheme.themeTextColorBasic)
        tabSelectedColor = pointColor

        findViewById<View>(R.id.missionRoot).setBackgroundResource(currentTheme.backgroundColor)
        findViewById<View>(R.id.topBar).setBackgroundResource(currentTheme.backgroundColor)
        findViewById<View>(R.id.pointCenterTabs).setBackgroundResource(currentTheme.backgroundColor)
        questIndicator.setBackgroundColor(pointColor)
        purchaseIndicator.setBackgroundColor(pointColor)

        // 광고 카드는 강조(활성) 블록, 포인트 상품 카드는 기본 블록
        adCard.setBackgroundResource(currentTheme.blockStyleD)
        findViewById<ImageView>(R.id.ivAdMissionIcon).imageTintList = ColorStateList.valueOf(pointColor)

        POINT_CARD_IDS.forEach { id ->
            findViewById<View>(id).setBackgroundResource(currentTheme.blockStyleA)
        }

        applyTaggedTextColors(purchaseContent, basicTextColor, pointColor)
        showPointTab(isQuestTabSelected)
        adapter.setTheme(currentTheme)
    }

    // 구매 탭 카드의 반복 텍스트는 id 대신 layout의 android:tag로 구분한다
    private fun applyTaggedTextColors(view: View, basicTextColor: Int, pointColor: Int) {
        if (view is TextView) {
            when (view.tag) {
                TAG_TEXT_BASIC -> view.setTextColor(basicTextColor)
                TAG_TEXT_ACCENT -> view.setTextColor(pointColor)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTaggedTextColors(view.getChildAt(index), basicTextColor, pointColor)
            }
        }
    }

    private fun showPointTab(isQuestTab: Boolean) {
        isQuestTabSelected = isQuestTab
        missionList.visibility = if (isQuestTab) View.VISIBLE else View.GONE
        purchaseContent.visibility = if (isQuestTab) View.GONE else View.VISIBLE

        val selectedColor = tabSelectedColor ?: getColor(R.color.foxrange)
        val unselectedColor = getColor(android.R.color.darker_gray)
        questTab.setTextColor(if (isQuestTab) selectedColor else unselectedColor)
        purchaseTab.setTextColor(if (isQuestTab) unselectedColor else selectedColor)
        questIndicator.visibility = if (isQuestTab) View.VISIBLE else View.INVISIBLE
        purchaseIndicator.visibility = if (isQuestTab) View.INVISIBLE else View.VISIBLE
    }

    private fun startPointPurchase(productId: String) {
        val userId = SessionManager.getUserId()
        if (userId < 0 || SessionManager.getToken() == null) {
            Toast.makeText(this, "포인트 구매는 로그인 후 이용할 수 있어요.", Toast.LENGTH_SHORT).show()
            return
        }
        pointBilling.launchPurchase(this, productId, userId)
    }

    private fun redeemGooglePlayPointPurchase(productId: String, purchaseToken: String) {
        if (!pendingPointTokens.add(purchaseToken)) return
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken()
        if (userId < 0 || token == null) {
            pendingPointTokens.remove(purchaseToken)
            Toast.makeText(this, "로그인 상태를 확인한 뒤 구매를 다시 열어 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.redeemGooglePlayPoints(
                    "Bearer $token",
                    GooglePlayPointRedeemRequest(productId, purchaseToken),
                )
                if (!response.isSuccessful) {
                    error(response.body()?.message ?: "구매 확인 실패: ${response.code()}")
                }
                val result = response.body()?.data ?: error("포인트 지급 응답이 없습니다.")
                if (SessionManager.getUserId() == userId) {
                    PointPrefs.savePoint(applicationContext, userId, result.pointBalance)
                    Toast.makeText(
                        this@MissionActivity,
                        "+%,dP가 충전됐어요".format(result.pointAmount),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (error: Exception) {
                Toast.makeText(
                    this@MissionActivity,
                    error.message ?: "구매를 확인하지 못했습니다. 포인트 구매 화면을 다시 열어 주세요.",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                pendingPointTokens.remove(purchaseToken)
            }
        }
    }

    private fun claimReward(mission: Mission) {
        lifecycleScope.launch {
            try {
                val reward = repository.claimReward(mission.definition.code) ?: return@launch
                renderMissions()
                Toast.makeText(
                    this@MissionActivity,
                    "+%,dP를 수령했어요".format(reward),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    this@MissionActivity,
                    "보상을 받지 못했습니다. 잠시 후 다시 시도해 주세요",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private suspend fun renderMissions() {
        val missionsByCategory = repository.getMissions()
            .groupBy { it.definition.category }

        val rows = MissionCategory.entries
            .sortedBy(MissionCategory::order)
            .flatMap { category ->
                val missions = missionsByCategory[category]
                    .orEmpty()
                    .sortedBy { it.definition.displayOrder }

                if (missions.isEmpty()) {
                    emptyList()
                } else {
                    listOf(MissionListItem.Section(category)) +
                        missions.map(MissionListItem::Card)
                }
            }

        adapter.submitList(rows)
    }

    private fun updateAdBanner() {
        val state = repository.getAdRewardState()
        val pendingReward = PendingAdRewardStore.get(this)
        adCard.isEnabled = state.canWatch && !isAdRewardSubmitting
        adCard.alpha = if (adCard.isEnabled) 1f else 0.65f
        adDescription.text = when {
            isAdRewardSubmitting -> "광고 보상을 지급하는 중이에요"
            pendingReward != null -> "완료한 광고 보상을 다시 확인하고 있어요"
            !state.canWatch -> "오늘의 광고 포인트를 모두 받았어요 · %d/%d회".format(
                state.watchedCount,
                state.dailyLimit,
            )
            rewardedAdController.currentState() == RewardedAdState.READY ->
                "광고 시청 시 +%,dP · 오늘 %d/%d회".format(
                    state.rewardPoints,
                    state.watchedCount,
                    state.dailyLimit,
                )
            rewardedAdController.currentState() == RewardedAdState.FAILED ->
                "광고를 준비하지 못했어요 · 눌러서 다시 시도"
            else -> "광고를 준비하고 있어요 · 오늘 %d/%d회".format(
                state.watchedCount,
                state.dailyLimit,
            )
        }
    }

    private fun showRewardedAd() {
        val state = repository.getAdRewardState()
        if (!state.canWatch) {
            Toast.makeText(this, "오늘 받을 수 있는 광고 포인트를 모두 받았어요", Toast.LENGTH_SHORT).show()
            return
        }
        PendingAdRewardStore.get(this)?.let {
            submitAdReward(it)
            return
        }

        rewardedAdController.show(
            onRewardEarned = { rewardId ->
                PendingAdRewardStore.save(this, rewardId)
                submitAdReward(rewardId)
            },
            onUnavailable = {
                Toast.makeText(this, "광고를 준비 중이에요. 잠시 후 다시 눌러 주세요", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun submitAdReward(rewardId: String) {
        if (isAdRewardSubmitting) return
        isAdRewardSubmitting = true
        updateAdBanner()
        lifecycleScope.launch {
            try {
                when (val result = repository.claimAdReward(rewardId)) {
                    is AdRewardResult.Granted -> {
                        PendingAdRewardStore.clear(this@MissionActivity, rewardId)
                        Toast.makeText(
                            this@MissionActivity,
                            "+%,dP를 받았어요! 오늘 %d/%d회".format(
                                result.rewardPoints,
                                result.watchedCount,
                                result.dailyLimit,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    AdRewardResult.DailyLimitReached -> {
                        PendingAdRewardStore.clear(this@MissionActivity, rewardId)
                        Toast.makeText(
                            this@MissionActivity,
                            "오늘 받을 수 있는 광고 포인트를 모두 받았어요",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this@MissionActivity,
                    "광고 시청은 완료됐어요. 보상은 연결되면 자동으로 다시 확인할게요",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                isAdRewardSubmitting = false
                updateAdBanner()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 테마를 바꾸고 돌아와도 반영되도록 매번 적용
        applyThemeModeSettings()
        if (::pointBilling.isInitialized) pointBilling.refreshPurchases()
        if (::repository.isInitialized && ::adapter.isInitialized) {
            lifecycleScope.launch {
                try {
                    renderMissions()
                    updateAdBanner()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Toast.makeText(
                        this@MissionActivity,
                        error.message ?: "미션 정보를 불러오지 못했습니다",
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                val pendingReward = PendingAdRewardStore.get(this@MissionActivity)
                if (pendingReward != null) {
                    submitAdReward(pendingReward)
                } else if (repository.getAdRewardState().canWatch) {
                    rewardedAdController.load()
                }
            }
        }
    }

    override fun onDestroy() {
        if (::pointBilling.isInitialized) pointBilling.close()
        rewardedAdController.destroy()
        super.onDestroy()
    }

    private companion object {
        const val POINT_PRODUCT_1000 = "sumdays_points_1000"
        const val POINT_PRODUCT_3300 = "sumdays_points_3300"
        const val POINT_PRODUCT_5800 = "sumdays_points_5800"
        const val POINT_PRODUCT_12000 = "sumdays_points_12000"

        const val TAG_TEXT_BASIC = "theme_text_basic"
        const val TAG_TEXT_ACCENT = "theme_text_accent"
        val POINT_CARD_IDS = listOf(
            R.id.cardPoint1000,
            R.id.cardPoint3300,
            R.id.cardPoint5800,
            R.id.cardPoint12000,
        )
    }
}
