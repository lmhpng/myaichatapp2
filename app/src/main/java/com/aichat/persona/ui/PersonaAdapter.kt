package com.aichat.persona.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.aichat.persona.R
import com.aichat.persona.model.Persona

class PersonaAdapter(
    private val personas: List<Persona>,
    private val onPersonaSelected: (Persona) -> Unit
) : RecyclerView.Adapter<PersonaAdapter.ViewHolder>() {

    private var selectedId: String = personas.firstOrNull()?.id ?: ""

    fun setSelectedPersona(id: String) {
        val old = personas.indexOfFirst { it.id == selectedId }
        val new = personas.indexOfFirst { it.id == id }
        selectedId = id
        if (old >= 0) notifyItemChanged(old)
        if (new >= 0) notifyItemChanged(new)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_persona, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(personas[position])
    }

    override fun getItemCount() = personas.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.card_persona)
        private val tvEmoji: TextView = itemView.findViewById(R.id.tv_persona_emoji)
        private val tvName: TextView = itemView.findViewById(R.id.tv_persona_name)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tv_persona_subtitle)

        fun bind(persona: Persona) {
            tvEmoji.text = persona.emoji
            tvName.text = persona.name
            tvSubtitle.text = persona.subtitle

            val isSelected = persona.id == selectedId
            card.setCardBackgroundColor(
                if (isSelected) persona.colorSecondary
                else itemView.context.getColor(R.color.surface)
            )
            tvName.setTextColor(
                if (isSelected) persona.colorPrimary
                else itemView.context.getColor(R.color.on_surface)
            )

            // Left accent bar
            val accent = itemView.findViewById<View>(R.id.view_accent)
            accent.setBackgroundColor(persona.colorPrimary)
            accent.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            itemView.setOnClickListener { onPersonaSelected(persona) }
        }
    }
}
