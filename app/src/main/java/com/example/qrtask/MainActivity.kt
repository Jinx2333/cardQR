package com.example.qrtask

import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.qrtask.omr.GradingManager
import com.example.qrtask.omr.GradingResult
import com.example.qrtask.omr.OmrGrader
import com.example.qrtask.ui.BatchScanScreen
import com.example.qrtask.ui.MainScreen
import com.example.qrtask.ui.ScanSetupScreen
import com.example.qrtask.ui.theme.QRTaskTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.InputStream

class MainActivity : ComponentActivity() {

    private var scannedImageUri: Uri? by mutableStateOf(null)
    private var studentGradingResult: com.example.qrtask.omr.GradingResult? by mutableStateOf(null)
    private var isProcessing: Boolean by mutableStateOf(false)
    private var scanMode: ScanMode by mutableStateOf(ScanMode.MASTER_KEY)
    private var currentScreen: AppScreen by mutableStateOf(AppScreen.MAIN)
    private var masterKeyUpdated: Int by mutableStateOf(0)  // 用于触发界面更新
    private var debugImageBitmap: Bitmap? by mutableStateOf(null)  // 调试图像（显示检测到的行）
    
    // OMR 识别器
    private val omrGrader = OmrGrader()
    
    enum class ScanMode {
        MASTER_KEY,  // 扫描 Master Key（上传标准模板）
        STUDENT      // 扫描学生答题卡（批量扫描）
    }
    
    enum class AppScreen {
        MAIN,           // 主入口界面
        SCAN_SETUP,     // 扫描准备界面
        BATCH_SCAN      // 批量扫描界面
    }

    // ML Kit Document Scanner 配置
    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setPageLimit(1)
        .build()

    private val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(scannerOptions)

    // ActivityResultLauncher for Document Scanner
    // ML Kit Document Scanner 使用 IntentSender，所以需要使用 StartIntentSenderForResult
    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                // ML Kit Document Scanner 返回的结果在 Intent 中
                val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data!!)
                scanningResult?.pages?.firstOrNull()?.let { page ->
                    // 获取扫描结果的 URI
                    scannedImageUri = page.imageUri
                    Log.d("MainActivity", "扫描成功: ${page.imageUri}")
                    
                    // 调用 OpenCV 进行 OMR 处理
                    processScannedImage(page.imageUri)
                } ?: run {
                    Log.w("MainActivity", "扫描结果中没有页面")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "解析扫描结果失败", e)
            }
        } else {
            Log.d("MainActivity", "扫描取消或失败")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 OpenCV
        val isOpenCVLoaded = try {
            OpenCVLoader.initDebug()
        } catch (e: Exception) {
            Log.e("OpenCV", "无法加载 OpenCV", e)
            false
        }

        if (isOpenCVLoaded) {
            Log.i("OpenCV", "OpenCV 加载成功")
        } else {
            Log.e("OpenCV", "OpenCV 初始化失败!")
        }

        enableEdgeToEdge()

        setContent {
            QRTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        AppScreen.MAIN -> {
                            MainScreen(
                                onStartNewScan = {
                                    currentScreen = AppScreen.SCAN_SETUP
                                },
                                onScanHistory = {
                                    // TODO: 实现扫描记录功能
                                },
                                onSettings = {
                                    // TODO: 实现设置功能
                                }
                            )
                        }
                        AppScreen.SCAN_SETUP -> {
                            // 使用 key 来强制重组，当 masterKeyUpdated 变化时
                            ScanSetupScreen(
                                onUploadTemplate = {
                                    scanMode = ScanMode.MASTER_KEY
                                    launchDocumentScanner()
                                },
                                onStartBatchScan = {
                                    currentScreen = AppScreen.BATCH_SCAN
                                },
                                onBack = {
                                    currentScreen = AppScreen.MAIN
                                },
                                key = masterKeyUpdated
                            )
                        }
                        AppScreen.BATCH_SCAN -> {
                            BatchScanScreen(
                                onScanStudent = {
                                    scanMode = ScanMode.STUDENT
                                    launchDocumentScanner()
                                },
                                isProcessing = isProcessing,
                                gradingResult = studentGradingResult,
                                debugImageBitmap = debugImageBitmap,
                                onBack = {
                                    currentScreen = AppScreen.SCAN_SETUP
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 启动 ML Kit Document Scanner
     */
    private fun launchDocumentScanner() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                // getStartScanIntent 返回的是 IntentSender，需要包装成 IntentSenderRequest
                val request = IntentSenderRequest.Builder(intentSender).build()
                scanLauncher.launch(request)
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "启动扫描器失败", e)
            }
    }
    
    /**
     * 处理扫描后的图片
     */
    private fun processScannedImage(imageUri: Uri) {
        isProcessing = true
        studentGradingResult = null
        
        // 在后台线程处理图片
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    // 从 URI 加载 Bitmap
                    val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
                    val bitmap = inputStream?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    
                    if (bitmap != null) {
                        // 识别答案
                        val answers = omrGrader.recognizeAnswers(bitmap)
                        
                        // 生成调试图像（显示检测到的行）
                        val debugBitmap = omrGrader.drawDetectedRows(bitmap)
                        
                        when (scanMode) {
                            ScanMode.MASTER_KEY -> {
                                // 设置为 Master Key
                                GradingManager.setMasterKey(answers)
                                Log.d("MainActivity", "Master Key 已设置: ${answers.size} 题")
                                // 触发界面更新
                                masterKeyUpdated++
                                Pair(null, debugBitmap)
                            }
                            ScanMode.STUDENT -> {
                                // 对学生答案进行评分
                                val gradingResult = if (GradingManager.hasMasterKey()) {
                                    GradingManager.gradeStudent(answers)
                                } else {
                                    Log.w("MainActivity", "Master Key 未设置，无法评分")
                                    null
                                }
                                Pair(gradingResult, debugBitmap)
                            }
                        }
                    } else {
                        Pair(null, null)
                    }
                }
                
                // 更新 UI（主线程）
                val (gradingResult, debugBitmap) = result
                if (scanMode == ScanMode.STUDENT) {
                    studentGradingResult = gradingResult
                    debugImageBitmap = debugBitmap
                    Log.d("MainActivity", "评分完成: ${gradingResult?.scoreDisplay}")
                } else {
                    debugImageBitmap = debugBitmap
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "处理图片失败", e)
            } finally {
                isProcessing = false
            }
        }
    }
}
