package com.example.sumdays.customize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.example.sumdays.R
import com.example.sumdays.shop.AllItemMap

object FoxBitmapRenderer {

    /**
     * CompleteFox 하나를 미리보기 Bitmap으로 렌더링
     */
    fun createPreview(
        context: Context,
        fox: CompleteFox
    ): Bitmap {

        val body = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.dailyread_fox_face_level_3
        ).copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(body)

        // 레이어 순서
        drawItem(canvas, context, fox.scarf)
        drawItem(canvas, context, fox.glasses)
        drawItem(canvas, context, fox.hat)
        drawItem(canvas, context, fox.accessory)

        return body
    }

    /**
     * 아이템 하나 그리기
     */
    private fun drawItem(
        canvas: Canvas,
        context: Context,
        itemId: Int?
    ) {

        if (itemId == null) return

        val item = AllItemMap.allItemMap[itemId] ?: return

        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            item.imageRes
        )

        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            null
        )
    }
}