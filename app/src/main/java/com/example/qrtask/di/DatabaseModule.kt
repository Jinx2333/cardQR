package com.example.qrtask.di

import android.content.Context
import androidx.room.Room
import com.example.qrtask.data.dao.ExamTemplateDao
import com.example.qrtask.data.dao.StudentResultDao
import com.example.qrtask.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库模块 - 提供Room数据库实例
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // 开发阶段使用，生产环境应提供Migration
            .build()
    }
    
    @Provides
    fun provideExamTemplateDao(database: AppDatabase): ExamTemplateDao {
        return database.examTemplateDao()
    }
    
    @Provides
    fun provideStudentResultDao(database: AppDatabase): StudentResultDao {
        return database.studentResultDao()
    }
}
