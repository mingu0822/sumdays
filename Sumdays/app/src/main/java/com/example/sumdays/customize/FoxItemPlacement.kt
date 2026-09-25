package com.example.sumdays.customize

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

/** Coordinates are relative to the base fox, so a saved layout survives density changes.
 * List order is the drawing order (back to front).
 */
@Parcelize
data class FoxItemPlacement(
    val itemId: Int,
    val centerX: Float,
    val centerY: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f
) : Parcelable {
    companion object {
        fun toJson(placements: List<FoxItemPlacement>): JSONArray = JSONArray().apply {
            placements.forEach { placement ->
                put(JSONObject().apply {
                    put("itemId", placement.itemId)
                    put("centerX", placement.centerX)
                    put("centerY", placement.centerY)
                    put("scale", placement.scale)
                    put("rotation", placement.rotation)
                })
            }
        }

        fun fromJson(array: JSONArray?): List<FoxItemPlacement> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val x = json.optDouble("centerX", Double.NaN).toFloat()
                val y = json.optDouble("centerY", Double.NaN).toFloat()
                val scale = json.optDouble("scale", 1.0).toFloat()
                val rotation = json.optDouble("rotation", 0.0).toFloat()
                if (!x.isFinite() || !y.isFinite() || !scale.isFinite() || !rotation.isFinite()) continue
                add(FoxItemPlacement(
                    json.optInt("itemId"), x.coerceIn(-4f, 5f), y.coerceIn(-4f, 5f),
                    scale.coerceIn(0.25f, 2f), rotation % 360f
                ))
            }
        }
    }
}
