package com.example.museumguide.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [AiTourGuideService] multimodal fallback behavior.
 *
 * Tests cover:
 * - Fallback with no API key configured (no network needed)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiTourGuideServiceMultimodalTest {

    private val testBitmap: Bitmap by lazy {
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty api key returns fallback multimodal response`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescriptionFromImage(testBitmap)

        assertNotNull(response)
        assertTrue(response.description.contains("API") || response.description.contains("密钥"))
    }

    @Test
    fun `multimodal fallback shows helpful message`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescriptionFromImage(testBitmap)

        assertNotNull(response)
        // Should return guidance about configuring API
        assertTrue(response.title == "图片识别")
    }
}
