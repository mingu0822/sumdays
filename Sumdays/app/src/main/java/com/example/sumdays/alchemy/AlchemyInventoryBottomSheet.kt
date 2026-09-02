package com.example.sumdays.alchemy

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.ItemPrefs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AlchemyInventoryBottomSheet(

    private val onSelectionChanged:
        (List<FoxShopItem>) -> Unit,

    private val onCombine:
        (List<FoxShopItem>) -> Unit,

    private val onSheetClosed:
        () -> Unit

) : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlchemyItemAdapter

    private var currentCategory =
        ItemCategory.GLASSES

    /**
     * 조합이 완료되었는지 여부
     *
     * true:
     * 실제 여우 생성까지 완료되었으므로
     * onDismiss에서 선택 상태를 다시 clear하지 않음
     *
     * false:
     * 사용자가 그냥 닫은 것이므로
     * 선택 상태만 초기화
     */
    private var combined = false


    // --------------------------------------------------
    // BottomSheet 크기 설정
    // --------------------------------------------------

    override fun onStart() {

        super.onStart()

        dialog?.window?.setDimAmount(0f)

        val bottomSheet =
            dialog?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return

        val behavior =
            BottomSheetBehavior.from(bottomSheet)

        bottomSheet.setBackgroundColor(
            android.graphics.Color.TRANSPARENT
        )

        val screenHeight =
            resources.displayMetrics.heightPixels

        bottomSheet.layoutParams.height =
            (screenHeight * 0.45f).toInt()

        behavior.state =
            BottomSheetBehavior.STATE_EXPANDED

        behavior.skipCollapsed = true
    }


    // --------------------------------------------------
    // View 생성
    // --------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.bottomsheet_alchemy_inventory,
            container,
            false
        )
    }


    // --------------------------------------------------
    // View 초기화
    // --------------------------------------------------

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        // --------------------------------------------------
        // RecyclerView
        // --------------------------------------------------

        recyclerView =
            view.findViewById(
                R.id.rvItems
            )

        recyclerView.layoutManager =
            GridLayoutManager(
                requireContext(),
                4
            )


        // --------------------------------------------------
        // Adapter
        // --------------------------------------------------

        adapter =
            AlchemyItemAdapter(

                onItemClick = { item ->

                    // 선택 / 선택 해제
                    val selectedItems =
                        AlchemySelectionManager.toggle(
                            requireContext(),
                            item
                        )


                    // 현재 카테고리 갱신
                    //
                    // 선택 여부와 재고 표시를
                    // RecyclerView에 반영
                    loadCategory(
                        currentCategory
                    )


                    // 선택된 전체 아이템을
                    // FoxAlchemyActivity로 전달
                    //
                    // FoxAlchemyActivity에서는
                    //
                    // selectedItems = items
                    // updateSlots(items)
                    //
                    // 로 처리
                    onSelectionChanged(
                        selectedItems
                    )
                }
            )


        recyclerView.adapter =
            adapter


        // --------------------------------------------------
        // 기본 카테고리
        // --------------------------------------------------

        loadCategory(
            ItemCategory.GLASSES
        )


        // --------------------------------------------------
        // 안경
        // --------------------------------------------------

        view.findViewById<View>(
            R.id.btnGlasses
        ).setOnClickListener {

            loadCategory(
                ItemCategory.GLASSES
            )
        }


        // --------------------------------------------------
        // 모자
        // --------------------------------------------------

        view.findViewById<View>(
            R.id.btnHat
        ).setOnClickListener {

            loadCategory(
                ItemCategory.HAT
            )
        }


        // --------------------------------------------------
        // 목도리
        // --------------------------------------------------

        view.findViewById<View>(
            R.id.btnScarf
        ).setOnClickListener {

            loadCategory(
                ItemCategory.SCARF
            )
        }


        // --------------------------------------------------
        // 액세서리
        // --------------------------------------------------

        view.findViewById<View>(
            R.id.btnAccessory
        ).setOnClickListener {

            loadCategory(
                ItemCategory.ACCESSORY
            )
        }


        // --------------------------------------------------
        // 조합 버튼
        // --------------------------------------------------

        view.findViewById<Button>(
            R.id.btnCombine
        ).setOnClickListener {

            val items =
                AlchemySelectionManager
                    .getSelectedItems()


            // 아무것도 선택하지 않은 경우
            if (items.isEmpty()) {

                return@setOnClickListener
            }


            // --------------------------------------------------
            // 조합 시작
            // --------------------------------------------------

            combined = true


            // --------------------------------------------------
            // 실제 여우 생성은
            // FoxAlchemyActivity에서 처리
            //
            // 이름 입력
            // CompleteFox 생성
            // 미리보기 생성
            // 저장
            // 아이템 commit
            // --------------------------------------------------

            onCombine(
                items
            )


            // 중요
            //
            // 여기서 commit() 하지 않는다.
            //
            // 실제 아이템 차감은
            // FoxAlchemyActivity.completeFox()
            // 에서 한다.
        }
    }


    // --------------------------------------------------
    // BottomSheet 닫힘
    // --------------------------------------------------

    override fun onDismiss(
        dialog: DialogInterface
    ) {

        super.onDismiss(
            dialog
        )


        if (!combined) {

            // --------------------------------------------------
            // 사용자가 조합하지 않고 닫음
            // --------------------------------------------------
            //
            // 선택만 초기화
            // 실제 재고는 차감하지 않음
            // --------------------------------------------------

            AlchemySelectionManager.clear(
                requireContext()
            )


            // 상단 슬롯 초기화
            onSelectionChanged(
                emptyList()
            )
        }


        // Activity에 BottomSheet가 닫혔다고 알림
        onSheetClosed()
    }


    // --------------------------------------------------
    // 카테고리 로드
    // --------------------------------------------------

    private fun loadCategory(
        category: ItemCategory
    ) {

        currentCategory =
            category

        updateCategoryTabs(category)


        // 현재 카테고리의 아이템만 가져오기
        val ownedItems =
            AllItemMap.allItemMap.values
                .filter {

                    it.itemCategory ==
                            category
                }
                .onEach {

                    // 실제 보유 수량 갱신
                    it.count =
                        ItemPrefs.getCount(
                            requireContext(),
                            it.id
                        )
                }


        // RecyclerView 갱신
        adapter.submitList(
            ownedItems
        )
    }

    private fun updateCategoryTabs(category: ItemCategory) {

        val tabs = listOf(
            Triple(R.id.btnGlasses, R.id.indicatorGlasses, ItemCategory.GLASSES),
            Triple(R.id.btnHat, R.id.indicatorHat, ItemCategory.HAT),
            Triple(R.id.btnScarf, R.id.indicatorScarf, ItemCategory.SCARF),
            Triple(R.id.btnAccessory, R.id.indicatorAccessory, ItemCategory.ACCESSORY)
        )

        tabs.forEach { (textId, indicatorId, tabCategory) ->
            val selected = tabCategory == category

            view?.findViewById<TextView>(textId)?.setTextColor(
                requireContext().getColor(
                    if (selected) R.color.foxrange else android.R.color.black
                )
            )

            view?.findViewById<View>(indicatorId)?.visibility =
                if (selected) View.VISIBLE else View.INVISIBLE
        }
    }
}
