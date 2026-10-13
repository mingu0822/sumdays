package com.example.sumdays.stickers

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StickerCanvasTest {
    private val sticker = DiarySticker("example", 101, 101, "나", "2026-10-11", 1001, .5f, .5f, .18f, 0f)

    @Test fun `rotated stickers at every edge stay inside the diary at different screen sizes`() {
        for (width in listOf(240, 400, 800)) for (rotation in listOf(-180f, -45f, 0f, 45f, 90f, 180f)) {
            for (x in listOf(0f, .5f, 1f)) for (y in listOf(0f, .5f, 1f)) {
                val bounds = StickerBounds.calculate(width, width * 2, StickerGeometry(x, y, .35f, rotation))
                assertTrue(bounds.centerX - bounds.extent / 2 >= -.001f)
                assertTrue(bounds.centerX + bounds.extent / 2 <= width + .001f)
                assertTrue(bounds.centerY - bounds.extent / 2 >= -.001f)
                assertTrue(bounds.centerY + bounds.extent / 2 <= width * 2 + .001f)
            }
        }
    }

    @Test fun `drag clamps at page edge and blank space leaves scrolling available`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = FrameLayout(context)
        val canvas = StickerCanvasView(context)
        parent.addView(canvas)
        canvas.layout(0, 0, 400, 600)
        canvas.stickers = listOf(sticker)
        canvas.selectedId = sticker.id
        assertFalse(touch(canvas, MotionEvent.ACTION_DOWN, 0f, 0f))
        assertTrue(touch(canvas, MotionEvent.ACTION_DOWN, 200f, 300f))
        touch(canvas, MotionEvent.ACTION_MOVE, 1000f, -100f)
        touch(canvas, MotionEvent.ACTION_UP, 1000f, -100f)
        assertEquals(1f, canvas.stickers.single().x)
        assertEquals(0f, canvas.stickers.single().y)
    }

    @Test fun `reader tapping a sticker selects it and disabled editor cannot move it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val canvas = StickerCanvasView(context)
        FrameLayout(context).addView(canvas)
        canvas.layout(0, 0, 400, 600)
        canvas.stickers = listOf(sticker)
        var clicked: DiarySticker? = null
        canvas.onStickerClick = { clicked = it }
        touch(canvas, MotionEvent.ACTION_DOWN, 200f, 300f)
        touch(canvas, MotionEvent.ACTION_UP, 200f, 300f)
        assertEquals(sticker, clicked)
        canvas.selectedId = sticker.id
        canvas.isEnabled = false
        assertFalse(touch(canvas, MotionEvent.ACTION_DOWN, 200f, 300f))
        assertEquals(sticker, canvas.stickers.single())
    }

    @Test fun `short diary page proportions are independent of available scroll viewport height`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val page = StickerPageLayout(context)
        page.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertEquals(540, page.measuredHeight)
        page.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertEquals(1080, page.measuredHeight)
    }

    private fun touch(view: View, action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(0, 1, action, x, y, 0)
        return view.onTouchEvent(event).also { event.recycle() }
    }
}
