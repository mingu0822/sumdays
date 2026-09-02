package com.example.sumdays

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.example.sumdays.alchemy.AlchemyInventoryBottomSheet
import com.example.sumdays.alchemy.AlchemyRecipeManager
import com.example.sumdays.alchemy.AlchemySelectionManager
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.customize.FoxBitmapRenderer
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory

class FoxAlchemyActivity : AppCompatActivity() {

    private lateinit var alchemyPot: ImageButton
    private lateinit var btnBack: ImageButton

    // 상단 재료 패널
    private lateinit var materialPanel: View

    // 구름 / 결과 여우
    private lateinit var alchemyCloud: ImageView
    private lateinit var imgResultFox: ImageView

    // 슬롯
    private lateinit var slotGlasses: ImageView
    private lateinit var slotHat: ImageView
    private lateinit var slotScarf: ImageView
    private lateinit var slotAccessory: ImageView

    // 현재 선택된 아이템
    private var selectedItems: List<FoxShopItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fox_alchemy)

        FoxPrefs.loadAll(this)

        // -----------------------------------------
        // View 연결
        // -----------------------------------------

        alchemyPot =
            findViewById(R.id.alchemy_pot)

        btnBack =
            findViewById(R.id.btnBack)

        materialPanel =
            findViewById(R.id.materialPanel)

        alchemyCloud =
            findViewById(R.id.alchemyCloud)

        imgResultFox =
            findViewById(R.id.imgResultFox)

        slotGlasses =
            findViewById(R.id.slotGlasses)

        slotHat =
            findViewById(R.id.slotHat)

        slotScarf =
            findViewById(R.id.slotScarf)

        slotAccessory =
            findViewById(R.id.slotAccessory)

        // -----------------------------------------
        // 초기 상태
        // -----------------------------------------

        materialPanel.visibility = View.GONE
        alchemyCloud.visibility = View.GONE
        imgResultFox.visibility = View.GONE

        selectedItems = emptyList()

        clearSlots()

        // -----------------------------------------
        // 뒤로가기
        // -----------------------------------------

        btnBack.setOnClickListener {

            AlchemySelectionManager.clear(this)

            selectedItems = emptyList()

            finish()
        }

        // -----------------------------------------
        // 연금술 솥 클릭
        // -----------------------------------------

        alchemyPot.setOnClickListener {

            showMaterialPanel()

            /*
             * 이미 BottomSheet가 열려 있다면
             * 중복으로 띄우지 않는다.
             */
            val existingSheet =
                supportFragmentManager.findFragmentByTag(
                    "AlchemyInventory"
                )

            if (existingSheet != null) {
                return@setOnClickListener
            }

            AlchemyInventoryBottomSheet(

                // ---------------------------------
                // 아이템 선택 변경
                // ---------------------------------

                onSelectionChanged = { items ->

                    selectedItems = items

                    // 슬롯 갱신
                    updateSlots(items)

                    // 여우 미리보기 갱신
                    updatePreviewFox()
                },

                // ---------------------------------
                // 조합 버튼
                // ---------------------------------

                onCombine = { items ->

                    /*
                     * 여기서는 바로 저장하지 않는다.
                     *
                     * 먼저 이름을 입력받는다.
                     */
                    showFoxNameDialog(items)
                },

                // ---------------------------------
                // BottomSheet 닫힘
                // ---------------------------------

                onSheetClosed = {

                    /*
                     * 이름 입력 후 조합이 성공하면
                     * BottomSheet가 닫히더라도
                     * 연금술 화면 자체는 유지한다.
                     */
                    hideMaterialPanel()
                }

            ).show(
                supportFragmentManager,
                "AlchemyInventory"
            )
        }

        // -----------------------------------------
        // 솥 애니메이션
        // -----------------------------------------

        startPotAnimation()

        setupPotTouchEffect()
    }

    // =========================================================
    // 여우 ID
    // =========================================================

    private fun getNextFoxId(): Int {

        return (
                AllFoxMap.allFoxMap.keys.maxOrNull() ?: 0
                ) + 1
    }

    // =========================================================
    // 여우 완성
    // =========================================================

    private fun completeFox(
        name: String,
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

        // -----------------------------------------
        // 1. ID 생성
        // -----------------------------------------

        val foxId =
            getNextFoxId()

        // -----------------------------------------
        // 2. 아이템으로 CompleteFox 생성
        // -----------------------------------------

        val fox =
            AlchemyRecipeManager.createFox(
                id = foxId,
                name = name,
                items = items
            )

        // -----------------------------------------
        // 3. 아이템이 포함된 실제 Bitmap 생성
        // -----------------------------------------

        val bitmap =
            FoxBitmapRenderer.createPreview(
                context = this,
                fox = fox
            )

        // -----------------------------------------
        // 4. PNG 저장
        // -----------------------------------------

        val previewPath =
            FoxBitmapRenderer.savePreview(
                context = this,
                foxId = foxId,
                bitmap = bitmap
            )

        // -----------------------------------------
        // 5. previewPath 포함
        // -----------------------------------------

        val savedFox =
            fox.copy(
                previewPath = previewPath
            )

        // -----------------------------------------
        // 6. 메모리에 저장
        // -----------------------------------------

        AllFoxMap.allFoxMap[foxId] =
            savedFox

        // -----------------------------------------
        // 7. 영구 저장
        // -----------------------------------------

        FoxPrefs.save(
            context = this,
            fox = savedFox
        )

        // -----------------------------------------
        // 8. 아이템 차감
        //
        // 중요:
        // BottomSheet에서 이미 commit했다면
        // 여기서 다시 commit하면 안 된다.
        // 따라서 commit은 여기서 딱 한 번만 한다.
        // -----------------------------------------

        AlchemySelectionManager.commit(this)

        // -----------------------------------------
        // 9. 선택 상태 초기화
        // -----------------------------------------

        selectedItems = emptyList()

        clearSlots()

        // -----------------------------------------
        // 10. 화면에 새로 만들어진 여우 표시
        // -----------------------------------------

        FoxBitmapRenderer.displayPreview(imgResultFox, bitmap, savedFox)

        imgResultFox.visibility =
            View.VISIBLE

        imgResultFox.alpha = 1f
        imgResultFox.scaleX = 1f
        imgResultFox.scaleY = 1f
        imgResultFox.translationY = 0f

        // -----------------------------------------
        // 11. 완료 메시지
        // -----------------------------------------

        Toast.makeText(
            this,
            "\"$name\" 여우가 완성되었습니다!",
            Toast.LENGTH_SHORT
        ).show()

        /*
         * 여기서 finish() 하지 않는다.
         *
         * 사용자가 말한
         * "여우를 만들고 나서 뒤로가기 하기 싫어"
         * 를 해결하는 부분.
         */
    }

    // =========================================================
    // 여우 이름 입력
    // =========================================================

    private fun showFoxNameDialog(
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

        val editText =
            EditText(this).apply {

                hint = "여우 이름을 입력하세요"

                setSingleLine(true)

                setPadding(
                    48,
                    0,
                    48,
                    0
                )
            }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("새로운 여우")
                .setMessage(
                    "여우의 이름을 지어주세요."
                )
                .setView(editText)
                .setNegativeButton(
                    "취소",
                    null
                )
                .setPositiveButton(
                    "완성",
                    null
                )
                .create()

        /*
         * 기본 setPositiveButton을 사용하면
         * 이름이 비어 있어도 Dialog가 닫혀버린다.
         *
         * 따라서 직접 클릭 이벤트를 제어한다.
         */
        dialog.setOnShowListener {

            val positiveButton =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            positiveButton.setOnClickListener {

                val name =
                    editText.text
                        .toString()
                        .trim()

                if (name.isEmpty()) {

                    editText.error =
                        "여우 이름을 입력해주세요."

                    return@setOnClickListener
                }

                // -----------------------------
                // 여우 완성
                // -----------------------------

                completeFox(
                    name = name,
                    items = items
                )

                // -----------------------------
                // 이름 입력창 닫기
                // -----------------------------

                dialog.dismiss()

                /*
                 * BottomSheet는 이미 조합 과정에서
                 * 닫혔거나 dismiss될 수 있으므로
                 * 여기서는 Activity를 종료하지 않는다.
                 */
            }
        }

        dialog.show()
    }

    // =========================================================
    // 솥 둥둥 애니메이션
    // =========================================================

    private fun startPotAnimation() {

        val animator =
            ObjectAnimator.ofFloat(
                alchemyPot,
                "translationY",
                -20f,
                20f
            )

        animator.duration =
            1200L

        animator.repeatCount =
            ValueAnimator.INFINITE

        animator.repeatMode =
            ValueAnimator.REVERSE

        animator.interpolator =
            AccelerateDecelerateInterpolator()

        animator.start()
    }

    // =========================================================
    // 솥 터치 효과
    // =========================================================

    private fun setupPotTouchEffect() {

        alchemyPot.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    alchemyPot.setBackgroundResource(
                        R.drawable.alchemy_pot_glowing
                    )
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    alchemyPot.setBackgroundResource(
                        R.drawable.alchemy_pot
                    )
                }
            }

            false
        }
    }

    // =========================================================
    // 상단 재료 패널 표시
    // =========================================================

    private fun showMaterialPanel() {

        if (
            materialPanel.visibility ==
            View.VISIBLE
        ) {
            return
        }

        /*
         * 새 조합을 시작할 때만 초기화한다.
         */
        selectedItems = emptyList()

        clearSlots()

        updatePreviewFox()

        // -----------------------------------------
        // 여우
        // -----------------------------------------

        imgResultFox.visibility =
            View.VISIBLE

        imgResultFox.alpha =
            0f

        imgResultFox.scaleX =
            0.7f

        imgResultFox.scaleY =
            0.7f

        imgResultFox.translationY =
            30f

        // -----------------------------------------
        // 패널
        // -----------------------------------------

        materialPanel.visibility =
            View.VISIBLE

        alchemyCloud.visibility =
            View.VISIBLE

        materialPanel.alpha =
            0f

        materialPanel.translationY =
            -120f

        alchemyCloud.alpha =
            0f

        alchemyCloud.translationY =
            -80f

        // -----------------------------------------
        // 패널 애니메이션
        // -----------------------------------------

        materialPanel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        // -----------------------------------------
        // 구름 애니메이션
        // -----------------------------------------

        alchemyCloud.animate()
            .alpha(0.75f)
            .translationY(0f)
            .setDuration(300)
            .start()

        // -----------------------------------------
        // 여우 애니메이션
        // -----------------------------------------

        imgResultFox.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(80)
            .start()
    }

    // =========================================================
    // 현재 선택 아이템으로 여우 미리보기
    // =========================================================

    private fun updatePreviewFox() {

        var glasses: Int? =
            null

        var hat: Int? =
            null

        var scarf: Int? =
            null

        var accessory: Int? =
            null

        // -----------------------------------------
        // 선택된 아이템 분류
        // -----------------------------------------

        selectedItems.forEach { item ->

            when (item.itemCategory) {

                ItemCategory.GLASSES -> {
                    glasses = item.id
                }

                ItemCategory.HAT -> {
                    hat = item.id
                }

                ItemCategory.SCARF -> {
                    scarf = item.id
                }

                ItemCategory.ACCESSORY -> {
                    accessory = item.id
                }
            }
        }

        // -----------------------------------------
        // 미리보기용 여우
        // -----------------------------------------

        val previewFox =
            CompleteFox(
                id = -1,
                name = "Preview",

                /*
                 * createPreview()에서
                 * previewImage == 0이면
                 * 기본 여우 이미지를 사용하도록
                 * 만들어둔 상태여야 한다.
                 */
                previewImage = 0,

                previewPath = null,

                glasses = glasses,
                hat = hat,
                scarf = scarf,
                accessory = accessory
            )

        // -----------------------------------------
        // Bitmap 생성
        // -----------------------------------------

        val bitmap =
            FoxBitmapRenderer.createPreview(
                context = this,
                fox = previewFox
            )

        // -----------------------------------------
        // 화면 표시
        // -----------------------------------------

        FoxBitmapRenderer.displayPreview(imgResultFox, bitmap, previewFox)
    }

    // =========================================================
    // 슬롯 갱신
    // =========================================================

    private fun updateSlots(
        items: List<FoxShopItem>
    ) {

        clearSlots()

        items.forEach { item ->

            when (item.itemCategory) {

                ItemCategory.GLASSES -> {

                    slotGlasses.setBackgroundResource(
                        R.drawable.bg_alchemy_inventory_item
                    )
                    slotGlasses.setImageResource(
                        item.imageRes
                    )
                }

                ItemCategory.HAT -> {

                    slotHat.setBackgroundResource(
                        R.drawable.bg_alchemy_inventory_item
                    )
                    slotHat.setImageResource(
                        item.imageRes
                    )
                }

                ItemCategory.SCARF -> {

                    slotScarf.setBackgroundResource(
                        R.drawable.bg_alchemy_inventory_item
                    )
                    slotScarf.setImageResource(
                        item.imageRes
                    )
                }

                ItemCategory.ACCESSORY -> {

                    slotAccessory.setBackgroundResource(
                        R.drawable.bg_alchemy_inventory_item
                    )
                    slotAccessory.setImageResource(
                        item.imageRes
                    )
                }
            }
        }
    }

    // =========================================================
    // 슬롯 초기화
    // =========================================================

    private fun clearSlots() {

        slotGlasses.setBackgroundResource(R.drawable.bg_alchemy_slot_empty)
        slotHat.setBackgroundResource(R.drawable.bg_alchemy_slot_empty)
        slotScarf.setBackgroundResource(R.drawable.bg_alchemy_slot_empty)
        slotAccessory.setBackgroundResource(R.drawable.bg_alchemy_slot_empty)

        slotGlasses.setImageResource(
            R.drawable.ic_add_white_24
        )

        slotHat.setImageResource(
            R.drawable.ic_add_white_24
        )

        slotScarf.setImageResource(
            R.drawable.ic_add_white_24
        )

        slotAccessory.setImageResource(
            R.drawable.ic_add_white_24
        )
    }

    // =========================================================
    // 상단 패널 숨기기
    // =========================================================

    private fun hideMaterialPanel() {

        /*
         * 이미 닫혀 있다면 아무것도 하지 않는다.
         */
        if (
            materialPanel.visibility ==
            View.GONE
        ) {
            return
        }

        materialPanel.animate()
            .alpha(0f)
            .translationY(-120f)
            .setDuration(180)
            .withEndAction {

                materialPanel.visibility =
                    View.GONE

                materialPanel.alpha =
                    1f

                materialPanel.translationY =
                    0f
            }
            .start()

        imgResultFox.animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .translationY(30f)
            .setDuration(60)
            .withEndAction {

                imgResultFox.visibility =
                    View.GONE
            }
            .start()

        alchemyCloud.animate()
            .alpha(0f)
            .translationY(-80f)
            .setDuration(180)
            .withEndAction {

                alchemyCloud.visibility =
                    View.GONE

                alchemyCloud.alpha =
                    1f

                alchemyCloud.translationY =
                    0f

                clearSlots()
            }
            .start()
    }
}
