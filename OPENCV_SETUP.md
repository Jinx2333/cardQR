# OpenCV Android SDK 设置指南

## 方法一：使用Maven依赖（推荐）

当前项目已配置使用Maven Central的OpenCV依赖。如果构建时遇到问题，请尝试以下方法：

### 1. 检查依赖版本

确保 `gradle/libs.versions.toml` 中的OpenCV版本正确：
```toml
opencv = "4.10.0"
```

### 2. 如果Maven依赖不可用，使用手动集成方式

#### 步骤1：下载OpenCV Android SDK
1. 访问 [OpenCV官网](https://opencv.org/releases/)
2. 下载 Android 版本的 OpenCV SDK（例如：opencv-4.10.0-android-sdk.zip）
3. 解压到本地目录

#### 步骤2：导入OpenCV模块
1. 在Android Studio中，选择 `File` -> `New` -> `Import Module`
2. 选择解压后的 `sdk` 目录
3. 模块名称保持为 `opencv`（或自定义）

#### 步骤3：添加模块依赖
在 `settings.gradle.kts` 中添加：
```kotlin
include(":opencv")
```

在 `app/build.gradle.kts` 的 dependencies 中替换：
```kotlin
// 移除这行：
// implementation(libs.opencv.android)

// 添加：
implementation(project(":opencv"))
```

#### 步骤4：配置NDK
确保 `app/build.gradle.kts` 中已配置：
```kotlin
defaultConfig {
    ndk {
        abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }
}
```

## 方法二：使用JitPack（备选）

如果Maven Central不可用，可以在 `build.gradle.kts` 中添加JitPack仓库：

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

然后使用：
```kotlin
implementation("com.github.opencv:opencv:4.10.0")
```

## 验证安装

运行应用后，检查Logcat中是否有以下日志：
```
OpenCVUtils: OpenCV初始化成功
OpenCVUtils: OpenCV版本: 4.10.0
```

如果看到这些日志，说明OpenCV已成功集成。

## 常见问题

### 问题1：找不到OpenCV类
- 确保已正确添加依赖
- 清理并重新构建项目（Build -> Clean Project，然后 Build -> Rebuild Project）

### 问题2：运行时崩溃，提示找不到.so文件
- 确保NDK配置正确
- 检查是否包含了所需的ABI（armeabi-v7a, arm64-v8a等）

### 问题3：初始化失败
- 检查Application类是否正确初始化OpenCV
- 查看Logcat中的详细错误信息
