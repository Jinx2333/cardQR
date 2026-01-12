package com.example.qrtask.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.qrtask.data.dao.ExamTemplateDao
import com.example.qrtask.data.dao.StudentResultDao
import com.example.qrtask.data.entity.ExamTemplate
import com.example.qrtask.data.entity.StudentResult

/**
 * Room数据库
 */
@Database(
    entities = [ExamTemplate::class, StudentResult::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun examTemplateDao(): ExamTemplateDao
    abstract fun studentResultDao(): StudentResultDao
    
    companion object {
        const val DATABASE_NAME = "omr_database"
    }
}
