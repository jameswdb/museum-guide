package com.example.museumguide.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AiTourGuideService] — focusing on fallback behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiTourGuideServiceTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── No API key tests ──────────────────────────────────────────

    @Test
    fun `empty api key returns fallback with no network call`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescription("vase")

        assertNotNull(response)
        assertTrue(response.description.isNotBlank())
        // Fallback description should be in Chinese
        assertTrue(response.description.contains("陶瓷") || response.description.contains("器皿"))
    }

    @Test
    fun `fallback for unknown label returns generic description`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescription("unknown_artifact_xyz")

        assertNotNull(response)
        assertTrue(response.description.contains("unknown_artifact_xyz") || response.description.contains("博物馆藏品"))
    }

    @Test
    fun `fallback label vase contains chinese text about porcelain`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescription("vase")
        assertTrue(
            "vase fallback should mention ceramic/porcelain",
            response.description.contains("陶瓷") || response.description.contains("瓷")
        )
    }

    @Test
    fun `fallback label bronze contains chinese text about bronze`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescription("bronze")
        assertTrue(
            "bronze fallback should mention bronze",
            response.description.contains("青铜")
        )
    }

    @Test
    fun `fallback label jade contains chinese text about jade`() = runBlocking {
        val service = AiTourGuideService()
        val response = service.generateDescription("jade")
        assertTrue(
            "jade fallback should mention jade",
            response.description.contains("玉")
        )
    }

    // ── Cache tests ───────────────────────────────────────────────

    @Test
    fun `same label returns cached result on second call`() = runBlocking {
        val service = AiTourGuideService()
        val first = service.generateDescription("vase")
        val second = service.generateDescription("vase")

        assertNotNull(first)
        assertNotNull(second)
        // Same instance from cache
        assertEquals(first, second)
    }

    @Test
    fun `different labels return different cached responses`() = runBlocking {
        val service = AiTourGuideService()
        val vase = service.generateDescription("vase")
        val bronze = service.generateDescription("bronze")

        assertNotEquals(vase.title, bronze.title)
        assertNotEquals(vase.description, bronze.description)
    }

    @Test
    fun `clear cache forces fresh fallback generation`() = runBlocking {
        val service = AiTourGuideService()
        service.generateDescription("vase")
        service.clearCache()
        // After clearing cache, should still produce fallback text
        val second = service.generateDescription("vase")
        assertNotNull(second)
        assertTrue(second.description.isNotBlank())
    }

    // ── Concurrent access tests ───────────────────────────────────

    @Test
    fun `concurrent access is thread safe`() = runBlocking {
        val service = AiTourGuideService()
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
        val service = AiTourGuideService()

        // Launch many concurrent requests for the same label
        val results = (1..10).map {
            async { service.generateDescription("vase") }
        }.awaitAll()

        // All should return the same cached instance
        val first = results.first()
        results.forEach { assertEquals(first, it) }
    }
}
