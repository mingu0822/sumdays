package com.example.sumdays.alchemy

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

    private val onSelectionChanged: (List<FoxShopItem>) -> Unit,

    private val onCombine: (List<FoxShopItem>) -> Unit,

    private val onSheetClosed: () -> Unit

) : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlchemyItemAdapter

    private var currentCategory = ItemCategory.GLASSES

    private var combined = false

    override fun onStart() {
        super.onStart()

        dialog?.window?.setDimAmount(0f)

        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val behavior = BottomSheetBehavior.from(bottomSheet)

        val screenHeight = resources.displayMetrics.heightPixels

        bottomSheet.layoutParams.height =
            (screenHeight * 0.45f).toInt()

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        recyclerView = view.findViewById(R.id.rvItems)

        recyclerView.layoutManager =
            GridLayoutManager(requireContext(), 4)

        adapter = AlchemyItemAdapter(

            onItemClick = { item ->

                val selectedItems =
                    AlchemySelectionManager.toggle(
                        requireContext(),
                        item
                    )

                // 현재 카테고리 다시 불러오기
                loadCategory(currentCategory)

                // 위 슬롯 갱신
                onSelectionChanged(selectedItems)
            }

        )

        recyclerView.adapter = adapter

        loadCategory(ItemCategory.GLASSES)

        view.findViewById<View>(R.id.btnGlasses)
            .setOnClickListener {

                loadCategory(ItemCategory.GLASSES)
            }

        view.findViewById<View>(R.id.btnHat)
            .setOnClickListener {

                loadCategory(ItemCategory.HAT)
            }

        view.findViewById<View>(R.id.btnScarf)
            .setOnClickListener {

                loadCategory(ItemCategory.SCARF)
            }

        view.findViewById<View>(R.id.btnAccessory)
            .setOnClickListener {

                loadCategory(ItemCategory.ACCESSORY)
            }

        view.findViewById<Button>(R.id.btnCombine)
            .setOnClickListener {

                combined = true

                val items =
                    AlchemySelectionManager.getSelectedItems()

                AlchemySelectionManager.commit()

                onCombine(items)

                dismiss()
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!combined) {

            // 선택 취소 → 예약했던 아이템 복원
            AlchemySelectionManager.clear(requireContext())

            // 위 슬롯도 초기화
            onSelectionChanged(emptyList())
        }

        onSheetClosed()
    }

    private fun loadCategory(
        category: ItemCategory
    ) {

        currentCategory = category

        val ownedItems =
            AllItemMap.allItemMap.values
                .filter {

                    // 선택중인 아이템은 count가 0이어도 보여준다.
                    it.itemCategory == category
                }
                .onEach {

                    it.count =
                        ItemPrefs.getCount(
                            requireContext(),
                            it.id
                        )
                }

        adapter.submitList(ownedItems)
    }
}