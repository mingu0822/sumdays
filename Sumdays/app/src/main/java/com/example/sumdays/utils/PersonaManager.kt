package com.example.sumdays.utils

import android.content.Context
import com.example.sumdays.R
import com.example.sumdays.data.model.Persona
import com.example.sumdays.data.model.PersonaResponse
import com.google.gson.Gson
import java.io.InputStreamReader

class PersonaManager(private val context: Context) {

    private val gson = Gson()

    /**
     * res/raw/personas.json 파일을 읽어서 리스트로 반환합니다.
     */
    fun getPersonas(): List<Persona> {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.personas)
            val reader = InputStreamReader(inputStream)
            val response = gson.fromJson(reader, PersonaResponse::class.java)
            response.personas
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 특정 ID의 페르소나를 가져옵니다.
     */
    fun getPersonaById(id: Int): Persona? {
        return getPersonas().find { it.id == id }
    }
}