package com.example.qrtask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 顶部信息栏组件
 * 包含5个信息显示区域
 * 现在用于显示评分结果
 */
@Composable
fun StatusBar(
    scanName: String = "图片占位",
    correctionAreas: Int = 0,
    validCorrections: Int = 0,
    status: String = "OK",
    scoreDisplay: String? = null,
    totalQuestions: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 总题目数
        StatusBox(
            title = "总题目数",
            content = totalQuestions?.toString() ?: "-",
            modifier = Modifier.weight(1f)
        )
        
        // 2. 分数显示区域（如果有评分结果，优先显示分数）
        if (scoreDisplay != null) {
            StatusBox(
                title = "总分",
                content = scoreDisplay,
                modifier = Modifier.weight(1f)
            )
        } else {
            StatusBox(
                title = "扫描姓名",
                content = scanName,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 3. 涂改区区域（如果有评分结果，显示正确题数）
        if (validCorrections > 0) {
            StatusBox(
                title = "正确题数",
                content = "${validCorrections}题",
                modifier = Modifier.weight(1f)
            )
        } else {
            StatusBox(
                title = "涂改区",
                content = "${correctionAreas}处",
                modifier = Modifier.weight(1f)
            )
        }
        
        // 4. 有效涂改区域（如果有评分结果，显示百分比）
        if (scoreDisplay != null && correctionAreas > 0) {
            StatusBox(
                title = "正确率",
                content = "${correctionAreas}%",
                modifier = Modifier.weight(1f)
            )
        } else {
            StatusBox(
                title = "有效涂改",
                content = "${validCorrections}个",
                modifier = Modifier.weight(1f)
            )
        }
        
        // 5. 状态区域
        OkStatusBox(
            status = status,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusBox(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Color.Gray.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
private fun OkStatusBox(
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Color.Gray.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "状态",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 简单的状态指示器（可以用图标替换）
            val statusColor = when (status) {
                "OK", "完成" -> Color.Green
                "处理中" -> Color.Blue
                else -> Color.Red
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}
