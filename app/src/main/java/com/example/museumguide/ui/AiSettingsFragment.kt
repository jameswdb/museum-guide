package com.example.museumguide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.museumguide.R
import com.example.museumguide.ai.AiConfiguration
import com.example.museumguide.databinding.FragmentAiSettingsBinding

/**
 * AI model configuration page.
 * Allows users to select provider, input API keys, and customise model names.
 */
class AiSettingsFragment : Fragment() {

    private var _binding: FragmentAiSettingsBinding? = null
    private val binding get() = _binding!!
    private var currentConfig: AiConfiguration = AiConfiguration()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentConfig = AiConfiguration.load(requireContext())
        applyConfig()

        binding.rgAiProvider.setOnCheckedChangeListener { _, checkedId ->
            updateApiKeyHint(checkedId)
            updateModelNameVisibility(checkedId)
        }

        binding.btnSaveAiConfig.setOnClickListener { saveConfig() }
    }

    private fun applyConfig() {
        val radioId = when (currentConfig.activeProviderId) {
            AiConfiguration.PROVIDER_GEMINI -> R.id.rb_gemini
            AiConfiguration.PROVIDER_DEEPSEEK -> R.id.rb_deepseek
            AiConfiguration.PROVIDER_QWEN -> R.id.rb_qwen
            AiConfiguration.PROVIDER_ERNIE -> R.id.rb_ernie
            else -> R.id.rb_gemini
        }
        binding.rgAiProvider.check(radioId)

        when (currentConfig.activeProviderId) {
            AiConfiguration.PROVIDER_GEMINI -> binding.etApiKey.setText(currentConfig.geminiApiKey)
            AiConfiguration.PROVIDER_DEEPSEEK -> binding.etApiKey.setText(currentConfig.deepseekApiKey)
            AiConfiguration.PROVIDER_QWEN -> binding.etApiKey.setText(currentConfig.qwenApiKey)
            AiConfiguration.PROVIDER_ERNIE -> binding.etApiKey.setText(currentConfig.ernieApiKey)
        }

        when (currentConfig.activeProviderId) {
            AiConfiguration.PROVIDER_DEEPSEEK -> binding.etModelName.setText(currentConfig.deepseekModelName)
            AiConfiguration.PROVIDER_QWEN -> binding.etModelName.setText(currentConfig.qwenModelName)
            AiConfiguration.PROVIDER_ERNIE -> binding.etModelName.setText(currentConfig.ernieModelName)
            else -> binding.etModelName.setText("")
        }

        updateModelNameVisibility(radioId)
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

    private fun updateModelNameVisibility(checkedId: Int) {
        val isGemini = checkedId == R.id.rb_gemini
        binding.modelNameInputLayout.visibility = if (isGemini) View.GONE else View.VISIBLE
    }

    private fun saveConfig() {
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

        currentConfig = when (providerId) {
            AiConfiguration.PROVIDER_GEMINI -> currentConfig.copy(
                activeProviderId = providerId, geminiApiKey = apiKey
            )
            AiConfiguration.PROVIDER_DEEPSEEK -> currentConfig.copy(
                activeProviderId = providerId, deepseekApiKey = apiKey,
                deepseekModelName = modelName.ifBlank { "deepseek-chat" }
            )
            AiConfiguration.PROVIDER_QWEN -> currentConfig.copy(
                activeProviderId = providerId, qwenApiKey = apiKey,
                qwenModelName = modelName.ifBlank { "qwen-turbo" }
            )
            AiConfiguration.PROVIDER_ERNIE -> currentConfig.copy(
                activeProviderId = providerId, ernieApiKey = apiKey,
                ernieModelName = modelName.ifBlank { "ernie-speed-8k" }
            )
            else -> currentConfig
        }

        AiConfiguration.save(requireContext(), currentConfig)
        Toast.makeText(requireContext(), "AI配置已保存", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
