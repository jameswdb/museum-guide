package com.example.museumguide.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration for the AI tour guide service.
 * Supports multiple AI model providers with user-configurable API keys.
 */
data class AiConfiguration(
    /** Currently selected provider ID: "gemini", "deepseek", "qwen", "ernie" */
    val activeProviderId: String = PROVIDER_GEMINI,
    val geminiApiKey: String = "",
    val deepseekApiKey: String = "",
    val deepseekModelName: String = "deepseek-chat",
    val qwenApiKey: String = "",
    val qwenModelName: String = "qwen-turbo",
    val ernieApiKey: String = "",
    val ernieModelName: String = "ernie-speed-8k",
    /** User-configured or GPS-detected museum location */
    val museumCity: String = "北京",
    val museumLatitude: Double = 39.9042,
    val museumLongitude: Double = 116.4074
) {
    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_QWEN = "qwen"
        const val PROVIDER_ERNIE = "ernie"

        private const val PREFS_NAME = "ai_config"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_DEEPSEEK_KEY = "deepseek_api_key"
        private const val KEY_DEEPSEEK_MODEL = "deepseek_model"
        private const val KEY_QWEN_KEY = "qwen_api_key"
        private const val KEY_QWEN_MODEL = "qwen_model"
        private const val KEY_ERNIE_KEY = "ernie_api_key"
        private const val KEY_ERNIE_MODEL = "ernie_model"
        private const val KEY_MUSEUM_CITY = "museum_city"
        private const val KEY_MUSEUM_LAT = "museum_lat"
        private const val KEY_MUSEUM_LNG = "museum_lng"

        /** All available providers with their display info. */
        val AVAILABLE_PROVIDERS = listOf(
            ProviderInfo(PROVIDER_GEMINI, "Google Gemini 2.0 Flash", supportsMultimodal = true),
            ProviderInfo(PROVIDER_DEEPSEEK, "DeepSeek (深度求索)", supportsMultimodal = false),
            ProviderInfo(PROVIDER_QWEN, "通义千问 Qwen (阿里云)", supportsMultimodal = false),
            ProviderInfo(PROVIDER_ERNIE, "文心一言 ERNIE (百度)", supportsMultimodal = false)
        )

        /** Load configuration from SharedPreferences. */
        fun load(context: Context): AiConfiguration {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AiConfiguration(
                activeProviderId = prefs.getString(KEY_ACTIVE_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI,
                geminiApiKey = prefs.getString(KEY_GEMINI_KEY, "") ?: "",
                deepseekApiKey = prefs.getString(KEY_DEEPSEEK_KEY, "") ?: "",
                deepseekModelName = prefs.getString(KEY_DEEPSEEK_MODEL, "deepseek-chat") ?: "deepseek-chat",
                qwenApiKey = prefs.getString(KEY_QWEN_KEY, "") ?: "",
                qwenModelName = prefs.getString(KEY_QWEN_MODEL, "qwen-turbo") ?: "qwen-turbo",
                ernieApiKey = prefs.getString(KEY_ERNIE_KEY, "") ?: "",
                ernieModelName = prefs.getString(KEY_ERNIE_MODEL, "ernie-speed-8k") ?: "ernie-speed-8k",
                museumCity = prefs.getString(KEY_MUSEUM_CITY, "北京") ?: "北京",
                museumLatitude = prefs.getFloat(KEY_MUSEUM_LAT, 39.9042f).toDouble(),
                museumLongitude = prefs.getFloat(KEY_MUSEUM_LNG, 116.4074f).toDouble()
            )
        }

        /** Save configuration to SharedPreferences. */
        fun save(context: Context, config: AiConfiguration) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_ACTIVE_PROVIDER, config.activeProviderId)
                .putString(KEY_GEMINI_KEY, config.geminiApiKey)
                .putString(KEY_DEEPSEEK_KEY, config.deepseekApiKey)
                .putString(KEY_DEEPSEEK_MODEL, config.deepseekModelName)
                .putString(KEY_QWEN_KEY, config.qwenApiKey)
                .putString(KEY_QWEN_MODEL, config.qwenModelName)
                .putString(KEY_ERNIE_KEY, config.ernieApiKey)
                .putString(KEY_ERNIE_MODEL, config.ernieModelName)
                .putString(KEY_MUSEUM_CITY, config.museumCity)
                .putFloat(KEY_MUSEUM_LAT, config.museumLatitude.toFloat())
                .putFloat(KEY_MUSEUM_LNG, config.museumLongitude.toFloat())
                .apply()
        }

        /** A provider already configured (has API key). */
        fun isProviderConfigured(config: AiConfiguration, providerId: String): Boolean {
            return when (providerId) {
                PROVIDER_GEMINI -> config.geminiApiKey.isNotBlank()
                PROVIDER_DEEPSEEK -> config.deepseekApiKey.isNotBlank()
                PROVIDER_QWEN -> config.qwenApiKey.isNotBlank()
                PROVIDER_ERNIE -> config.ernieApiKey.isNotBlank()
                else -> false
            }
        }

        /** Get the API key for the active provider. */
        fun getActiveApiKey(config: AiConfiguration): String {
            return when (config.activeProviderId) {
                PROVIDER_GEMINI -> config.geminiApiKey
                PROVIDER_DEEPSEEK -> config.deepseekApiKey
                PROVIDER_QWEN -> config.qwenApiKey
                PROVIDER_ERNIE -> config.ernieApiKey
                else -> ""
            }
        }
    }

    data class ProviderInfo(
        val id: String,
        val displayName: String,
        val supportsMultimodal: Boolean
    )
}
