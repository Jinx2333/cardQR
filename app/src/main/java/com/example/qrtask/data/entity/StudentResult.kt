package com.example.qrtask.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学生答题结果实体
 */
@Entity(
    tableName = "student_results",
    foreignKeys = [
        ForeignKey(
            entity = ExamTemplate::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["examId"])]
)
data class StudentResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 关联的考试模板ID
     */
    val examId: Long,
    
    /**
     * 学生ID（可以是学号、姓名或时间戳）
     */
    val studentId: String,
    
    /**
     * 学生姓名（可选）
     */
    val studentName: String? = null,
    
    /**
     * 识别的答案列表，格式：["A", "B", "C", "D", ...]
     * 索引对应题目编号（从0开始）
     */
    val recognizedAnswers: List<String>,
    
    /**
     * 得分
     */
    val score: Int,
    
    /**
     * 总分
     */
    val totalScore: Int,
    
    /**
     * 原始扫描图像的保存路径
     */
    val imagePath: String? = null,
    
    /**
     * 处理后的图像路径（带标记的）
     */
    val processedImagePath: String? = null,
    
    /**
     * 扫描时间戳
     */
    val scannedAt: Long = System.currentTimeMillis(),
    
    /**
     * 备注信息
     */
    val notes: String? = null
) {
    /**
     * 计算正确率（百分比）
     */
    fun getAccuracy(): Float {
        return if (totalScore > 0) {
            (score.toFloat() / totalScore.toFloat()) * 100f
        } else {
            0f
        }
    }
}
