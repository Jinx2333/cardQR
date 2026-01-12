package com.example.qrtask.util

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.OpenCVLoaderCallback
import org.opencv.android.LoaderCallbackInterface

/**
 * OpenCV初始化工具类
 */
object OpenCVUtils {
    
    private const val TAG = "OpenCVUtils"
    private var isInitialized = false
    
    /**
     * 初始化OpenCV库
     * 必须在Application的onCreate中调用，或在首次使用OpenCV前调用
     */
    fun initOpenCV(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "OpenCV已经初始化")
            return true
        }
        
        return try {
            // 尝试使用initLocal()初始化（适用于已打包.so文件的情况）
            val success = OpenCVLoader.initLocal()
            if (success) {
                isInitialized = true
                Log.d(TAG, "OpenCV初始化成功")
                
                // 打印OpenCV版本信息
                try {
                    val version = org.opencv.core.Core.VERSION
                    Log.d(TAG, "OpenCV版本: $version")
                } catch (e: Exception) {
                    Log.d(TAG, "无法获取OpenCV版本信息")
                }
            } else {
                Log.w(TAG, "OpenCV initLocal()失败，将尝试异步初始化")
                // 如果initLocal失败，尝试异步初始化
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, object : OpenCVLoaderCallback(context) {
                    override fun onManagerConnected(status: Int) {
                        when (status) {
                            LoaderCallbackInterface.SUCCESS -> {
                                isInitialized = true
                                Log.d(TAG, "OpenCV异步初始化成功")
                            }
                            else -> {
                                Log.e(TAG, "OpenCV异步初始化失败，状态码: $status")
                            }
                        }
                    }
                })
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV初始化异常", e)
            // 即使异常也尝试异步初始化
            try {
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, object : OpenCVLoaderCallback(context) {
                    override fun onManagerConnected(status: Int) {
                        if (status == LoaderCallbackInterface.SUCCESS) {
                            isInitialized = true
                            Log.d(TAG, "OpenCV异步初始化成功（异常恢复）")
                        }
                    }
                })
            } catch (e2: Exception) {
                Log.e(TAG, "OpenCV异步初始化也失败", e2)
            }
            false
        }
    }
    
    /**
     * 检查OpenCV是否已初始化
     */
    fun isOpenCVInitialized(): Boolean {
        return isInitialized
    }
}
