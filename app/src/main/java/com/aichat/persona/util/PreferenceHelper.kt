package com.aichat.persona.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferenceHelper(context: Context) {

    // 普通配置（非敏感）
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 加密存储（API Key 敏感数据）
    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 降级到普通存储（极少数设备不支持加密）
            context.getSharedPreferences(SECURE_PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREF_NAME = "aichat_prefs"
        private const val SECURE_PREF_NAME = "aichat_secure_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_LAST_PERSONA = "last_persona"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_STREAM_ENABLED = "stream_enabled"

        const val DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct"
        val AVAILABLE_MODELS = listOf(
            "Qwen/Qwen2.5-7B-Instruct",
            "Qwen/Qwen2.5-14B-Instruct",
            "deepseek-ai/DeepSeek-V2.5",
            "meta-llama/Meta-Llama-3.1-8B-Instruct",
            "internlm/internlm2_5-7b-chat"
        )
    }

    // API Key 加密存储
    var apiKey: String
        get() = securePrefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = securePrefs.edit { putString(KEY_API_KEY, value) }

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit { putString(KEY_MODEL, value) }

    var lastPersonaId: String
        get() = prefs.getString(KEY_LAST_PERSONA, "teacher") ?: "teacher"
        set(value) = prefs.edit { putString(KEY_LAST_PERSONA, value) }

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.75f)
        set(value) = prefs.edit { putFloat(KEY_TEMPERATURE, value) }

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_TTS_ENABLED, value) }

    var streamEnabled: Boolean
        get() = prefs.getBoolean(KEY_STREAM_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_STREAM_ENABLED, value) }

    fun isApiKeySet(): Boolean = apiKey.isNotBlank()
}
