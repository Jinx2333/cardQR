package com.example.qrtask.omr

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * OMR（光学标记识别）处理器
 * 负责图像处理和答题卡识别
 */
class OmrProcessor(
    /**
     * 题目总数
     */
    private val totalQuestions: Int = 20,
    
    /**
     * 每题选项数（默认4：A、B、C、D）
     */
    private val optionsPerQuestion: Int = 4,
    
    /**
     * 答题区域在图像中的位置（归一化坐标，0.0-1.0）
     * [top, left, bottom, right]
     */
    private val answerRegionBounds: FloatArray = floatArrayOf(0.2f, 0.1f, 0.9f, 0.9f),
    
    /**
     * 标记检测阈值（黑色像素占比，默认30%）
     */
    private val markThreshold: Double = 0.3
) {
    
    companion object {
        private const val TAG = "OmrProcessor"
        
        // 图像预处理参数
        private const val GAUSSIAN_BLUR_SIZE = 5
        private const val CANNY_LOW_THRESHOLD = 50.0
        private const val CANNY_HIGH_THRESHOLD = 150.0
        
        // 透视变换目标尺寸
        private const val WARPED_WIDTH = 800
        private const val WARPED_HEIGHT = 1000
    }
    
    /**
     * OMR处理结果
     */
    data class OmrResult(
        /**
         * 识别的答案列表（索引对应题目编号）
         */
        val recognizedAnswers: List<String>,
        
        /**
         * 处理后的图像（带标记）
         */
        val processedBitmap: Bitmap?,
        
        /**
         * 是否成功检测到答题卡
         */
        val isPaperDetected: Boolean,
        
        /**
         * 处理过程中的错误信息
         */
        val errorMessage: String? = null
    )
    
    /**
     * 处理答题卡图像
     * 
     * @param bitmap 原始图像
     * @return OMR处理结果
     */
    fun processImage(bitmap: Bitmap): OmrResult {
        return try {
            // 将Bitmap转换为OpenCV Mat
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)
            
            // 步骤1：预处理和纸张检测
            val paperCorners = detectPaper(srcMat)
            if (paperCorners == null) {
                return OmrResult(
                    recognizedAnswers = emptyList(),
                    processedBitmap = null,
                    isPaperDetected = false,
                    errorMessage = "未检测到答题卡，请确保答题卡完整显示在画面中"
                )
            }
            
            // 步骤2：透视变换
            val warpedMat = performPerspectiveTransform(srcMat, paperCorners)
            
            // 步骤3：网格和气泡检测
            val answers = detectBubbles(warpedMat)
            
            // 步骤4：生成带标记的处理图像
            val processedBitmap = createMarkedBitmap(warpedMat, answers)
            
            OmrResult(
                recognizedAnswers = answers,
                processedBitmap = processedBitmap,
                isPaperDetected = true
            )
        } catch (e: Exception) {
            OmrResult(
                recognizedAnswers = emptyList(),
                processedBitmap = null,
                isPaperDetected = false,
                errorMessage = "处理失败: ${e.message}"
            )
        }
    }
    
    /**
     * 步骤A：预处理和纸张检测
     */
    private fun detectPaper(srcMat: Mat): MatOfPoint2f? {
        // 转换为灰度图
        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 高斯模糊降噪
        val blurredMat = Mat()
        Imgproc.GaussianBlur(
            grayMat,
            blurredMat,
            Size(GAUSSIAN_BLUR_SIZE.toDouble(), GAUSSIAN_BLUR_SIZE.toDouble()),
            0.0
        )
        
        // Canny边缘检测
        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, CANNY_LOW_THRESHOLD, CANNY_HIGH_THRESHOLD)
        
        // 查找轮廓
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edgesMat,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        // 找到最大的四边形轮廓（答题卡）
        var largestContour: MatOfPoint? = null
        var maxArea = 0.0
        
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > maxArea) {
                // 近似多边形
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(
                    MatOfPoint2f(*contour.toArray()),
                    approx,
                    0.02 * peri,
                    true
                )
                
                // 检查是否是四边形
                if (approx.rows() == 4) {
                    maxArea = area
                    largestContour = MatOfPoint(*approx.toArray())
                }
            }
        }
        
        return largestContour?.let { MatOfPoint2f(*it.toArray()) }
    }
    
    /**
     * 步骤B：透视变换
     */
    private fun performPerspectiveTransform(srcMat: Mat, corners: MatOfPoint2f): Mat {
        // 排序角点：左上、右上、右下、左下
        val sortedCorners = sortCorners(corners)
        
        // 定义目标角点
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(WARPED_WIDTH.toDouble(), 0.0),
            Point(WARPED_WIDTH.toDouble(), WARPED_HEIGHT.toDouble()),
            Point(0.0, WARPED_HEIGHT.toDouble())
        )
        
        // 计算透视变换矩阵
        val transformMatrix = Imgproc.getPerspectiveTransform(sortedCorners, dstPoints)
        
        // 执行透视变换
        val warpedMat = Mat()
        Imgproc.warpPerspective(
            srcMat,
            warpedMat,
            transformMatrix,
            Size(WARPED_WIDTH.toDouble(), WARPED_HEIGHT.toDouble())
        )
        
        return warpedMat
    }
    
    /**
     * 排序角点：左上、右上、右下、左下
     */
    private fun sortCorners(corners: MatOfPoint2f): MatOfPoint2f {
        val points = corners.toArray()
        
        // 计算中心点
        val center = Point(
            points.map { it.x }.average(),
            points.map { it.y }.average()
        )
        
        // 按角度排序
        val sortedPoints = points.sortedBy { point ->
            val angle = kotlin.math.atan2(point.y - center.y, point.x - center.x)
            // 调整角度使左上角为0
            (angle + Math.PI / 4 + 2 * Math.PI) % (2 * Math.PI)
        }
        
        // 确定左上、右上、右下、左下
        val topLeft = sortedPoints.minByOrNull { it.x + it.y } ?: sortedPoints[0]
        val topRight = sortedPoints.maxByOrNull { it.x - it.y } ?: sortedPoints[1]
        val bottomRight = sortedPoints.maxByOrNull { it.x + it.y } ?: sortedPoints[2]
        val bottomLeft = sortedPoints.minByOrNull { it.x - it.y } ?: sortedPoints[3]
        
        return MatOfPoint2f(topLeft, topRight, bottomRight, bottomLeft)
    }
    
    /**
     * 步骤C：网格和气泡检测
     */
    private fun detectBubbles(warpedMat: Mat): List<String> {
        // 转换为灰度图
        val grayMat = Mat()
        Imgproc.cvtColor(warpedMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // 二值化（Otsu方法）
        val binaryMat = Mat()
        Imgproc.threshold(grayMat, binaryMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        
        // 计算答题区域
        val answerRegion = Rect(
            (WARPED_WIDTH * answerRegionBounds[1]).toInt(),
            (WARPED_HEIGHT * answerRegionBounds[0]).toInt(),
            ((answerRegionBounds[3] - answerRegionBounds[1]) * WARPED_WIDTH).toInt(),
            ((answerRegionBounds[2] - answerRegionBounds[0]) * WARPED_HEIGHT).toInt()
        )
        
        val answerArea = Mat(binaryMat, answerRegion)
        
        // 计算每个气泡的尺寸
        val cellWidth = answerArea.width() / optionsPerQuestion
        val cellHeight = answerArea.height() / totalQuestions
        
        val answers = mutableListOf<String>()
        
        // 遍历每个题目
        for (questionIndex in 0 until totalQuestions) {
            val questionAnswers = mutableListOf<Pair<String, Int>>()
            
            // 遍历每个选项
            for (optionIndex in 0 until optionsPerQuestion) {
                val optionChar = ('A' + optionIndex).toString()
                
                // 计算气泡区域
                val x = optionIndex * cellWidth
                val y = questionIndex * cellHeight
                val bubbleRect = Rect(x, y, cellWidth, cellHeight)
                
                // 提取气泡区域
                val bubbleMat = Mat(answerArea, bubbleRect)
                
                // 计算黑色像素数量
                val blackPixels = Core.countNonZero(bubbleMat)
                val totalPixels = bubbleMat.rows() * bubbleMat.cols()
                val fillRatio = blackPixels.toDouble() / totalPixels
                
                questionAnswers.add(Pair(optionChar, blackPixels))
            }
            
            // 找到填充最多的选项
            val maxFill = questionAnswers.maxByOrNull { it.second }?.second ?: 0
            val minFill = questionAnswers.minByOrNull { it.second }?.second ?: 0
            
            // 如果最大填充明显大于最小填充，则认为该选项被标记
            val answer = if (maxFill > minFill * 2 && maxFill > (cellWidth * cellHeight * markThreshold).toInt()) {
                questionAnswers.maxByOrNull { it.second }?.first ?: ""
            } else {
                "" // 未检测到标记
            }
            
            answers.add(answer)
        }
        
        return answers
    }
    
    /**
     * 创建带标记的处理图像
     */
    private fun createMarkedBitmap(warpedMat: Mat, answers: List<String>): Bitmap {
        val markedMat = warpedMat.clone()
        
        // 转换为灰度图用于计算
        val grayMat = Mat()
        Imgproc.cvtColor(warpedMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        val binaryMat = Mat()
        Imgproc.threshold(grayMat, binaryMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        
        // 计算答题区域
        val answerRegion = Rect(
            (WARPED_WIDTH * answerRegionBounds[1]).toInt(),
            (WARPED_HEIGHT * answerRegionBounds[0]).toInt(),
            ((answerRegionBounds[3] - answerRegionBounds[1]) * WARPED_WIDTH).toInt(),
            ((answerRegionBounds[2] - answerRegionBounds[0]) * WARPED_HEIGHT).toInt()
        )
        
        val answerArea = Mat(binaryMat, answerRegion)
        val cellWidth = answerArea.width() / optionsPerQuestion
        val cellHeight = answerArea.height() / totalQuestions
        
        // 在图像上绘制标记
        for (questionIndex in 0 until totalQuestions) {
            for (optionIndex in 0 until optionsPerQuestion) {
                val optionChar = ('A' + optionIndex).toString()
                val x = answerRegion.x + optionIndex * cellWidth + cellWidth / 2
                val y = answerRegion.y + questionIndex * cellHeight + cellHeight / 2
                
                // 如果这是识别的答案，绘制绿色圆圈；否则绘制红色圆圈
                val color = if (answers.getOrNull(questionIndex) == optionChar) {
                    Scalar(0.0, 255.0, 0.0) // 绿色
                } else {
                    Scalar(0.0, 0.0, 255.0) // 红色
                }
                
                Imgproc.circle(
                    markedMat,
                    Point(x.toDouble(), y.toDouble()),
                    min(cellWidth, cellHeight) / 4,
                    color,
                    3
                )
            }
        }
        
        // 转换为Bitmap
        val bitmap = Bitmap.createBitmap(markedMat.width(), markedMat.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(markedMat, bitmap)
        
        return bitmap
    }
}
