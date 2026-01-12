package com.example.qrtask.data.dao

import androidx.room.*
import com.example.qrtask.data.entity.ExamTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 考试模板数据访问对象
 */
@Dao
interface ExamTemplateDao {
    
    /**
     * 获取所有考试模板
     */
    @Query("SELECT * FROM exam_templates ORDER BY createdAt DESC")
    fun getAllTemplates(): Flow<List<ExamTemplate>>
    
    /**
     * 根据ID获取考试模板
     */
    @Query("SELECT * FROM exam_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): ExamTemplate?
    
    /**
     * 插入考试模板
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExamTemplate): Long
    
    /**
     * 更新考试模板
     */
    @Update
    suspend fun updateTemplate(template: ExamTemplate)
    
    /**
     * 删除考试模板
     */
    @Delete
    suspend fun deleteTemplate(template: ExamTemplate)
    
    /**
     * 根据ID删除考试模板
     */
    @Query("DELETE FROM exam_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)
}
