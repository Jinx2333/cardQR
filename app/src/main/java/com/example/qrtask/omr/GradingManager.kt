package com.example.qrtask.omr

/**
 * 评分管理器单例
 * 管理 Master Key 和学生评分逻辑
 */
object GradingManager {
    /**
     * Master Key：正确答案列表
     * 使用整数表示：0=A, 1=B, 2=C, 3=D, -1=未作答
     */
    var masterAnswers: List<Int>? = null
        private set
    
    /**
     * 总题目数
     */
    var totalQuestions: Int = 0
        private set
    
    /**
     * 设置 Master Key
     */
    fun setMasterKey(answers: List<Int>) {
        masterAnswers = answers
        totalQuestions = answers.size
    }
    
    /**
     * 检查 Master Key 是否已设置
     */
    fun hasMasterKey(): Boolean = masterAnswers != null
    
    /**
     * 获取 Master Key 状态文本
     */
    fun getMasterKeyStatusText(): String {
        return if (hasMasterKey()) {
            "Set ($totalQuestions Qs)"
        } else {
            "Not Set"
        }
    }
    
    /**
     * 对学生答案进行评分
     * @param studentAnswers 学生答案列表（整数：0=A, 1=B, 2=C, 3=D, -1=未作答）
     * @return 得分（每题1分）
     */
    fun gradeStudent(studentAnswers: List<Int>): GradingResult {
        val master = masterAnswers ?: throw IllegalStateException("Master Key not set")
        
        if (studentAnswers.size != master.size) {
            throw IllegalArgumentException("Student answers count (${studentAnswers.size}) doesn't match master key count (${master.size})")
        }
        
        var correctCount = 0
        var validAnswerCount = 0  // 有效涂改数（有涂改的题目数）
        val wrongQuestions = mutableListOf<WrongAnswer>()
        
        for (i in studentAnswers.indices) {
            val studentAnswer = studentAnswers[i]
            val masterAnswer = master[i]
            
            // 统计有效涂改数（studentAnswer != -1 表示有涂改）
            if (studentAnswer != -1) {
                validAnswerCount++
            }
            
            if (studentAnswer == masterAnswer && studentAnswer != -1) {
                correctCount++
            } else if (studentAnswer != masterAnswer) {
                // 记录错误答案
                val studentLabel = answerIntToLabel(studentAnswer)
                val masterLabel = answerIntToLabel(masterAnswer)
                wrongQuestions.add(WrongAnswer(questionNumber = i + 1, student = studentLabel, master = masterLabel))
            }
        }
        
        return GradingResult(
            score = correctCount,
            totalQuestions = master.size,
            validAnswerCount = validAnswerCount,
            wrongQuestions = wrongQuestions
        )
    }
    
    /**
     * 将整数答案转换为标签
     */
    private fun answerIntToLabel(answer: Int): String {
        return when (answer) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            3 -> "D"
            -1 -> "未作答"
            else -> "?"
        }
    }
    
    /**
     * 清除 Master Key
     */
    fun clearMasterKey() {
        masterAnswers = null
        totalQuestions = 0
    }
}

/**
 * 评分结果
 */
data class GradingResult(
    val score: Int,
    val totalQuestions: Int,
    val validAnswerCount: Int,  // 有效涂改数（有涂改的题目数）
    val wrongQuestions: List<WrongAnswer>
) {
    val scoreDisplay: String
        get() = "$score / $totalQuestions"
}

/**
 * 错误答案信息
 */
data class WrongAnswer(
    val questionNumber: Int,
    val student: String,
    val master: String
) {
    val displayText: String
        get() = "Q$questionNumber(Student:$student, Key:$master)"
}
