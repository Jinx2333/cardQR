package com.example.qrtask.data.dao

import androidx.room.*
import com.example.qrtask.data.entity.StudentResult
import kotlinx.coroutines.flow.Flow

/**
 * 学生结果数据访问对象
 */
@Dao
interface StudentResultDao {
    
    /**
     * 获取所有学生结果
     */
    @Query("SELECT * FROM student_results ORDER BY scannedAt DESC")
    fun getAllResults(): Flow<List<StudentResult>>
    
    /**
     * 根据考试ID获取所有结果
     */
    @Query("SELECT * FROM student_results WHERE examId = :examId ORDER BY scannedAt DESC")
    fun getResultsByExamId(examId: Long): Flow<List<StudentResult>>
    
    /**
     * 根据ID获取学生结果
     */
    @Query("SELECT * FROM student_results WHERE id = :id")
    suspend fun getResultById(id: Long): StudentResult?
    
    /**
     * 根据学生ID获取结果
     */
    @Query("SELECT * FROM student_results WHERE studentId = :studentId ORDER BY scannedAt DESC")
    fun getResultsByStudentId(studentId: String): Flow<List<StudentResult>>
    
    /**
     * 插入学生结果
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: StudentResult): Long
    
    /**
     * 更新学生结果
     */
    @Update
    suspend fun updateResult(result: StudentResult)
    
    /**
     * 删除学生结果
     */
    @Delete
    suspend fun deleteResult(result: StudentResult)
    
    /**
     * 根据ID删除学生结果
     */
    @Query("DELETE FROM student_results WHERE id = :id")
    suspend fun deleteResultById(id: Long)
    
    /**
     * 根据考试ID删除所有相关结果
     */
    @Query("DELETE FROM student_results WHERE examId = :examId")
    suspend fun deleteResultsByExamId(examId: Long)
    
    /**
     * 获取考试的平均分
     */
    @Query("SELECT AVG(score) FROM student_results WHERE examId = :examId")
    suspend fun getAverageScoreByExamId(examId: Long): Double?
}
