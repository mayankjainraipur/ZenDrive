package com.example.zendrive

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CarDao {
    @Query("SELECT * FROM car_logs ORDER BY date DESC")
    suspend fun getAllLogs(): List<CarEntry>

    @Insert
    suspend fun insertLog(entry: CarEntry)
}

@Database(entities = [CarEntry::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
}
