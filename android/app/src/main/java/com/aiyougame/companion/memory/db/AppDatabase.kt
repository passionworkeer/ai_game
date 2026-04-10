package com.aiyougame.companion.memory.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

/** Temporary dummy entity to keep KSP happy. Real entities TBD. */
@Entity(tableName = "_dummy")
data class DummyEntity(
    @PrimaryKey val id: Int = 0,
    val value: String = "",
)

@Database(
    entities = [DummyEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "aiyougame.db"
    }
}
