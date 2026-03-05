package com.aichat.persona.model

import com.google.gson.annotations.SerializedName

// ---- Request ----
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Double = 0.75,
    @SerializedName("top_p") val topP: Double = 0.9,
    val stream: Boolean = false
)

data class ApiMessage(
    val role: String,
    val content: String
)

// ---- Non-stream Response ----
data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?,
    val error: ApiError?
)

data class Choice(
    val message: ApiMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)

data class ApiError(
    val message: String?,
    val type: String?,
    val code: String?
)

// ---- Streaming Response ----
data class StreamResponse(
    val id: String?,
    val choices: List<StreamChoice>?
)

data class StreamChoice(
    val delta: StreamDelta?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class StreamDelta(
    val role: String?,
    val content: String?
)
