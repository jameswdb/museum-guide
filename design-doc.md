# 博物馆导游应用 — 产品设计文档
# Museum Guide App — Product Design Document

---

## 1. 产品概述 / Product Overview

### 1.1 产品定位 / Positioning

"博物馆导游"（Museum Guide）是一款面向博物馆参观者的 Android 导游应用，利用设备端摄像头识别和 AR 技术，为用户提供"即扫即讲"的沉浸式导览体验。用户只需将手机摄像头对准展品，等待 2 秒即可自动获得语音讲解和图文介绍。

Museum Guide is an Android companion app for museum visitors that delivers a "point-and-learn" experience using on-device camera recognition and TTS narration. Users simply point their phone camera at an exhibit, hold steady for 2 seconds, and receive automatic audio narration with rich text details.

### 1.2 核心价值 / Core Value Proposition

| 用户痛点 | 解决方案 |
|---------|---------|
| 展品标签信息有限 | 扫描后获得详细的文物历史、文化意义介绍 |
| 租用讲解器麻烦、成本高 | 自带手机即可，零成本 |
| 排队等待人工讲解 | 即时识别，无需等待 |
| 容易错过重要展品 | 导游图标记所有展品位置，引导参观 |
| 外文展品看不懂 | 本地化语音讲解 |

### 1.3 目标用户群体 / Target Audience

- **Primary**: 18–45 岁博物馆参观者，习惯于使用智能手机获取信息
- **Secondary**: 家庭游客（孩子可通过图像识别互动学习）
- **Tertiary**: 视障人士（依赖语音导航和讲解）

---

## 2. 用户画像与用例 / User Personas & Use Cases

### 2.1 用户画像 / Personas

#### Persona A: 李明（32岁，自由行游客）
- **背景**: 历史爱好者，周末独自参观博物馆
- **期望**: 深入了解每件文物的历史背景
- **痛点**: 租讲解器排队太久，手机信号在地下展厅不好
- **典型场景**: 进入展厅 → 打开 App → 扫描文物 → 听完整讲解 → 查看地图找下一个感兴趣的展品

#### Persona B: 王芳（40岁，带孩子的家长）
- **背景**: 带 8 岁儿子参观，希望孩子能学到知识
- **期望**: 孩子能自己操作，有趣的交互方式
- **痛点**: 孩子注意力难以集中，需要视觉+听觉双重刺激
- **典型场景**: 孩子举手机扫描 → 看到动画提示 → 听到语音 → 点击"了解更多"看图片

#### Persona C: 张老师（55岁，历史教师）
- **背景**: 带学生团参观，需要全面了解展品信息
- **期望**: 能提前预览展品列表，规划参观路线
- **痛点**: 时间有限，需要快速找到重点展品
- **典型场景**: 提前浏览地图 → 规划路线 → 现场快速核验 → 标记已看完的展品

### 2.2 核心用例 / Core Use Cases

| ID | 用例名称 | 描述 | 优先级 |
|----|---------|------|-------|
| UC-01 | 摄像头扫描识别 | 用户打开摄像头，实时检测展品 | P0 |
| UC-02 | 2秒停留触发讲解 | 对准展品停留2秒，自动播放语音 | P0 |
| UC-03 | 语音讲解播放 | TTS朗读展品简介和历史背景 | P0 |
| UC-04 | 查看展品详情 | 点击"了解更多"查看完整图文 | P0 |
| UC-05 | 博物馆导游图 | 查看所有展品在博物馆中的位置 | P0 |
| UC-06 | 地图标记导航 | 点击标记跳转到展品详情 | P1 |
| UC-07 | 展品浏览列表 | 按展厅浏览所有展品 | P1 |
| UC-08 | 参观路线规划 | 推荐高效参观路线 | P2 |
| UC-09 | 收藏/标记已看 | 标记已看过的展品 | P2 |
| UC-10 | 多语言切换 | 切换语音和文字语言 | P2 |

---

## 3. 功能规格说明 / Feature Specifications

### 3.1 Feature F-01: 摄像头扫描识别 (Camera Scan & Recognition)

#### 3.1.1 当前实现分析 / Current Implementation Review

| 组件 | 当前实现 | 状态 |
|------|---------|------|
| CameraX 预览 | PreviewView 全屏预览 + 250x250dp 扫描框 | ✅ 已实现 |
| CameraManager | 启动前后摄像头，YUV→Bitmap 转换 | ✅ 已实现 |
| ObjectDetector | MobileNet SSD (COCO)，置信度阈值 0.5 | ✅ 已实现 |
| 帧率控制 | 每 3 帧分析一次 (frameInterval=3) | ✅ 已实现 |
| 扫描框 UI | CardView 描边，半透明暗色遮罩 | ⚠️ 需优化 |
| 检测可视化 | 无 bounding box 绘制 | ❌ 缺失 |
| 置信度指示器 | 无 | ❌ 缺失 |
| 加载状态 | ProgressBar 存在但从未显示 (visibility=gone) | ❌ 未使用 |

#### 3.1.2 设计规范 / Design Specification

