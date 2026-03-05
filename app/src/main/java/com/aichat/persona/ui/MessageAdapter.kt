package com.aichat.persona.ui

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aichat.persona.R
import com.aichat.persona.model.Message
import com.aichat.persona.model.MessageRole
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 列表项 = 时间分隔线 or 消息
sealed class ChatItem {
    data class TimeHeader(val label: String) : ChatItem()
    data class ChatMessage(val message: Message) : ChatItem()
}

class MessageAdapter(
    private val onTtsClick: (Message) -> Unit
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_TIME_HEADER = 0
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_TYPING = 3
    }

    // 将消息列表转为带时间分组的列表
    fun submitMessages(messages: List<Message>) {
        val items = mutableListOf<ChatItem>()
        var lastDate = ""
        for (msg in messages) {
            val dateLabel = formatDateLabel(msg.timestamp)
            if (dateLabel != lastDate) {
                items.add(ChatItem.TimeHeader(dateLabel))
                lastDate = dateLabel
            }
            items.add(ChatItem.ChatMessage(msg))
        }
        submitList(items)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.TimeHeader -> VIEW_TYPE_TIME_HEADER
            is ChatItem.ChatMessage -> when {
                item.message.isTyping -> VIEW_TYPE_TYPING
                item.message.role == MessageRole.USER -> VIEW_TYPE_SENT
                else -> VIEW_TYPE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TIME_HEADER -> TimeHeaderViewHolder(
                inflater.inflate(R.layout.item_time_header, parent, false)
            )
            VIEW_TYPE_SENT -> SentVH(
                inflater.inflate(R.layout.item_message_sent, parent, false)
            )
            VIEW_TYPE_TYPING -> TypingVH(
                inflater.inflate(R.layout.item_message_typing, parent, false)
            )
            else -> ReceivedVH(
                inflater.inflate(R.layout.item_message_received, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is TimeHeaderViewHolder && item is ChatItem.TimeHeader -> holder.bind(item)
            holder is SentVH && item is ChatItem.ChatMessage -> holder.bind(item.message)
            holder is ReceivedVH && item is ChatItem.ChatMessage -> holder.bind(item.message)
            holder is TypingVH -> holder.bind()
        }
    }

    // ── ViewHolders ─────────────────────────────────────────────

    inner class TimeHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tv_time_header)
        fun bind(item: ChatItem.TimeHeader) { tvLabel.text = item.label }
    }

    inner class SentVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)

        fun bind(message: Message) {
            tvMessage.text = message.content
            tvTime.text = formatTime(message.timestamp)
            // 长按复制
            tvMessage.setOnLongClickListener {
                copyToClipboard(it.context, message.content)
                true
            }
            // 入场动画
            itemView.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.fade_in))
        }
    }

    inner class ReceivedVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)
        private val btnTts: ImageButton = view.findViewById(R.id.btn_tts)

        fun bind(message: Message) {
            tvMessage.text = message.content
            tvTime.text = formatTime(message.timestamp)

            // 长按复制
            tvMessage.setOnLongClickListener {
                copyToClipboard(it.context, message.content)
                true
            }

            // TTS 朗读按钮
            btnTts.setOnClickListener { onTtsClick(message) }

            // 入场动画
            itemView.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.fade_in))
        }
    }

    inner class TypingVH(view: View) : RecyclerView.ViewHolder(view) {
        private val dot1: View = view.findViewById(R.id.dot1)
        private val dot2: View = view.findViewById(R.id.dot2)
        private val dot3: View = view.findViewById(R.id.dot3)

        fun bind() {
            animateDot(dot1, 0L)
            animateDot(dot2, 150L)
            animateDot(dot3, 300L)
        }

        private fun animateDot(dot: View, delay: Long) {
            ObjectAnimator.ofFloat(dot, "translationY", 0f, -10f, 0f).apply {
                duration = 600
                startDelay = delay
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    // ── 工具方法 ─────────────────────────────────────────────────

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    private fun formatDateLabel(timestamp: Long): String {
        val cal = Calendar.getInstance()
        val today = cal.clone() as Calendar
        cal.timeInMillis = timestamp

        return when {
            isSameDay(cal, today) -> "今天"
            isYesterday(cal, today) -> "昨天"
            else -> SimpleDateFormat("MM月dd日", Locale.CHINESE).format(Date(timestamp))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(a: Calendar, today: Calendar): Boolean {
        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(a, yesterday)
    }
}

class ChatItemDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return when {
            oldItem is ChatItem.TimeHeader && newItem is ChatItem.TimeHeader ->
                oldItem.label == newItem.label
            oldItem is ChatItem.ChatMessage && newItem is ChatItem.ChatMessage ->
                oldItem.message.id == newItem.message.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean =
        oldItem == newItem
}
