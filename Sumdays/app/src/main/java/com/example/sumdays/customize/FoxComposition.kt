package com.example.sumdays.customize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.example.sumdays.shop.AllItemMap
import kotlin.math.ceil
import kotlin.math.floor

/** Shared geometry for the editing canvas, saved PNG and every preview. */
class FoxComposition(context: Context, fox: CompleteFox) {
    val base: Bitmap = requireNotNull(BitmapFactory.decodeResource(
        context.resources,
        FoxBaseImage.resource(fox)
    ))
    private val items = listOfNotNull(fox.glasses, fox.hat, fox.scarf, fox.accessory)
        .mapNotNull(AllItemMap.allItemMap::get)
    val bitmaps: Map<Int, Bitmap> = items.mapNotNull { item ->
        BitmapFactory.decodeResource(context.resources, item.imageRes)?.let { item.id to it }
    }.toMap()
    val defaults: List<FoxItemPlacement> = items.mapNotNull { item ->
        bitmaps[item.id]?.let { bitmap ->
            FoxItemPlacement(
                item.id,
                (item.offsetX + bitmap.width / 2f) / base.width,
                (item.offsetY + bitmap.height / 2f) / base.height
            )
        }
    }
    val placements: List<FoxItemPlacement> =
        fox.placements.filter { it.itemId in bitmaps }.distinctBy { it.itemId }.let { saved ->
            saved + defaults.filter { default -> saved.none { it.itemId == default.itemId } }
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun matrix(placement: FoxItemPlacement): Matrix {
        val bitmap = bitmaps.getValue(placement.itemId)
        return Matrix().apply {
            setTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            postScale(placement.scale, placement.scale)
            postRotate(placement.rotation)
            postTranslate(placement.centerX * base.width, placement.centerY * base.height)
        }
    }

    fun itemBounds(placement: FoxItemPlacement): RectF {
        val bitmap = bitmaps.getValue(placement.itemId)
        return RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()).apply {
            matrix(placement).mapRect(this)
        }
    }

    fun bounds(placements: List<FoxItemPlacement>): RectF =
        RectF(0f, 0f, base.width.toFloat(), base.height.toFloat()).apply {
            placements.forEach { union(itemBounds(it)) }
            set(floor(left), floor(top), ceil(right), ceil(bottom))
        }

    fun draw(canvas: Canvas, placements: List<FoxItemPlacement>) {
        canvas.drawBitmap(base, 0f, 0f, paint)
        placements.forEach { placement ->
            canvas.drawBitmap(bitmaps.getValue(placement.itemId), matrix(placement), paint)
        }
    }
}