**扫描界面布局 (自上而下):**
1. **状态栏区域**: 沉浸式，透明
2. **检测结果指示器**: 右上角小圆点（红/黄/绿表示检测置信度）
3. **扫描框**: 居中 250x250dp，圆角 8dp，带有扫描动画（边框流光效果）
4. **半透明遮罩**: 扫描框外部暗化，框内亮度正常
5. **Bounding Box**: 检测到物体时在预览上绘制绿色矩形框 + 标签
6. **底部提示**: "对准文物，停留2秒获取介绍"（有检测时渐隐）
7. **Loading Indicator**: 模型加载时显示，加载完成后隐藏

**扫描流程:**
```
[进入CameraFragment]
    ↓
[检查摄像头权限] → 未授权 → [请求权限] → 拒绝 → [显示提示]
    ↓ 已授权
[初始化ObjectDetector (background)]
    ↓
[初始化CameraManager → startCamera()]
    ↓
[onFrameCaptured 回调 → frameSkip % 3 == 0 时分析]
    ↓
[ObjectDetector.detect() 返回 Detection 列表]
    ↓
[取 top 检测结果] → [置信度 < 0.5] → [重置 dwell]
    ↓ 置信度 ≥ 0.5
[绘制 bounding box 和标签]
    ↓
[进入2秒停留逻辑 (F-02)]
```

#### 3.1.3 建议改进 / Recommended Improvements

1. **激活加载状态**: ProgressBar 在 `onViewCreated` 时 visible，`objectDetector.initialize()` 完成后 gone
2. **添加 Bounding Box 绘制**: 在 PreviewView 上层叠加一个自定义 SurfaceView 或重写 `onDraw` 绘制检测框
3. **置信度指示器**: 用颜色圆点实时显示检测置信度（>0.8 绿色，>0.5 黄色，<0.5 红色）
4. **扫描框动画**: 四个角有动态流光线，吸引用户视线聚焦
5. **遮罩优化**: 改为扫描框区域透明/外部暗化，而非全屏暗化

---

### 3.2 Feature F-02: 2秒停留检测 (2-Second Dwell Detection)

#### 3.2.1 当前实现分析 / Current Implementation Review

| 方面 | 当前实现 | 状态 |
|------|---------|------|
| 停留计时 | `System.nanoTime()` 差值，≥2000ms 触发 | ✅ 已实现 |
| 标签跟踪 | `currentDetectionLabel` 单标签跟踪 | ✅ 已实现 |
| 重复触发保护 | `introductionTriggered` 布尔标志 | ⚠️ 简单实现 |
| 无检测重置 | 无检测或置信度低时调用 `resetDwell()` | ✅ 已实现 |
| 标签变化重置 | 新标签时重置计时器 | ✅ 已实现 |

#### 3.2.2 设计规范 / Design Specification

**停留检测状态机:**
```
                ┌──────────────────────────────┐
                │          IDLE                 │
                │  (currentLabel = null)        │
                └──────┬───────────────────────┘
                       │ 检测到标签 L (conf ≥ 0.5)
                       ▼
                ┌──────────────────────────────┐
                │     TRACKING (L)              │◄──── 同标签持续
                │  startTime = now              │
                │  elapsed < 2000ms             │
                └──────┬───────────────────────┘
                       │ elapsed ≥ 2000ms
                       ▼
                ┌──────────────────────────────┐
                │    TRIGGERED (L)              │
                │  → show overlay               │
                │  → TTS narration              │
                │  → introductionTriggered=true │
                └──────┬───────────────────────┘
                       │ 标签变化 / 丢失
                       ▼
                ┌──────────────────────────────┐
                │          IDLE                 │
                └──────────────────────────────┘
```

**平滑检测建议 (改进):**
- 引入置信度滑动窗口（最近 5 帧），取平均置信度
- 标签切换使用多数表决（majority vote over 3 frames）
- 置信度阈值动态调整：光线不足时自动降低阈值

#### 3.2.3 建议改进 / Recommended Improvements

1. **置信度平滑**: 使用滑动窗口平均，避免单帧抖动导致误判
2. **标签稳定性**: 连续 3 帧中 ≥2 帧为同一标签才被认为"稳定"
3. **重复触发冷却**: 同一标签触发后设置 30 秒冷却期，期间不重复触发
4. **视觉反馈**: 跟踪期间显示进度环或进度条（倒计时动画——"还剩 1.5 秒..."）
5. **手动触发备用**: 提供"识别"按钮，用户点击后立即检测并触发

---

### 3.3 Feature F-03: 语音讲解 (TTS Narration)

#### 3.3.1 当前实现分析 / Current Implementation Review

| 方面 | 当前实现 | 状态 |
|------|---------|------|
| TTS 引擎 | Android `TextToSpeech`, `Locale.CHINESE` | ✅ 已实现 |
| 语速调整 | `speechRate = 0.9f` | ✅ 已实现 |
| 内容选择 | CameraFragment 只朗读 `exhibit.brief` | ⚠️ 内容太短 |
| 多段朗读 | ExhibitDetailFragment 朗读 name + era + description + significance | ✅ 完整 |
| 队列策略 | `QUEUE_FLUSH`（打断当前播放下一条） | ✅ 已实现 |
| 播放控制 | `speak()` / `silence()` 在 MainActivity | ✅ 已实现 |
| 生命周期 | `onDestroy` 中 shutdown | ✅ 已实现 |

