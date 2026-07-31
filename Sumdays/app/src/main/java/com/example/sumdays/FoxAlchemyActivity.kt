package com.example.sumdays

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.alchemy.AlchemyInventoryBottomSheet
import com.example.sumdays.alchemy.AlchemySelectionManager
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory

class FoxAlchemyActivity : AppCompatActivity() {

    private lateinit var alchemyPot: ImageButton
    private lateinit var btnBack: ImageButton

    // 상단 재료 패널
    private lateinit var materialPanel: View

    // 구름
    private lateinit var alchemyCloud: ImageView

    // 슬롯
    private lateinit var slotGlasses: ImageView
    private lateinit var slotHat: ImageView
    private lateinit var slotScarf: ImageView
    private lateinit var slotAccessory: ImageView

    // 현재 선택된 아이템
    private val selectedItems = mutableListOf<FoxShopItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fox_alchemy)

        alchemyPot = findViewById(R.id.alchemy_pot)
        btnBack = findViewById(R.id.btnBack)

        materialPanel = findViewById(R.id.materialPanel)
        alchemyCloud = findViewById(R.id.alchemyCloud)

        slotGlasses = findViewById(R.id.slotGlasses)
        slotHat = findViewById(R.id.slotHat)
        slotScarf = findViewById(R.id.slotScarf)
        slotAccessory = findViewById(R.id.slotAccessory)

        materialPanel.visibility = View.GONE
        alchemyCloud.visibility = View.GONE

        clearSlots()

        btnBack.setOnClickListener {

            AlchemySelectionManager.clear(this)
            finish()
        }

        alchemyPot.setOnClickListener {

            showMaterialPanel()

            AlchemyInventoryBottomSheet(

                onSelectionChanged = { items ->

                    selectedItems.clear()
                    selectedItems.addAll(items)

                    updateSlots(selectedItems)
                },

                onCombine = { items ->

                    selectedItems.clear()
                    selectedItems.addAll(items)

                    createFox(selectedItems)
                },

                onSheetClosed = {

                    hideMaterialPanel()
                }

            ).show(
                supportFragmentManager,
                "alchemy"
            )
        }

        startPotAnimation()
    }

    /**
     * 솥 둥둥 애니메이션
     */
    private fun startPotAnimation() {

        val animator = ObjectAnimator.ofFloat(
            alchemyPot,
            "translationY",
            -20f,
            20f
        )

        animator.duration = 1200L
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.interpolator =
            AccelerateDecelerateInterpolator()

        animator.start()
    }

    /**
     * 상단 재료 패널 표시
     */
    private fun showMaterialPanel() {

        if (materialPanel.visibility == View.VISIBLE)
            return

        clearSlots()

        materialPanel.visibility = View.VISIBLE
        alchemyCloud.visibility = View.VISIBLE

        materialPanel.alpha = 0f
        materialPanel.translationY = -120f

        alchemyCloud.alpha = 0f
        alchemyCloud.translationY = -80f

        materialPanel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        alchemyCloud.animate()
            .alpha(0.75f)
            .translationY(0f)
            .setDuration(300)
            .start()
    }

    /**
     * 상단 패널 숨김
     */
    private fun hideMaterialPanel() {

        materialPanel.animate()
            .alpha(0f)
            .translationY(-120f)
            .setDuration(180)
            .withEndAction {

                materialPanel.visibility = View.GONE
                materialPanel.alpha = 1f
                materialPanel.translationY = 0f
            }
            .start()

        alchemyCloud.animate()
            .alpha(0f)
            .translationY(-80f)
            .setDuration(180)
            .withEndAction {

                alchemyCloud.visibility = View.GONE
                alchemyCloud.alpha = 1f
                alchemyCloud.translationY = 0f

                clearSlots()
                selectedItems.clear()
            }
            .start()
    }

    /**
     * 슬롯 초기화
     */
    private fun clearSlots() {

        slotGlasses.setImageResource(R.drawable.ic_add_white_24)
        slotHat.setImageResource(R.drawable.ic_add_white_24)
        slotScarf.setImageResource(R.drawable.ic_add_white_24)
        slotAccessory.setImageResource(R.drawable.ic_add_white_24)
    }

    /**
     * 슬롯 갱신
     */
    private fun updateSlots(
        items: List<FoxShopItem>
    ) {

        clearSlots()

        items.forEach { item ->

            when (item.itemCategory) {

                ItemCategory.GLASSES -> {
                    slotGlasses.setImageResource(item.imageRes)
                }

                ItemCategory.HAT -> {
                    slotHat.setImageResource(item.imageRes)
                }

                ItemCategory.SCARF -> {
                    slotScarf.setImageResource(item.imageRes)
                }

                ItemCategory.ACCESSORY -> {
                    slotAccessory.setImageResource(item.imageRes)
                }
            }
        }
    }

    /**
     * 조합하기
     */
    private fun createFox(
        items: List<FoxShopItem>
    ) {

        if (items.isEmpty()) {

            Toast.makeText(
                this,
                "아이템을 선택해주세요.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // 이미 선택 시 차감되었으므로 확정만 한다.
        AlchemySelectionManager.commit()

        val names = items.joinToString(", ") {
            it.name
        }

        Toast.makeText(
            this,
            "$names 조합 완료!",
            Toast.LENGTH_SHORT
        ).show()

        selectedItems.clear()

        clearSlots()

        hideMaterialPanel()

        // TODO
        // 1. 선택된 아이템 조합 공식 계산
        // 2. 새로운 여우 생성
        // 3. 여우 저장
        // 4. 결과 팝업 표시
        // 5. alchemyCloud에 생성된 여우 미리보기 출력
    }

    override fun onDestroy() {

        // Activity 종료 시 예약 차감 복원
        AlchemySelectionManager.clear(this)

        super.onDestroy()
    }
}