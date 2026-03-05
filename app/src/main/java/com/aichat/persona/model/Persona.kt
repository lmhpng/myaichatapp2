package com.aichat.persona.model

data class Persona(
    val id: String,
    val name: String,
    val subtitle: String,
    val emoji: String,
    val colorPrimary: Int,
    val colorSecondary: Int,
    val systemPrompt: String,
    val greeting: String
)
