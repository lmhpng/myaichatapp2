package com.aichat.persona.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aichat.persona.util.PreferenceHelper

class ChatViewModelFactory(
    private val prefHelper: PreferenceHelper,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(prefHelper, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
