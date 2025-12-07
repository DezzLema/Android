// app/src/main/java/com/example/lab4/database/GameResultDao.kt
package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(gameRecord: GameRecord): Long

    @Query("SELECT * FROM game_records ORDER BY score DESC")
    fun getAllRecords(): Flow<List<GameRecord>>

    @Query("SELECT * FROM game_records ORDER BY score DESC LIMIT :limit")
    suspend fun getTopRecords(limit: Int): List<GameRecord>

    @Query("SELECT * FROM game_records WHERE player_name = :name ORDER BY score DESC")
    fun getPlayerRecords(name: String): Flow<List<GameRecord>>

    @Query("DELETE FROM game_records")
    suspend fun clearAll()

    @Query("SELECT MAX(score) FROM game_records")
    suspend fun getMaxScore(): Int?

    @Query("SELECT AVG(score) FROM game_records")
    suspend fun getAverageScore(): Float?
}