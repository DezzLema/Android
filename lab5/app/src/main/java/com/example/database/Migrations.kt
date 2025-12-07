// app/src/main/java/com/example/lab4/database/Migrations.kt
package com.example.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Если в будущем потребуется добавить новую колонку
        database.execSQL(
            "ALTER TABLE game_records ADD COLUMN difficulty TEXT DEFAULT 'normal'"
        )
    }
}

// Использовать в AppDatabase:
// .addMigrations(MIGRATION_1_2)