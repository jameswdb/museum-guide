package com.example.museumguide.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * Unit tests for [AiTourGuideService] multimodal image recognition.
 *
 * These tests require Robolectric because they use [Bitmap.createBitmap]
 * which depends on the Android framework.
 *
 * Tests cover:
 * - Fallback with empty API key (no network needed)
 * - Valid multimodal response parsing via MockWebServer
 * - HTTP error handling during multimodal requests
 * - Request body verification (inlineData presence)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiTourGuideServiceMultimodalTest {

    private var mockWebServer: MockWebServer? = null
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
        mockWebServer?.shutdown()
        mockWebServer = null
    }

    // ── No API key test ─────────────────────────────────────────────

    @Test
    fun `empty api key returns fallback with no network call`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val response = service.generateDescriptionFromImage(testBitmap)

        assertNotNull(response)
        assertEquals("图片识别", response.title)
        assertTrue(response.description.contains("GEMINI_API_KEY"))
    }

    // ── MockWebServer tests ─────────────────────────────────────────

    @Test
    fun `valid multimodal response is parsed correctly`() = runBlocking {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val innerJson = (
            "{" +
                "\"title\": \"青花瓷瓶\"," +
                "\"brief\": \"明代青花瓷精品\"," +
                "\"description\": \"这是一件精美的青花瓷瓶，釉色莹润。\"," +
                "\"significance\": \"青花瓷是中国陶瓷的代表。\"" +
            "}"
            )
        val escapedInner = innerJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val responseBody = """{"candidates":[{"content":{"parts":[{"text":"$escapedInner"}]}}]}"""

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
        )

        val service = AiTourGuideService(
            apiKey = "test-key",
            endpointBaseUrl = server.url("").toString().trimEnd('/')
        )

        val response = service.generateDescriptionFromImage(testBitmap)

        assertEquals("青花瓷瓶", response.title)
        assertTrue(response.description.contains("青花瓷瓶"))

        // Verify the request body contained multimodal inlineData
        val recordedRequest = server.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(
            "Request should contain inlineData for multimodal",
            requestBody.contains("inlineData")
        )
        assertTrue(
            "Request should specify mimeType",
            requestBody.contains("image/jpeg")
        )
        assertTrue(
            "Request should include base64 image data",
            requestBody.contains("data")
        )
    }

    @Test
    fun `multimodal http error returns fallback response`() = runBlocking {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        server.enqueue(
            MockResponse()
                .setResponseCode(429) // Too Many Requests
                .setBody("{\"error\":{\"message\":\"Rate limit exceeded\"}}")
        )

        val service = AiTourGuideService(
            apiKey = "test-key",
            endpointBaseUrl = server.url("").toString().trimEnd('/')
        )

        val response = service.generateDescriptionFromImage(testBitmap)

        assertNotNull(response)
        assertEquals("识别失败", response.title)
        // Brief field contains the HTTP status code
        assertTrue(response.brief.contains("HTTP"))
    }

    @Test
    fun `multimodal with empty api key never calls network`() = runBlocking {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val service = AiTourGuideService(
            apiKey = "",
            endpointBaseUrl = server.url("").toString().trimEnd('/')
        )

        // Should return immediately without any network call
        val response = service.generateDescriptionFromImage(testBitmap)

        assertEquals("图片识别", response.title)
        // No request should have been made
        assertEquals(0, server.requestCount)
    }
}
