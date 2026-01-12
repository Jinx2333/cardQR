package com.example.qrtask

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.example.qrtask.util.OpenCVUtils

/**
 * Application类 - 初始化Hilt和OpenCV
 */
@HiltAndroidApp
class OmrApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化OpenCV
        OpenCVUtils.initOpenCV(this)
    }
}
