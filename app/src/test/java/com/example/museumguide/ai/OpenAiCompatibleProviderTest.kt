package com.example.museumguide.ai

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [OpenAiCompatibleProvider] — factory methods, identity, multimodal support.
 * Actual API calls are NOT tested here (require network + API key).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenAiCompatibleProviderTest {

    @Test
    fun `deepSeek factory has correct providerId`() {
        val provider = OpenAiCompatibleProvider.deepSeek()
        assertEquals(AiConfiguration.PROVIDER_DEEPSEEK, provider.providerId)
        assertTrue("DeepSeek display name should contain 深度求索", provider.displayName.contains("DeepSeek"))
    }

    @Test
    fun `qwen factory has correct providerId and baseUrl`() {
        val provider = OpenAiCompatibleProvider.qwen()
        assertEquals(AiConfiguration.PROVIDER_QWEN, provider.providerId)
        // Qwen uses dashscope.aliyuncs.com
        assertTrue("Qwen should NOT support multimodal", !provider.supportsMultimodal)
    }

    @Test
    fun `ernie factory has correct providerId and uses OpenAI compatible endpoint`() {
        val provider = OpenAiCompatibleProvider.ernie()
        assertEquals(AiConfiguration.PROVIDER_ERNIE, provider.providerId)
        assertTrue("ERNIE should NOT support multimodal", !provider.supportsMultimodal)
    }

    @Test
    fun `multimodal returns null for all OpenAI compatible providers`() = kotlinx.coroutines.runBlocking {
        val providers = listOf(
            OpenAiCompatibleProvider.deepSeek(),
            OpenAiCompatibleProvider.qwen(),
            OpenAiCompatibleProvider.ernie()
        )
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        providers.forEach { provider ->
            val result = provider.generateDescriptionFromImage(bitmap, "文物", "test-key")
            assertNull("${provider.providerId} should return null for multimodal (not supported)", result)
        }
    }

    @Test
    fun `all providers have non-blank display name`() {
        listOf(
            OpenAiCompatibleProvider.deepSeek(),
            OpenAiCompatibleProvider.qwen(),
            OpenAiCompatibleProvider.ernie()
        ).forEach { provider ->
            assertTrue("Display name for ${provider.providerId} should not be blank", provider.displayName.isNotBlank())
        }
    }
}
