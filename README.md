# 博物馆导游应用 🏛️

**Museum Guide** — 一款 Android 导游应用，利用摄像头识别文物并自动提供语音讲解，同时配有博物馆导游图。

---

## 功能简介

### 📷 摄像头扫描识别
打开应用即启动摄像头预览，对准展品即可实时检测。检测基于设备端 **TensorFlow Lite**（MobileNet SSD COCO 模型），无需网络连接即可运行。

### ⏱️ 2秒停留自动触发
对准同一件展品持续 **2 秒**，自动触发讲解——无需手动操作，真正做到"即扫即讲"。

### 🔊 语音讲解（TTS）
识别到展品后自动播放语音介绍（中文，语速 0.9x），支持多段内容朗读。同时集成 **Google Gemini AI** 能力，当本地数据匹配时可生成更丰富的 AI 增强描述。

### 🗺️ 博物馆导游图
集成 **Google Maps SDK**，在博物馆地图上标注所有展品位置。点击标记可直接跳转到展品详情页，方便规划参观路线。

### 📖 展品详情页
查看展品的完整信息，包括名称、年代、详细描述和历史意义，支持手动播放/暂停语音。

### 🖼️ 相册图片识别
支持从相册选择图片进行离线识别，检测结果（物体名称 + 置信度）实时显示在屏幕上。

### 🤖 AI 导游讲解
集成 **Google Gemini 2.0 Flash** API，实现 AI 驱动的导游讲解：
- **本地匹配**：TTS 朗读本地数据 → 延时 3 秒 → 追加 AI 增强描述
- **无匹配**：调用 Gemini API 实时生成文物介绍
- **降级策略**：未配置 API Key 时自动使用内置 15 种标签的中文介绍

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | **Kotlin** |
| 构建 | **Gradle Kotlin DSL**（AGP 8.2.0, Kotlin 1.9.20） |
| 最低 SDK | **API 24**（Android 7.0） |
| 架构 | **MVVM** + Navigation Component + ViewBinding |
| 摄像头 | **CameraX**（预览、帧捕获） |
| 物体检测 | **TensorFlow Lite**（MobileNet SSD COCO） |
| 地图 | **Google Maps SDK** |
| 数据库 | **Room**（本地文物数据存储） |
| 语音 | **Android TTS**（TextToSpeech） |
| AI | **Google Gemini 2.0 Flash**（OkHttp + Gson） |
| 测试 | JUnit 4, Mockito, Robolectric, MockWebServer |

---

## 环境要求

| 项目 | 要求 |
|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更新 |
| JDK | 17 |
| Android SDK | compileSdk 34, build tools 34+ |
| Gradle | 8.x（由 wrapper 管理） |
| 设备/模拟器 | API 24+（Android 7.0+），arm64-v8a |

---

## 编译

### 1️⃣ 克隆项目

```bash
git clone https://github.com/jameswdb/museum-guide.git
cd museum-guide
```

### 2️⃣ 准备构建环境

首次构建前需要完成以下准备工作：

#### a) 生成 Gradle Wrapper

```bash
gradle wrapper
```

生成 `gradlew` 和 `gradlew.bat`（Windows）文件。

#### b) 添加 TFLite 模型文件

下载 MobileNet SSD COCO 模型文件 `detect.tflite`，放入：

```
app/src/main/assets/detect.tflite
```

