package com.aichat.persona.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    var isReady = false
        private set

    var onSpeakStart: (() -> Unit)? = null
    var onSpeakDone: (() -> Unit)? = null

    // 每个角色的音色配置：Pair(pitch, speechRate)
    // pitch: 1.0 = 正常，> 1.0 偏高（女生感），< 1.0 偏低（男生感）
    // rate:  1.0 = 正常，> 1.0 偏快，< 1.0 偏慢
    private val voiceConfigs: Map<String, VoiceConfig> = mapOf(
        "teacher"       to VoiceConfig(pitch = 0.95f, rate = 0.88f),  // 沉稳、慢速、权威感
        "junior_sister" to VoiceConfig(pitch = 1.45f, rate = 1.10f),  // 高音、活泼快
        "elder_sister"  to VoiceConfig(pitch = 1.20f, rate = 0.92f),  // 温柔、中音稍慢
        "elder_brother" to VoiceConfig(pitch = 0.78f, rate = 1.00f),  // 低音、正常速
        "auntie"        to VoiceConfig(pitch = 1.10f, rate = 1.08f),  // 中音、偏快热情
        "royal_sister"  to VoiceConfig(pitch = 1.05f, rate = 0.82f),  // 中音、慢速冷艳
    )

    data class VoiceConfig(val pitch: Float, val rate: Float)

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                isReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onSpeakStart?.invoke() }
                    override fun onDone(utteranceId: String?) { onSpeakDone?.invoke() }
                    override fun onError(utteranceId: String?) { onSpeakDone?.invoke() }
                })
            }
        }
    }

    fun speak(text: String, personaId: String) {
        if (!isReady) return
        val config = voiceConfigs[personaId] ?: VoiceConfig(1.0f, 1.0f)
        tts?.apply {
            setPitch(config.pitch)
            setSpeechRate(config.rate)
            // 清理 markdown 符号，朗读时不需要
            val cleanText = text
                .replace(Regex("\\*{1,2}(.+?)\\*{1,2}"), "$1")
                .replace(Regex("`(.+?)`"), "$1")
                .replace(Regex("#{1,6}\\s"), "")
                .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
                .trim()
            speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        }
    }

    fun stop() {
        tts?.stop()
    }

    val isSpeaking: Boolean
        get() = tts?.isSpeaking ?: false

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
