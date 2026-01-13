package com.example.qrtask.omr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * OMR 答题卡识别器（基于锚点的行检测）
 * 使用投影轮廓和时序标记来检测行和列
 */
class OmrGrader {
    
    companion object {
        // 块布局配置
        const val NUM_BLOCKS = 2  // 垂直块（列）的数量
        const val ROWS_PER_BLOCK = 20  // 每个块的行数（题目数）
        const val NUM_OPTIONS = 4  // 选项数（A, B, C, D）
        
        // 图像增强参数
        const val UPSCALE_FACTOR = 2.0  // 上采样倍数
        
        // 投影检测参数
        const val MIN_PEAK_DISTANCE = 20  // 峰值之间的最小距离（像素）
        const val PEAK_THRESHOLD_RATIO = 0.3f  // 峰值阈值比例（相对于平均投影值）
        
        // 评分参数
        const val BUBBLE_RADIUS = 15  // 气泡检测半径（像素）
        const val INTENSITY_THRESHOLD = 100  // 强度阈值（0-255，越低越暗）
    }
    
    // 存储最近一次检测的行中心（用于调试）
    private var lastDetectedRowCenters: List<Int> = emptyList()
    
    /**
     * 识别答题卡答案
     * @param bitmap ML Kit 已经裁剪和透视校正后的图片
     * @return 答案列表（整数：0=A, 1=B, 2=C, 3=D, -1=未作答）
     */
    fun recognizeAnswers(bitmap: Bitmap): List<Int> {
        // 将 Bitmap 转换为 OpenCV Mat
        val originalMat = Mat()
        Utils.bitmapToMat(bitmap, originalMat)
        
        try {
            // Step 1: 图像增强
            val enhancedMat = enhanceImage(originalMat)
            
            // Step 2: 行检测（基于垂直投影）
            val rowCenters = detectRows(enhancedMat)
            lastDetectedRowCenters = rowCenters  // 保存用于调试
            
            if (rowCenters.size < ROWS_PER_BLOCK * NUM_BLOCKS) {
                // 如果检测到的行数不足，回退到简单的网格方法
                return fallbackGridMethod(enhancedMat)
            }
            
            // Step 3: 列检测和答案识别
            val answers = detectAnswersWithAnchors(enhancedMat, rowCenters)
            
            enhancedMat.release()
            return answers
        } finally {
            originalMat.release()
        }
    }
    
    /**
     * Step 1: 图像增强
     * 上采样、CLAHE对比度增强、自适应阈值
     */
    private fun enhanceImage(mat: Mat): Mat {
        val gray = Mat()
        val upscaled = Mat()
        val claheResult = Mat()
        val thresholded = Mat()
        
        try {
            // 转换为灰度图
            if (mat.channels() == 3 || mat.channels() == 4) {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(gray)
            }
            
            // 上采样（2倍）
            Imgproc.resize(
                gray,
                upscaled,
                Size(0.0, 0.0),
                UPSCALE_FACTOR,
                UPSCALE_FACTOR,
                Imgproc.INTER_CUBIC
            )
            
            // CLAHE 对比度增强
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(upscaled, claheResult)
            // CLAHE 对象不需要手动释放，由 OpenCV 管理
            
            // 自适应阈值
            Imgproc.adaptiveThreshold(
                claheResult,
                thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,
                2.0
            )
            
            gray.release()
            upscaled.release()
            claheResult.release()
            
            return thresholded
        } catch (e: Exception) {
            gray.release()
            upscaled.release()
            claheResult.release()
            thresholded.release()
            throw e
        }
    }
    