模型可从 [TensorFlow 官方模型库](https://www.tensorflow.org/lite/models/object_detection/overview) 下载。

#### c) 配置 Google Maps API Key

在 `local.properties` 中添加：

```properties
MAPS_API_KEY=你的Google Maps API密钥
```

获取 API Key：[Google Cloud Console](https://console.cloud.google.com/google/maps-apis)

> **注意**：未配置时地图页不可用，其他功能不受影响。

#### d) 配置 Gemini AI API Key（可选）

在 `local.properties` 中添加：

```properties
GEMINI_API_KEY=你的Gemini API密钥
```

或在构建时通过命令行传入：

```bash
./gradlew assembleDebug -PGEMINI_API_KEY=你的密钥
```

免费获取 API Key：[Google AI Studio](https://aistudio.google.com/apikey)

> **注意**：未配置时 AI 增强讲解功能自动降级，使用内置本地化描述。

### 3️⃣ 构建 APK

```bash
# 构建调试 APK
./gradlew assembleDebug

# 构建发布 APK
./gradlew assembleRelease
```

### 📦 APK 输出位置

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 编译测试工程

### 运行单元测试

项目包含 **36 个单元测试**，覆盖三个核心模块：

| 测试文件 | 测试数量 | 覆盖模块 |
|---------|---------|---------|
| `ObjectDetectorTest.kt` | 19 | TFLite 物体检测功能 |
| `ExhibitRepositoryTest.kt` | 7 | 文物数据仓库与标签映射 |
| `AiTourGuideServiceTest.kt` | 10 | Gemini AI 导游服务 |

#### 运行所有单元测试

```bash
./gradlew test
```

#### 运行指定模块测试

```bash
./gradlew app:test
```

#### 运行指定测试类

```bash
# macOS / Linux
./gradlew app:test --tests "com.example.museumguide.detection.ObjectDetectorTest"

# Windows PowerShell
./gradlew app:test --tests "com.example.museumguide.detection.ObjectDetectorTest"
```

### 测试报告

测试完成后，HTML 报告生成在：

```
app/build/reports/tests/testDebugUnitTest/index.html
```

可在浏览器中打开查看详细测试结果。

### 插桩测试（需模拟器/真机）

项目还包含 5 个 Room 数据库插桩测试（`ExhibitDaoTest.kt`），需要 Android 设备或模拟器运行：

```bash
./gradlew connectedAndroidTest
```

### 测试框架

| 框架 | 用途 |
|------|------|
| JUnit 4 | 测试运行器和断言 |
| Mockito + Mockito-Kotlin | 对象模拟 |
| Robolectric | Android 框架模拟（无需模拟器） |
| OkHttp MockWebServer | HTTP API 模拟 |
| kotlinx-coroutines-test | 协程测试 |
| Room Testing | 数据库测试辅助 |

---

## 项目结构

```
viewP/
├── app/
│   ├── build.gradle.kts              # 应用模块配置
│   ├── proguard-rules.pro            # 混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # 权限和组件声明
│       │   ├── assets/               # TFLite 模型等静态资源
│       │   ├── java/com/example/museumguide/
│       │   │   ├── MainActivity.kt         # 入口 Activity，TTS 管理
│       │   │   ├── camera/CameraManager.kt # CameraX 封装
│       │   │   ├── detection/ObjectDetector.kt  # TFLite 物体检测
│       │   │   ├── ai/AiTourGuideService.kt # Gemini AI 导游讲解
│       │   │   ├── exhibit/ExhibitRepository.kt # 文物数据仓库
│       │   │   ├── map/MuseumMapFragment.kt # 博物馆导游图
│       │   │   ├── model/（Exhibit.kt, Dao, Database）
│       │   │   └── ui/（CameraFragment, ExhibitDetailFragment）
│       │   └── res/                   # 布局、导航、资源
│       └── test/                      # 单元测试
│           └── java/com/example/museumguide/
│               ├── detection/ObjectDetectorTest.kt
│               ├── exhibit/ExhibitRepositoryTest.kt
│               └── ai/AiTourGuideServiceTest.kt
├── build.gradle.kts                  # 项目级配置
├── settings.gradle.kts               # 项目设置
├── gradle.properties                 # Gradle 属性
└── gradle/wrapper/                   # Gradle Wrapper 配置
```

---

## 架构说明

### 核心流程：摄像头 → 识别 → 讲解

```
CameraX 预览帧 → ObjectDetector 推理 → 检测结果
                                          ↓
                                   2秒停留检测
                                          ↓
                                   ExhibitRepository 查询
                                          ↓
                                   显示信息浮层 + TTS 语音
                                          ↓
                                   (可选) Gemini AI 增强描述
```

### 2秒停留检测机制

- 每 3 帧（约 10-15 FPS）执行一次检测
- 同一标签持续出现 ≥ 2000ms 触发介绍
- 标签变化或无检测时立即重置计时器
- 触发后设置标记防止重复触发（标签丢失后可重新触发）

### 已知限制

- 当前使用通用 COCO 模型，只能识别 80 类常见物体，无法准确识别特定文物
- 生产环境需训练博物馆专属文物识别模型
- 地图使用标准化坐标占位，需替换为实际博物馆坐标
- 示例数据仅 6 条，需替换为真实文物数据库
