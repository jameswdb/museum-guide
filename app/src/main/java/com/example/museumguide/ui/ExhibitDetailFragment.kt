package com.example.museumguide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.museumguide.MainActivity
import com.example.museumguide.R
import com.example.museumguide.databinding.FragmentExhibitDetailBinding
import com.example.museumguide.exhibit.ExhibitRepository
import com.example.museumguide.model.ExhibitDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detail screen for a single museum exhibit.
 * Shows full description, image, and provides a text-to-speech button.
 */
class ExhibitDetailFragment : Fragment() {

    private var _binding: FragmentExhibitDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var exhibitRepository: ExhibitRepository
    private var isSpeaking = false
    private var exhibitId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExhibitDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exhibitId = arguments?.getLong("exhibit_id", -1L) ?: -1L

        if (exhibitId <= 0) {
            Toast.makeText(requireContext(), "文物信息不可用", Toast.LENGTH_SHORT).show()
            return
        }

        val db = ExhibitDatabase.getInstance(requireContext())
        exhibitRepository = ExhibitRepository(db.exhibitDao())

        loadExhibit()

        // TTS button
        binding.btnListen.setOnClickListener {
            toggleNarration()
        }
    }

    private fun loadExhibit() {
        lifecycleScope.launch {
            try {
                val exhibit = withContext(Dispatchers.IO) {
                    exhibitRepository.getExhibitById(exhibitId)
                }
                exhibit?.let {
                    binding.exhibitName.text = it.name
                    binding.exhibitEra.text = it.era
                    binding.exhibitDescription.text = it.description
                    binding.exhibitSignificance.text = it.significance
                    binding.btnListen.isEnabled = true
                } ?: run {
                    Toast.makeText(requireContext(), "未找到文物信息", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "加载文物信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleNarration() {
        val activity = requireActivity() as? MainActivity ?: return
        if (isSpeaking) {
            activity.silence()
            isSpeaking = false
            binding.btnListen.text = getString(R.string.play_audio)
        } else {
            val text = buildString {
                append(binding.exhibitName.text ?: "").append("。")
                append(binding.exhibitEra.text ?: "").append("。")
                append(binding.exhibitDescription.text ?: "").append("。")
                append(binding.exhibitSignificance.text ?: "")
            }
            if (text.isNotBlank()) {
                activity.speak(text)
                isSpeaking = true
                binding.btnListen.text = getString(R.string.stop_audio)
            }
        }
    }

    override fun onDestroyView() {
        if (isSpeaking) {
            (requireActivity() as? MainActivity)?.silence()
        }
        super.onDestroyView()
        _binding = null
    }
}
