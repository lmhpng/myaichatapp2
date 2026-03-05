package com.aichat.persona.model

data class Message(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isTyping: Boolean = false
)

enum class MessageRole {
    USER, ASSISTANT
}