    /**
     * Step 2: 行检测（基于垂直投影）
     * 使用投影轮廓找到时序标记（黑色块）的位置
     */
    private fun detectRows(binaryMat: Mat): List<Int> {
        val rowCenters = mutableListOf<Int>()
        
        // 裁剪内部区域（去除边缘）
        val marginRatio = 0.05f
        val startX = (binaryMat.cols() * marginRatio).toInt()
        val startY = (binaryMat.rows() * marginRatio).toInt()
        val endX = (binaryMat.cols() * (1 - marginRatio)).toInt()
        val endY = (binaryMat.rows() * (1 - marginRatio)).toInt()
        
        val roiWidth = endX - startX
        val roiHeight = endY - startY
        val blockWidth = roiWidth / NUM_BLOCKS
        
        // 对每个块进行行检测
        for (blockIndex in 0 until NUM_BLOCKS) {
            val blockStartX = startX + blockIndex * blockWidth
            val blockEndX = blockStartX + blockWidth
            
            // 提取块区域（使用左侧边缘区域来检测时序标记）
            val timingMarkWidth = blockWidth / 10  // 时序标记区域宽度（左侧10%）
            val timingMarkRoi = Rect(
                blockStartX,
                startY,
                timingMarkWidth,
                roiHeight
            )
            val timingMat = Mat(binaryMat, timingMarkRoi)
            
            // 垂直投影：计算每行的非零像素数
            val projection = DoubleArray(timingMat.rows())
            for (y in 0 until timingMat.rows()) {
                val row = Mat(timingMat, Rect(0, y, timingMat.cols(), 1))
                projection[y] = Core.countNonZero(row).toDouble()
                row.release()
            }
            
            timingMat.release()
            
            // 计算平均投影值
            val avgProjection = projection.average()
            val peakThreshold = avgProjection * (1 + PEAK_THRESHOLD_RATIO)
            
            // 找到峰值（时序标记的位置）
            val peaks = mutableListOf<Int>()
            for (y in MIN_PEAK_DISTANCE until projection.size - MIN_PEAK_DISTANCE) {
                if (projection[y] > peakThreshold) {
                    // 检查是否是局部最大值
                    var isPeak = true
                    for (dy in -MIN_PEAK_DISTANCE / 2 until MIN_PEAK_DISTANCE / 2) {
                        if (dy != 0 && projection[y + dy] > projection[y]) {
                            isPeak = false
                            break
                        }
                    }
                    if (isPeak) {
                        peaks.add(y + startY)  // 转换回原始坐标
                    }
                }
            }
            
            // 如果检测到的峰值数量接近预期，使用它们
            // 否则使用均匀分布
            if (peaks.size >= ROWS_PER_BLOCK * 0.8) {
                // 选择最接近预期数量的峰值
                peaks.sort()
                val selectedPeaks = if (peaks.size > ROWS_PER_BLOCK) {
                    // 如果峰值太多，均匀选择
                    val step = peaks.size / ROWS_PER_BLOCK
                    peaks.filterIndexed { index, _ -> index % step == 0 }.take(ROWS_PER_BLOCK)
                } else {
                    peaks
                }
                rowCenters.addAll(selectedPeaks)
            } else {
                // 回退到均匀分布
                val rowHeight = roiHeight / ROWS_PER_BLOCK
                for (rowIndex in 0 until ROWS_PER_BLOCK) {
                    rowCenters.add(startY + rowIndex * rowHeight + rowHeight / 2)
                }
            }
        }
        
        return rowCenters.sorted()
    }
    
    /**
     * Step 3: 列检测和答案识别（基于锚点）
     */
    private fun detectAnswersWithAnchors(binaryMat: Mat, rowCenters: List<Int>): List<Int> {
        val answers = mutableListOf<Int>()
        
        val marginRatio = 0.05f
        val startX = (binaryMat.cols() * marginRatio).toInt()
        val endX = (binaryMat.cols() * (1 - marginRatio)).toInt()
        val roiWidth = endX - startX
        val blockWidth = roiWidth / NUM_BLOCKS
        
        var questionIndex = 0
        
        // 对每个块处理
        for (blockIndex in 0 until NUM_BLOCKS) {
            val blockStartX = startX + blockIndex * blockWidth
            val blockEndX = blockStartX + blockWidth
            
            // 获取该块的行中心
            val blockRowStart = blockIndex * ROWS_PER_BLOCK
            val blockRowEnd = (blockIndex + 1) * ROWS_PER_BLOCK
            
            for (rowIndex in blockRowStart until blockRowEnd) {
                if (rowIndex >= rowCenters.size) break
                
                val rowCenterY = rowCenters[rowIndex]
                
                // 提取该行的水平区域
                val rowHeight = BUBBLE_RADIUS * 2
                val rowTop = (rowCenterY - rowHeight / 2).coerceAtLeast(0)
                val rowBottom = (rowCenterY + rowHeight / 2).coerceAtMost(binaryMat.rows())
                
                val rowRoi = Rect(
                    blockStartX,
                    rowTop,
                    blockWidth,
                    rowBottom - rowTop
                )
                val rowMat = Mat(binaryMat, rowRoi)
                
                // 水平投影：计算每列的平均强度
                val optionWidth = blockWidth / NUM_OPTIONS
                val optionIntensities = mutableListOf<Pair<Int, Double>>()
                
                for (optionIndex in 0 until NUM_OPTIONS) {
                    val optionStartX = optionIndex * optionWidth
                    val optionEndX = (optionIndex + 1) * optionWidth
                    
                    // 提取选项区域
                    val optionRoi = Rect(
                        optionStartX,
                        0,
                        optionEndX - optionStartX,
                        rowMat.rows()
                    )
                    val optionMat = Mat(rowMat, optionRoi)
                    
                    // 计算平均强度（非零像素比例）
                    val nonZeroCount = Core.countNonZero(optionMat)
                    val totalPixels = optionMat.rows() * optionMat.cols()
                    val intensity = if (totalPixels > 0) {
                        (nonZeroCount.toDouble() / totalPixels) * 255.0
                    } else {
                        0.0
                    }
                    
                    optionIntensities.add(Pair(optionIndex, intensity))
                    optionMat.release()
                }
                
                rowMat.release()
                
                // 找到最暗的选项（强度最低）
                val darkestOption = optionIntensities.minByOrNull { it.second }
                
                if (darkestOption != null && darkestOption.second < INTENSITY_THRESHOLD) {
                    answers.add(darkestOption.first)  // 0=A, 1=B, 2=C, 3=D
                } else {
                    answers.add(-1)  // 未作答
                }
                
                questionIndex++
            }
        }
        
        return answers
    }
    
