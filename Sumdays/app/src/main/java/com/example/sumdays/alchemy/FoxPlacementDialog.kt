package com.example.sumdays.alchemy

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.example.sumdays.R
import com.example.sumdays.customize.FoxItemPlacement
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.ItemCategory
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlin.math.roundToInt

class FoxPlacementDialog : DialogFragment() {
    private var canvas: FoxPlacementView? = null
    private var accepted = false
    private var draft = arrayListOf<FoxItemPlacement>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_First)
        draft = (savedInstanceState ?: requireArguments()).getParcelableArrayList(PLACEMENTS) ?: arrayListOf()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.dialog_fox_placement, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val editor = view.findViewById<FoxPlacementView>(R.id.foxPlacementCanvas)
        canvas = editor
        val items = requireArguments().getIntArray(ITEM_IDS)?.toList().orEmpty().mapNotNull(AllItemMap.allItemMap::get)
        editor.setFox(AlchemyRecipeManager.createFox(-1, "Preview", items, draft))
        val faceName = items.find { it.itemCategory == ItemCategory.FOXFACE }?.name ?: "기본 표정"
        savedInstanceState?.getInt(SELECTED)?.let(editor::select)
        val layerGroup = view.findViewById<ChipGroup>(R.id.placementLayers)
        val scale = view.findViewById<SeekBar>(R.id.placementScale)
        val rotation = view.findViewById<SeekBar>(R.id.placementRotation)
        var shownOrder = emptyList<Int>()

        fun refresh() {
            draft = ArrayList(editor.placements)
            val selected = editor.selectedPlacement
            val order = editor.placements.map { it.itemId }.reversed()
            if (shownOrder != order) {
                shownOrder = order
                layerGroup.removeAllViews()
                order.forEach { itemId ->
                    layerGroup.addView(Chip(requireContext()).apply {
                        id = View.generateViewId()
                        tag = itemId
                        text = AllItemMap.allItemMap[itemId]?.name
                        isCheckable = true
                        setOnClickListener { editor.select(itemId) }
                    })
                }
            }
            for (index in 0 until layerGroup.childCount) {
                val chip = layerGroup.getChildAt(index) as Chip
                chip.isChecked = chip.tag == editor.selectedItemId
            }
            scale.isEnabled = selected != null
            rotation.isEnabled = selected != null
            scale.progress = ((selected?.scale ?: 1f) * 100).roundToInt() - 25
            rotation.progress = (selected?.rotation ?: 0f).roundToInt() + 180
            view.findViewById<TextView>(R.id.placementScaleLabel).text =
                if (selected == null) "아이템 선택" else "크기 ${(selected.scale * 100).roundToInt()}%"
            view.findViewById<TextView>(R.id.placementRotationLabel).text =
                if (selected == null) "회전" else "회전 ${selected.rotation.roundToInt()}°"
            val index = editor.placements.indexOfFirst { it.itemId == editor.selectedItemId }
            view.findViewById<View>(R.id.btnLayerBackward).isEnabled = index > 0
            view.findViewById<View>(R.id.btnLayerForward).isEnabled = index >= 0 && index < editor.placements.lastIndex
            view.findViewById<View>(R.id.btnResetPlacement).isEnabled = selected != null
            view.findViewById<View>(R.id.btnPlacementUndo).apply {
                isEnabled = editor.canUndo
                alpha = if (isEnabled) 1f else 0.35f
            }
            view.findViewById<View>(R.id.btnPlacementRedo).apply {
                isEnabled = editor.canRedo
                alpha = if (isEnabled) 1f else 0.35f
            }
        }
        editor.onChanged = ::refresh
        fun bindSlider(slider: SeekBar, update: (Int) -> Unit) {
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                private var tracking = false
                override fun onStartTrackingTouch(seekBar: SeekBar) { tracking = true; editor.beginEdit() }
                override fun onStopTrackingTouch(seekBar: SeekBar) { tracking = false; editor.endEdit() }
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    if (!tracking) editor.beginEdit()
                    update(progress)
                    if (!tracking) editor.endEdit()
                }
            })
        }
        bindSlider(scale) { editor.transformSelected(scale = (it + 25) / 100f) }
        bindSlider(rotation) { editor.transformSelected(rotation = (it - 180).toFloat()) }
        view.findViewById<View>(R.id.btnLayerBackward).setOnClickListener { editor.moveLayer(false) }
        view.findViewById<View>(R.id.btnLayerForward).setOnClickListener { editor.moveLayer(true) }
        view.findViewById<View>(R.id.btnResetPlacement).setOnClickListener { editor.resetSelected() }
        view.findViewById<View>(R.id.btnPlacementUndo).setOnClickListener { editor.undo() }
        view.findViewById<View>(R.id.btnPlacementRedo).setOnClickListener { editor.redo() }
        view.findViewById<View>(R.id.btnPlacementBack).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnPlacementDone).setOnClickListener {
            accepted = true
            dismiss()
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.setCanceledOnTouchOutside(false)
        view?.let(ViewCompat::requestApplyInsets)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(PLACEMENTS, draft)
        outState.putInt(SELECTED, canvas?.selectedItemId ?: -1)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (activity?.isChangingConfigurations == true || activity?.isFinishing == true) return
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
            putBoolean(ACCEPTED, accepted)
            putIntArray(ITEM_IDS, requireArguments().getIntArray(ITEM_IDS))
            putParcelableArrayList(PLACEMENTS, draft)
        })
    }

    override fun onDestroyView() {
        canvas?.onChanged = null
        canvas = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "FoxPlacement"
        const val RESULT = "fox_placement_result"
        const val PLACEMENTS = "placements"
        const val ITEM_IDS = "item_ids"
        const val ACCEPTED = "accepted"
        private const val SELECTED = "selected"

        fun newInstance(itemIds: List<Int>, placements: List<FoxItemPlacement>) = FoxPlacementDialog().apply {
            arguments = Bundle().apply {
                putIntArray(ITEM_IDS, itemIds.toIntArray())
                putParcelableArrayList(PLACEMENTS, ArrayList(placements))
            }
        }
    }
}
