package com.example.museumguide.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeminiProvider] — identity and capability flags.
 * Actual API calls are NOT tested here (require network + API key).
 */
class GeminiProviderTest {

    private val provider = GeminiProvider()

    @Test
    fun `providerId returns gemini`() {
        assertEquals("Gemini provider ID should be 'gemini'", AiConfiguration.PROVIDER_GEMINI, provider.providerId)
    }

    @Test
    fun `supportsMultimodal returns true`() {
        assertTrue("Gemini should support multimodal (image) recognition", provider.supportsMultimodal)
    }

    @Test
    fun `displayName is not blank`() {
        assertTrue("Gemini display name should not be blank", provider.displayName.isNotBlank())
    }
}
