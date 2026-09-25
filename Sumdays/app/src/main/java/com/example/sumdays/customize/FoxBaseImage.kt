package com.example.sumdays.customize

import com.example.sumdays.R
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.ItemCategory

/** Android resource integers change between builds. Persist these stable names instead. */
object FoxBaseImage {
    private val images = mapOf(
        "dailyread_fox_face_level_1" to R.drawable.dailyread_fox_face_level_1,
        "dailyread_fox_face_level_2" to R.drawable.dailyread_fox_face_level_2,
        "dailyread_fox_face_level_3" to R.drawable.dailyread_fox_face_level_3,
        "dailyread_fox_face_level_4" to R.drawable.dailyread_fox_face_level_4,
        "dailyread_fox_face_level_5" to R.drawable.dailyread_fox_face_level_5
    )

    fun resource(fox: CompleteFox): Int =
        selectedFace(fox) ?: fox.previewImage.takeIf { it in images.values } ?: legacyResource(fox)

    fun name(fox: CompleteFox): String = images.entries.first { it.value == resource(fox) }.key

    fun restore(savedName: String?, fox: CompleteFox): Int =
        selectedFace(fox) ?: images[savedName] ?: legacyResource(fox)

    private fun selectedFace(fox: CompleteFox): Int? = AllItemMap.allItemMap[fox.foxFace]
        ?.takeIf { it.itemCategory == ItemCategory.FOXFACE && it.imageRes in images.values }
        ?.imageRes

    private fun legacyResource(fox: CompleteFox): Int {
        // Before named resources, only the two built-in faces and level-3 recipes existed.
        // Never reuse the old integer: it may now refer to XML or a different bitmap.
        val hasItems = listOf(fox.glasses, fox.hat, fox.scarf, fox.accessory).any { it != null }
        return when {
            hasItems -> R.drawable.dailyread_fox_face_level_3
            fox.id == 1 -> R.drawable.dailyread_fox_face_level_1
            fox.id == 2 -> R.drawable.dailyread_fox_face_level_5
            else -> R.drawable.dailyread_fox_face_level_3
        }
    }
}
