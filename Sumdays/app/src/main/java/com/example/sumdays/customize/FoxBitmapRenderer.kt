import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.example.sumdays.R
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.shop.AllItemMap

object FoxBitmapRenderer {

    fun createPreview(
        context: Context,
        fox: CompleteFox
    ): Bitmap {

        val body = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.dailyread_fox_face_level_3
        ).copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(body)

        drawItem(canvas, context, fox.scarf)
        drawItem(canvas, context, fox.glasses)
        drawItem(canvas, context, fox.hat)
        drawItem(canvas, context, fox.accessory)

        return body
    }

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