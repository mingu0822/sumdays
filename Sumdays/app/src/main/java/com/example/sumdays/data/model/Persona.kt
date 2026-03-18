package com.example.sumdays.data.model

data class PersonaResponse(
    val personas: List<Persona>
)

data class Persona(
    val id: Int,
    val name: String,                // 여우 페르소나 이름
    val description: String,         // 사용자에게 보여줄 짧은 설명
    val tags: List<String>,          // 여러 개의 태그 (검색, 분류에 사용)
    val systemPrompt: String
)