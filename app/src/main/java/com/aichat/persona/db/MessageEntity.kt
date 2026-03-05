package com.aichat.persona.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personaId: String,
    val content: String,
    val role: String,         // "user" or "assistant"
    val timestamp: Long = System.currentTimeMillis()
)
