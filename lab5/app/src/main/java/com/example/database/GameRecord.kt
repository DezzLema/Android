// app/src/main/java/com/example/lab4/database/GameRecord.kt
package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "player_name")
    val playerName: String,

    @ColumnInfo(name = "score")
    val score: Int,

    @ColumnInfo(name = "level")
    val level: Int,

    @ColumnInfo(name = "time_seconds")
    val timeSeconds: Int,

    @ColumnInfo(name = "date")
    val date: Date = Date()
)