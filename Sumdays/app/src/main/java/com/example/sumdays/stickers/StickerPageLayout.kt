package com.example.sumdays.stickers

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** The same page proportions in the reader and editor, independent of toolbar/viewport height. */
class StickerPageLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val pageHeight = (measuredWidth * 1.35f).toInt()
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY && measuredHeight < pageHeight) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY))
        }
    }
}
