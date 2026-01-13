package com.example.qrtask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.example.qrtask.omr.GradingManager
import com.example.qrtask.omr.GradingResult

/**
 * 批量扫描界面
 * 使用 ML Kit Document Scanner 扫描学生答题卡
 */
@Composable
fun BatchScanScreen(
    onScanStudent: () -> Unit,
    isProcessing: Boolean,
    gradingResult: GradingResult?,
    debugImageBitmap: android.graphics.Bitmap?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 返回按钮
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("← 返回", fontSize = 16.sp)
        }
        
        // 顶部信息栏
        TopInfoBar(
            totalQuestions = gradingResult?.totalQuestions ?: 0,
            validAnswerCount = gradingResult?.validAnswerCount ?: 0,
            scoreDisplay = gradingResult?.scoreDisplay,
            correctCount = gradingResult?.score ?: 0,
            correctRate = if (gradingResult != null && gradingResult.totalQuestions > 0) {
                (gradingResult.score * 100 / gradingResult.totalQuestions)
            } else 0,
            status = when {
                isProcessing -> "处理中"
                gradingResult != null -> "完成"
                else -> "就绪"
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Master Key 状态提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "标准模板状态",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = GradingManager.getMasterKeyStatusText(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // 扫描学生答题卡按钮
        Button(
            onClick = onScanStudent,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isProcessing && GradingManager.hasMasterKey(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text("扫描学生答题卡", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        if (!GradingManager.hasMasterKey()) {
            Text(
                text = "请先上传标准模板",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        // 处理中状态
        if (isProcessing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "正在处理...",
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        // 调试图像显示（显示检测到的行）
        debugImageBitmap?.let { bitmap ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "检测结果预览（红色线条标记检测到的行）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "检测结果预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    )
                }
            }
        }
        
        // 评分结果显示
        gradingResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "评分结果",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 分数显示
                    Text(
                        text = "Student Score: ${result.scoreDisplay}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // 错误题目列表
                    if (result.wrongQuestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "错误题目:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .heightIn(max = 200.dp)
                        ) {
                            result.wrongQuestions.forEach { wrong ->
                                Text(
                                    text = wrong.displayText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "全部正确！",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * 顶部信息栏
 */
@Composable
private fun TopInfoBar(
    totalQuestions: Int,
    validAnswerCount: Int,
    scoreDisplay: String?,
    correctCount: Int,
    correctRate: Int,
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 总题目数
        InfoBox(
            title = "总题目数",
            content = if (totalQuestions > 0) totalQuestions.toString() else "-",
            modifier = Modifier.weight(1f)
        )
        
        // 有效涂改数
        InfoBox(
            title = "有效涂改数",
            content = if (validAnswerCount > 0) validAnswerCount.toString() else "-",
            modifier = Modifier.weight(1f)
        )
        
        // 总分
        InfoBox(
            title = "总分",
            content = scoreDisplay ?: "-",
            modifier = Modifier.weight(1f)
        )
        
        // 正确题数
        InfoBox(
            title = "正确题数",
            content = if (correctCount > 0) "${correctCount}题" else "-",
            modifier = Modifier.weight(1f)
        )
        
        // 正确率
        InfoBox(
            title = "正确率",
            content = if (correctRate > 0) "${correctRate}%" else "-",
            modifier = Modifier.weight(1f)
        )
        
        // 状态
        InfoBox(
            title = "状态",
            content = status,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoBox(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(60.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
