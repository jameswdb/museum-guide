package com.example.museumguide.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [AiConfiguration] — default values, persistence, provider info.
 *
 * Uses Robolectric for SharedPreferences access.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiConfigurationTest {

    @Before
    fun setUp() {
        // Clear SharedPreferences before each test to ensure isolation
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ai_config", 0).edit().clear().apply()
    }

    @Test
    fun `default config uses Gemini provider`() {
        val config = AiConfiguration()
        assertEquals("Default provider should be Gemini", AiConfiguration.PROVIDER_GEMINI, config.activeProviderId)
    }

    @Test
    fun `save config then load all fields match`() {
        val context = RuntimeEnvironment.getApplication()
        val original = AiConfiguration(
            activeProviderId = AiConfiguration.PROVIDER_DEEPSEEK,
            geminiApiKey = "gemini-key-123",
            deepseekApiKey = "deepseek-key-456",
            deepseekModelName = "deepseek-chat",
            qwenApiKey = "qwen-key-789",
            qwenModelName = "qwen-turbo",
            ernieApiKey = "ernie-key-000",
            ernieModelName = "ernie-speed-8k",
            museumCity = "西安",
            museumLatitude = 34.2025,
            museumLongitude = 108.9465
        )

        AiConfiguration.save(context, original)
        val loaded = AiConfiguration.load(context)

        assertEquals(original.activeProviderId, loaded.activeProviderId)
        assertEquals(original.geminiApiKey, loaded.geminiApiKey)
        assertEquals(original.deepseekApiKey, loaded.deepseekApiKey)
        assertEquals(original.deepseekModelName, loaded.deepseekModelName)
        assertEquals(original.qwenApiKey, loaded.qwenApiKey)
        assertEquals(original.qwenModelName, loaded.qwenModelName)
        assertEquals(original.ernieApiKey, loaded.ernieApiKey)
        assertEquals(original.ernieModelName, loaded.ernieModelName)
        assertEquals(original.museumCity, loaded.museumCity)
        assertEquals(original.museumLatitude, loaded.museumLatitude, 0.001)
        assertEquals(original.museumLongitude, loaded.museumLongitude, 0.001)
    }

    @Test
    fun `AVAILABLE_PROVIDERS has exactly 4 entries with non-null fields`() {
        val providers = AiConfiguration.AVAILABLE_PROVIDERS
        assertEquals("Should have exactly 4 providers", 4, providers.size)

        val ids = providers.map { it.id }.toSet()
        assertTrue("Should include Gemini", ids.contains(AiConfiguration.PROVIDER_GEMINI))
        assertTrue("Should include DeepSeek", ids.contains(AiConfiguration.PROVIDER_DEEPSEEK))
        assertTrue("Should include Qwen", ids.contains(AiConfiguration.PROVIDER_QWEN))
        assertTrue("Should include ERNIE", ids.contains(AiConfiguration.PROVIDER_ERNIE))

        providers.forEach { info ->
            assertNotNull("Provider ID should not be null for ${info.displayName}", info.id)
            assertNotNull("Display name should not be null for ${info.id}", info.displayName)
            assertTrue("Display name should not be blank for ${info.id}", info.displayName.isNotBlank())
        }
    }

    @Test
    fun `isProviderConfigured returns correct true for configured providers`() {
        val config = AiConfiguration(
            geminiApiKey = "g-key",
            deepseekApiKey = "d-key",
            qwenApiKey = "q-key",
            ernieApiKey = "e-key"
        )

        assertTrue("Gemini should be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_GEMINI))
        assertTrue("DeepSeek should be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_DEEPSEEK))
        assertTrue("Qwen should be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_QWEN))
        assertTrue("ERNIE should be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_ERNIE))
    }

    @Test
    fun `isProviderConfigured returns false for unconfigured providers`() {
        val config = AiConfiguration() // all keys empty

        assertFalse("Gemini should NOT be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_GEMINI))
        assertFalse("DeepSeek should NOT be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_DEEPSEEK))
        assertFalse("Qwen should NOT be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_QWEN))
        assertFalse("ERNIE should NOT be configured", AiConfiguration.isProviderConfigured(config, AiConfiguration.PROVIDER_ERNIE))
    }

    @Test
    fun `getActiveApiKey returns correct key for active provider`() {
        val config = AiConfiguration(
            activeProviderId = AiConfiguration.PROVIDER_QWEN,
            geminiApiKey = "gemini-key",
            deepseekApiKey = "deepseek-key",
            qwenApiKey = "qwen-key",
            ernieApiKey = "ernie-key"
        )

        assertEquals("Should return qwen-key when Qwen is active", "qwen-key", AiConfiguration.getActiveApiKey(config))

        // Switch to Gemini
        val geminiConfig = config.copy(activeProviderId = AiConfiguration.PROVIDER_GEMINI)
        assertEquals("Should return gemini-key when Gemini is active", "gemini-key", AiConfiguration.getActiveApiKey(geminiConfig))

        // Switch to unknown provider
        val unknownConfig = config.copy(activeProviderId = "unknown")
        assertEquals("Should return empty string for unknown provider", "", AiConfiguration.getActiveApiKey(unknownConfig))
    }

    @Test
    fun `empty config after load returns defaults`() {
        val context = RuntimeEnvironment.getApplication()
        // Clear any pre-existing config
        context.getSharedPreferences("ai_config", 0).edit().clear().apply()

        val config = AiConfiguration.load(context)
        assertEquals(AiConfiguration.PROVIDER_GEMINI, config.activeProviderId)
        assertEquals("", config.geminiApiKey)
        assertEquals("", config.deepseekApiKey)
        assertEquals("deepseek-chat", config.deepseekModelName)
        assertEquals("", config.qwenApiKey)
        assertEquals("qwen-turbo", config.qwenModelName)
        assertEquals("", config.ernieApiKey)
        assertEquals("ernie-speed-8k", config.ernieModelName)
        assertEquals("北京", config.museumCity)
        assertEquals(39.9042, config.museumLatitude, 0.001)
        assertEquals(116.4074, config.museumLongitude, 0.001)
    }
}