#### 3.3.2 设计规范 / Design Specification

**语音分层策略:**
```
首次识别 → 朗读 brief（简短介绍，15秒内）
  ↓
用户停留继续 → 朗读 description（详细描述，30-60秒）
  ↓
用户点击"了解更多" → 进入详情页 → 朗读完整内容
  ↓
用户点击"播放"按钮 → toggle play/pause
```

**建议改进:**
1. **分层朗读**: 首次只读 brief，若用户继续对准则追加 description
2. **播放控制按钮**: 在 info overlay 中添加暂停/继续按钮
3. **跳过功能**: 点击屏幕任意位置可跳过当前段落
4. **音量反馈**: 朗读时显示音量波形动画
5. **TTS 状态指示**: 朗读时显示"🔊 正在讲解..."提示

---

### 3.4 Feature F-04: 博物馆导游图 (Museum Map)

#### 3.4.1 当前实现分析 / Current Implementation Review

| 方面 | 当前实现 | 状态 |
|------|---------|------|
| 地图 SDK | Google Maps SDK → SupportMapFragment | ✅ 已实现 |
| 展品标记 | MarkerOptions，颜色 HUE_ORANGE | ✅ 已实现 |
| 坐标映射 | normalizedToLatLng() (0~1 → LatLng) | ⚠️ 占位实现 |
| 点击交互 | setOnMarkerClickListener → 导航到详情页 | ✅ 已实现 |
| 缩放范围 | 15f ~ 22f | ✅ 已实现 |
| 地图标题 | MaterialCardView 顶栏 "博物馆导游图" | ✅ 已实现 |
| 展厅过滤 | 无（显示所有展品） | ❌ 缺失 |
| 自定义瓦片 | 无（MAP_TYPE_NORMAL） | ❌ 缺失 |
| 用户位置 | 无 | ❌ 缺失 |
| 路线导航 | 无 | ❌ 缺失 |

#### 3.4.2 设计规范 / Design Specification

**地图界面布局:**
```
┌─────────────────────────────┐
│  [←] 博物馆导游图    [🔍]    │  ← 顶部导航栏
├─────────────────────────────┤
│                             │
│   [展品A]          [展品C]  │
│                             │
│         [展品B]            │  ← Google Map
│                             │
│   [展品D]    [展品E]       │
│                             │
├─────────────────────────────┤
│ [A厅] [B厅] [C厅] [全部]   │  ← 展厅过滤器 chips
└─────────────────────────────┘
```

**标记设计:**
- 未查看: 橙色标记
- 已查看: 灰色标记
- 选中: 放大 + 信息卡片弹出
- 标记分组: 缩放层级较小时，邻近展品聚合为一个计数标记

**建议改进:**
1. **展厅过滤器**: 底部 ChipGroup 按 hallId 过滤展品
2. **自定义地图瓦片**: 上传博物馆平面图作为 GroundOverlay
3. **用户位置**: 请求位置权限，显示用户蓝点
4. **展品搜索**: 顶部搜索框，按名称搜索展品
5. **参观路线**: 预设"精华路线""亲子路线"等多条路线
6. **标记状态**: 在数据库中增加 `isVisited` 字段，记录用户已看过的展品
7. **地图缩略图**: 加载时使用缩略图占位，避免白屏

---

### 3.5 Feature F-05: 展品详情页 (Exhibit Detail)

#### 3.5.1 当前实现分析 / Current Implementation Review

| 方面 | 当前实现 | 状态 |
|------|---------|------|
| 展品名称 | TextView `exhibit_name` | ✅ 已实现 |
| 朝代年代 | TextView `exhibit_era` | ✅ 已实现 |
| 详细描述 | TextView `exhibit_description` | ✅ 已实现 |
| 历史意义 | TextView `exhibit_significance`，灰色背景 | ✅ 已实现 |
| 语音播放 | MaterialButton `btn_listen`，toggle 播放/停止 | ✅ 已实现 |
| 展品图片 | ImageView `exhibit_image`，灰色占位背景 | ❌ 未加载实际图片 |
| 滚动 | ScrollView 包裹 | ✅ 已实现 |

#### 3.5.2 建议改进 / Recommended Improvements

1. **图片加载**: 使用 Coil 或 Glide 加载 `exhibit.imageUrl`
2. **图片占位**: 加载中显示 shimmer 效果
3. **相关展品推荐**: 底部展示同展厅的其他展品，可横向滑动
4. **分享功能**: 分享展品信息到社交平台
5. **收藏按钮**: 点击收藏，存入本地收藏列表
6. **语音进度**: 显示 TTS 播放进度条，可拖动
7. **画廊模式**: 多张展品图片可左右滑动浏览

---

## 4. 用户流程 / User Flow (Text-Based)

### 4.1 主流程 / Main Flow

