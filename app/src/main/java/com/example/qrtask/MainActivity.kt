package com.example.qrtask

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.qrtask.ui.theme.QRTaskTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化 OpenCV
        // 使用 initDebug() 是最通用的方法，它会自动尝试加载 app 里的 native 库
        val isOpenCVLoaded = try {
            OpenCVLoader.initDebug()
        } catch (e: Exception) {
            Log.e("OpenCV", "Unable to load OpenCV", e)
            false
        }

        if (isOpenCVLoaded) {
            Log.i("OpenCV", "OpenCV loaded successfully")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed!")
        }

        enableEdgeToEdge()

        setContent {
            QRTaskTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 2. 将加载状态传递给 Greeting
                    Greeting(
                        name = "Android",
                        isOpenCVLoaded = isOpenCVLoaded,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, isOpenCVLoaded: Boolean, modifier: Modifier = Modifier) {
    val statusText = if (isOpenCVLoaded) {
        "OpenCV 库加载成功! ✅"
    } else {
        "OpenCV 库加载失败! ❌"
    }

    Text(
        text = "Hello $name!\n$statusText",
        modifier = modifier,
        color = if (isOpenCVLoaded) Color.Green else Color.Red
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    QRTaskTheme {
        Greeting("Android", isOpenCVLoaded = true)
    }
}
