package com.example.qrtask.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.PointF
import com.example.qrtask.omr.GradingManager
import com.example.qrtask.omr.GradingResult
import com.example.qrtask.omr.OmrGrader
import com.example.qrtask.omr.PaperDetector
import com.example.qrtask.omr.StabilityChecker
import com.example.qrtask.util.imageProxyToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * 实时相机预览扫描界面
 * 顶部信息栏（20%）+ 底部相机预览区域（80%）
 */
@Composable
fun CameraPreviewScreen(
    onBack: () -> Unit,
    onScanResult: (GradingResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var isProcessing by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<GradingResult?>(null) }
    var previewSize by remember { mutableStateOf<Size?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val omrGrader = remember { OmrGrader() }
    val paperDetector = remember { PaperDetector() }
    val stabilityChecker = remember { StabilityChecker() }
    
    // 检测状态
    var detectedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var isStable by remember { mutableStateOf(false) }
    var analysisSize by remember { mutableStateOf<android.util.Size?>(null) }
    
    // 顶部信息栏数据
    var totalQuestions by remember { mutableStateOf(0) }
    var scoreDisplay by remember { mutableStateOf<String?>(null) }
    var correctCount by remember { mutableStateOf(0) }
    var correctRate by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("扫描中") }
    
    // 权限申请
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            permissionError = "需要相机权限才能使用此功能"
            Log.e("CameraPreview", "相机权限被拒绝")
        } else {
            permissionError = null
        }
    }
    
    // 检查相机权限
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            // 自动申请权限
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // 相机预览
    val previewView = remember { 
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            return@LaunchedEffect
        }
        
        try {
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        if (!isProcessing) {
                            scope.launch {
                                try {
                                    val bitmap = imageProxyToBitmap(imageProxy)
                                    previewSize = android.util.Size(bitmap.width, bitmap.height)
                                    analysisSize = android.util.Size(bitmap.width, bitmap.height)
                                    
                                    // Step 1: 检测答题卡边界
                                    val detectionResult = withContext(Dispatchers.IO) {
                                        paperDetector.detectPaperCorners(bitmap)
                                    }
                                    
                                    // Step 2: 更新检测到的角点
                                    detectedCorners = detectionResult.corners
                                    
                                    // Step 3: 检查稳定性
                                    val stable = stabilityChecker.checkStability(detectionResult.corners)
                                    isStable = stable
                                    
                                    // Step 4: 如果稳定且未处理过，进行识别和评分
                                    if (stable && !isProcessing && GradingManager.hasMasterKey()) {
                                        isProcessing = true
                                        status = "识别中..."
                                        
                                        // 识别答案
                                        val answers = withContext(Dispatchers.IO) {
                                            omrGrader.recognizeAnswers(bitmap)
                                        }
                                        
                                        // 评分
                                        val result = GradingManager.gradeStudent(answers)
                                        
                                        // 更新UI
                                        scanResult = result
                                        totalQuestions = result.totalQuestions
                                        scoreDisplay = result.scoreDisplay
                                        correctCount = result.score
                                        correctRate = if (result.totalQuestions > 0) {
                                            (result.score * 100 / result.totalQuestions)
                                        } else 0
                                        status = "完成"
                                        
                                        // 回调结果
                                        onScanResult(result)
                                        
                                        // 保持处理状态，等待用户重置
                                    } else if (!stable) {
                                        status = if (detectionResult.corners != null) "请保持稳定" else "扫描中"
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraPreview", "处理图片失败", e)
                                    e.printStackTrace()
                                    status = "错误"
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            Log.d("CameraPreview", "相机初始化成功")
        } catch (e: Exception) {
            Log.e("CameraPreview", "相机初始化失败", e)
            permissionError = "相机初始化失败: ${e.message}"
            e.printStackTrace()
        }
    }
    
    DisposableEffect(hasPermission) {
        onDispose {
            if (hasPermission) {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (e: Exception) {
                    Log.e("CameraPreview", "释放相机失败", e)
                }
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部信息栏（固定高度，不占用太多空间）
        TopInfoBar(
            totalQuestions = totalQuestions,
            scoreDisplay = scoreDisplay,
            correctCount = correctCount,
            correctRate = correctRate,
            status = status,
            modifier = Modifier.fillMaxWidth()
        )
        
        // 相机预览区域（占满剩余空间）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (hasPermission) {
                // 相机预览
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 权限错误提示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = permissionError ?: "需要相机权限",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "请在设置中授予相机权限",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 取景框覆盖层（显示检测到的边界）
            ViewfinderOverlay(
                detectedCorners = detectedCorners,
                isStable = isStable,
                previewSize = previewSize,
                analysisSize = analysisSize,
                modifier = Modifier.fillMaxSize()
            )
            
            // 提示文字和重置按钮
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        isProcessing -> "识别完成"
                        isStable -> "保持稳定，正在识别..."
                        detectedCorners != null -> "请保持稳定"
                        else -> "请将答题卡放入框内"
                    },
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                if (isProcessing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            // 重置状态，准备扫描下一张
                            isProcessing = false
                            scanResult = null
                            detectedCorners = null
                            isStable = false
                            stabilityChecker.reset()
                            status = "扫描中"
                            totalQuestions = 0
                            scoreDisplay = null
                            correctCount = 0
                            correctRate = 0
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("扫描下一张", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * 顶部信息栏
 */
@Composable
private fun TopInfoBar(
    totalQuestions: Int,
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

/**
 * 取景框覆盖层
 * 显示检测到的答题卡边界框
 */
@Composable
private fun ViewfinderOverlay(
    detectedCorners: List<PointF>?,
    isStable: Boolean,
    previewSize: android.util.Size?,
    analysisSize: android.util.Size?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // 如果没有检测到边界，显示默认的引导框
        if (detectedCorners == null || detectedCorners.size != 4) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val viewfinderWidth = size.width * 0.8f
            val viewfinderHeight = size.height * 0.6f
            
            val left = centerX - viewfinderWidth / 2
            val top = centerY - viewfinderHeight / 2
            val right = centerX + viewfinderWidth / 2
            val bottom = centerY + viewfinderHeight / 2
            
            // 绘制半透明背景
            val backgroundPath = Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRect(
                    androidx.compose.ui.geometry.Rect(
                        left,
                        top,
                        right,
                        bottom
                    )
                )
            }
            drawPath(
                backgroundPath,
                Color.Black.copy(alpha = 0.5f)
            )
            
            // 绘制取景框（虚线，白色表示搜索中）
            val dashPath = Path().apply {
                moveTo(left, top)
                lineTo(right, top)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(
                dashPath,
                Color.White,
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(20f, 20f)
                    )
                )
            )
        } else {
            // 检测到边界，绘制检测到的边界框
            // 坐标映射：从分析尺寸映射到预览尺寸
            val scaleX = if (analysisSize != null && analysisSize.width > 0) {
                size.width / analysisSize.width.toFloat()
            } else 1f
            val scaleY = if (analysisSize != null && analysisSize.height > 0) {
                size.height / analysisSize.height.toFloat()
            } else 1f
            
            // 映射角点到预览坐标
            val mappedCorners = detectedCorners.map { corner ->
                Offset(corner.x * scaleX, corner.y * scaleY)
            }
            
            // 绘制检测到的边界框
            val detectedPath = Path().apply {
                moveTo(mappedCorners[0].x, mappedCorners[0].y)
                for (i in 1 until mappedCorners.size) {
                    lineTo(mappedCorners[i].x, mappedCorners[i].y)
                }
                close()
            }
            
            // 根据稳定性选择颜色：稳定=绿色，不稳定=红色
            val borderColor = if (isStable) Color.Green else Color.Red
            val borderWidth = if (isStable) 5f else 3f
            
            drawPath(
                detectedPath,
                borderColor,
                style = Stroke(width = borderWidth)
            )
            
            // 绘制半透明背景（排除检测到的区域）
            val backgroundPath = Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                moveTo(mappedCorners[0].x, mappedCorners[0].y)
                for (i in 1 until mappedCorners.size) {
                    lineTo(mappedCorners[i].x, mappedCorners[i].y)
                }
                close()
            }
            drawPath(
                backgroundPath,
                Color.Black.copy(alpha = 0.5f)
            )
        }
    }
}
