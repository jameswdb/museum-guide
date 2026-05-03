package com.example.museumguide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.museumguide.MainActivity
import com.example.museumguide.R
import com.example.museumguide.databinding.FragmentMuseumDetailBinding
import com.example.museumguide.model.ChinaMuseums
import com.example.museumguide.model.MuseumExhibit
import com.example.museumguide.model.MuseumInfo

/**
 * Detail screen for a museum from the China Top 10 list.
 * Shows full museum info + exhibit list + TTS playback.
 */
class MuseumDetailFragment : Fragment() {

    private var _binding: FragmentMuseumDetailBinding? = null
    private val binding get() = _binding!!
    private var museum: MuseumInfo? = null
    private var isPlaying = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMuseumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val museumId = arguments?.getInt("museum_id", -1) ?: -1
        museum = ChinaMuseums.MUSEUMS.find { it.id == museumId }

        museum?.let { m ->
            displayMuseum(m)
            setupExhibitCards(m)
            setupPlayAllButton(m)
        }
    }

    private fun displayMuseum(museum: MuseumInfo) {
        binding.museumDetailName.text = museum.name
        binding.museumDetailCity.text = "📍 ${museum.city}"
        binding.museumDetailBrief.text = museum.brief
        binding.museumDetailDescription.text = museum.description
        binding.museumDetailSignificance.text = "🏛️ 文化意义\n${museum.significance}"
    }

    private fun setupExhibitCards(museum: MuseumInfo) {
        binding.exhibitListContainer.removeAllViews()

        for ((index, exhibit) in museum.exhibits.withIndex()) {
            val card = createExhibitCard(exhibit, index)
            binding.exhibitListContainer.addView(card)
        }
    }

    private fun createExhibitCard(exhibit: MuseumExhibit, index: Int): View {
        val context = requireContext()
        val inflater = LayoutInflater.from(context)

        val card = inflater.inflate(R.layout.card_exhibit, binding.exhibitListContainer, false) as CardView

        val nameText: TextView = card.findViewById(R.id.exhibit_card_name)
        val eraText: TextView = card.findViewById(R.id.exhibit_card_era)
        val descText: TextView = card.findViewById(R.id.exhibit_card_description)

        nameText.text = "${index + 1}. ${exhibit.name}"
        eraText.text = exhibit.era
        descText.text = exhibit.description

        // Click to play TTS for this exhibit
        card.setOnClickListener {
            playExhibitNarration(exhibit)
        }

        return card
    }

    private fun setupPlayAllButton(museum: MuseumInfo) {
        binding.btnPlayAll.setOnClickListener {
            togglePlayAll(museum)
        }
    }

    private fun togglePlayAll(museum: MuseumInfo) {
        val activity = requireActivity() as? MainActivity ?: return
        if (isPlaying) {
            activity.silence()
            isPlaying = false
            binding.btnPlayAll.text = "🎧 播报博物馆全部介绍"
        } else {
            val text = buildFullNarration(museum)
            activity.speak(text)
            isPlaying = true
            binding.btnPlayAll.text = "⏹ 停止播报"
        }
    }

    /** Build a full narration string for the entire museum. */
    private fun buildFullNarration(museum: MuseumInfo): String {
        val sb = StringBuilder()
        sb.append("欢迎来到${museum.name}。").append("\n\n")
        sb.append(museum.brief).append("。").append("\n\n")
        sb.append(museum.description).append("\n\n")
        sb.append(museum.significance).append("\n\n")
        sb.append("以下是镇馆之宝介绍：").append("\n\n")
        for ((index, exhibit) in museum.exhibits.withIndex()) {
            sb.append("第${index + 1}件，${exhibit.name}，${exhibit.era}。")
                .append(exhibit.description).append("\n\n")
        }
        return sb.toString()
    }

    private fun playExhibitNarration(exhibit: MuseumExhibit) {
        val activity = requireActivity() as? MainActivity ?: return
        val text = "${exhibit.name}，${exhibit.era}。${exhibit.description}"
        activity.speak(text)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop TTS if playing
        if (isPlaying) {
            (requireActivity() as? MainActivity)?.silence()
        }
        _binding = null
    }
}