    /**
     * 回退方法：简单的网格切片（当投影检测失败时使用）
     */
    private fun fallbackGridMethod(binaryMat: Mat): List<Int> {
        val answers = mutableListOf<Int>()
        
        val marginRatio = 0.05f
        val startX = (binaryMat.cols() * marginRatio).toInt()
        val startY = (binaryMat.rows() * marginRatio).toInt()
        val endX = (binaryMat.cols() * (1 - marginRatio)).toInt()
        val endY = (binaryMat.rows() * (1 - marginRatio)).toInt()
        
        val roiWidth = endX - startX
        val roiHeight = endY - startY
        val blockWidth = roiWidth / NUM_BLOCKS
        val rowHeight = roiHeight / ROWS_PER_BLOCK
        val optionWidth = blockWidth / NUM_OPTIONS
        
        for (blockIndex in 0 until NUM_BLOCKS) {
            val blockStartX = startX + blockIndex * blockWidth
            
            for (rowIndex in 0 until ROWS_PER_BLOCK) {
                val rowStartY = startY + rowIndex * rowHeight
                
                val optionIntensities = mutableListOf<Pair<Int, Double>>()
                
                for (optionIndex in 0 until NUM_OPTIONS) {
                    val optionStartX = blockStartX + optionIndex * optionWidth
                    val optionRoi = Rect(
                        optionStartX,
                        rowStartY,
                        optionWidth,
                        rowHeight
                    )
                    val optionMat = Mat(binaryMat, optionRoi)
                    
                    val nonZeroCount = Core.countNonZero(optionMat)
                    val totalPixels = optionMat.rows() * optionMat.cols()
                    val intensity = if (totalPixels > 0) {
                        (nonZeroCount.toDouble() / totalPixels) * 255.0
                    } else {
                        0.0
                    }
                    
                    optionIntensities.add(Pair(optionIndex, intensity))
                    optionMat.release()
                }
                
                val darkestOption = optionIntensities.minByOrNull { it.second }
                if (darkestOption != null && darkestOption.second < INTENSITY_THRESHOLD) {
                    answers.add(darkestOption.first)
                } else {
                    answers.add(-1)
                }
            }
        }
        
        return answers
    }
    
    /**
     * 在图像上绘制检测到的行（用于调试）
     * @param bitmap 原始图片
     * @return 绘制了红色线条的图片
     */
    fun drawDetectedRows(bitmap: Bitmap): Bitmap? {
        if (lastDetectedRowCenters.isEmpty()) {
            return null
        }
        
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        
        // 绘制红色水平线标记检测到的行
        // 注意：rowCenters 是基于增强后的图像坐标（上采样了 UPSCALE_FACTOR 倍），需要缩放回原始尺寸
        val scaleY = 1.0f / UPSCALE_FACTOR.toFloat()
        val bitmapHeight = resultBitmap.height.toFloat()
        for (y in lastDetectedRowCenters) {
            val scaledY = y * scaleY
            if (scaledY >= 0f && scaledY <= bitmapHeight) {
                canvas.drawLine(0f, scaledY, resultBitmap.width.toFloat(), scaledY, paint)
            }
        }
        
        return resultBitmap
    }
    
    /**
     * 获取最近一次检测到的行中心（用于调试）
     */
    fun getLastDetectedRowCenters(): List<Int> = lastDetectedRowCenters
}
