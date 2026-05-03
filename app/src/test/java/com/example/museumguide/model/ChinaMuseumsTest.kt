package com.example.museumguide.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChinaMuseums] embedded data integrity.
 * Validates that all museum data is complete and consistent.
 */
class ChinaMuseumsTest {

    @Test
    fun `MUSEUMS contains exactly 10 museums`() {
        assertEquals("China should have exactly 10 museums in the list", 10, ChinaMuseums.MUSEUMS.size)
    }

    @Test
    fun `all museums have at least 3 exhibits each`() {
        ChinaMuseums.MUSEUMS.forEach { museum ->
            assertTrue(
                "${museum.name} should have at least 3 exhibits, but has ${museum.exhibits.size}",
                museum.exhibits.size >= 3
            )
        }
    }

    @Test
    fun `all exhibits have non-empty description`() {
        ChinaMuseums.MUSEUMS.forEach { museum ->
            museum.exhibits.forEach { exhibit ->
                assertNotNull("Exhibit '${exhibit.name}' in ${museum.name} should not have null description", exhibit.description)
                assertTrue(
                    "Exhibit '${exhibit.name}' in ${museum.name} should have non-blank description",
                    exhibit.description.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `all museums have non-empty name and city`() {
        ChinaMuseums.MUSEUMS.forEach { museum ->
            assertTrue("Museum name should not be blank", museum.name.isNotBlank())
            assertTrue("Museum city should not be blank", museum.city.isNotBlank())
            assertTrue("Museum brief should not be blank", museum.brief.isNotBlank())
            assertTrue("Museum description should not be blank", museum.description.isNotBlank())
        }
    }

    @Test
    fun `all coordinates in reasonable range for Chinese museums`() {
        ChinaMuseums.MUSEUMS.forEach { museum ->
            assertTrue(
                "${museum.name} latitude ${museum.latitude} should be in China range (20-45)",
                museum.latitude >= 20.0 && museum.latitude <= 45.0
            )
            assertTrue(
                "${museum.name} longitude ${museum.longitude} should be in China range (100-125)",
                museum.longitude >= 100.0 && museum.longitude <= 125.0
            )
        }
    }

    @Test
    fun `every museum has unique id`() {
        val ids = ChinaMuseums.MUSEUMS.map { it.id }
        assertEquals("Museum IDs should be unique", ids.size, ids.distinct().size)
    }

    @Test
    fun `all exhibits have non-empty name and era`() {
        ChinaMuseums.MUSEUMS.forEach { museum ->
            museum.exhibits.forEach { exhibit ->
                assertTrue("Exhibit name in ${museum.name} should not be blank", exhibit.name.isNotBlank())
                assertTrue("Exhibit era in ${museum.name} should not be blank", exhibit.era.isNotBlank())
            }
        }
    }
}
