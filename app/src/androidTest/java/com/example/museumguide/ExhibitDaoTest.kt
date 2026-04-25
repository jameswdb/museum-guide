package com.example.museumguide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.museumguide.model.Exhibit
import com.example.museumguide.model.ExhibitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExhibitDaoTest {

    private lateinit var database: ExhibitDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ExhibitDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAllAndGetAllExhibits() = runBlocking {
        val exhibits = listOf(
            Exhibit(exhibitCode = "A01", name = "青铜鼎", era = "商代", brief = "商代青铜鼎", description = "desc", significance = "sig", hallId = "main"),
            Exhibit(exhibitCode = "B02", name = "唐三彩", era = "唐代", brief = "唐代唐三彩", description = "desc", significance = "sig", hallId = "hall2"),
            Exhibit(exhibitCode = "C03", name = "青花瓷", era = "明代", brief = "明代青花瓷", description = "desc", significance = "sig", hallId = "hall3")
        )
        database.exhibitDao().insertAll(exhibits)

        val all = database.exhibitDao().getAllExhibits()
        assertEquals(3, all.size)
    }

    @Test
    fun getExhibitByCodeReturnsCorrectExhibit() = runBlocking {
        val exhibit = Exhibit(exhibitCode = "A01", name = "青铜鼎", era = "商代", brief = "商代青铜鼎", description = "desc", significance = "sig", hallId = "main")
        database.exhibitDao().insertAll(listOf(exhibit))

        val result = database.exhibitDao().getExhibitByCode("A01")
        assertNotNull(result)
        assertEquals("青铜鼎", result!!.name)
        assertEquals("商代", result.era)
    }

    @Test
    fun getExhibitByCodeWithNonexistentCodeReturnsNull() = runBlocking {
        val result = database.exhibitDao().getExhibitByCode("NONEXISTENT")
        assertNull(result)
    }

    @Test
    fun clearAllEmptiesDatabase() = runBlocking {
        val exhibits = listOf(
            Exhibit(exhibitCode = "A01", name = "青铜鼎", era = "商代", brief = "商代青铜鼎", description = "desc", significance = "sig", hallId = "main"),
            Exhibit(exhibitCode = "B02", name = "唐三彩", era = "唐代", brief = "唐代唐三彩", description = "desc", significance = "sig", hallId = "hall2")
        )
        database.exhibitDao().insertAll(exhibits)
        assertEquals(2, database.exhibitDao().getCount())

        database.exhibitDao().deleteAll()
        assertTrue(database.exhibitDao().getAllExhibits().isEmpty())
    }

    @Test
    fun getExhibitByIdReturnsCorrectExhibit() = runBlocking {
        val exhibit = Exhibit(exhibitCode = "A01", name = "青铜鼎", era = "商代", brief = "商代青铜鼎", description = "desc", significance = "sig", hallId = "main")
        val id = database.exhibitDao().insertAll(listOf(exhibit))

        val result = database.exhibitDao().getExhibitById(1L)
        assertNotNull(result)
        assertEquals("A01", result!!.exhibitCode)
        assertEquals("青铜鼎", result.name)
    }
}
