package com.aichat.persona

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aichat.persona.databinding.ActivityMainBinding
import com.aichat.persona.model.Message
import com.aichat.persona.model.Persona
import com.aichat.persona.ui.ChatViewModel
import com.aichat.persona.ui.ChatViewModelFactory
import com.aichat.persona.ui.MessageAdapter
import com.aichat.persona.ui.PersonaAdapter
import com.aichat.persona.util.PersonaManager
import com.aichat.persona.util.PreferenceHelper
import com.aichat.persona.util.TtsManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var ttsManager: TtsManager
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var personaAdapter: PersonaAdapter

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(prefHelper, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefHelper = PreferenceHelper(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initTts()
        setupToolbar()
        setupDrawer()
        setupChatRecyclerView()
        setupMessageInput()
        setupObservers()

        if (!prefHelper.isApiKeySet()) showApiKeyPrompt()
    }

    // ── TTS 初始化 ────────────────────────────────────────────────
    private fun initTts() {
        ttsManager = TtsManager(this)
        ttsManager.init()
        ttsManager.onSpeakDone = {
            runOnUiThread { updateTtsButton() }
        }
        updateTtsButton()
    }

    private fun updateTtsButton() {
        val icon = if (prefHelper.ttsEnabled) R.drawable.ic_volume else R.drawable.ic_volume_off
        binding.btnTts.setImageResource(icon)
    }

    // ── 工具栏 ────────────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnClearChat.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清空对话")
                .setMessage("确定要清空当前对话记录吗？")
                .setPositiveButton("清空") { _, _ -> viewModel.clearHistory() }
                .setNegativeButton("取消", null)
                .show()
        }
        binding.btnTts.setOnClickListener {
            if (ttsManager.isSpeaking) {
                ttsManager.stop()
                updateTtsButton()
            } else {
                prefHelper.ttsEnabled = !prefHelper.ttsEnabled
                if (!prefHelper.ttsEnabled) ttsManager.stop()
                updateTtsButton()
                val hint = if (prefHelper.ttsEnabled) "已开启朗读" else "已关闭朗读"
                Toast.makeText(this, hint, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 侧边抽屉 ──────────────────────────────────────────────────
    private fun setupDrawer() {
        personaAdapter = PersonaAdapter(PersonaManager.personas) { persona ->
            viewModel.setPersona(persona)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        binding.rvPersonas.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = personaAdapter
        }
    }

    // ── 聊天列表 ──────────────────────────────────────────────────
    private fun setupChatRecyclerView() {
        messageAdapter = MessageAdapter { message ->
            // 点击单条消息的 TTS 按钮
            speakMessage(message)
        }
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            adapter = messageAdapter
        }
    }

    private fun speakMessage(message: Message) {
        val persona = viewModel.currentPersona.value ?: return
        if (ttsManager.isSpeaking) {
            ttsManager.stop()
        } else {
            ttsManager.speak(message.content, persona.id)
        }
    }

    // ── 输入区 ────────────────────────────────────────────────────
    private fun setupMessageInput() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        binding.etMessage.text?.clear()
        ttsManager.stop()  // 发新消息时停止朗读
        viewModel.sendMessage(text)
    }

    // ── 观察 ViewModel ────────────────────────────────────────────
    private fun setupObservers() {
        viewModel.messages.observe(this) { messages ->
            messageAdapter.submitMessages(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessages.post {
                    binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.btnSend.isEnabled = !isLoading
            binding.etMessage.isEnabled = !isLoading
        }

        viewModel.error.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.currentPersona.observe(this) { updatePersonaUI(it) }

        viewModel.ttsMessage.observe(this) { pair ->
            pair?.let { (content, personaId) ->
                ttsManager.speak(content, personaId)
                viewModel.clearTtsMessage()
            }
        }
    }

    // ── 更新 UI 主题（跟随角色色调） ─────────────────────────────
    private fun updatePersonaUI(persona: Persona) {
        binding.tvPersonaName.text = persona.name
        binding.tvPersonaSubtitle.text = persona.subtitle
        binding.tvPersonaEmoji.text = persona.emoji
        binding.toolbarContainer.setBackgroundColor(persona.colorPrimary)
        binding.drawerHeaderContainer.setBackgroundColor(persona.colorPrimary)
        personaAdapter.setSelectedPersona(persona.id)
        binding.btnSend.backgroundTintList =
            android.content.res.ColorStateList.valueOf(persona.colorPrimary)
    }

    private fun showApiKeyPrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("👋 欢迎使用 AI 聊伴")
            .setMessage("请先前往设置页面填写硅基流动 API Key，才能开始聊天。")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTtsButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
