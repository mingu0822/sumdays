package com.example.sumdays.alchemy

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.FoxAlchemyActivity
import com.example.sumdays.CustomizeActivity
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.ShopAdapter
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.FoxBitmapRenderer
import com.example.sumdays.customize.FoxComposition
import com.example.sumdays.customize.FoxItemPlacement
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.customize.FoxAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.PointPrefs
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoxPlacementTest {
    private lateinit var context: Context
    private val glasses get() = AllItemMap.allItemMap.getValue(1)
    private val hat get() = AllItemMap.allItemMap.getValue(101)
    private val originalFoxes = AllFoxMap.allFoxMap.toMap()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AlchemySelectionManager.clear(context)
        ItemPrefs.clear(context)
        FoxPrefs.clear(context)
        AllFoxMap.allFoxMap.clear()
    }

    @After fun cleanup() {
        AlchemySelectionManager.clear(context)
        AllFoxMap.allFoxMap.clear()
        AllFoxMap.allFoxMap.putAll(originalFoxes)
    }

    @Test fun `layout round trip keeps order position scale and rotation`() {
        val placements = listOf(
            FoxItemPlacement(hat.id, -0.2f, 0.1f, 0.7f, -37f),
            FoxItemPlacement(glasses.id, 0.6f, 0.8f, 1.3f, 23f)
        )
        val fox = AlchemyRecipeManager.createFox(7, "자유 여우", listOf(glasses, hat), placements)
        FoxPrefs.save(context, fox)
        AllFoxMap.allFoxMap.clear()
        FoxPrefs.loadAll(context)
        assertEquals(placements, AllFoxMap.allFoxMap.getValue(7).placements)
        assertEquals(60f, glasses.offsetX)
        assertEquals(350f, glasses.offsetY)
    }

    @Test fun `old saved fox without placements retains its original offsets`() {
        val json = JSONObject().apply {
            put("id", 7); put("name", "기존 여우"); put("previewImage", 0)
            put("glasses", glasses.id)
        }
        context.getSharedPreferences("fox_prefs", Context.MODE_PRIVATE).edit()
            .putString("foxes", JSONArray().put(json).toString()).commit()
        FoxPrefs.loadAll(context)
        val fox = AllFoxMap.allFoxMap.getValue(7)
        assertTrue(fox.placements.isEmpty())
        val scene = FoxComposition(context, fox)
        val bounds = scene.itemBounds(scene.placements.single())
        assertEquals(glasses.offsetX, bounds.left, 0.01f)
        assertEquals(glasses.offsetY, bounds.top, 0.01f)
    }

    @Test fun `closet migrates resource IDs from an older APK and binds every saved fox`() {
        val legacyFoxes = JSONArray().apply {
            put(JSONObject().apply {
                put("id", 1); put("name", "angry"); put("previewImage", 2131230871)
            })
            put(JSONObject().apply {
                put("id", 2); put("name", "happy"); put("previewImage", 2131230875)
            })
            put(JSONObject().apply {
                put("id", 3); put("name", "meow"); put("previewImage", 2131230873)
                put("previewPath", "/missing/fox_3.png")
                put("hat", 102); put("scarf", 201); put("accessory", 301)
            })
            put(JSONObject().apply {
                put("id", 4); put("name", "magician"); put("previewImage", 2131230873)
                put("hat", 102)
            })
        }
        val prefs = context.getSharedPreferences("fox_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("foxes", legacyFoxes.toString()).commit()
        val controller = Robolectric.buildActivity(CustomizeActivity::class.java).setup().visible()
        val activity = controller.get()
        val list = activity.findViewById<RecyclerView>(R.id.rvFox)
        val adapter = list.adapter as FoxAdapter
        assertEquals(4, adapter.itemCount)
        for (index in 0 until adapter.itemCount) {
            val holder = adapter.onCreateViewHolder(list, 0)
            adapter.onBindViewHolder(holder, index)
            assertNotNull(holder.image.drawable)
            holder.itemView.performClick()
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(R.drawable.dailyread_fox_face_level_1, AllFoxMap.allFoxMap.getValue(1).previewImage)
        assertEquals(R.drawable.dailyread_fox_face_level_5, AllFoxMap.allFoxMap.getValue(2).previewImage)
        assertEquals(R.drawable.dailyread_fox_face_level_3, AllFoxMap.allFoxMap.getValue(3).previewImage)
        assertEquals("/missing/fox_3.png", AllFoxMap.allFoxMap.getValue(3).previewPath)
        assertEquals(201, AllFoxMap.allFoxMap.getValue(3).scarf)
        val migrated = JSONArray(prefs.getString("foxes", null))
        assertEquals("dailyread_fox_face_level_1", migrated.getJSONObject(0).getString("previewImageName"))
        assertFalse(migrated.getJSONObject(0).has("previewImage"))
        // A second load must preserve the complete migrated inventory.
        val firstLoad = AllFoxMap.allFoxMap.toMap()
        FoxPrefs.loadAll(activity)
        assertEquals(firstLoad, AllFoxMap.allFoxMap)
        activity.finish()
        controller.pause().stop().destroy()
    }

    @Test fun `stable image name wins over an obsolete numeric resource`() {
        val saved = JSONObject().apply {
            put("id", 7); put("name", "stable")
            put("previewImage", R.drawable.bg_alchemy_selected)
            put("previewImageName", "dailyread_fox_face_level_4")
        }
        context.getSharedPreferences("fox_prefs", Context.MODE_PRIVATE).edit()
            .putString("foxes", JSONArray().put(saved).toString()).commit()
        FoxPrefs.loadAll(context)
        val fox = AllFoxMap.allFoxMap.getValue(7)
        assertEquals(R.drawable.dailyread_fox_face_level_4, fox.previewImage)
        assertTrue(FoxBitmapRenderer.createPreview(context, fox).width > 0)
    }

    @Test fun `renderer safely replaces invalid base resource for legacy and edited foxes`() {
        val fox = AlchemyRecipeManager.createFox(7, "Preview", listOf(glasses))
        val invalid = fox.copy(previewImage = R.drawable.bg_alchemy_selected)
        assertTrue(FoxBitmapRenderer.createPreview(context, fox).sameAs(FoxBitmapRenderer.createPreview(context, invalid)))
        val placements = listOf(FoxItemPlacement(glasses.id, 0.5f, 0.5f, 0.7f, 30f))
        assertTrue(FoxBitmapRenderer.createPreview(context, fox.copy(placements = placements)).sameAs(
            FoxBitmapRenderer.createPreview(context, invalid.copy(placements = placements))))
    }

    @Test fun `drag uses canvas coordinates at different screen sizes and undo restores it`() {
        val fox = AlchemyRecipeManager.createFox(7, "Preview", listOf(glasses))
        val scene = FoxComposition(context, fox)
        val initial = scene.placements.single()
        for ((width, height) in listOf(360 to 420, 720 to 560)) {
            val view = FoxPlacementView(context)
            view.layout(0, 0, width, height)
            view.setFox(fox)
            val scale = centeredCanvasScale(scene, width, height)
            val x = width / 2f + (initial.centerX - 0.5f) * scene.base.width * scale
            val y = height / 2f + (initial.centerY - 0.5f) * scene.base.height * scale
            touch(view, MotionEvent.ACTION_DOWN, x, y)
            touch(view, MotionEvent.ACTION_MOVE, x + 30f * scale, y - 40f * scale)
            touch(view, MotionEvent.ACTION_UP, x + 30f * scale, y - 40f * scale)
            val moved = view.placements.single()
            assertEquals(initial.centerX + 30f / scene.base.width, moved.centerX, 0.001f)
            assertEquals(initial.centerY - 40f / scene.base.height, moved.centerY, 0.001f)
            assertTrue(view.canUndo)
            view.undo()
            assertEquals(initial, view.placements.single())
            view.redo()
            assertEquals(moved, view.placements.single())
        }
    }

    @Test fun `transform and layer edits are undoable without changing catalog items`() {
        val view = FoxPlacementView(context)
        view.layout(0, 0, 400, 500)
        view.setFox(AlchemyRecipeManager.createFox(7, "Preview", listOf(glasses, hat)))
        view.select(glasses.id)
        view.beginEdit()
        view.transformSelected(scale = 0.6f, rotation = 42f)
        view.endEdit()
        val edited = view.selectedPlacement!!
        assertEquals(0.6f, edited.scale)
        assertEquals(42f, edited.rotation)
        view.moveLayer(true)
        assertEquals(glasses.id, view.placements.last().itemId)
        view.undo()
        assertEquals(hat.id, view.placements.last().itemId)
        assertEquals(edited, view.selectedPlacement)
        view.resetSelected()
        assertEquals(1f, view.selectedPlacement!!.scale)
        view.undo()
        assertEquals(edited, view.selectedPlacement)
    }

    @Test fun `cancelled touch does not leave a partial transform`() {
        val view = FoxPlacementView(context)
        view.layout(0, 0, 400, 500)
        view.setFox(AlchemyRecipeManager.createFox(7, "Preview", listOf(glasses)))
        val original = view.placements
        view.beginEdit()
        view.transformSelected(scale = 1.5f, rotation = -75f)
        touch(view, MotionEvent.ACTION_CANCEL, 0f, 0f)
        assertEquals(original, view.placements)
        assertFalse(view.canUndo)
    }

    @Test fun `rotated negative position is fully contained in exported PNG`() {
        val placement = FoxItemPlacement(glasses.id, -0.2f, -0.1f, 0.7f, 43f)
        val fox = AlchemyRecipeManager.createFox(7, "Preview", listOf(glasses), listOf(placement))
        val scene = FoxComposition(context, fox)
        val bounds = scene.bounds(scene.placements)
        assertTrue(bounds.left < 0)
        assertTrue(bounds.top < 0)
        assertTrue(bounds.contains(scene.itemBounds(placement)))
        val bitmap = FoxBitmapRenderer.createPreview(context, fox)
        assertEquals(bounds.width().roundToInt(), bitmap.width)
        assertEquals(bounds.height().roundToInt(), bitmap.height)
        // Independently render into a padded reference to check both transform and cropping.
        val reference = Bitmap.createBitmap(bitmap.width + 40, bitmap.height + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(reference)
        canvas.translate(20f - bounds.left, 20f - bounds.top)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scene.base, 0f, 0f, paint)
        val item = scene.bitmaps.getValue(glasses.id)
        canvas.translate(placement.centerX * scene.base.width, placement.centerY * scene.base.height)
        canvas.rotate(placement.rotation)
        canvas.scale(placement.scale, placement.scale)
        canvas.drawBitmap(item, -item.width / 2f, -item.height / 2f, paint)
        // Matrix concatenation can differ by one raster rounding step on native Skia.
        var maximumDifference = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val actual = bitmap.getPixel(x, y)
            val expected = reference.getPixel(x + 20, y + 20)
            val alpha = Color.alpha(actual)
            val expectedAlpha = Color.alpha(expected)
            maximumDifference = maxOf(maximumDifference, kotlin.math.abs(alpha - expectedAlpha))
            for (channel in listOf(Color::red, Color::green, Color::blue)) {
                maximumDifference = maxOf(maximumDifference,
                    kotlin.math.abs(channel(actual) * alpha / 255 - channel(expected) * expectedAlpha / 255))
            }
        }
        assertTrue("Maximum premultiplied pixel difference: $maximumDifference", maximumDifference <= 2)
        for (x in 0 until reference.width) {
            assertEquals(Color.TRANSPARENT, reference.getPixel(x, 0))
            assertEquals(Color.TRANSPARENT, reference.getPixel(x, reference.height - 1))
        }
    }

    @Test fun `restoring reserved items does not spend twice and cancelling refunds once`() {
        ItemPrefs.setCount(context, glasses.id, 2)
        AlchemySelectionManager.toggle(context, glasses)
        assertEquals(1, ItemPrefs.getCount(context, glasses.id))
        AlchemySelectionManager.resumePendingSelection(context)
        AlchemySelectionManager.resumePendingSelection(context)
        assertEquals(listOf(glasses.id), AlchemySelectionManager.getSelectedItems().map { it.id })
        assertEquals(1, ItemPrefs.getCount(context, glasses.id))
        AlchemySelectionManager.clear(context)
        AlchemySelectionManager.clear(context)
        assertEquals(2, ItemPrefs.getCount(context, glasses.id))
    }

    @Test fun `saving consumes reserved items once`() {
        ItemPrefs.setCount(context, glasses.id, 2)
        AlchemySelectionManager.toggle(context, glasses)
        AlchemySelectionManager.resumePendingSelection(context)
        AlchemySelectionManager.commit(context)
        AlchemySelectionManager.restorePendingSelection(context)
        assertEquals(1, ItemPrefs.getCount(context, glasses.id))
    }

    @Test fun `two finger gesture scales rotates and lifting a finger does not jump`() {
        val fox = AlchemyRecipeManager.createFox(7, "Preview", listOf(glasses))
        val scene = FoxComposition(context, fox)
        val view = FoxPlacementView(context)
        view.layout(0, 0, 600, 600)
        view.setFox(fox)
        val initial = view.placements.single()
        val scale = centeredCanvasScale(scene, 600, 600)
        val x = 300f + (initial.centerX - 0.5f) * scene.base.width * scale
        val y = 300f + (initial.centerY - 0.5f) * scene.base.height * scale
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        multiTouch(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            x, y, x + 40, y)
        val firstX = x + 20 - 25 * 0.8660254f
        val firstY = y - 12.5f
        val secondX = x + 20 + 25 * 0.8660254f
        val secondY = y + 12.5f
        multiTouch(view, MotionEvent.ACTION_MOVE, firstX, firstY, secondX, secondY)
        val transformed = view.placements.single()
        assertEquals(1.25f, transformed.scale, 0.001f)
        assertEquals(30f, transformed.rotation, 0.001f)
        multiTouch(view, MotionEvent.ACTION_POINTER_UP, firstX, firstY, secondX, secondY)
        touch(view, MotionEvent.ACTION_MOVE, secondX, secondY)
        assertEquals(transformed, view.placements.single())
        touch(view, MotionEvent.ACTION_UP, secondX, secondY)
        view.undo()
        assertEquals(initial, view.placements.single())
    }

    @Test fun `editor recreation and return to inventory retain draft and reservations`() {
        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup()
        var activity = controller.get()
        ItemPrefs.setCount(activity, glasses.id, 2)
        AlchemySelectionManager.toggle(activity, glasses)
        val fragment = FoxPlacementDialog.newInstance(listOf(glasses.id), emptyList())
        fragment.showNow(activity.supportFragmentManager, FoxPlacementDialog.TAG)
        var editor = fragment.requireView().findViewById<FoxPlacementView>(R.id.foxPlacementCanvas)
        editor.beginEdit()
        editor.transformSelected(scale = 0.65f, rotation = 27f)
        editor.endEdit()
        val draft = editor.placements
        controller.recreate()
        activity = controller.get()
        val restored = activity.supportFragmentManager.findFragmentByTag(FoxPlacementDialog.TAG) as FoxPlacementDialog
        editor = restored.requireView().findViewById(R.id.foxPlacementCanvas)
        assertEquals(draft, editor.placements)
        assertEquals(1, ItemPrefs.getCount(activity, glasses.id))
        restored.requireView().findViewById<View>(R.id.btnPlacementBack).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.supportFragmentManager.executePendingTransactions()
        val inventory = activity.supportFragmentManager.findFragmentByTag(AlchemyInventoryBottomSheet.TAG) as AlchemyInventoryBottomSheet
        inventory.requireView().findViewById<View>(R.id.btnCombine).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.supportFragmentManager.executePendingTransactions()
        val reopened = activity.supportFragmentManager.findFragmentByTag(FoxPlacementDialog.TAG) as FoxPlacementDialog
        assertEquals(draft, reopened.requireView().findViewById<FoxPlacementView>(R.id.foxPlacementCanvas).placements)
        activity.finish()
        controller.pause().stop().destroy()
        assertEquals(2, ItemPrefs.getCount(context, glasses.id))
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp-xxhdpi")
    fun `editor controls and canvas fit a phone and render for visual review`() {
        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup()
        val activity = controller.get()
        val fragment = FoxPlacementDialog.newInstance(listOf(1, 101, 201, 301), emptyList())
        fragment.showNow(activity.supportFragmentManager, FoxPlacementDialog.TAG)
        val root = fragment.requireView()
        val width = 1080
        val height = 2160
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        val editor = root.findViewById<FoxPlacementView>(R.id.foxPlacementCanvas)
        assertTrue(editor.height >= 360)
        assertTrue(root.findViewById<View>(R.id.btnPlacementDone).isShown)
        assertTrue(root.findViewById<View>(R.id.btnPlacementBack).isShown)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val output = File("build/reports/fox-placement/editor.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        activity.finish()
        controller.pause().stop().destroy()
    }

    @Test fun `combination saves the edited layout and PNG without a second item charge`() {
        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup().visible()
        val activity = controller.get()
        ItemPrefs.setCount(activity, glasses.id, 2)
        AlchemySelectionManager.toggle(activity, glasses)
        val edited = listOf(FoxItemPlacement(glasses.id, 0.45f, 0.35f, 0.6f, -28f))
        val fragment = FoxPlacementDialog.newInstance(listOf(glasses.id), edited)
        fragment.showNow(activity.supportFragmentManager, FoxPlacementDialog.TAG)
        fragment.requireView().findViewById<View>(R.id.btnPlacementDone).performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        dialog.findViewById<EditText>(R.id.editFoxName).setText("자유롭게")
        dialog.findViewById<View>(R.id.btnCloseAfterNaming).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val saved = AllFoxMap.allFoxMap.values.single()
        assertEquals(edited, saved.placements)
        assertEquals("자유롭게", saved.name)
        val storedBitmap = FoxBitmapRenderer.loadPreview(saved.previewPath)
        assertNotNull(storedBitmap)
        assertTrue(storedBitmap!!.sameAs(FoxBitmapRenderer.createPreview(activity, saved)))
        assertEquals(1, ItemPrefs.getCount(activity, glasses.id))
        assertTrue(AlchemySelectionManager.getSelectedItems().isEmpty())
        activity.finish()
        controller.pause().stop().destroy()
        assertEquals(1, ItemPrefs.getCount(context, glasses.id))
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp-xxhdpi")
    fun `purchased face appears in inventory preview and saved composition after restart`() {
        PointPrefs.savePoint(context, 900)
        val face = AllItemMap.allItemMap.getValue(405)
        val shopController = Robolectric.buildActivity(ShopActivity::class.java).setup().visible()
        var shop = shopController.get()
        shop.findViewById<View>(R.id.chipItem).performClick()
        fun chooseFaces() {
            val categories = shop.findViewById<ChipGroup>(R.id.shopItemCategories)
            (0 until categories.childCount).map(categories::getChildAt)
                .single { it.tag == ItemCategory.FOXFACE }.performClick()
        }
        chooseFaces()
        shopController.recreate()
        shop = shopController.get()
        val shopList = shop.findViewById<RecyclerView>(R.id.rvShopItems)
        val shopAdapter = shopList.adapter as ShopAdapter
        assertEquals(4, shopAdapter.itemCount)
        val row = shopAdapter.onCreateViewHolder(shopList, 0)
        shopAdapter.onBindViewHolder(row, 3)
        assertEquals(face.name, row.itemView.findViewById<TextView>(R.id.tvShopItemName).text.toString())
        row.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
        assertEquals(700, PointPrefs.getPoint(context))
        assertEquals(1, ItemPrefs.getCount(context, face.id))
        renderPhoneView(shop.findViewById(R.id.shopRoot), "face-shop", 2160)
        shop.finish()
        shopController.pause().stop().destroy()

        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup().visible()
        var activity = controller.get()
        activity.findViewById<View>(R.id.alchemy_pot).performClick()
        activity.supportFragmentManager.executePendingTransactions()
        var inventory = activity.supportFragmentManager.findFragmentByTag(AlchemyInventoryBottomSheet.TAG) as AlchemyInventoryBottomSheet
        val list = inventory.requireView().findViewById<RecyclerView>(R.id.rvItems)
        val adapter = list.adapter as AlchemyItemAdapter
        val faceRow = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(faceRow, 3)
        faceRow.itemView.performClick()
        assertEquals(face.id, AlchemySelectionManager.getSelected(ItemCategory.FOXFACE)?.id)
        assertEquals(0, ItemPrefs.getCount(context, face.id))
        val preview = (activity.findViewById<ImageView>(R.id.imgResultFox).drawable as BitmapDrawable).bitmap
        assertTrue(preview.sameAs(BitmapFactory.decodeResource(activity.resources, face.imageRes)))
        renderPhoneView(inventory.requireView(), "face-inventory", 1188)

        controller.recreate()
        activity = controller.get()
        inventory = activity.supportFragmentManager.findFragmentByTag(AlchemyInventoryBottomSheet.TAG) as AlchemyInventoryBottomSheet
        inventory.requireView().findViewById<View>(R.id.btnCombine).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.supportFragmentManager.executePendingTransactions()
        val dialog = activity.supportFragmentManager.findFragmentByTag(FoxPlacementDialog.TAG) as FoxPlacementDialog
        assertEquals("표정: ${face.name}", dialog.requireView().findViewById<TextView>(R.id.placementFaceLabel).text.toString())
        assertTrue(dialog.requireView().findViewById<FoxPlacementView>(R.id.foxPlacementCanvas).placements.isEmpty())
        renderPhoneView(dialog.requireView(), "face-editor", 2160)
        dialog.requireView().findViewById<View>(R.id.btnPlacementDone).performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val nameDialog = ShadowDialog.getLatestDialog()
        nameDialog.findViewById<EditText>(R.id.editFoxName).setText("웃는 여우")
        nameDialog.findViewById<View>(R.id.btnCloseAfterNaming).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val saved = AllFoxMap.allFoxMap.values.single()
        assertEquals(face.id, saved.foxFace)
        assertEquals(0, ItemPrefs.getCount(context, face.id))
        assertTrue(FoxBitmapRenderer.loadPreview(saved.previewPath)!!.sameAs(preview))
        AllFoxMap.allFoxMap.clear()
        FoxPrefs.loadAll(context)
        assertEquals(saved, AllFoxMap.allFoxMap.getValue(saved.id))
        activity.finish()
        controller.pause().stop().destroy()
        assertEquals(0, ItemPrefs.getCount(context, face.id))
    }

    @Test fun `face swaps and cancellation refund only reserved expressions and preserve accessories`() {
        val angry = AllItemMap.allItemMap.getValue(401)
        val smile = AllItemMap.allItemMap.getValue(404)
        ItemPrefs.setCount(context, angry.id, 1)
        ItemPrefs.setCount(context, smile.id, 1)
        ItemPrefs.setCount(context, glasses.id, 1)
        AlchemySelectionManager.toggle(context, glasses)
        AlchemySelectionManager.toggle(context, angry)
        AlchemySelectionManager.toggle(context, AllItemMap.allItemMap.getValue(402))
        assertEquals(angry.id, AlchemySelectionManager.getSelected(ItemCategory.FOXFACE)?.id)
        AlchemySelectionManager.toggle(context, smile)
        assertEquals(1, ItemPrefs.getCount(context, angry.id))
        assertEquals(0, ItemPrefs.getCount(context, smile.id))
        AlchemySelectionManager.resumePendingSelection(context)
        assertEquals(setOf(smile.id, glasses.id), AlchemySelectionManager.getSelectedItems().map { it.id }.toSet())
        AlchemySelectionManager.clear(context)
        AlchemySelectionManager.clear(context)
        assertEquals(1, ItemPrefs.getCount(context, angry.id))
        assertEquals(1, ItemPrefs.getCount(context, smile.id))
        assertEquals(1, ItemPrefs.getCount(context, glasses.id))
        assertEquals(0, ItemPrefs.getCount(context, 402))
    }

    @Test fun `each expression remains the fixed base with accessory placement and stable persistence`() {
        val placed = FoxItemPlacement(glasses.id, 0.45f, 0.35f, 0.6f, -28f)
        AllItemMap.allItemMap.values.filter { it.itemCategory == ItemCategory.FOXFACE }.forEach { face ->
            val fox = AlchemyRecipeManager.createFox(7, "표정", listOf(face, glasses),
                listOf(placed, FoxItemPlacement(face.id, -1f, -1f, 2f, 80f)))
            val scene = FoxComposition(context, fox)
            assertTrue(scene.base.sameAs(BitmapFactory.decodeResource(context.resources, face.imageRes)))
            assertEquals(listOf(placed), scene.placements)
            assertEquals(setOf(glasses.id), scene.bitmaps.keys)
            FoxPrefs.save(context, fox)
            AllFoxMap.allFoxMap.clear()
            FoxPrefs.loadAll(context)
            val restored = AllFoxMap.allFoxMap.getValue(7)
            assertEquals(face.id, restored.foxFace)
            assertTrue(FoxBitmapRenderer.createPreview(context, fox).sameAs(FoxBitmapRenderer.createPreview(context, restored)))
        }
        val basic = AlchemyRecipeManager.createFox(8, "기본", listOf(glasses))
        assertNull(basic.foxFace)
        assertEquals(R.drawable.dailyread_fox_face_level_3, basic.previewImage)
    }

    @Test fun `face purchase with insufficient points leaves balance and stock unchanged`() {
        PointPrefs.savePoint(context, 199)
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup().visible()
        val activity = controller.get()
        activity.findViewById<View>(R.id.chipItem).performClick()
        val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
        val adapter = list.adapter as ShopAdapter
        val row = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(row, 0)
        row.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
        assertEquals(199, PointPrefs.getPoint(context))
        assertEquals(0, ItemPrefs.getCount(context, 401))
        activity.finish()
        controller.pause().stop().destroy()
    }

    // Position synthetic gestures in the face-centered workspace (mdpi test devices).
    private fun renderPhoneView(root: View, name: String, height: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, height)
        val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val output = File("build/reports/fox-placement/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun centeredCanvasScale(scene: FoxComposition, width: Int, height: Int): Float {
        val area = scene.bounds(scene.defaults)
        val centerX = scene.base.width / 2f
        val centerY = scene.base.height / 2f
        val halfWidth = maxOf(centerX - area.left, area.right - centerX) + scene.base.width * 0.3f
        val halfHeight = maxOf(centerY - area.top, area.bottom - centerY) + scene.base.height * 0.3f
        return minOf((width - 24f) / (2f * halfWidth), (height - 24f) / (2f * halfHeight))
    }

    private fun touch(view: FoxPlacementView, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(0, 16, action, x, y, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
    }

    private fun multiTouch(view: FoxPlacementView, action: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        val pointers = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = x1; y = y1; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = x2; y = y2; pressure = 1f; size = 1f }
        )
        MotionEvent.obtain(0, 16, action, 2, pointers, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
    }
}
