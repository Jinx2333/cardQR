package com.example.qrtask.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 考试模板实体 - 存储正确答案和考试元数据
 */
@Entity(tableName = "exam_templates")
data class ExamTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 考试名称
     */
    val examName: String,
    
    /**
     * 题目总数
     */
    val totalQuestions: Int,
    
    /**
     * 每题的选项数（例如：4表示A、B、C、D）
     */
    val optionsPerQuestion: Int = 4,
    
    /**
     * 正确答案列表，格式：["A", "B", "C", "D", ...]
     * 索引对应题目编号（从0开始）
     */
    val correctAnswers: List<String>,
    
    /**
     * 每题分值（默认1分）
     */
    val pointsPerQuestion: Int = 1,
    
    /**
     * 创建时间戳
     */
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * 更新时间戳
     */
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取指定题目的正确答案
     */
    fun getCorrectAnswer(questionIndex: Int): String? {
        return if (questionIndex in 0 until correctAnswers.size) {
            correctAnswers[questionIndex]
        } else {
            null
        }
    }
    
    /**
     * 计算总分
     */
    fun getTotalScore(): Int {
        return totalQuestions * pointsPerQuestion
    }
}
