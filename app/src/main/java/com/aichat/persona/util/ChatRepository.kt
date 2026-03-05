package com.aichat.persona.util

import com.aichat.persona.api.ApiClient
import com.aichat.persona.model.ApiMessage
import com.aichat.persona.model.ChatRequest
import com.aichat.persona.model.Message
import com.aichat.persona.model.MessageRole
import com.aichat.persona.model.Persona
import com.aichat.persona.model.StreamResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(private val prefHelper: PreferenceHelper) {

    private val gson = Gson()

    // ── 非流式请求 ──────────────────────────────────────────────
    suspend fun sendMessage(
        persona: Persona,
        conversationHistory: List<Message>,
        userMessage: String
    ): Result<String> {
        if (!prefHelper.isApiKeySet()) {
            return Result.failure(Exception("请先在设置中填写硅基流动 API Key"))
        }
        val messages = buildMessageList(persona, conversationHistory, userMessage)
        val request = ChatRequest(
            model = prefHelper.model,
            messages = messages,
            maxTokens = 1024,
            temperature = prefHelper.temperature.toDouble(),
            stream = false
        )
        return try {
            val response = ApiClient.service.chatCompletion(
                authorization = "Bearer ${prefHelper.apiKey}",
                request = request
            )
            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) Result.success(content.trim())
                else Result.failure(Exception("返回内容为空"))
            } else {
                Result.failure(Exception("请求失败 (${response.code()}): ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    // ── 流式请求（SSE） ─────────────────────────────────────────
    fun streamMessage(
        persona: Persona,
        conversationHistory: List<Message>,
        userMessage: String
    ): Flow<Result<String>> = flow {

        if (!prefHelper.isApiKeySet()) {
            emit(Result.failure(Exception("请先在设置中填写硅基流动 API Key")))
            return@flow
        }

        val messages = buildMessageList(persona, conversationHistory, userMessage)
        val chatRequest = ChatRequest(
            model = prefHelper.model,
            messages = messages,
            maxTokens = 1024,
            temperature = prefHelper.temperature.toDouble(),
            stream = true
        )

        val json = gson.toJson(chatRequest)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("${ApiClient.BASE_URL}chat/completions")
            .addHeader("Authorization", "Bearer ${prefHelper.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        try {
            ApiClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(Result.failure(Exception("请求失败 (${response.code})")))
                    return@use
                }

                val source = response.body?.source()
                    ?: run { emit(Result.failure(Exception("响应体为空"))); return@use }

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("data: [DONE]") -> break
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isBlank()) continue
                            try {
                                val streamResp = gson.fromJson(data, StreamResponse::class.java)
                                val delta = streamResp.choices?.firstOrNull()?.delta?.content
                                if (!delta.isNullOrEmpty()) {
                                    emit(Result.success(delta))
                                }
                            } catch (_: Exception) {
                                // 跳过格式异常的行
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(Exception("网络错误: ${e.message}")))
        }
    }.flowOn(Dispatchers.IO)

    // ── 构建消息列表 ────────────────────────────────────────────
    private fun buildMessageList(
        persona: Persona,
        history: List<Message>,
        newUserMessage: String
    ): List<ApiMessage> {
        val messages = mutableListOf<ApiMessage>()
        messages.add(ApiMessage(role = "system", content = persona.systemPrompt))
        history.takeLast(20).forEach { msg ->
            messages.add(ApiMessage(
                role = if (msg.role == MessageRole.USER) "user" else "assistant",
                content = msg.content
            ))
        }
        messages.add(ApiMessage(role = "user", content = newUserMessage))
        return messages
    }
}
