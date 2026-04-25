package com.example.museumguide.exhibit

import com.example.museumguide.model.Exhibit
import com.example.museumguide.model.ExhibitDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExhibitRepositoryTest {

    private lateinit var exhibitDao: ExhibitDao
    private lateinit var repository: ExhibitRepository

    @Before
    fun setUp() {
        exhibitDao = mock()
        repository = ExhibitRepository(exhibitDao)
    }

    @Test
    fun `findExhibitByLabel with known label vase returns exhibit`() = runTest {
        val exhibit = Exhibit(
            exhibitCode = "vase", name = "青花瓷瓶", era = "明代 · 永乐年间",
            brief = "明代永乐青花瓷，纹饰精美，釉色莹润",
            description = "desc", significance = "sig"
        )
        whenever(exhibitDao.getExhibitByCode("vase")).thenReturn(exhibit)

        val result = repository.findExhibitByLabel("vase")

        assertEquals(exhibit, result)
        verify(exhibitDao).getExhibitByCode("vase")
    }

    @Test
    fun `findExhibitByLabel with unknown label returns null`() = runTest {
        val result = repository.findExhibitByLabel("unknown_label")

        assertNull(result)
        verify(exhibitDao, never()).getExhibitByCode(any())
    }

    @Test
    fun `getExhibitById with valid id returns exhibit`() = runTest {
        val exhibit = Exhibit(
            id = 1L, exhibitCode = "bronze", name = "青铜鼎", era = "西周 · 早期",
            brief = "西周青铜鼎，造型庄重，铭文珍贵",
            description = "desc", significance = "sig"
        )
        whenever(exhibitDao.getExhibitById(1L)).thenReturn(exhibit)

        val result = repository.getExhibitById(1L)

        assertEquals(exhibit, result)
        verify(exhibitDao).getExhibitById(1L)
    }

    @Test
    fun `getExhibitById with invalid id returns null`() = runTest {
        whenever(exhibitDao.getExhibitById(999L)).thenReturn(null)

        val result = repository.getExhibitById(999L)

        assertNull(result)
        verify(exhibitDao).getExhibitById(999L)
    }

    @Test
    fun `seedSampleData inserts 6 exhibits when database is empty`() = runTest {
        whenever(exhibitDao.getCount()).thenReturn(0)

        repository.seedSampleData()

        verify(exhibitDao).getCount()
        val captor = argumentCaptor<List<Exhibit>>()
        verify(exhibitDao).insertAll(captor.capture())
        assertEquals(6, captor.firstValue.size)
    }

    @Test
    fun `seedSampleData does nothing when database already has data`() = runTest {
        whenever(exhibitDao.getCount()).thenReturn(5)

        repository.seedSampleData()

        verify(exhibitDao).getCount()
        verify(exhibitDao, never()).insertAll(any())
    }

    @Test
    fun `LABEL_TO_CODE maps known labels to correct exhibit codes`() = runTest {
        val expectedMappings = mapOf(
            "vase" to "vase",
            "bottle" to "vase_2",
            "scissors" to "bronze",
            "clock" to "jade",
            "tv" to "painting",
            "potted plant" to "ceramic"
        )

        for ((label, code) in expectedMappings) {
            val exhibit = Exhibit(
                exhibitCode = code, name = "test", era = "test",
                brief = "b", description = "d", significance = "s"
            )
            whenever(exhibitDao.getExhibitByCode(code)).thenReturn(exhibit)

            val result = repository.findExhibitByLabel(label)

            assertNotNull("Expected non-null for label '$label'", result)
            assertEquals(code, result!!.exhibitCode)
            verify(exhibitDao).getExhibitByCode(code)
        }
    }
}
