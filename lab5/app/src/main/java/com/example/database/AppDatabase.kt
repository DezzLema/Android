// AppDatabase.kt
package com.example.database

import android.content.Context
import androidx.room.*
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GameRecord::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class) // Добавьте эту аннотацию
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameResultDao(): GameResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "game_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}