```
[启动 App]
    |
    ▼
[ MainActivity ]
    |── 初始化 TTS (Locale.CHINESE, rate=0.9)
    |── 设置 BottomNavigation + NavHostFragment
    |── 默认目的地: CameraFragment
    |
    ├──→ [Tab: 扫描] ─────────────────────────────────────────┐
    |       |                                                  |
    |       ▼                                                  |
    |   [CameraFragment]                                       |
    |       |── 检查 CAMERA 权限                               |
    |       |── 初始化 ObjectDetector (IO)                     |
    |       |── 初始化 CameraManager → startCamera()           |
    |       |── 设置 onFrameCaptured 回调                      |
    |       |                                                    |
    |       ▼ (每3帧)                                          |
    |   [processFrame]                                         |
    |       |── ObjectDetector.detect(bitmap)                  |
    |       |── handleDetectionResult(detections)              |
    |       |                                                    |
    |       ├── 无检测 → resetDwell()                          |
    |       ├── 新标签 → 开始计时                              |
    |       └── 同标签 ≥2秒 → onExhibitRecognised() ──────┐   |
    |              |                                        |   |
    |              ▼                                        |   |
    |          [ showExhibitInfo() ]                        |   |
    |              |── 显示 info overlay (名称/年代/简介)   |   |
    |              |── startNarration(exhibit.brief)        |   |
    |              |── TTS 开始朗读                          |   |
    |              |                                        |   |
    |              ▼                                        |   |
    |          [点击 "文物详情" 按钮]                        |   |
    |              |── navigate(R.id.action_camera_to_...) ──┤──┤
    |              |                                         |  |
    |              ▼                                         |  |
    |          [ ExhibitDetailFragment ] ◄───────────────────┘  |
    |              |── 加载展品数据 (Room)                     |
    |              |── 显示完整描述 + 意义                     |
    |              |── TTS 全文朗读                            |
    |              |── 点击 "播放/停止" 控制音频              |
    |                                                          |
    ├──→ [Tab: 地图] ─────────────────────────────────────────┘
    |       |
    |       ▼
    |   [MuseumMapFragment]
    |       |── 初始化 Google Map
    |       |── 加载所有展品 Marker
    |       |── 点击 Marker → navigate(R.id.action_map_to_...)
    |       |       └──→ ExhibitDetailFragment
    |       |
    |       └── (建议: 展厅过滤 ChipGroup)
    |
    └──→ [后台生命周期管理]
            |── onDestroy → TTS shutdown
            |── onDestroyView → ObjectDetector.close()
            |── onDestroyView → CameraManager.shutdown()
```

### 4.2 错误处理流程 / Error Handling Flow

```
[启动 App]
    |
    ├── TTS 初始化失败 ──────────────────────────────────────┐
    |       |── onInit status != SUCCESS                     |
    |       |── 静默降级：不播放语音，只显示文字            |
    |       └── "语音不可用" Toast                           |
    |                                                         |
    ├── Camera 权限被拒 ─────────────────────────────────────┐
    |       |── 显示权限说明对话框                           |
    |       |── 引导用户到系统设置手动开启                  |
    |       └── 禁用扫描功能，保留地图Tab                   |
    |                                                         |
    ├── TFLite 模型加载失败 ────────────────────────────────┐
    |       |── detect() 返回 emptyList                      |
    |       |── 显示 "识别模型加载失败" 错误状态            |
    |       └── 提供重试按钮                                 |
    |                                                         |
    ├── 检测到但未匹配展品 ─────────────────────────────────┐
    |       |── OnExhibitRecognised 中 exhibit == null       |
    |       |── 显示 "未识别到文物，请调整角度"             |
    |       |── resetDwell()                                 |
    |       └── 振动反馈 (短振)                              |
    |                                                         |
    └── Google Maps 加载失败 ───────────────────────────────┐
            |── onMapReady 未回调                            |
            |── 检查 Google Play Services                    |
            |── 显示 "地图加载失败" + 重试按钮              |
            └── (建议: 备用的静态展品列表视图)               |
```

---

## 5. UI/UX 设计建议 / UI/UX Design Recommendations

### 5.1 视觉设计 / Visual Design

#### 色彩体系 / Color System

| Token | 当前值 | 建议 | 说明 |
|-------|--------|------|------|
| primary | `#1565C0` | 保持 | 深蓝——可靠、专业、博物馆气质 |
| secondary | `#FF8F00` | 保持 | 琥珀色——高亮、引导注意力 |
| background | `#FFF8E1` | `#FAFAF5` | 米白——更柔和的阅读背景 |
| overlay_dark | `#88000000` | `#99000000` | 更暗的遮罩以突出扫描框 |
| detector_green | — | `#4CAF50` | 检测成功/高置信度 |
| detector_yellow | — | `#FFC107` | 检测中/中等置信度 |
| detector_red | — | `#F44336` | 无检测/低置信度 |

#### 字体 / Typography

- **展品名称**: 24sp, Bold, Noto Sans SC
- **正文描述**: 16sp, Regular, lineSpacingExtra=6sp
- **标注/副文本**: 14sp, Medium, #666666
- **扫描提示**: 16sp, Regular, 白色 80% 透明度

#### 扫描界面动画 / Scan UI Animations

