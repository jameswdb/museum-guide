package com.example.museumguide.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.museumguide.R
import com.example.museumguide.model.MuseumInfo

/**
 * RecyclerView adapter for the Top 10 Museums list on the Home page.
 */
class MuseumAdapter(
    private val museums: List<MuseumInfo>,
    private val onClick: (MuseumInfo) -> Unit
) : RecyclerView.Adapter<MuseumAdapter.MuseumViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MuseumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_museum_card, parent, false)
        return MuseumViewHolder(view)
    }

    override fun onBindViewHolder(holder: MuseumViewHolder, position: Int) {
        val museum = museums[position]
        holder.bind(museum, onClick)
    }

    override fun getItemCount() = museums.size

    class MuseumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.museum_card)
        private val nameText: TextView = itemView.findViewById(R.id.museum_name)
        private val cityText: TextView = itemView.findViewById(R.id.museum_city)
        private val briefText: TextView = itemView.findViewById(R.id.museum_brief)
        private val countText: TextView = itemView.findViewById(R.id.museum_exhibit_count)

        fun bind(museum: MuseumInfo, onClick: (MuseumInfo) -> Unit) {
            nameText.text = museum.name
            cityText.text = "📍 ${museum.city}"
            briefText.text = museum.brief
            countText.text = "${museum.exhibits.size}件著名展品"
            card.setOnClickListener { onClick(museum) }
        }
    }
}
