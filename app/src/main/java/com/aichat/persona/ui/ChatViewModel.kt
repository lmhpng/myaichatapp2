package com.aichat.persona.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.persona.db.ChatDatabase
import com.aichat.persona.db.MessageEntity
import com.aichat.persona.model.Message
import com.aichat.persona.model.MessageRole
import com.aichat.persona.model.Persona
import com.aichat.persona.util.ChatRepository
import com.aichat.persona.util.PersonaManager
import com.aichat.persona.util.PreferenceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChatViewModel(
    private val prefHelper: PreferenceHelper,
    context: Context
) : ViewModel() {

    private val repository = ChatRepository(prefHelper)
    private val db = ChatDatabase.getInstance(context)
    private val messageDao = db.messageDao()

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _currentPersona = MutableLiveData<Persona>()
    val currentPersona: LiveData<Persona> = _currentPersona

    // 触发 TTS 朗读的最新消息
    private val _ttsMessage = MutableLiveData<Pair<String, String>?>()  // (content, personaId)
    val ttsMessage: LiveData<Pair<String, String>?> = _ttsMessage

    private val conversationHistory = mutableListOf<Message>()
    private var streamJob: Job? = null

    init {
        val persona = PersonaManager.getPersonaById(prefHelper.lastPersonaId)
            ?: PersonaManager.getDefaultPersona()
        loadPersona(persona)
    }

    // ── 切换角色 ─────────────────────────────────────────────────
    fun setPersona(persona: Persona) {
        if (_currentPersona.value?.id == persona.id) return
        streamJob?.cancel()
        loadPersona(persona)
    }

    private fun loadPersona(persona: Persona) {
        _currentPersona.value = persona
        prefHelper.lastPersonaId = persona.id
        conversationHistory.clear()

        viewModelScope.launch {
            // 从数据库加载历史消息
            val dbMessages = messageDao.getMessagesForPersonaOnce(persona.id)
            val history = dbMessages.map { entity ->
                Message(
                    id = entity.id,
                    content = entity.content,
                    role = if (entity.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                    timestamp = entity.timestamp
                )
            }
            conversationHistory.addAll(history)

            val displayList = if (history.isEmpty()) {
                listOf(Message(content = persona.greeting, role = MessageRole.ASSISTANT))
            } else {
                history.toList()
            }
            _messages.postValue(displayList)
        }
    }

    // ── 发送消息 ─────────────────────────────────────────────────
    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _isLoading.value == true) return
        val persona = _currentPersona.value ?: return

        val userMessage = Message(content = userInput.trim(), role = MessageRole.USER)
        conversationHistory.add(userMessage)

        val currentList = _messages.value?.toMutableList() ?: mutableListOf()
        currentList.add(userMessage)

        // 添加正在输入占位
        val typingMessage = Message(content = "", role = MessageRole.ASSISTANT, isTyping = true)
        currentList.add(typingMessage)
        _messages.value = currentList.toList()

        _isLoading.value = true
        _error.value = null

        // 保存用户消息到数据库
        viewModelScope.launch {
            messageDao.insertMessage(
                MessageEntity(
                    personaId = persona.id,
                    content = userInput.trim(),
                    role = "user",
                    timestamp = userMessage.timestamp
                )
            )
        }

        if (prefHelper.streamEnabled) {
            startStreaming(persona, userInput.trim())
        } else {
            startNonStreaming(persona, userInput.trim())
        }
    }

    // ── 流式输出 ─────────────────────────────────────────────────
    private fun startStreaming(persona: Persona, userInput: String) {
        var accumulated = StringBuilder()
        val assistantMsgId = System.currentTimeMillis()

        streamJob = viewModelScope.launch {
            repository.streamMessage(
                persona = persona,
                conversationHistory = conversationHistory.dropLast(1),
                userMessage = userInput
            ).catch { e ->
                handleError(e.message ?: "流式请求失败")
            }.collect { result ->
                result.onSuccess { delta ->
                    accumulated.append(delta)

                    val list = _messages.value?.toMutableList() ?: return@collect
                    val lastIdx = list.indexOfLast { it.isTyping || it.id == assistantMsgId }
                    if (lastIdx >= 0) {
                        list[lastIdx] = Message(
                            id = assistantMsgId,
                            content = accumulated.toString(),
                            role = MessageRole.ASSISTANT,
                            isTyping = false
                        )
                        _messages.value = list.toList()
                    }
                }.onFailure { e ->
                    handleError(e.message ?: "请求失败")
                    return@collect
                }
            }

            // 流结束
            val finalContent = accumulated.toString()
            if (finalContent.isNotBlank()) {
                finishAssistantMessage(persona, finalContent, assistantMsgId)
            }
        }
    }

    // ── 非流式输出 ───────────────────────────────────────────────
    private fun startNonStreaming(persona: Persona, userInput: String) {
        viewModelScope.launch {
            val result = repository.sendMessage(
                persona = persona,
                conversationHistory = conversationHistory.dropLast(1),
                userMessage = userInput
            )
            result.onSuccess { reply ->
                val assistantMsgId = System.currentTimeMillis()
                finishAssistantMessage(persona, reply, assistantMsgId)
            }.onFailure { e ->
                handleError(e.message ?: "请求失败")
            }
        }
    }

    // ── 完成一条 AI 消息 ─────────────────────────────────────────
    private suspend fun finishAssistantMessage(persona: Persona, content: String, msgId: Long) {
        val assistantMessage = Message(id = msgId, content = content, role = MessageRole.ASSISTANT)
        conversationHistory.add(assistantMessage)

        // 更新显示（替换 typing 或更新流式消息）
        val list = _messages.value?.toMutableList() ?: mutableListOf()
        val idx = list.indexOfLast { it.isTyping || it.id == msgId }
        if (idx >= 0) list[idx] = assistantMessage else list.add(assistantMessage)
        _messages.postValue(list.toList())

        // 保存到数据库
        messageDao.insertMessage(
            MessageEntity(
                personaId = persona.id,
                content = content,
                role = "assistant",
                timestamp = assistantMessage.timestamp
            )
        )
        messageDao.trimMessages(persona.id, 200)

        // 触发 TTS
        if (prefHelper.ttsEnabled) {
            _ttsMessage.postValue(Pair(content, persona.id))
        }

        _isLoading.postValue(false)
    }

    // ── 错误处理 ─────────────────────────────────────────────────
    private fun handleError(msg: String) {
        conversationHistory.removeLastOrNull()
        val list = _messages.value?.toMutableList() ?: mutableListOf()
        list.removeLastOrNull() // 移除 typing 或不完整的 assistant 消息
        list.removeLastOrNull() // 移除 user 消息（重新输入）
        _messages.postValue(list.toList())
        _error.postValue(msg)
        _isLoading.postValue(false)
    }

    fun clearError() { _error.value = null }
    fun clearTtsMessage() { _ttsMessage.value = null }

    fun clearHistory() {
        streamJob?.cancel()
        conversationHistory.clear()
        val persona = _currentPersona.value ?: return
        viewModelScope.launch {
            messageDao.deleteMessagesForPersona(persona.id)
            val greeting = Message(content = persona.greeting, role = MessageRole.ASSISTANT)
            _messages.postValue(listOf(greeting))
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
