package com.example.qrtask.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qrtask.omr.GradingManager
import kotlinx.coroutines.delay

/**
 * 扫描准备界面
 * 包含：上传标准模板、开始批量扫描
 */
@Composable
fun ScanSetupScreen(
    onUploadTemplate: () -> Unit,
    onStartBatchScan: () -> Unit,
    onBack: () -> Unit,
    key: Int = 0,  // 用于强制重组
    modifier: Modifier = Modifier
) {
    // 使用 remember 和 key 来监听状态变化
    var hasMasterKey by remember(key) { mutableStateOf(GradingManager.hasMasterKey()) }
    var masterKeyStatus by remember(key) { mutableStateOf(GradingManager.getMasterKeyStatusText()) }
    
    // 当 key 变化时更新状态
    LaunchedEffect(key) {
        hasMasterKey = GradingManager.hasMasterKey()
        masterKeyStatus = GradingManager.getMasterKeyStatusText()
    }
    
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
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Master Key 状态提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasMasterKey) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
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
                    text = masterKeyStatus,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // 上传标准模板按钮
        Button(
            onClick = onUploadTemplate,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("上传标准模板", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        // 开始批量扫描按钮
        Button(
            onClick = onStartBatchScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = hasMasterKey,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text("开始批量扫描", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        if (!hasMasterKey) {
            Text(
                text = "请先上传标准模板",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
