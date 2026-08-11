package com.example.sumdays.customize

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FoxPrefs {

    private const val PREF_NAME = "fox_prefs"
    private const val KEY_FOXES = "foxes"

    /**
     * 현재 AllFoxMap 전체 저장
     */
    fun saveAll(context: Context) {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        val jsonArray = JSONArray()

        AllFoxMap.allFoxMap.values.forEach { fox ->

            val json = JSONObject()

            json.put("id", fox.id)
            json.put("name", fox.name)
            json.put("previewImage", fox.previewImage)

            // 실제 조합된 PNG 파일 경로
            if (fox.previewPath != null) {
                json.put("previewPath", fox.previewPath)
            } else {
                json.put("previewPath", JSONObject.NULL)
            }

            // 안경
            if (fox.glasses != null) {
                json.put("glasses", fox.glasses)
            } else {
                json.put("glasses", JSONObject.NULL)
            }

            // 모자
            if (fox.hat != null) {
                json.put("hat", fox.hat)
            } else {
                json.put("hat", JSONObject.NULL)
            }

            // 목도리
            if (fox.scarf != null) {
                json.put("scarf", fox.scarf)
            } else {
                json.put("scarf", JSONObject.NULL)
            }

            // 악세사리
            if (fox.accessory != null) {
                json.put("accessory", fox.accessory)
            } else {
                json.put("accessory", JSONObject.NULL)
            }

            jsonArray.put(json)
        }

        prefs.edit()
            .putString(
                KEY_FOXES,
                jsonArray.toString()
            )
            .apply()
    }

    /**
     * 저장된 여우 불러오기
     */
    fun loadAll(context: Context) {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        val saved = prefs.getString(
            KEY_FOXES,
            null
        ) ?: return

        try {

            val jsonArray = JSONArray(saved)

            AllFoxMap.allFoxMap.clear()

            for (i in 0 until jsonArray.length()) {

                val json =
                    jsonArray.getJSONObject(i)

                val fox = CompleteFox(

                    id = json.getInt("id"),

                    name = json.getString("name"),

                    previewImage =
                        json.getInt("previewImage"),

                    // 저장된 PNG 경로
                    previewPath =
                        json.getNullableString(
                            "previewPath"
                        ),

                    glasses =
                        json.getNullableInt(
                            "glasses"
                        ),

                    hat =
                        json.getNullableInt(
                            "hat"
                        ),

                    scarf =
                        json.getNullableInt(
                            "scarf"
                        ),

                    accessory =
                        json.getNullableInt(
                            "accessory"
                        )
                )

                AllFoxMap.allFoxMap[
                    fox.id
                ] = fox
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    /**
     * 특정 여우 저장
     */
    fun save(
        context: Context,
        fox: CompleteFox
    ) {

        AllFoxMap.allFoxMap[
            fox.id
        ] = fox

        saveAll(context)
    }

    fun delete(
        context: Context,
        foxId: Int
    ) {
        val fox = AllFoxMap.allFoxMap[foxId]

        // 저장된 미리보기 이미지 파일도 삭제
        fox?.previewPath?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 메모리에서 삭제
        AllFoxMap.allFoxMap.remove(foxId)

        // SharedPreferences에 다시 저장
        saveAll(context)
    }

    /**
     * 저장 데이터 전체 삭제
     */
    fun clear(
        context: Context
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_FOXES)
            .apply()
    }

    /**
     * nullable Int 가져오기
     */
    private fun JSONObject.getNullableInt(
        key: String
    ): Int? {

        return if (
            !has(key) ||
            isNull(key)
        ) {
            null
        } else {
            getInt(key)
        }
    }

    /**
     * nullable String 가져오기
     */
    private fun JSONObject.getNullableString(
        key: String
    ): String? {

        return if (
            !has(key) ||
            isNull(key)
        ) {
            null
        } else {
            getString(key)
        }
    }
}