1. **扫描框流光**: 四个角有流动光线（顺时针旋转），周期 3s
2. **检测到物体**: 框边闪烁 → 变为绿色 → 缩小到 bounding box
3. **停留进度**: 底部提示显示圆形进度条 "识别中... 1.2s/2.0s"
4. **识别成功**: 轻微振动 + 成功音效（可选） + 底部 overlay 滑入
5. **TTS 朗读**: 信息卡片上出现声波动画

### 5.2 信息架构 / Information Architecture

```
App
├── Tab 1: 扫描 (CameraFragment)
│   ├── 摄像头预览 (全屏)
│   ├── 扫描框 (居中 250x250dp)
│   ├── 检测信息叠加层 (底部)
│   │   ├── 展品名称
│   │   ├── 年代
│   │   ├── 简短介绍
│   │   ├── 🔊 暂停/继续 按钮 (建议)
│   │   └── [了解更多] 按钮 → ExhibitDetailFragment
│   └── 置信度指示器 (右上角)
│
├── Tab 2: 地图 (MuseumMapFragment)
│   ├── Google Map (全屏)
│   ├── 顶栏 "博物馆导游图"
│   ├── 展厅过滤器 (底部 ChipGroup) (建议)
│   ├── 搜索框 (顶部) (建议)
│   └── Marker → ExhibitDetailFragment
│
└── Detail: 展品详情 (ExhibitDetailFragment)
    ├── 展品图片 (建议: 使用 Coil/Glide)
    ├── 展品名称
    ├── 年代
    ├── 详细描述
    ├── 历史意义
    ├── [播放/停止] 按钮
    ├── 相关推荐 (横向滑动) (建议)
    └── [收藏] 按钮 (建议)
```

### 5.3 交互细节 / Interaction Details

| 交互 | 行为 | 反馈 |
|------|------|------|
| 对准展品 | 持续 0~2 秒 | 扫描框四角流动光效 + 底部圆形进度条 |
| 对准展品 | 持续 ≥2 秒 | 振动 (HapticFeedback) + overlay 滑入 |
| 点击"了解更多" | 导航到详情页 | 页面从右向左滑入 (slide_in_right) |
| 点击地图 Markers | 弹出信息卡片 | Marker 弹跳动画 |
| TTS 播放中切换 Tab | 停止当前播放 | 语音立即停止 |
| 权限被拒后重新请求 | 引导用户到设置 | 说明为什么需要该权限 |

---

## 6. 错误处理与边界情况 / Error Handling & Edge Cases

### 6.1 已知问题 / Known Issues

| # | 问题 | 影响 | 严重性 | 建议修复 |
|---|------|------|--------|---------|
| 1 | COCO 模型只能识别通用物体，无法准确识别特定文物 | 识别准确率低 | 🔴 高 | 训练博物馆专属模型 |
| 2 | `scan_hint_text` 在识别后仍占用布局空间 (visibility=GONE) | UI 正常但浪费空间 | 🟢 低 | 当前做法可接受 |
| 3 | Loading indicator 始终 GONE，从未使用 | 用户无加载反馈 | 🟡 中 | 在模型初始化时 visible |
| 4 | `ExhibitDetailFragment` 创建了独立的 TTS 实例 | 重复初始化，资源浪费 | 🟡 中 | 复用 MainActivity 的 TTS |
| 5 | `MuseumMapFragment` 使用独立 CoroutineScope 而非 lifecycleScope | 生命周期不安全 | 🟡 中 | 替换为 `viewLifecycleOwner.lifecycleScope` |
| 6 | 坐标映射使用北京坐标占位 | 地图位置不准确 | 🟡 中 | 替换为实际博物馆坐标 |
| 7 | `imageUrl` 字段始终为空字符串 | 详情页图片无法显示 | 🟡 中 | 添加实际图片 URL |
| 8 | 地图无用户位置 | 用户无法知道自己在地图上的位置 | 🔴 高 | 添加位置权限 + 我的位置 |
| 9 | 重复触发保护只有单次 boolean | 标签丢失后重识别可以再次触发（正确）但无冷却期 | 🟢 低 | 可添加冷却期防止干扰 |
| 10 | 没有展厅内展品列表入口 | 用户只能通过扫描或地图发现展品 | 🟡 中 | 添加展品列表 Fragment |

### 6.2 边界情况 / Edge Cases

| 场景 | 预期行为 |
|------|---------|
| 摄像头被其他 App 占用 | 捕获异常，显示"摄像头不可用"提示 |
| TTS 语言包未下载 | 降级为文字显示，提示"语音包未安装" |
| 博物馆有 200+ 展品 | 地图标记聚合 (Marker Clustering)，列表分页加载 |
| 用户快速扫过多件展品 | 每次新识别打断当前 TTS (QUEUE_FLUSH)，添加 1s 冷却 |
| 光线极暗的展厅 | 提示"光线不足，请打开闪光灯"（建议添加闪光灯控制） |
| 玻璃展柜反光 | 检测置信度低，提示"请调整角度避免反光" |
| 多件展品同时出现在画面 | 取置信度最高者，UI 标注所有检测到的物体 |
| 网络断开（地图需要网络） | 地图显示缓存瓦片或提示"需要网络加载地图" |
| 应用在后台被回收 | ViewModel 保存当前检测状态，恢复后重新初始化 |
| Android 6.0 以下设备 | 不请求运行时权限，AndroidManifest 声明中静态声明 |

