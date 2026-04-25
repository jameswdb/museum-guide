package com.example.museumguide.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for [Exhibit] entities.
 */
@Dao
interface ExhibitDao {

    @Query("SELECT * FROM exhibits ORDER BY name ASC")
    suspend fun getAllExhibits(): List<Exhibit>

    @Query("SELECT * FROM exhibits WHERE exhibitCode = :code LIMIT 1")
    suspend fun getExhibitByCode(code: String): Exhibit?

    @Query("SELECT * FROM exhibits WHERE id = :id LIMIT 1")
    suspend fun getExhibitById(id: Long): Exhibit?

    @Query("SELECT * FROM exhibits WHERE hallId = :hallId ORDER BY name ASC")
    suspend fun getExhibitsByHall(hallId: String): List<Exhibit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exhibits: List<Exhibit>)

    @Query("DELETE FROM exhibits")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM exhibits")
    suspend fun getCount(): Int
}
