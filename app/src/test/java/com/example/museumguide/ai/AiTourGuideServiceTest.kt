package com.example.museumguide.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AiTourGuideService].
 *
 * Tests cover:
 * - Fallback behaviour with empty API key (no network needed)
 * - In-memory caching (same label → cached result)
 * - Label-specific fallback descriptions in Chinese
 * - Gemini API response parsing via MockWebServer
 * - HTTP error handling
 * - Cache clear
 * - Concurrent access safety
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiTourGuideServiceTest {

    private var mockWebServer: MockWebServer? = null

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

    // ── No API key tests ──────────────────────────────────────────

    @Test
    fun `empty api key returns fallback with no network call`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val response = service.generateDescription("vase")

        assertNotNull(response)
        assertTrue(response.description.isNotBlank())
        // Fallback description should be in Chinese
        assertTrue(response.description.contains("陶瓷") || response.description.contains("器皿"))
    }

    @Test
    fun `fallback for unknown label returns generic description`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val response = service.generateDescription("unknown_artifact_xyz")

        assertNotNull(response)
        assertTrue(response.description.contains("unknown_artifact_xyz") || response.description.contains("博物馆藏品"))
    }

    @Test
    fun `fallback label vase contains chinese text about porcelain`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val response = service.generateDescription("vase")
        assertTrue(
            "vase fallback should mention ceramic/porcelain",
            response.description.contains("陶瓷") || response.description.contains("瓷")
        )
    }

    @Test
    fun `fallback label bronze contains chinese text about bronze`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val response = service.generateDescription("bronze")
        assertTrue(
            "bronze fallback should mention bronze",
            response.description.contains("青铜")
        )
    }

    @Test
    fun `fallback label jade contains chinese text about jade`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val response = service.generateDescription("jade")
        assertTrue(
            "jade fallback should mention jade",
            response.description.contains("玉")
        )
    }

    // ── Cache tests ───────────────────────────────────────────────

    @Test
    fun `same label returns cached result on second call`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val first = service.generateDescription("vase")
        val second = service.generateDescription("vase")

        assertNotNull(first)
        assertNotNull(second)
        // Same instance from cache
        assertEquals(first, second)
    }

    @Test
    fun `different labels return different cached responses`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val vase = service.generateDescription("vase")
        val bronze = service.generateDescription("bronze")

        assertNotEquals(vase.title, bronze.title)
        assertNotEquals(vase.description, bronze.description)
    }

    @Test
    fun `clear cache forces fresh fallback generation`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        service.generateDescription("vase")
        service.clearCache()
        // After clearing cache, should still produce fallback text
        val second = service.generateDescription("vase")
        assertNotNull(second)
        assertTrue(second.description.isNotBlank())
    }

    // ── MockWebServer tests (simulated Gemini API) ────────────────

    @Test
    fun `valid json response from api is parsed correctly`() = runBlocking {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        // Build the response with properly escaped inner JSON
        val innerJson = """{"title": "青花瓷瓶", "brief": "明代青花瓷精品", "description": "这是一件精美的青花瓷瓶，釉色莹润。", "significance": "青花瓷是中国陶瓷的代表。"}"""
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

        val response = service.generateDescription("vase")
        assertEquals("青花瓷瓶", response.title)
        assertTrue(response.description.contains("青花瓷瓶"))
    }

    @Test
    fun `api returns plain text without json structure`() = runBlocking {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val responseBody = """{"candidates":[{"content":{"parts":[{"text":"这是一件古代文物，具有重要的历史价值。"}]}}]}"""

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
        )

        val service = AiTourGuideService(
            apiKey = "test-key",
            endpointBaseUrl = server.url("").toString().trimEnd('/')
        )

        val response = service.generateDescription("vase")
        // Should fall back to using raw text as description
        assertNotNull(response)
        assertTrue(response.description.isNotBlank())
    }

    @Test
    fun `http error returns fallback response`() = runBlocking {
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

        val response = service.generateDescription("vase")
        // Should return fallback with error hint
        assertNotNull(response)
        assertTrue(response.description.isNotBlank())
    }

    @Test
    fun `api returns empty candidates list`() = runBlocking {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"candidates": []}""")
        )

        val service = AiTourGuideService(
            apiKey = "test-key",
            endpointBaseUrl = server.url("").toString().trimEnd('/')
        )

        val response = service.generateDescription("vase")
        assertNotNull(response)
        assertTrue(response.description.isNotBlank())
    }

    // ── Concurrent access tests ───────────────────────────────────

    @Test
    fun `concurrent access is thread safe`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")
        val labels = listOf("vase", "bronze", "jade", "clock", "book", "tv")

        // Launch concurrent requests for all labels
        val results = labels.map { label ->
            async {
                service.generateDescription(label)
            }
        }.awaitAll()

        assertEquals(labels.size, results.size)
        results.forEach { assertNotNull(it) }
        // Verify each label produced a unique description
        val descriptions = results.map { it.description }
        assertEquals(descriptions.size, descriptions.distinct().size)
    }

    @Test
    fun `concurrent access to same label is safe`() = runBlocking {
        val service = AiTourGuideService(apiKey = "")

        // Launch many concurrent requests for the same label
        val results = (1..10).map {
            async { service.generateDescription("vase") }
        }.awaitAll()

        // All should return the same cached instance
        val first = results.first()
        results.forEach { assertEquals(first, it) }
    }
}