### 6.3 性能考虑 / Performance Considerations

| 方面 | 当前状态 | 建议 |
|------|---------|------|
| 检测帧率 | 每3帧分析，约 10-15 FPS | 保持，足够流畅 |
| 模型大小 | MobileNet SSD ~6MB | 合理 |
| YUV→Bitmap 转换 | 每次转换，开销大 | 考虑直接使用 YUV 输入到 TFLite |
| 内存 | Bitmap 频繁分配 | 复用 Bitmap 对象，使用对象池 |
| 地图瓦片加载 | 依赖网络 | 预缓存博物馆区域瓦片 |
| TTS 初始化 | 每次 Activity onCreate | 保持（单例 TTS） |
| 数据库查询 | Room 自动异步 | 已在 IO 线程执行，OK |

---

## 7. 无障碍设计 / Accessibility Considerations

### 7.1 无障碍需求 / Accessibility Requirements

| 需求 | 实现方式 | 优先级 |
|------|---------|-------|
| 屏幕阅读器支持 | 所有交互元素添加 `contentDescription` | P0 |
| 字体缩放 | 使用 sp 单位，适配系统字体大小 | P0 |
| 色彩对比度 | 文字与背景对比度 ≥ 4.5:1 | P1 |
| 非依赖颜色识别的 UI | 检测状态不仅用颜色，也用文字/图标表示 | P1 |
| 大点击目标 | 按钮大小 ≥ 48x48dp | P0 |
| 振动反馈 | 识别成功时提供 HapticFeedback | P1 |
| 字幕/文字替代 | TTS 内容同时显示在屏幕上 | P0 |
| 简化布局 | 保持线性布局，避免复杂嵌套 | P1 |
| 触觉导航 | 地图标记支持 TalkBack 导航 | P2 |

### 7.2 具体实现指导 / Implementation Guidance

**视觉障碍用户:**
- CameraFragment 默认：识别到展品后自动朗读，无需用户操作
- 所有展品名称和描述使用 `contentDescription`
- 地图支持 TalkBack 焦点导航，每个 Marker 可选中朗读

**听力障碍用户:**
- 所有 TTS 内容同时以文字形式展示在屏幕
- TTS 播放时有可视化提示（如声波动画）
- 支持振动反馈替代声音提示

**行动障碍用户:**
- 底部信息卡片中的所有按钮 ≥ 48dp 高度
- 无需精细操作（停留检测是自动的）
- 所有功能可通过底部 Tab 和明确的按钮完成

**认知障碍用户:**
- 界面简洁，减少干扰元素
- 扫描时有明确视觉引导（扫描框 + 提示文字）
- 信息分层展示（先概要，后详情）

---

## 8. 优先级改进清单 / Prioritized Improvement Backlog

### P0 — 必须修复 (核心体验)

| # | 改进项 | 当前问题 | 影响面 |
|---|--------|---------|-------|
| 1 | 训练博物馆专属识别模型 | COCO 通用模型准确率低 | 识别准确率 |
| 2 | 激活 loading indicator | 用户无模型加载反馈 | 用户体验 |
| 3 | 添加 bounding box 绘制 | 用户看不到检测结果 | 扫描体验 |
| 4 | 添加识别进度可视化 | 2秒等待无反馈 | 停留体验 |
| 5 | 复用 MainActivity TTS | ExhibitDetailFragment 重复创建 TTS | 资源浪费 |
| 6 | 替换地图坐标为实际坐标 | 当前为北京占位坐标 | 地图准确性 |

### P1 — 重要改进 (体验优化)

| # | 改进项 | 说明 |
|---|--------|------|
| 7 | 使用 lifecycleScope 替换独立 scope | MuseumMapFragment 生命周期安全 |
| 8 | 展厅过滤器 (ChipGroup) | 按 hallId 过滤地图上的展品 |
| 9 | 图片加载 (Coil/Glide) | 详情页显示实际展品图片 |
| 10 | 置信度滑动窗口平滑 | 避免单帧抖动误判 |
| 11 | 重复触发冷却期 (30s) | 防止同一展品反复触发 |
| 12 | 分层 TTS 朗读策略 | 首次短介绍，继续对准则详细讲解 |
| 13 | 添加用户位置到地图 | 用户知道自己在哪里 |
| 14 | 添加闪光灯控制 | 光线不足时辅助识别 |

### P2 — 锦上添花 (差异化功能)

| # | 改进项 | 说明 |
|---|--------|------|
| 15 | 展品列表浏览 | 按展厅列出所有展品，可搜索 |
| 16 | 参观路线推荐 | 预设多条参观路线 |
| 17 | 收藏/已看标记 | 记录用户参观进度 |
| 18 | 相关展品推荐 | 详情页底部展示同展厅展品 |
| 19 | 自定义地图瓦片 | 上传博物馆平面图代替默认地图 |
| 20 | 多语言支持 | 英文/日文等多语言讲解 |
| 21 | 离线模式 | 缓存地图和展品数据 |
| 22 | 社交分享 | 分享展品信息到微信/微博 |
| 23 | 语音搜索 | 语音输入搜索展品 |
| 24 | 观展数据统计 | 展示用户参观了多少展品、时长等 |

