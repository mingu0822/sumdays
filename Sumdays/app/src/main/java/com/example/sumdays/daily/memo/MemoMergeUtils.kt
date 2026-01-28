package com.example.sumdays.daily.memo

import android.util.Log
import com.example.sumdays.data.style.StylePrompt
import com.google.gson.Gson
import com.google.gson.JsonObject

object MemoMergeUtils {
    /** StylePrompt 객체를 서버 요청 형식(Map<String, Any>)으로 변환 */
    fun convertStylePromptToMap(prompt: StylePrompt): Map<String, Any> {
        val gson = Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type

        val jsonString = gson.toJson(prompt)
        return gson.fromJson(jsonString, type)
    }

    /** extract merged text from json file */
    fun extractMergedText(json: JsonObject): String {
        Log.d("test", "TEST: " + json.toString())

        // case 1: end_flag = true → diary
        if (json.has("result")) {
            val result = json.getAsJsonObject("result")

            if (result.has("diary") && result.get("diary").isJsonPrimitive) {
                return result.get("diary").asString
            }
        }
        // case 2: end_flag = false → {"merged_content": {"merged_content": "..."}}
        if (json.has("merged_content") && json.get("merged_content").isJsonObject) {
            val inner = json.getAsJsonObject("merged_content")
            if (inner.has("merged_content") && inner.get("merged_content").isJsonPrimitive) {
                return inner.get("merged_content").asString
            }
        }
        return ""
    }
}