package com.example.museumguide.ui

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.museumguide.R
import com.example.museumguide.ai.AiConfiguration
import com.example.museumguide.databinding.FragmentHomeBinding
import com.example.museumguide.model.ChinaMuseums
import com.example.museumguide.model.MuseumInfo
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home / Settings screen for the Museum Guide app.
 *
 * Features:
 * - AI model provider configuration (Gemini, DeepSeek, Qwen, ERNIE)
 * - Museum location setting (manual + GPS auto-detect)
 * - China Top 10 Museums quick-access list
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var currentConfig: AiConfiguration = AiConfiguration()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load current config
        currentConfig = AiConfiguration.load(requireContext())
        applyConfigToUi()

        // Save location
        binding.btnSaveLocation.setOnClickListener { saveLocation() }

        // GPS location
        binding.btnGpsLocate.setOnClickListener { detectLocationByGps() }

        // Save AI config
        binding.btnSaveAiConfig.setOnClickListener { saveAiConfig() }

        // Provider radio group listener — update API key hint
        binding.rgAiProvider.setOnCheckedChangeListener { _, checkedId ->
            updateApiKeyHint(checkedId)
        }

        // Set up museum list
        setupMuseumList()

        // Set rank numbers on museum cards after binding
        binding.rvMuseums.post {
            updateMuseumRanks()
        }
    }

    private fun applyConfigToUi() {
        // Set provider radio
        val radioId = when (currentConfig.activeProviderId) {
            AiConfiguration.PROVIDER_GEMINI -> R.id.rb_gemini
            AiConfiguration.PROVIDER_DEEPSEEK -> R.id.rb_deepseek
            AiConfiguration.PROVIDER_QWEN -> R.id.rb_qwen
            AiConfiguration.PROVIDER_ERNIE -> R.id.rb_ernie
            else -> R.id.rb_gemini
        }
        binding.rgAiProvider.check(radioId)

        // Set API key
        when (currentConfig.activeProviderId) {
            AiConfiguration.PROVIDER_GEMINI -> binding.etApiKey.setText(currentConfig.geminiApiKey)
            AiConfiguration.PROVIDER_DEEPSEEK -> binding.etApiKey.setText(currentConfig.deepseekApiKey)
            AiConfiguration.PROVIDER_QWEN -> binding.etApiKey.setText(currentConfig.qwenApiKey)
            AiConfiguration.PROVIDER_ERNIE -> binding.etApiKey.setText(currentConfig.ernieApiKey)
        }

        // Set model name
        when (currentConfig.activeProviderId) {
            AiConfiguration.PROVIDER_DEEPSEEK -> binding.etModelName.setText(currentConfig.deepseekModelName)
            AiConfiguration.PROVIDER_QWEN -> binding.etModelName.setText(currentConfig.qwenModelName)
            AiConfiguration.PROVIDER_ERNIE -> binding.etModelName.setText(currentConfig.ernieModelName)
            else -> binding.etModelName.setText("")
        }

        // Show/hide model name field
        updateModelNameVisibility(currentConfig.activeProviderId)

        // Museum city
        binding.etMuseumCity.setText(currentConfig.museumCity)
        updateCurrentMuseumText(currentConfig.museumCity)

        // API key hint
        updateApiKeyHint(radioId)
    }

    private fun updateApiKeyHint(checkedId: Int) {
        val hint = when (checkedId) {
            R.id.rb_gemini -> "获取地址: https://aistudio.google.com/apikey"
            R.id.rb_deepseek -> "获取地址: https://platform.deepseek.com （注册送500万Token）"
            R.id.rb_qwen -> "获取地址: https://bailian.console.aliyun.com （新用户7000万免费Token）"
            R.id.rb_ernie -> "获取地址: https://console.bce.baidu.com/qianfan （ERNIE-Speed永久免费）"
            else -> ""
        }
        binding.tvApiKeyHint.text = hint
    }

    private fun updateModelNameVisibility(providerId: String) {
        val visible = providerId != AiConfiguration.PROVIDER_GEMINI
        binding.modelNameInputLayout.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun saveLocation() {
        val city = binding.etMuseumCity.text.toString().trim()
        if (city.isBlank()) {
            Toast.makeText(requireContext(), "请输入城市名称", Toast.LENGTH_SHORT).show()
            return
        }
        currentConfig = currentConfig.copy(museumCity = city)
        AiConfiguration.save(requireContext(), currentConfig)
        updateCurrentMuseumText(city)
        Toast.makeText(requireContext(), "博物馆位置已保存: $city", Toast.LENGTH_SHORT).show()
    }

    private fun updateCurrentMuseumText(city: String) {
        // Try to find a known museum in this city
        val museum = ChinaMuseums.MUSEUMS.find {
            it.city.contains(city) || city.contains(it.city)
        }
        val museumName = museum?.let { " · ${it.name}" } ?: ""
        binding.tvCurrentMuseum.text = "当前定位：$city$museumName"
    }

    private fun detectLocationByGps() {
        binding.btnGpsLocate.isEnabled = false
        binding.btnGpsLocate.text = "定位中…"

        lifecycleScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())
                val location = withContext(Dispatchers.IO) {
                    fusedClient.lastLocation.result
                }

                if (location != null) {
                    val geocoder = Geocoder(requireContext(), java.util.Locale.CHINESE)
                    val addresses = withContext(Dispatchers.IO) {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    }

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = extractCity(address)
                        if (city.isNotBlank()) {
                            binding.etMuseumCity.setText(city)
                            currentConfig = currentConfig.copy(
                                museumCity = city,
                                museumLatitude = location.latitude,
                                museumLongitude = location.longitude
                            )
                            AiConfiguration.save(requireContext(), currentConfig)
                            updateCurrentMuseumText(city)
                            Toast.makeText(requireContext(), "已自动定位到: $city", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "无法识别城市，请手动输入", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "无法获取位置信息", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "定位失败，请确保已开启位置权限和GPS", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "定位出错: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnGpsLocate.isEnabled = true
                binding.btnGpsLocate.text = "📡 自动定位"
            }
        }
    }

    /** Extract the city name from an Android Address. */
    private fun extractCity(address: Address): String {
        // Try admin area (province-level city like 北京市)
        val adminArea = address.adminArea ?: ""
        // Try locality (city name like 北京)
        val locality = address.locality ?: ""
        // Try sub-admin area
        val subAdmin = address.subAdminArea ?: ""

        return when {
            locality.isNotBlank() -> locality
            adminArea.isNotBlank() -> adminArea
            subAdmin.isNotBlank() -> subAdmin
            else -> ""
        }
    }

    private fun saveAiConfig() {
        val selectedId = binding.rgAiProvider.checkedRadioButtonId
        val providerId = when (selectedId) {
            R.id.rb_gemini -> AiConfiguration.PROVIDER_GEMINI
            R.id.rb_deepseek -> AiConfiguration.PROVIDER_DEEPSEEK
            R.id.rb_qwen -> AiConfiguration.PROVIDER_QWEN
            R.id.rb_ernie -> AiConfiguration.PROVIDER_ERNIE
            else -> AiConfiguration.PROVIDER_GEMINI
        }
        val apiKey = binding.etApiKey.text.toString().trim()
        val modelName = binding.etModelName.text.toString().trim()

        // Preserve other API keys
        currentConfig = when (providerId) {
            AiConfiguration.PROVIDER_GEMINI -> currentConfig.copy(
                activeProviderId = providerId,
                geminiApiKey = apiKey
            )
            AiConfiguration.PROVIDER_DEEPSEEK -> currentConfig.copy(
                activeProviderId = providerId,
                deepseekApiKey = apiKey,
                deepseekModelName = modelName.ifBlank { "deepseek-chat" }
            )
            AiConfiguration.PROVIDER_QWEN -> currentConfig.copy(
                activeProviderId = providerId,
                qwenApiKey = apiKey,
                qwenModelName = modelName.ifBlank { "qwen-turbo" }
            )
            AiConfiguration.PROVIDER_ERNIE -> currentConfig.copy(
                activeProviderId = providerId,
                ernieApiKey = apiKey,
                ernieModelName = modelName.ifBlank { "ernie-speed-8k" }
            )
            else -> currentConfig
        }

        AiConfiguration.save(requireContext(), currentConfig)
        Toast.makeText(requireContext(), "AI配置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun setupMuseumList() {
        binding.rvMuseums.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMuseums.adapter = MuseumAdapter(
            museums = ChinaMuseums.MUSEUMS,
            onClick = { museum ->
                val bundle = Bundle().apply {
                    putInt("museum_id", museum.id)
                }
                findNavController().navigate(R.id.action_home_to_museum_detail, bundle)
            }
        )
    }

    /** Set the rank number on each museum card. */
    private fun updateMuseumRanks() {
        val adapter = binding.rvMuseums.adapter
        if (adapter != null) {
            for (i in 0 until adapter.itemCount) {
                val holder = binding.rvMuseums.findViewHolderForAdapterPosition(i)
                val rankView = holder?.itemView?.findViewById<android.widget.TextView>(
                    com.example.museumguide.R.id.museum_rank
                )
                rankView?.text = "${i + 1}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh config in case it was changed elsewhere
        currentConfig = AiConfiguration.load(requireContext())
        applyConfigToUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