### P3 — 远期愿景 (长期演进)

| # | 改进项 | 说明 |
|---|--------|------|
| 25 | AR 导航指引 | 摄像头画面上叠加箭头指引到展品 |
| 26 | 展品 3D 模型 | 使用 SceneView 展示 3D 文物模型 |
| 27 | 智能推荐算法 | 根据用户停留时间推荐类似展品 |
| 28 | 线上预约/购票 | 接入博物馆票务系统 |
| 29 | 音频导览下载 | 离线下载专业讲解员录制的音频 |
| 30 | 用户评论/评分 | 用户可对展品发表感想 |
| 31 | AI 问答助手 | 基于 LLM 的回答展品相关问题 |
| 32 | 多博物馆支持 | 一个 App 覆盖多个博物馆 |

---

## 9. 技术债务 / Technical Debt

| # | 项目 | 文件 | 说明 | 难度 |
|---|------|------|------|------|
| TD-01 | 图片加载缺失 | ExhibitDetailFragment | `imageView` 没有设置图片 | 低 |
| TD-02 | 独立协程作用域 | MuseumMapFragment | 使用自定义 scope 而非 lifecycleScope | 低 |
| TD-03 | 重复 TTS 实例 | ExhibitDetailFragment | 独立 TTS，应用层应有单例 | 低 |
| TD-04 | 硬编码标签映射 | ExhibitRepository.LABEL_TO_CODE | 演示映射，生产需替换 | 中 |
| TD-05 | YUV→JPEG→Bitmap 双转换 | CameraManager.proxyToBitmap | 性能浪费，可直接送 ByteBuffer 到 TFLite | 中 |
| TD-06 | 占位坐标 | MuseumMapFragment.normalizedToLatLng | 需要实际博物馆坐标数据 | 中 |
| TD-07 | exportSchema = false | ExhibitDatabase | 缺失 Schema 导出阻碍 Migration 测试 | 低 |
| TD-08 | ✅ 已解决 — 测试覆盖 | 全局 | ObjectDetector(19), ExhibitRepository(7), AiTourGuideService(10) | 已修复 |

---

## 10. 竞品分析 / Competitive Analysis

| 维度 | 本 App | 传统语音导览器 | 扫码看展 | 人工讲解 |
|------|--------|---------------|---------|---------|
| 使用成本 | 免费 | ¥20-50 租金 | 免费 | 免费/付费 |
| 获取信息速度 | 2秒自动触发 | 手动输入编号 | 需寻找二维码 | 排队等待 |
| 信息丰富度 | 图文+语音+地图 | 仅语音 | 仅图文 | 互动性强 |
| 是否需联网 | 地图需要，识别不需要 | 不需要 | 需要 | 不需要 |
| 个性化 | 可暂停、重播、收藏 | 有限 | 有限 | 取决于讲解员 |
| 自主性 | 自由选择参观顺序 | 固定编号顺序 | 需寻找二维码 | 跟随团队 |
| 可扩展性 | 可无限增加展品 | 受硬件限制 | 受二维码限制 | 受人力限制 |

**差异化优势:** 零摩擦体验（无需扫码/输入编号）、自动停留检测、设备端识别无网络依赖、结合导游图。

---

## 11. 附录 / Appendix

### 11.1 参考文件 / Reference Files

| 文件 | 用途 |
|------|------|
| `AGENTS.md` | 项目配置、构建命令、架构笔记 |
| `app/src/main/java/com/example/museumguide/MainActivity.kt` | 入口 Activity，TTS 管理器 |
| `app/src/main/java/com/example/museumguide/camera/CameraManager.kt` | CameraX 封装，帧捕获 |
| `app/src/main/java/com/example/museumguide/detection/ObjectDetector.kt` | TFLite 物体检测 |
| `app/src/main/java/com/example/museumguide/ai/AiTourGuideService.kt` | AI 导游讲解（Gemini API） |
| `app/src/main/java/com/example/museumguide/exhibit/ExhibitRepository.kt` | 数据仓库，标签映射 |
| `app/src/main/java/com/example/museumguide/map/MuseumMapFragment.kt` | 博物馆导游图 |
| `app/src/main/java/com/example/museumguide/model/Exhibit.kt` | 文物数据模型 |
| `app/src/main/java/com/example/museumguide/model/ExhibitDao.kt` | Room DAO |
| `app/src/main/java/com/example/museumguide/model/ExhibitDatabase.kt` | Room 数据库 |
| `app/src/main/java/com/example/museumguide/ui/CameraFragment.kt` | 摄像头扫描 Fragment |
| `app/src/main/java/com/example/museumguide/ui/ExhibitDetailFragment.kt` | 详情页 Fragment |
| `app/src/main/res/layout/fragment_camera.xml` | 扫描界面布局 |
| `app/src/main/res/layout/activity_main.xml` | 主界面布局 |
| `app/src/main/res/layout/fragment_map.xml` | 地图界面布局 |
| `app/src/main/res/layout/fragment_exhibit_detail.xml` | 详情页布局 |
| `app/src/main/res/values/strings.xml` | 字符串资源 |
| `app/src/main/res/values/colors.xml` | 颜色主题 |
| `app/src/main/res/navigation/nav_graph.xml` | 导航图 |

