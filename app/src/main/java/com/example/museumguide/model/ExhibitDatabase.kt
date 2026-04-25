package com.example.museumguide.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database holding all museum exhibit metadata.
 *
 * Created with a singleton pattern so only one instance exists
 * across the application lifecycle.
 */
@Database(entities = [Exhibit::class], version = 1, exportSchema = false)
abstract class ExhibitDatabase : RoomDatabase() {

    abstract fun exhibitDao(): ExhibitDao

    companion object {
        @Volatile
        private var INSTANCE: ExhibitDatabase? = null

        fun getInstance(context: Context): ExhibitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExhibitDatabase::class.java,
                    "museum_guide.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
