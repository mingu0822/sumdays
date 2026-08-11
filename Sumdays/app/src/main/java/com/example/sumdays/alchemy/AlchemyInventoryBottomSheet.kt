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

    private var combined = false

    override fun onStart() {
        super.onStart()

        dialog?.window?.setDimAmount(0f)

        val bottomSheet =
            dialog?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return

        val behavior =
            BottomSheetBehavior.from(bottomSheet)

        val screenHeight =
            resources.displayMetrics.heightPixels

        bottomSheet.layoutParams.height =
            (screenHeight * 0.45f).toInt()

        behavior.state =
            BottomSheetBehavior.STATE_EXPANDED

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

        recyclerView =
            view.findViewById(R.id.rvItems)

        recyclerView.layoutManager =
            GridLayoutManager(
                requireContext(),
                4
            )

        adapter = AlchemyItemAdapter(

            onItemClick = { item ->

                val selectedItems =
                    AlchemySelectionManager.toggle(
                        requireContext(),
                        item
                    )

                // 현재 카테고리 갱신
                loadCategory(currentCategory)

                // 상단 슬롯 + 여우 미리보기 갱신
                onSelectionChanged(
                    selectedItems
                )
            }
        )

        recyclerView.adapter = adapter

        loadCategory(
            ItemCategory.GLASSES
        )

        view.findViewById<View>(
            R.id.btnGlasses
        ).setOnClickListener {

            loadCategory(
                ItemCategory.GLASSES
            )
        }

        view.findViewById<View>(
            R.id.btnHat
        ).setOnClickListener {

            loadCategory(
                ItemCategory.HAT
            )
        }

        view.findViewById<View>(
            R.id.btnScarf
        ).setOnClickListener {

            loadCategory(
                ItemCategory.SCARF
            )
        }

        view.findViewById<View>(
            R.id.btnAccessory
        ).setOnClickListener {

            loadCategory(
                ItemCategory.ACCESSORY
            )
        }

        view.findViewById<Button>(R.id.btnCombine)
            .setOnClickListener {

                val items =
                    AlchemySelectionManager.getSelectedItems()

                if (items.isEmpty()) {
                    return@setOnClickListener
                }

                combined = true

                onCombine(items)

                dismiss()
            }
    }

    override fun onDismiss(
        dialog: DialogInterface
    ) {

        super.onDismiss(dialog)

        if (!combined) {

            // 선택 상태만 초기화
            // 실제 재고는 애초에 차감하지 않았음
            AlchemySelectionManager.clear(
                requireContext()
            )

            onSelectionChanged(
                emptyList()
            )
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

                    it.itemCategory == category
                }
                .onEach {

                    it.count =
                        ItemPrefs.getCount(
                            requireContext(),
                            it.id
                        )
                }

        adapter.submitList(
            ownedItems
        )
    }
}