package com.example.sumdays

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.example.sumdays.alchemy.AlchemyInventoryBottomSheet
import com.example.sumdays.alchemy.AlchemyRecipeManager
import com.example.sumdays.alchemy.AlchemySelectionManager
import com.example.sumdays.alchemy.FoxPlacementDialog
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.customize.FoxBitmapRenderer
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.customize.FoxItemPlacement
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.google.android.material.button.MaterialButton

class FoxAlchemyActivity : AppCompatActivity() {

    private lateinit var alchemyPot: ImageButton
    private lateinit var btnBack: ImageButton

    // 상단 재료 패널
    private lateinit var materialPanel: View

    // 구름 / 결과 여우
    private lateinit var alchemyCloud: ImageView
    private lateinit var imgResultFox: ImageView

    // 슬롯
    private lateinit var slotFoxFace: ImageView
    private lateinit var slotGlasses: ImageView
    private lateinit var slotHat: ImageView
    private lateinit var slotScarf: ImageView
    private lateinit var slotAccessory: ImageView

    // 현재 선택된 아이템
    private var selectedItems: List<FoxShopItem> = emptyList()
    private var selectedPlacements: List<FoxItemPlacement> = emptyList()
    private var isCombining = false
    private var potAnimator: ObjectAnimator? = null
    private var nameDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fox_alchemy)

        FoxPrefs.loadAll(this)

        if (savedInstanceState != null) {
            AlchemySelectionManager.resumePendingSelection(this)
            selectedPlacements = savedInstanceState.getParcelableArrayList<FoxItemPlacement>("draft_placements").orEmpty()
        } else {
            AlchemySelectionManager.restorePendingSelection(this)
        }

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

        slotFoxFace = findViewById(R.id.slotFoxFace)

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

        selectedItems = AlchemySelectionManager.getSelectedItems()

        clearSlots()

        supportFragmentManager.setFragmentResultListener(AlchemyInventoryBottomSheet.SELECTION_CHANGED, this) { _, _ ->
            selectedItems = AlchemySelectionManager.getSelectedItems()
            selectedPlacements = selectedPlacements.filter { placement -> selectedItems.any { it.id == placement.itemId } }
            updateSlots(selectedItems)
            updatePreviewFox()
        }
        supportFragmentManager.setFragmentResultListener(AlchemyInventoryBottomSheet.CLOSED, this) { _, result ->
            hideMaterialPanel()
            if (result.getBoolean(AlchemyInventoryBottomSheet.EDIT)) {
                selectedItems = AlchemySelectionManager.getSelectedItems()
                showPlacementEditor()
            } else {
                selectedItems = emptyList()
                selectedPlacements = emptyList()
            }
        }
        supportFragmentManager.setFragmentResultListener(FoxPlacementDialog.RESULT, this) { _, result ->
            selectedItems = AlchemySelectionManager.getSelectedItems()
            selectedPlacements = result.getParcelableArrayList<FoxItemPlacement>(FoxPlacementDialog.PLACEMENTS).orEmpty()
            if (result.getBoolean(FoxPlacementDialog.ACCEPTED) && selectedItems.isNotEmpty()) {
                isCombining = true
                alchemyPot.isEnabled = false
                playIngredientDropAnimation(selectedItems) {
                    if (!isFinishing && !isDestroyed) showFoxNameDialog(selectedItems)
                }
            } else {
                openInventory()
            }
        }

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
            openInventory()
        }

        // -----------------------------------------
        // 솥 애니메이션
        // -----------------------------------------

        startPotAnimation()

        setupPotTouchEffect()

        if (savedInstanceState != null) {
            if (supportFragmentManager.findFragmentByTag(AlchemyInventoryBottomSheet.TAG) != null) {
                showMaterialPanel()
            } else if (savedInstanceState.getBoolean("combining")) {
                // Resume interrupted animation/naming in the editor; never spend an item again.
                alchemyPot.post { showPlacementEditor() }
            }
        }
    }

    private fun openInventory() {
        if (isCombining || isFinishing || supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(AlchemyInventoryBottomSheet.TAG) != null) return
        showMaterialPanel()
        AlchemyInventoryBottomSheet().show(supportFragmentManager, AlchemyInventoryBottomSheet.TAG)
    }

    private fun showPlacementEditor() {
        if (selectedItems.isEmpty() || isFinishing || supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(FoxPlacementDialog.TAG) != null) return
        FoxPlacementDialog.newInstance(selectedItems.map { it.id }, selectedPlacements)
            .show(supportFragmentManager, FoxPlacementDialog.TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList("draft_placements", ArrayList(selectedPlacements))
        outState.putBoolean("combining", isCombining)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        potAnimator?.cancel()
        nameDialog?.setOnDismissListener(null)
        nameDialog?.dismiss()
        if (isFinishing) AlchemySelectionManager.clear(this)
        super.onDestroy()
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
                items = items,
                placements = selectedPlacements
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

        selectedPlacements = emptyList()

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

        val contentView = layoutInflater.inflate(
            R.layout.dialog_fox_name,
            null
        )
        val dialog = Dialog(this)
        nameDialog = dialog
        var foxSaved = false

        dialog.setContentView(contentView)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                dimAmount = 0.68f
            }
        }

        val editText = contentView.findViewById<EditText>(R.id.editFoxName)
        val preview = contentView.findViewById<ImageView>(R.id.imgFoxNamePreview)
        val closeButton = contentView.findViewById<ImageButton>(R.id.btnCloseFoxName)
        val closetButton = contentView.findViewById<MaterialButton>(R.id.btnGoCloset)
        val stayButton = contentView.findViewById<MaterialButton>(R.id.btnCloseAfterNaming)

        val previewFox = AlchemyRecipeManager.createFox(
            id = -1,
            name = "Preview",
            items = items,
            placements = selectedPlacements
        )
        val previewBitmap = FoxBitmapRenderer.createPreview(this, previewFox)

        fun saveFox(returnToCloset: Boolean) {
            val name = editText.text.toString().trim()

            if (name.isEmpty()) {
                editText.error = "여우 이름을 입력해주세요."
                editText.requestFocus()
                return
            }

            foxSaved = true
            completeFox(name = name, items = items)
            dialog.dismiss()

            if (returnToCloset) {
                finish()
            }
        }

        closetButton.setOnClickListener {
            saveFox(returnToCloset = true)
        }
        stayButton.setOnClickListener {
            saveFox(returnToCloset = false)
        }
        closeButton.setOnClickListener {
            dialog.cancel()
        }

        dialog.setOnDismissListener {
            nameDialog = null
            isCombining = false
            alchemyPot.isEnabled = true
            if (!foxSaved) showPlacementEditor()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        FoxBitmapRenderer.displayPreview(
            imageView = preview,
            bitmap = previewBitmap,
            fox = previewFox,
            baseScaleMultiplier = 0.56f
        )
    }

    // =========================================================
    // 재료가 솥으로 들어가는 연금술 애니메이션
    // =========================================================

    private fun playIngredientDropAnimation(
        items: List<FoxShopItem>,
        onComplete: () -> Unit
    ) {
        if (items.isEmpty()) {
            onComplete()
            return
        }

        playNextIngredient(
            items = items,
            index = 0,
            onComplete = onComplete
        )
    }

    private fun playNextIngredient(
        items: List<FoxShopItem>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (isFinishing || isDestroyed) return
        if (index >= items.size) {
            alchemyPot.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(120L)
                .withEndAction {
                    alchemyPot.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .withEndAction(onComplete)
                        .start()
                }
                .start()
            return
        }

        val root = findViewById<FrameLayout>(android.R.id.content)
        val item = items[index]
        val size = (180 * resources.displayMetrics.density).toInt()
        val fallingItem = ImageView(this).apply {
            setImageResource(item.imageRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 1f
            scaleY = 1f
            rotation = if (index % 2 == 0) -12f else 12f
        }

        root.addView(
            fallingItem,
            FrameLayout.LayoutParams(size, size)
        )

        val rootLocation = IntArray(2)
        val potLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        alchemyPot.getLocationOnScreen(potLocation)

        val targetX =
            potLocation[0] - rootLocation[0] + alchemyPot.width / 2f - size / 2f
        val targetY =
            potLocation[1] - rootLocation[1] + alchemyPot.height * 0.48f - size / 2f
        val startY =
            materialPanel.bottom.toFloat().coerceAtLeast(0f) +
                    index * 10f * resources.displayMetrics.density

        fallingItem.x = targetX
        fallingItem.y = startY

        // 크게 등장한 상태를 잠시 보여준 뒤 솥으로 떨어뜨린다.
        fallingItem.animate()
            .alpha(1f)
            .setDuration(220L)
            .withEndAction {
                fallingItem.postDelayed({
                    fallingItem.animate()
                        .y(targetY)
                        .rotationBy(if (index % 2 == 0) 24f else -24f)
                        .scaleX(0.18f)
                        .scaleY(0.18f)
                        .alpha(0.15f)
                        .setDuration(1_100L)
                        .setInterpolator(android.view.animation.AccelerateInterpolator())
                        .withEndAction {
                            root.removeView(fallingItem)

                            playNextIngredient(
                                items = items,
                                index = index + 1,
                                onComplete = onComplete
                            )
                        }
                        .start()
                }, 800L)
            }
            .start()
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

        potAnimator = animator
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

        // 배치 편집에서 돌아왔을 때도 선택한 재료를 유지한다.
        selectedItems = AlchemySelectionManager.getSelectedItems()

        updateSlots(selectedItems)

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
        val previewFox = AlchemyRecipeManager.createFox(
            id = -1,
            name = "Preview",
            items = selectedItems,
            placements = selectedPlacements
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

                ItemCategory.FOXFACE -> {
                    slotFoxFace.setBackgroundResource(R.drawable.bg_alchemy_inventory_item)
                    slotFoxFace.setImageResource(item.imageRes)
                    slotFoxFace.contentDescription = item.name
                }

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

        slotFoxFace.setBackgroundResource(R.drawable.bg_alchemy_slot_empty)
        slotFoxFace.setImageResource(R.drawable.dailyread_fox_face_level_3)
        slotFoxFace.contentDescription = "기본 표정"

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
