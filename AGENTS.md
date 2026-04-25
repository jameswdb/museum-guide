# AGENTS.md - Project Guidance

## Overview
博物馆导游应用 (Museum Guide App) — Android应用，使用摄像头识别文物并自动提供语音讲解，同时提供博物馆导游图。

## Tech Stack
- **语言**: Kotlin
- **构建**: Gradle Kotlin DSL, Android Gradle Plugin 8.2.0, Kotlin 1.9.20
- **最低SDK**: API 24 (Android 7.0)
- **架构**: MVVM, Navigation Component, ViewBinding
- **关键依赖**:
  - CameraX (摄像头预览与帧捕获)
  - TensorFlow Lite (设备端物体检测)
  - Google Maps SDK (博物馆导游图)
  - Room (本地文物数据库)
  - Navigation Component (页面导航)
  - Material Design (UI组件)
  - Android TTS (语音讲解)

## Commands
```bash
# 构建调试APK
./gradlew assembleDebug

# 构建发布APK
./gradlew assembleRelease

# 运行测试
./gradlew test

# 运行指定模块测试
./gradlew app:test
```

**注意**: 首次构建前需要执行以下操作：
1. 运行 `gradle wrapper` 生成 `gradlew` 和 `gradlew.bat`
2. 添加 TensorFlow Lite 模型文件到 `app/src/main/assets/detect.tflite`
3. 在 `local.properties` 或 `AndroidManifest.xml` 中配置 Google Maps API Key

## Project Structure
```
viewP/
├── app/
│   ├── build.gradle.kts              # 应用模块配置
│   ├── proguard-rules.pro            # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml       # 权限和组件声明
│       ├── assets/                   # TFLite模型等静态资源
│       ├── java/com/example/museumguide/
│       │   ├── MainActivity.kt       # 入口Activity，管理TTS
│       │   ├── camera/
│       │   │   └── CameraManager.kt  # CameraX封装
│       │   ├── detection/
│       │   │   └── ObjectDetector.kt # TFLite物体检测
│       │   ├── exhibit/
│       │   │   └── ExhibitRepository.kt  # 文物数据仓库
│       │   ├── map/
│       │   │   └── MuseumMapFragment.kt  # Google Maps导游图
│       │   ├── model/
│       │   │   ├── Exhibit.kt        # 文物数据实体
│       │   │   ├── ExhibitDao.kt     # Room DAO
│       │   │   └── ExhibitDatabase.kt # Room数据库
│       │   └── ui/
│       │       ├── CameraFragment.kt       # 摄像头扫描界面
│       │       └── ExhibitDetailFragment.kt # 文物详情界面
│       └── res/                      # 布局、导航、资源
├── build.gradle.kts                  # 项目级配置
├── settings.gradle.kts               # 项目设置
├── gradle.properties                 # Gradle属性
└── gradle/wrapper/
    └── gradle-wrapper.properties     # Gradle版本配置
```

## Architecture Notes

### 摄像头 → 识别 → 讲解 流程
1. `CameraManager` 通过 CameraX 获取预览帧
2. 帧数据转换成 Bitmap 后传给 `ObjectDetector`
3. `ObjectDetector` 运行 TFLite 推理，返回检测结果
4. `CameraFragment` 跟踪同一标签持续2秒后触发讲解
5. 通过 `ExhibitRepository` 查询文物数据
6. 显示文物信息浮层并调用 `MainActivity.speak()` 播放TTS

### 2秒停留检测逻辑
`CameraFragment` 维护 `currentDetectionLabel` 和 `detectionStartTime`：
- 每 N 帧 (frameInterval=3) 运行一次检测
- 同一标签持续出现 ≥2000ms 触发介绍
- 标签变化或无检测时重置计时器

### 博物馆导游图
- 使用 Google Maps SDK 展示博物馆平面图
- 文物位置使用标准化坐标 (0.0–1.0) 映射到 LatLng
- 点击标记跳转到文物详情页
- 生产环境应替换为博物馆实景地图瓦片

### 文物数据
- 使用 Room 本地数据库存储
- `ExhibitRepository` 管理数据访问
- 首次启动通过 `seedSampleData()` 写入示例数据
- 通用检测标签（如 "vase"）到文物编码的映射在 `LABEL_TO_CODE` 中

## Conventions
- **包命名**: `com.example.museumguide.*`
- **视图绑定**: 使用 ViewBinding 访问视图
- **协程**: 使用 `lifecycleScope` 启动协程，IO操作在 `Dispatchers.IO`
- **导航**: 使用 Navigation Component，底部导航栏切换摄像头/地图

## Required Setup Before Building
1. **Gradle Wrapper**: 运行 `gradle wrapper` 生成 `gradlew`/`gradlew.bat`
2. **TFLite Model**: 下载 `detect.tflite` (MobileNet SSD COCO) 放入 `app/src/main/assets/`
3. **Google Maps API Key**: 在 `local.properties` 添加 `MAPS_API_KEY=your_key`
4. **Android SDK**: 确保 compileSdk 34 和 build tools 已安装

## Known Limitations
- 当前使用通用 COCO 模型只能识别常见物体，无法准确识别特定文物
- 生产环境需要训练博物馆专属的文物识别模型
- 地图使用模拟坐标，需替换为实际博物馆地图数据
- 示例数据仅6条，需替换为真实文物数据库

## License
Private project — no license specified.
