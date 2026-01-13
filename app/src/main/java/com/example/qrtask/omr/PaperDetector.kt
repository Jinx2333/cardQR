package com.example.qrtask.omr

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 答题卡边界检测器
 * 用于实时检测答题卡的四个角点
 */
class PaperDetector {
    
    companion object {
        private const val TAG = "PaperDetector"
        
        // 检测参数
        private const val MIN_CONTOUR_AREA_RATIO = 0.2f  // 最小轮廓面积比例
        private const val APPROX_POLY_EPSILON_RATIO = 0.02f  // 多边形近似精度
        private const val STABILITY_THRESHOLD = 10f  // 稳定性阈值（像素）
        private const val STABILITY_FRAME_COUNT = 10  // 稳定帧数
    }
    
    /**
     * 检测结果
     */
    data class DetectionResult(
        val corners: List<PointF>?,  // 检测到的四个角点（null表示未检测到）
        val isStable: Boolean  // 是否稳定
    )
    
    /**
     * 检测答题卡边界
     * @param bitmap 输入图片
     * @return DetectionResult
     */
    fun detectPaperCorners(bitmap: Bitmap): DetectionResult {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        try {
            // 预处理
            val processedMat = preprocessForDetection(mat)
            
            // 查找轮廓
            val corners = findCorners(processedMat, bitmap.width, bitmap.height)
            
            processedMat.release()
            
            return DetectionResult(
                corners = corners,
                isStable = corners != null
            )
        } catch (e: Exception) {
            Log.e(TAG, "检测失败", e)
            return DetectionResult(null, false)
        } finally {
            mat.release()
        }
    }
    
    /**
     * 预处理图片用于检测
     */
    private fun preprocessForDetection(mat: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val thresholded = Mat()
        val inverted = Mat()
        val dilated = Mat()
        
        try {
            // 转换为灰度图
            if (mat.channels() == 3 || mat.channels() == 4) {
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(gray)
            }
            
            // 降采样以提高速度（可选）
            val scaleFactor = if (gray.cols() > 800) 800.0 / gray.cols() else 1.0
            val resized = if (scaleFactor < 1.0) {
                val resizedMat = Mat()
                Imgproc.resize(gray, resizedMat, Size(0.0, 0.0), scaleFactor, scaleFactor, Imgproc.INTER_AREA)
                resizedMat
            } else {
                gray
            }
            
            // 高斯模糊（模糊文字，突出边缘）
            Imgproc.GaussianBlur(resized, blurred, Size(11.0, 11.0), 0.0)
            
            // 自适应阈值
            Imgproc.adaptiveThreshold(
                blurred,
                thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
            
            // 反转（使边界为白色）
            Core.bitwise_not(thresholded, inverted)
            
            // 膨胀（连接断开的边缘）
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(5.0, 5.0)
            )
            Imgproc.dilate(inverted, dilated, kernel)
            
            if (scaleFactor < 1f) {
                resized.release()
            }
            
            gray.release()
            blurred.release()
            thresholded.release()
            inverted.release()
            
            return dilated
        } catch (e: Exception) {
            gray.release()
            blurred.release()
            thresholded.release()
            inverted.release()
            dilated.release()
            throw e
        }
    }
    
    /**
     * 查找四个角点
     */
    private fun findCorners(binaryMat: Mat, originalWidth: Int, originalHeight: Int): List<PointF>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        
        try {
            Imgproc.findContours(
                binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            val imageArea = binaryMat.rows() * binaryMat.cols()
            val minArea = imageArea * MIN_CONTOUR_AREA_RATIO
            
            var bestContour: MatOfPoint? = null
            var bestArea = 0.0
            
            // 查找最大的符合条件的轮廓
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                
                if (area < minArea) continue
                if (!Imgproc.isContourConvex(contour)) continue
                
                // 多边形近似
                val contour2f = MatOfPoint2f()
                contour.convertTo(contour2f, CvType.CV_32F)
                val epsilon = Imgproc.arcLength(contour2f, true) * APPROX_POLY_EPSILON_RATIO
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
                contour2f.release()
                
                // 检查是否有4个顶点
                if (approx.rows() == 4) {
                    if (area > bestArea) {
                        bestArea = area
                        bestContour = contour
                    }
                }
                
                approx.release()
            }
            
            hierarchy.release()
            
            if (bestContour == null) {
                return null
            }
            
            // 提取四个角点
            val contour2f = MatOfPoint2f()
            bestContour.convertTo(contour2f, CvType.CV_32F)
            val epsilon = Imgproc.arcLength(contour2f, true) * APPROX_POLY_EPSILON_RATIO
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
            contour2f.release()
            
            if (approx.rows() != 4) {
                approx.release()
                return null
            }
            
            val pointArray = approx.toArray()
            val points = pointArray.map { point ->
                PointF(point.x.toFloat(), point.y.toFloat())
            }
            
            // 缩放回原始尺寸（如果进行了降采样）
            val scaleX = originalWidth.toFloat() / binaryMat.cols()
            val scaleY = originalHeight.toFloat() / binaryMat.rows()
            
            val scaledPoints = points.map { point ->
                PointF(point.x * scaleX, point.y * scaleY)
            }
            
            // 排序角点：左上、右上、右下、左下
            val sortedPoints = sortCorners(scaledPoints)
            
            approx.release()
            
            return sortedPoints
        } catch (e: Exception) {
            hierarchy.release()
            Log.e(TAG, "查找角点失败", e)
            return null
        }
    }
    
    /**
     * 排序角点：左上、右上、右下、左下
     */
    private fun sortCorners(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points
        
        // 计算中心点
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        
        // 分类：左上、右上、右下、左下
        val topLeft = points.minByOrNull { it.x + it.y } ?: points[0]
        val topRight = points.maxByOrNull { it.x - it.y } ?: points[1]
        val bottomRight = points.maxByOrNull { it.x + it.y } ?: points[2]
        val bottomLeft = points.minByOrNull { it.x - it.y } ?: points[3]
        
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }
}

/**
 * 稳定性检测器
 */
class StabilityChecker {
    private var previousCorners: List<PointF>? = null
    private var stableFrameCount = 0
    
    companion object {
        private const val STABILITY_THRESHOLD = 15f  // 像素阈值
        private const val STABILITY_FRAME_COUNT = 10  // 需要稳定的帧数
    }
    
    /**
     * 检查当前检测是否稳定
     * @param currentCorners 当前检测到的角点
     * @return 是否稳定
     */
    fun checkStability(currentCorners: List<PointF>?): Boolean {
        if (currentCorners == null || currentCorners.size != 4) {
            stableFrameCount = 0
            previousCorners = null
            return false
        }
        
        if (previousCorners == null) {
            previousCorners = currentCorners
            stableFrameCount = 1
            return false
        }
        
        // 计算角点移动距离
        var maxDistance = 0f
        for (i in currentCorners.indices) {
            val prev = previousCorners!![i]
            val curr = currentCorners[i]
            val distance = sqrt(
                (curr.x - prev.x) * (curr.x - prev.x) + 
                (curr.y - prev.y) * (curr.y - prev.y)
            )
            maxDistance = maxOf(maxDistance, distance)
        }
        
        if (maxDistance < STABILITY_THRESHOLD) {
            stableFrameCount++
            if (stableFrameCount >= STABILITY_FRAME_COUNT) {
                return true
            }
        } else {
            stableFrameCount = 0
        }
        
        previousCorners = currentCorners
        return false
    }
    
    fun reset() {
        previousCorners = null
        stableFrameCount = 0
    }
}