### 11.2 关键架构数据流 / Key Data Flow

```
CameraX PreviewView ───→ CameraManager.onFrameCaptured(Bitmap)
                                  │
                                  ▼
                         ObjectDetector.detect(bitmap)
                                  │
                                  ▼
                         List<Detection>(label, confidence, bbox)
                                  │
                                  ▼
                         CameraFragment.handleDetectionResult()
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
            dwell timer (2s)            exhibit lookup via
                    │               ExhibitRepository.findExhibitByLabel()
                    ▼                           │
            onExhibitRecognised()       ┌───────┴───────┐
                    │                   ▼               ▼
          ┌─────────┴─────────┐   Found in DB?    Not found?
          ▼                   ▼       │               │
    Local exhibit found   No match    │               ▼
          │                   │       │     AiTourGuideService
          ▼                   ▼       │     .generateDescription()
    showExhibitInfo()   showAiGenerated │               │
    startNarration()    Content(label)  │               ▼
          │                   │       │     AiResponse(title, brief,
          │                   ▼       │       description, significance)
          │           populates overlay│               │
          │           with AI response │               │
          │                   │       │               ▼
          └──────────┬────────┘       │     showExhibitInfo() with AI data
                     ▼                │     startNarration() with AI text
            MainActivity.speak(text)  │
                     │                │
                     ▼                │
            TextToSpeech (Locale.CHINESE)
                     │
                     ▼
            (if local match) fetchAndPlayAiNarration(label)
                     │ — delay 3s
                     ▼
            MainActivity.appendSpeak(AI description)
```

**AI增强流程 (当本地有匹配时)**:
```
Local exhibit found → TTS朗读本地brief → 延时3秒 → AiTourGuideService
→ Gemini API → AI生成详细描述 → appendSpeak() 追加到TTS队列
```

**AI替代流程 (当本地无匹配时)**:
```
TFLite检测到标签 → 本地无匹配 → AiTourGuideService → Gemini API
→ 生成完整文物介绍 → 显示AI内容 → TTS朗读
```

### 11.3 AI 导游讲解服务 / AI Tour Guide Service

**实现:** `app/src/main/java/com/example/museumguide/ai/AiTourGuideService.kt`

集成 Google Gemini 2.0 Flash 模型（免费 API）实现 AI 导游讲解功能。

**工作方式:**
1. TFLite 检测到物体标签后，先查询本地数据库
2. 若本地有匹配 → 显示本地数据并朗读，同时触发 AI 生成增强描述（延时 3 秒后追加到 TTS 队列）
3. 若本地无匹配 → 调用 Gemini API 生成完整文物描述，覆盖默认"未识别"状态
4. AI 响应缓存在内存中（ConcurrentHashMap，上限 100 条），避免重复请求

**Gemini API 配置:**
```
1. 访问 https://aistudio.google.com/apikey 获取免费 API Key
2. 设置环境变量 GEMINI_API_KEY 或在 gradle.properties 中添加:
   GEMINI_API_KEY=your_key_here
   或者通过命令行构建:
   ./gradlew assembleDebug -PGEMINI_API_KEY=your_key_here
3. 未配置 API Key 时，应用会自动降级为内置本地化描述（支持 15 种标签的中文介绍）
```

**测试文件:** `app/src/test/java/com/example/museumguide/ai/AiTourGuideServiceTest.kt`
- 10 个测试用例，覆盖：空 API Key 降级、标签特有中文描述、缓存命中/清理、MockWebServer API 模拟（正常 JSON/纯文本/HTTP 错误/空响应）、并发安全
- 依赖: OkHttp MockWebServer

### 11.4 APK 构建与获取 / Build & APK Location

**构建命令:**
```bash
# 调试 APK
./gradlew assembleDebug

# 发布 APK  
./gradlew assembleRelease
```

**APK 输出位置:**
```
app/build/outputs/apk/debug/app-debug.apk   (~41.2 MB)
app/build/outputs/apk/release/app-release.apk
```

**APK 要求:**
- Android SDK: 24+ (Android 7.0+)
- CPU: arm64-v8a (含 TFLite GPU 加速)
- 存储: 约 80 MB（含 TFLite 模型和地图数据）
- 权限: 摄像头、位置（地图）、网络

### 11.5 测试状态 / Test Status

| 测试类型 | 文件 | 数量 | 状态 |
|---------|------|------|------|
| 单元测试 | `ObjectDetectorTest.kt` | 19 | ✅ 全部通过 |
| 单元测试 | `ExhibitRepositoryTest.kt` | 7 | ✅ 全部通过 |
| 单元测试 | `AiTourGuideServiceTest.kt` | 10 | ✅ 全部通过 |
| Instrumented | `ExhibitDaoTest.kt` | 5 | ⏸ 需要 Android 设备/模拟器 |
| **总计** | | **41** | **✅ 36通过 / ⏸ 5跳过** |

---

*文档版本: v1.1 | 最后更新: 2026-04-24 | 状态: Draft*
