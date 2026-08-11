package com.example.sumdays.customize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.example.sumdays.R
import com.example.sumdays.shop.AllItemMap
import java.io.File

object FoxBitmapRenderer {

    /**
     * 여우 + 아이템 미리보기 Bitmap 생성
     *
     * offsetX / offsetY를 기준으로 모든 아이템이
     * 잘리지 않도록 최종 Bitmap 크기를 자동으로 확장한다.
     */
    fun createPreview(
        context: Context,
        fox: CompleteFox
    ): Bitmap {

        val baseResId = if (fox.previewImage != 0) {
            fox.previewImage
        } else {
            R.drawable.dailyread_fox_face_level_3
        }

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            baseResId
        ) ?: throw IllegalStateException(
            "여우 기본 이미지를 불러올 수 없습니다. resource=$baseResId"
        )

        // 이하 동일...

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
        )

        // --------------------------------------------------
        // 그릴 아이템 목록
        // --------------------------------------------------

        val itemIds = listOf(
            fox.glasses,
            fox.hat,
            fox.scarf,
            fox.accessory
        )

        val items = itemIds.mapNotNull { id ->
            if (id == null) {
                null
            } else {
                AllItemMap.allItemMap[id]
            }
        }

        // --------------------------------------------------
        // 전체 영역 계산
        // --------------------------------------------------

        var minX = 0f
        var minY = 0f

        var maxX = baseBitmap.width.toFloat()
        var maxY = baseBitmap.height.toFloat()

        items.forEach { item ->

            val itemBitmap = BitmapFactory.decodeResource(
                context.resources,
                item.imageRes
            ) ?: return@forEach

            val left = item.offsetX
            val top = item.offsetY

            val right =
                left + itemBitmap.width

            val bottom =
                top + itemBitmap.height

            minX = minOf(minX, left)
            minY = minOf(minY, top)

            maxX = maxOf(maxX, right)
            maxY = maxOf(maxY, bottom)
        }

        // --------------------------------------------------
        // 실제 Bitmap 크기
        // --------------------------------------------------

        val resultWidth =
            (maxX - minX).toInt().coerceAtLeast(1)

        val resultHeight =
            (maxY - minY).toInt().coerceAtLeast(1)

        val result = Bitmap.createBitmap(
            resultWidth,
            resultHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        // --------------------------------------------------
        // offset이 음수일 경우를 위한 보정
        // --------------------------------------------------

        val canvasOffsetX = -minX
        val canvasOffsetY = -minY

        // --------------------------------------------------
        // 기본 여우
        // --------------------------------------------------

        canvas.drawBitmap(
            baseBitmap,
            canvasOffsetX,
            canvasOffsetY,
            paint
        )

        // --------------------------------------------------
        // 아이템
        // --------------------------------------------------

        items.forEach { item ->

            val itemBitmap = BitmapFactory.decodeResource(
                context.resources,
                item.imageRes
            ) ?: return@forEach

            canvas.drawBitmap(
                itemBitmap,
                item.offsetX + canvasOffsetX,
                item.offsetY + canvasOffsetY,
                paint
            )
        }

        return result
    }


    /**
     * Bitmap을 파일로 저장
     */
    fun savePreview(
        context: Context,
        foxId: Int,
        bitmap: Bitmap
    ): String {

        val directory = context.getDir(
            "fox_previews",
            Context.MODE_PRIVATE
        )

        val file = File(
            directory,
            "fox_$foxId.png"
        )

        file.outputStream().use { outputStream ->

            bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                outputStream
            )
        }

        return file.absolutePath
    }


    /**
     * 저장된 미리보기 이미지 불러오기
     */
    fun loadPreview(
        previewPath: String?
    ): Bitmap? {

        if (previewPath.isNullOrEmpty())
            return null

        val file = File(previewPath)

        if (!file.exists())
            return null

        return BitmapFactory.decodeFile(
            file.absolutePath
        )
    }


    /**
     * 특정 여우의 미리보기 파일 삭제
     */
    fun deletePreview(
        previewPath: String?
    ) {

        if (previewPath.isNullOrEmpty())
            return

        val file = File(previewPath)

        if (file.exists()) {
            file.delete()
        }
    }
}