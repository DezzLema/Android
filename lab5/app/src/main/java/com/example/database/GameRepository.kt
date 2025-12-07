// app/src/main/java/com/example/lab4/database/GameRepository.kt
package com.example.database

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(context: Context) {
    private val gameResultDao = AppDatabase.getInstance(context).gameResultDao()

    suspend fun addGameResult(
        playerName: String,
        score: Int,
        level: Int,
        timeSeconds: Int
    ): Long {
        return withContext(Dispatchers.IO) {
            val record = GameRecord(
                playerName = playerName,
                score = score,
                level = level,
                timeSeconds = timeSeconds
            )
            gameResultDao.insert(record)
        }
    }

    fun getAllRecords(): Flow<List<GameRecord>> {
        return gameResultDao.getAllRecords()
    }

    suspend fun getTop10Records(): List<GameRecord> {
        return withContext(Dispatchers.IO) {
            gameResultDao.getTopRecords(10)
        }
    }

    suspend fun getPlayerRecords(name: String): List<GameRecord> {
        return withContext(Dispatchers.IO) {
            // Преобразуем Flow в List
            var result: List<GameRecord> = emptyList()
            gameResultDao.getPlayerRecords(name).collect { records ->
                result = records
            }
            result
        }
    }

    suspend fun getMaxScore(): Int? {
        return withContext(Dispatchers.IO) {
            gameResultDao.getMaxScore()
        }
    }

    suspend fun getAverageScore(): Float? {
        return withContext(Dispatchers.IO) {
            gameResultDao.getAverageScore()
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            gameResultDao.clearAll()
        }
    }
}