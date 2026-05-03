# 博物馆导游应用 — 需求设计文档 & 测试用例设计

> 版本: v1.2 | 最后更新: 2026-05-02

---

## 目录

1. [产品概述](#1-产品概述)
2. [功能模块总览](#2-功能模块总览)
3. [详细需求与交互设计](#3-详细需求与交互设计)
4. [架构设计](#4-架构设计)
5. [数据模型](#5-数据模型)
6. [异常处理与降级策略](#6-异常处理与降级策略)
7. [单元测试用例设计](#7-单元测试用例设计)
8. [功能测试用例设计](#8-功能测试用例设计)

---

## 1. 产品概述

### 1.1 产品定位

博物馆导游应用是一款 Android 端智能导览工具，利用摄像头识别展品并自动提供语音讲解，同时提供博物馆地图导览功能。适用于博物馆、美术馆、展览馆等场景。

### 1.2 目标用户

- 博物馆参观者 — 通过手机摄像头快速获取展品信息
- 自助游客 — 无需租用讲解器，手机即导游
- 文物爱好者 — 浏览中国十大博物馆及镇馆之宝

### 1.3 核心流程

```
用户打开App → [首页] 配置AI/查看博物馆
            → [扫描] CameraX预览 → TFLite物体检测 → 2秒停留确认
              → 本地数据库匹配 → 文物信息浮层 + TTS语音讲解
              → (可选) AI增强描述 → appendSpeak追加朗读
            → [地图] Google Maps → 标记点击 → 文物详情页
            → [首页] 博物馆列表 → 博物馆详情 → TTS播报
```

### 1.4 技术指标

| 指标 | 要求 |
|------|------|
| 最低 SDK | API 24 (Android 7.0) |
| 目标 SDK | API 34 |
| 摄像头帧率 | ≥10 FPS (帧间隔=3) |
| 检测延迟 | <500ms (本地TFLite推理) |
| TTS 响应 | 识别后立即播放 (<1s) |
| AI API 超时 | 15s |
| 离线可用 | 基础检测 + 内置描述无需网络 |
| 内存缓存 | AI响应最多缓存100条 |
| 内置数据 | 6条示例文物 + 10座博物馆 + 37件展品 |

---

## 2. 功能模块总览

| 模块 | 优先级 | 复杂度 | 离线可用 |
|------|--------|--------|---------|
| M1 - 导航系统 | P0 | 低 | ✅ |
| M2 - 摄像头扫描 | P0 | 高 | ✅ |
| M3 - TFLite物体检测 | P0 | 高 | ✅ |
| M4 - 2秒停留检测 | P0 | 中 | ✅ |
| M5 - 文物数据库 | P0 | 中 | ✅ |
| M6 - AI导游讲解 | P1 | 高 | ❌ (需网络) |
| M7 - AI增强讲解 | P1 | 中 | ❌ |
| M8 - 相册图片识别 | P1 | 中 | 部分 |
| M9 - TTS语音 | P0 | 低 | ✅ |
| M10 - 博物馆地图 | P1 | 中 | ❌ (需网络) |
| M11 - 文物详情页 | P0 | 低 | ✅ |
| M12 - AI多模型支持 | P1 | 中 | ❌ |
| M13 - 首页设置中心 | P1 | 中 | ✅ |
| M14 - 十大博物馆数据 | P1 | 低 | ✅ |
| M15 - 博物馆TTS播报 | P1 | 低 | ✅ |
| M16 - 权限管理 | P0 | 低 | ✅ |

---

## 3. 详细需求与交互设计

### 3.1 M1 - 导航系统

**需求描述**:
- 底部导航栏包含 3 个标签：首页、扫描、地图
- 首页 (HomeFragment) 为启动目的地
- 扫描页 (CameraFragment) 为摄像头扫描主界面
- 地图页 (MuseumMapFragment) 显示博物馆导游图
- 从扫描页和地图页均可跳转到文物详情页 (ExhibitDetailFragment)
- 从首页可跳转到博物馆详情页 (MuseumDetailFragment)

**交互流程**:
```
[底部导航] 首页 ↔ 扫描 ↔ 地图
[首页] → 点击博物馆卡片 → [博物馆详情]
[扫描] → 2秒识别 → 浮层 → 点击"文物详情" → [文物详情]
[地图] → 点击标记 → [文物详情]
```

**边界条件**:
- 底部导航切换时保留各 Fragment 状态
- 返回键按导航栈回退

### 3.2 M2 - 摄像头扫描

**需求描述**:
- 使用 CameraX 管理摄像头生命周期
- 4:3 比例预览，640×480 分析分辨率
- YUV_420_888 → NV21 → JPEG → Bitmap 转换，旋转校正
- 每 3 帧执行一次物体检测（帧跳过策略）

**边界条件**:
- 无摄像头权限 → 申请权限 → 拒绝则显示提示不启动
- 设备不支持 CameraX → 显示错误信息
- 预览过程中切换页面 → 释放 CameraX 资源

### 3.3 M3 - TFLite 物体检测

**需求描述**:
- 加载 `assets/detect.tflite` (MobileNet SSD COCO)
- 输入: 300×300 RGB Bitmap (uint8 量化)
- 输出: 边界框 / 类别 / 置信度 / 检测数量
- 置信度阈值 0.5，最多返回 10 个结果，按置信度降序排列
- 91 个 COCO 标签（80 类 + 背景 + N/A 占位）

**关键常量**:
| 名称 | 值 |
|------|-----|
| INPUT_SIZE | 300 |
| CONFIDENCE_THRESHOLD | 0.5f |
| MAX_DETECTIONS | 10 |

**边界条件**:
- 模型文件缺失 → initialize() 返回 false → 显示"AI检测不可用"
- 推理异常 → 返回空列表
- 标签索引越界 (≥91) → 返回 "unknown"

### 3.4 M4 - 2秒停留检测

**需求描述**:
- 维护 `currentDetectionLabel` + `detectionStartTime` (nanoTime)
- 同一标签持续 ≥2000ms 触发讲解
- 标签变化或无检测 → 重置计时器
- `introductionTriggered` 标记防止重复触发
- 标签丢失后可重新触发

**状态机**:

```
[无检测] ──检测到标签──→ [跟踪中: 记录label + startTime]
                           │
                           ├─ 同标签持续 <2000ms → [跟踪中]
                           ├─ 同标签持续 ≥2000ms → [已触发: 播放讲解]
                           ├─ 标签变化 → [重置 → 跟踪中: 新标签]
                           └─ 无检测 → [重置 → 无检测]
```

### 3.5 M5 - 文物数据库

**需求描述**:
- Room 数据库 `museum_guide.db`，单例模式
- Exhibit 实体: id, exhibitCode, name, era, brief, description, significance, imageUrl, mapPositionX/Y, hallId
- DAO 操作: getAll, getByCode, getById, getByHall, insertAll, deleteAll, getCount
- 首次启动自动写入 6 条示例文物
- LABEL_TO_CODE 映射: 6 个 COCO 标签 → 文物编码

**示例数据**:

| code | 名称 | 年代 | 映射标签 | 展厅 |
|------|------|------|---------|------|
| vase | 青花瓷瓶 | 明代·永乐 | vase | Hall A |
| vase_2 | 粉彩花卉纹瓶 | 清代·乾隆 | bottle | Hall A |
| bronze | 青铜鼎 | 西周·早期 | scissors | Hall B |
| jade | 玉璧 | 战国 | clock | Hall B |
| painting | 山水画长卷 | 北宋 | tv | Hall C |
| ceramic | 唐三彩骆驼 | 唐代 | potted plant | Hall C |

### 3.6 M6 - AI 导游讲解

**需求描述**:
- 支持 4 种 AI Provider: Gemini / DeepSeek / Qwen / ERNIE
- 统一 `AiProvider` 接口，`AiTourGuideService` 为门面
- 文本生成: 输入检测标签 → 输出结构化中文文物描述
- 多模态识别: 输入图片 → 输出文物描述
- 内置 15 种标签的中文降级描述 + 通用兜底
- 降级链: 已配置 Provider → Gemini(BuildConfig) → 内置描述

**AI 调用降级链 (generateDescription)**:
```
1. 检查缓存 → 命中则返回
2. 检查配置的 Provider → 有 API Key → 调用 → 成功则返回
3. 检查 BuildConfig.GEMINI_API_KEY → 非空 → 调用 Gemini → 成功则返回
4. 返回内置中文降级描述
```

**AI 调用降级链 (generateDescriptionFromImage)**:
```
1. 检查配置的 Provider → 支持多模态 + 有 Key → 调用 → 成功则返回
2. 检查 BuildConfig.GEMINI_API_KEY → 调用 Gemini 多模态 → 成功则返回
3. 返回提示"请配置API密钥"
```

**缓存策略**:
- ConcurrentHashMap，key=标签，value=AiResponse
- 最大 100 条，超出时移除最早条目
- clearCache() 手动清除

### 3.7 M7 - AI 增强讲解

**需求描述**:
- 本地数据库匹配成功 → TTS 播放 brief → **延时 3 秒** → AI 追加描述
- 本地无匹配 → AI 实时生成完整描述 → TTS 播放
- AI 生成中显示 Loading 状态

### 3.8 M8 - 相册图片识别

**需求描述**:
- 右上角相册按钮 → 系统图片选择器
- 全屏遮罩显示选中图片
- 两阶段识别: AI 多模态 → (失败) → TFLite 检测
- TFLite 结果: 显示标签 + 置信度百分比
- 关闭遮罩恢复实时检测

### 3.9 M9 - TTS 语音

**需求描述**:
- Android TextToSpeech 引擎
- 语言: Locale.CHINESE
- 语速: 1.0 (v1.2 从 0.9 调整)
- speak(): QUEUE_FLUSH 中断当前播放
- appendSpeak(): QUEUE_ADD 追加到队列
- silence(): 停止播放

### 3.10 M10 - 博物馆地图

**需求描述**:
- Google Maps SDK, SupportMapFragment
- 标准化坐标 (0.0-1.0) → LatLng (北京占位坐标)
- 橙色标记, 显示名称+年代
- 点击标记 → 跳转文物详情
- 缩放范围 15f–22f, 默认 17f

### 3.11 M11 - 文物详情页

**需求描述**:
- ScrollView 布局
- 名称(24sp bold) / 年代(16sp gray) / 描述(16sp) / 文化意义(灰底卡片)
- 图片占位(灰色背景 CardView)
- TTS 播放/暂停按钮

### 3.12 M12 - AI 多模型支持

**需求描述**:
- 支持 4 种 Provider: Gemini(默认), DeepSeek, Qwen, ERNIE
- Gemini: 专有 API 格式, 支持多模态
- DeepSeek/Qwen/ERNIE: OpenAI 兼容格式
- 配置存储于 SharedPreferences
- 首次调用时从 SharedPreferences 加载配置

**Provider 配置**:

| Provider | Base URL | 模型名 | 多模态 |
|----------|---------|--------|-------|
| Gemini | generativelanguage.googleapis.com | gemini-2.0-flash | ✅ |
| DeepSeek | api.deepseek.com | deepseek-chat | ❌ |
| Qwen | dashscope.aliyuncs.com/compatible-mode/v1 | qwen-turbo | ❌ |
| ERNIE | aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat | ernie-speed-8k | ❌ |

### 3.13 M13 - 首页设置中心

**需求描述**:
- AI 模型选择: RadioButton 组 (4 个 Provider)
- API Key: 密码输入框, 根据选中 Provider 动态切换提示
- 模型名: 可选输入, DeepSeek/Qwen/ERNIE 可见
- 博物馆城市: 手动输入 + GPS 自动定位
- GPS: FusedLocationProviderClient → Geocoder 反向地理编码 → 提取城市

**UI 布局**:
```
┌─────────────────────────────┐
│     博物馆导游 (v1.2)        │
│     欢迎来到博物馆           │
├─────────────────────────────┤
│ 📍 当前博物馆               │
│ [博物馆所在城市_________]   │
│ 当前定位：北京·故宫博物院    │
│ [保存设置] [📡 自动定位]    │
├─────────────────────────────┤
│ 🤖 AI 模型配置              │
│ ○ Google Gemini 2.0 Flash   │
│ ○ DeepSeek (深度求索)       │
│ ○ 通义千问 Qwen (阿里云)    │
│ ○ 文心一言 ERNIE (百度)     │
│ [API Key ______________] 👁 │
│ [模型名(可选)___________]   │
│               [保存AI配置]  │
│ 💡 获取地址: ...            │
├─────────────────────────────┤
│ 🏛️ 中国十大博物馆           │
│ 点击了解博物馆详情和镇馆之宝 │
│ ┌─ 1. 故宫博物院 ────────┐  │
│ │ 📍 北京   5件著名展品   │  │
│ └────────────────────────┘  │
│ ┌─ 2. 中国国家博物馆 ────┐  │
│ │ ...                     │  │
│ └────────────────────────┘  │
│           ...               │
└─────────────────────────────┘
```

### 3.14 M14 - 中国十大博物馆数据

**需求描述**:
- 本地内嵌 10 座博物馆数据 (无需网络)
- 每条包含: 名称/城市/简介/描述/意义/坐标/展品列表
- 共 37 件镇馆之宝, 每件含: 名称/年代/描述

**数据来源**: 权威博物馆公开资料整理, 编码在 `ChinaMuseums` 对象中

### 3.15 M15 - 博物馆 TTS 播报

**需求描述**:
- 博物馆详情页: 展示完整信息 + 展品卡片列表
- "播报博物馆全部介绍" 按钮: 组合朗读(名称+简介+描述+意义+所有展品)
- 单件展品卡片点击: 播放该展品语音
- 播放中按钮切换为"停止播报"

### 3.16 M16 - 权限管理

| 权限 | 申请时机 | 拒绝影响 |
|------|---------|---------|
| CAMERA | 扫描页启动时 | 无法使用摄像头识别 |
| ACCESS_FINE_LOCATION | GPS定位时 | GPS定位失效,可手动输入 |
| INTERNET | (清单声明) | AI/地图不可用 |

---

## 4. 架构设计

### 4.1 分层架构

```
┌──────────────────────────────────────────┐
│              UI Layer                     │
│  HomeFragment  CameraFragment  MapFragment│
│  MuseumDetailFragment  ExhibitDetailFrag  │
└────────────────────┬─────────────────────┘
                     │ ViewBinding + lifecycleScope
┌────────────────────▼─────────────────────┐
│          Service / Manager Layer          │
│  AiTourGuideService  CameraManager       │
│  AiProvider(Gemini/DeepSeek/Qwen/ERNIE)  │
│  ExhibitRepository                       │
└────────────────────┬─────────────────────┘
                     │ Coroutines (Dispatchers.IO)
┌────────────────────▼─────────────────────┐
│            Data Layer                     │
│  Room (ExhibitDao / ExhibitDatabase)      │
│  TFLite (ObjectDetector)                  │
│  SharedPreferences (AiConfiguration)      │
│  ChinaMuseums (内置数据)                  │
│  Google Maps SDK                          │
└──────────────────────────────────────────┘
```

### 4.2 AI Provider 架构

```
                    ┌──────────────────┐
                    │ AiTourGuideService│ (Facade)
                    │ - cache          │
                    │ - fallback desc  │
                    └──────┬───────────┘
                           │ delegates to
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   ┌──────────┐   ┌────────────────┐   ┌──────────┐
   │  Gemini  │   │OpenAiCompatible│   │  ...     │
   │ Provider │   │   Provider     │   │(extend)  │
   └──────────┘   └────────────────┘   └──────────┘
   Gemini API      DeepSeek / Qwen / ERNIE
                    (OpenAI format)
```

### 4.3 导航图

```
[HomeFragment] ──action_home_to_museum_detail──→ [MuseumDetailFragment]
     │                                                 museum_id(int)
     │ (bottom nav)
     ├── [CameraFragment] ──action_camera_to_exhibit_detail──→ [ExhibitDetailFragment]
     │                                                             exhibit_id(long)
     └── [MapFragment] ──action_map_to_exhibit_detail─────────→ [ExhibitDetailFragment]
```

---

## 5. 数据模型

### 5.1 Exhibit (Room Entity)

```
@Entity(tableName = "exhibits")
data class Exhibit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exhibitCode: String,       // 文物编码, 如 "vase"
    val name: String,              // 名称, 如 "青花瓷瓶"
    val era: String,               // 年代, 如 "明代 · 永乐年间"
    val brief: String,             // 一句话简介
    val description: String,       // 完整描述
    val significance: String,      // 文化意义
    val imageUrl: String = "",     // 图片URL
    val mapPositionX: Float = 0f,  // 地图X坐标 (0-1)
    val mapPositionY: Float = 0f,  // 地图Y坐标 (0-1)
    val hallId: String = "main"    // 展厅编号
)
```

### 5.2 MuseumInfo (内置数据)

```
data class MuseumInfo(
    val id: Int,
    val name: String,           // 博物馆名称
    val city: String,            // 所在城市
    val brief: String,           // 简介
    val description: String,     // 完整描述
    val significance: String,    // 文化意义
    val latitude: Double,        // 纬度
    val longitude: Double,       // 经度
    val exhibits: List<MuseumExhibit>  // 镇馆之宝列表
)

data class MuseumExhibit(
    val name: String,        // 展品名称
    val era: String,         // 年代
    val description: String  // 描述
)
```

### 5.3 AiConfiguration (SharedPreferences)

```
data class AiConfiguration(
    val activeProviderId: String,    // 当前选中的Provider
    val geminiApiKey: String,        // Gemini API Key
    val deepseekApiKey: String,      // DeepSeek API Key
    val deepseekModelName: String,   // DeepSeek 模型名
    val qwenApiKey: String,          // Qwen API Key
    val qwenModelName: String,       // Qwen 模型名
    val ernieApiKey: String,         // ERNIE API Key
    val ernieModelName: String,      // ERNIE 模型名
    val museumCity: String,          // 博物馆城市
    val museumLatitude: Double,      // 博物馆纬度
    val museumLongitude: Double      // 博物馆经度
)
```

### 5.4 AiResponse

```
data class AiResponse(
    val title: String,           // 文物中文名称
    val brief: String,           // 一句话简介
    val description: String,     // 详细描述
    val significance: String     // 文化历史意义
)
```

### 5.5 Detection

```
data class Detection(
    val label: String,           // COCO标签 (如 "vase")
    val confidence: Float,       // 置信度 (0-1)
    val boundingBox: RectF       // 边界框 [left, top, right, bottom]
)
```

---

## 6. 异常处理与降级策略

| 场景 | 处理方式 | 用户体验 |
|------|----------|---------|
| TFLite 模型加载失败 | initialize() 返回 false | 显示"AI检测不可用" |
| 相机权限被拒绝 | 不启动 CameraX | 扫描页无预览 |
| GPS 定位失败 | catch 异常 + Toast 提示 | Toast "定位失败" |
| 无 Maps API Key | 地图加载失败 | 地图页空白 |
| 无 AI API Key | 返回内置降级描述 | TTS 播放内置文本 |
| AI API 调用超时 (15s) | catch TimeoutException, 返回降级 | 降级描述 |
| AI API HTTP 错误 | 返回 fallback + 错误提示 | 降级描述 |
| AI 多模态失败 | 降级为 TFLite 检测 | 显示标签+置信度 |
| 相册图片加载失败 | Toast "无法加载图片" | Toast 提示 |
| Gallery AI 识别 | 自动降级 TFLite | 显示 TFLite 结果 |
| 并发缓存满 (100条) | 移除最早条目 | 无感 |
| 数据库为空 | seedSampleData() 写 6 条 | 自动完成 |

---

## 7. 单元测试用例设计

### 7.1 ObjectDetectorTest (19 个用例)

**测试目标**: TFLite 检测结果数据类、COCO 标签常量、后处理逻辑

| # | 用例名称 | 输入 | 预期结果 | 覆盖类型 |
|---|---------|------|---------|---------|
| 1 | Detection 存储所有字段正确 | label="vase", confidence=0.85, rect=(0.1,0.2,0.3,0.4) | 三个字段值均匹配 | 数据类 |
| 2 | Detection 解构函数正确 | Detection("vase",0.85,rect) | component1/2/3 返回正确值 | 数据类 |
| 3 | Detection copy 保留未修改字段 | copy(label="person") | label 变, 其他不变 | 数据类 |
| 4 | Detection toString 包含所有字段 | Detection("vase",0.85,rect) | toString 包含 "vase","0.85","RectF" | 数据类 |
| 5 | COCO_LABELS 索引86是 vase | 索引86 | "vase" | 常量 |
| 6 | COCO_LABELS 共91项 | labels.size | 91 | 常量 |
| 7 | COCO_LABELS 第0项是 background | labels[0] | "background" | 常量 |
| 8 | COCO_LABELS 第90项是 toothbrush | labels[90] | "toothbrush" | 常量 |
| 9 | CONFIDENCE_THRESHOLD = 0.5 | 反射读取 | 0.5f | 常量 |
| 10 | MAX_DETECTIONS = 10 | 反射读取 | 10 | 常量 |
| 11 | numDetections=0 返回空列表 | boxes=[],scores=[] | emptyList() | 后处理 |
| 12 | 所有分数低于阈值返回空列表 | scores=[0.1,0.4] | emptyList() | 后处理-过滤 |
| 13 | 过滤低于0.5的检测 | scores=[0.3,0.9] | 只有1个结果, confidence=0.9 | 后处理-过滤 |
| 14 | 等于阈值0.5的检测被保留 | scores=[0.5] | 1个结果 | 后处理-边界 |
| 15 | 结果按置信度降序排列 | scores=[0.6,0.9,0.7] | 0.9 > 0.7 > 0.6 | 后处理-排序 |
| 16 | 索引86映射到 vase | classes=[86f] | label="vase" | 后处理-映射 |
| 17 | 越界索引返回 unknown | classes=[999f] | label="unknown" | 后处理-异常 |
| 18 | TFLite [top,left,bottom,right] → RectF | boxes=[0.1,0.2,0.3,0.4] | RectF(0.2,0.1,0.4,0.3) | 后处理-转换 |
| 19 | 最多返回 MAX_DETECTIONS 个结果 | 20 个检测, 全部超阈值 | size ≤ 10 | 后处理-上限 |

### 7.2 ExhibitRepositoryTest (7 个用例)

**测试目标**: 标签 → 文物映射、数据种子、查询

| # | 用例名称 | 输入 | 预期结果 | 覆盖类型 |
|---|---------|------|---------|---------|
| 1 | 已知标签"vase"返回文物 | label="vase", DAO 返回 exhibit | 返回 exhibit, code="vase" | 映射-正常 |
| 2 | 未知标签返回 null | label="unknown_label" | null, DAO 未调用 | 映射-异常 |
| 3 | 有效 id 查询返回文物 | id=1L, DAO 返回 | 返回文物对象 | 查询-正常 |
| 4 | 无效 id 查询返回 null | id=999L, DAO 返回 null | null | 查询-异常 |
| 5 | 数据库为空时写入6条 | getCount=0 | verify insertAll 被调用, 6条 | 种子-正常 |
| 6 | 数据库已有数据时跳过 | getCount=5 | verify insertAll 未被调用 | 种子-去重 |
| 7 | 全部6个标签映射正确 | 6 个标签逐一验证 | 每个标签返回对应 code | 映射-全覆盖 |

### 7.3 AiTourGuideServiceTest (10 个用例)

**测试目标**: 降级行为、缓存、并发安全

| # | 用例名称 | 输入 | 预期结果 | 覆盖类型 |
|---|---------|------|---------|---------|
| 1 | 空 API Key 返回降级描述 | label="vase", 无 Key | description 非空, 含中文陶瓷描述 | 降级-正常 |
| 2 | 未知标签返回通用描述 | label="unknown_xyz" | 描述包含标签名或"博物馆藏品" | 降级-通用 |
| 3 | vase 降级包含陶瓷描述 | label="vase" | 描述含"陶瓷"或"瓷" | 降级-中文 |
| 4 | bronze 降级包含青铜描述 | label="bronze" | 描述含"青铜" | 降级-中文 |
| 5 | jade 降级包含玉描述 | label="jade" | 描述含"玉" | 降级-中文 |
| 6 | 同标签第二次调用返回缓存 | label="vase" × 2 | 两次结果相同 (同一实例) | 缓存-命中 |
| 7 | 不同标签返回不同缓存 | "vase" vs "bronze" | title 和 description 不同 | 缓存-区分 |
| 8 | 清除缓存后重新生成 | clearCache + 再次调用 | 返回有效描述 | 缓存-清除 |
| 9 | 并发多标签线程安全 | 6 个标签并发 | 全部有结果, 描述各不同 | 并发-正常 |
| 10 | 同标签并发安全 | 10 次并发请求 "vase" | 全部返回同一实例 | 并发-缓存 |

### 7.4 AiTourGuideServiceMultimodalTest (3 个用例)

**测试目标**: 多模态降级行为 (需 Robolectric)

| # | 用例名称 | 输入 | 预期结果 | 覆盖类型 |
|---|---------|------|---------|---------|
| 1 | 空 Key 多模态返回降级 | 100×100 bitmap, 无 Key | description 非空, 含配置引导 | 降级-多模态 |
| 2 | 降级标题为"图片识别" | bitmap | title == "图片识别" | 降级-标题 |
| 3 | 降级提示配置 API | bitmap | 描述提及 API 或密钥 | 降级-提示 |

### 7.5 ExhibitDaoTest (5 个用例, 插桩测试)

**测试目标**: Room 数据库 CRUD (需模拟器/真机)

| # | 用例名称 | 输入 | 预期结果 | 覆盖类型 |
|---|---------|------|---------|---------|
| 1 | 插入并查询全部 | 3 条 Exhibit | getAllExhibits 返回 3 条 | CRUD-读取 |
| 2 | 按编码查询 | code="A01", 已插入 | 返回对应文物, name="青铜鼎" | CRUD-查询 |
| 3 | 按不存在的编码查询 | code="NONEXISTENT" | 返回 null | CRUD-异常 |
| 4 | 清空数据库 | 插入2条 → deleteAll | getAllExhibits 为空 | CRUD-删除 |
| 5 | 按 ID 查询 | 插入1条 → getById(1L) | 返回文物, code="A01" | CRUD-ID查询 |

### 7.6 新增推荐单元测试用例

**测试目标**: AI 多模型配置、Provider 逻辑、博物馆数据完整性

| # | 模块 | 用例名称 | 输入 | 预期结果 |
|---|------|---------|------|---------|
| 1 | AiConfiguration | 默认配置为 Gemini | 新构造 AiConfiguration() | activeProviderId="gemini" |
| 2 | AiConfiguration | 保存后读取配置一致 | 写 config → 读 config | 所有字段一致 |
| 3 | AiConfiguration | 4 个 Provider 信息完整 | AVAILABLE_PROVIDERS | size=4, 各字段非空 |
| 4 | AiConfiguration | isProviderConfigured 正确 | 设 geminiApiKey="x" | isProviderConfigured(GEMINI)=true |
| 5 | AiConfiguration | getActiveApiKey 返回正确 | 切换 Provider | 返回对应 API Key |
| 6 | OpenAiCompatibleProvider | 构建 DeepSeek Provider | deepSeek() | providerId="deepseek" |
| 7 | OpenAiCompatibleProvider | 构建 Qwen Provider | qwen() | providerId="qwen", 正确 baseUrl |
| 8 | OpenAiCompatibleProvider | 构建 ERNIE Provider | ernie() | providerId="ernie", 正确 baseUrl |
| 9 | OpenAiCompatibleProvider | 多模态返回 null | generateDescriptionFromImage | null |
| 10 | GeminiProvider | Provider ID 正确 | GeminiProvider() | providerId="gemini" |
| 11 | GeminiProvider | supportsMultimodal=true | GeminiProvider() | true |
| 12 | ChinaMuseums | 恰好 10 座博物馆 | MUSEUMS.size | 10 |
| 13 | ChinaMuseums | 每座博物馆至少 3 件展品 | MUSEUMS.all { exhibits.size ≥ 3 } | true |
| 14 | ChinaMuseums | 每件展品描述非空 | MUSEUMS.flatMap exhibits | 所有 description 非空 |
| 15 | ChinaMuseums | 每个博物馆有非空 name | MUSEUMS.map name | 全部非空 |
| 16 | MuseumInfo | 坐标在合理范围 | latitude 和 longitude | latitude ∈ [20,45], longitude ∈ [100,125] |

---

## 8. 功能测试用例设计

### 8.1 导航测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| NAV-01 | 底部导航切换 | App 已启动, 首页显示 | 点击"扫描"标签 | 跳转到摄像头扫描页 |
| NAV-02 | 底部导航切换回首页 | 在扫描页 | 点击"首页"标签 | 跳转到首页 |
| NAV-03 | 底部导航切换到地图 | App 已启动 | 点击"地图"标签 | 跳转到地图页 (需 Maps Key) |
| NAV-04 | 首页→博物馆详情 | 首页博物馆列表加载 | 点击任意博物馆卡片 | 跳转到博物馆详情页 |
| NAV-05 | 博物馆详情返回 | 在博物馆详情页 | 按返回键 | 返回首页 |
| NAV-06 | 扫描页→文物详情 | 已识别到文物 | 点击浮层"文物详情"按钮 | 跳转到文物详情页 |
| NAV-07 | 地图→文物详情 | 地图标记已加载 | 点击橙色标记 | 跳转到文物详情页 |
| NAV-08 | 文物详情返回扫描页 | 从扫描页跳转的详情页 | 按返回键 | 返回扫描页, 预览继续 |
| NAV-09 | 文物详情返回地图 | 从地图跳转的详情页 | 按返回键 | 返回地图页, 标记可见 |

### 8.2 摄像头扫描测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| CAM-01 | 首次启动申请权限 | 首次安装, 未授权 | 打开 App → 切换到扫描页 | 弹出相机权限对话框 |
| CAM-02 | 权限同意后显示预览 | 权限已同意 | 切换到扫描页 | CameraX 预览正常显示 |
| CAM-03 | 权限拒绝后不启动 | 权限被拒绝 | 切换到扫描页 | 无预览, 显示提示 |
| CAM-04 | 扫描区域指示框可见 | 扫描页正常显示 | 观察 UI | 250dp 方框, 琥珀色描边 |
| CAM-05 | 扫描提示文字显示 | 扫描页正常显示 | 观察底部 | 显示"对准文物，停留2秒获取介绍" |
| CAM-06 | 切换到其他页面后返回 | 从扫描页切换到首页 | 切回扫描页 | 预览恢复, 摄像头重新绑定 |

### 8.3 物体检测测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| DET-01 | 普通物体检测 | 扫描页运行中 | 对准一个花瓶 | 检测到 "vase" 标签 (置信度≥0.5) |
| DET-02 | 无检测目标 | 扫描页运行中 | 对准空白墙壁 | 无检测结果, 提示文字不变 |
| DET-03 | 低置信度过滤 | 物体模糊/远距离 | 对准远处物体 | 置信度<0.5 的检测不显示 |
| DET-04 | 多个物体检测 | 视野中有多个物体 | 对准多个物品 | 最多显示 10 个, 按置信度排序 |
| DET-05 | 模型缺失检测 | 删除 detect.tflite | 重新打开扫描页 | 显示"AI检测不可用"提示 |
| DET-06 | TFLite 初始化异常 | 不兼容设备 | 打开扫描页 | initialize() 返回 false, 显示降级提示 |

### 8.4 2秒停留检测测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| DWELL-01 | 同标签停留2秒触发 | 对准花瓶 | 保持 2 秒 | 触发讲解, 浮层显示"青花瓷瓶" |
| DWELL-02 | 中途移动不触发 | 对准一个物体 | 1.5 秒后移开 | 计时器重置, 不触发 |
| DWELL-03 | 触发后不重复触发 | 已触发讲解 | 继续对准同一物体 5 秒 | 不重复触发 |
| DWELL-04 | 标签丢失后重新触发 | 已触发, 移开再移回 | 移开 1 秒 → 重新对准 2 秒 | 再次触发讲解 |
| DWELL-05 | 不同标签切换重置 | 对准花瓶 1 秒 | 切换到另一个物体 | 计时重置为新标签 |

### 8.5 文物数据库测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| DB-01 | 首次启动种子数据 | App 全新安装 | 打开扫描页 → 识别"vase" | 显示"青花瓷瓶"信息 |
| DB-02 | 已知标签映射正确 | 种子数据已写入 | 识别 6 个映射标签 | 每个标签对应正确文物 |
| DB-03 | 未知标签无映射 | 检测到未映射标签 | TFLite 返回 "person" | 跳转到 AI 生成流程 |
| DB-04 | 数据库不重复写入 | App 多次启动 | 检查数据库 | 只有 6 条, 不重复 |

### 8.6 AI 多模型配置测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| AI-01 | 首页 AI 模型选择 | 首页 AI 配置区可见 | 点击"DeepSeek" | RadioButton 选中 DeepSeek |
| AI-02 | 切换模型提示更新 | 选中 Gemini | 改为选中 Qwen | API Key 提示更新为 Qwen 获取地址 |
| AI-03 | 保存 API Key | 选中 DeepSeek, 输入 Key | 点击"保存AI配置" | Toast "AI配置已保存" |
| AI-04 | 重新打开后配置保留 | 已保存 DeepSeek Key | 退出 App 重新打开首页 | DeepSeek 仍选中, Key 值保留 |
| AI-05 | Gemini 模型名隐藏 | 选中 Gemini | 观察"模型名称"输入框 | 隐藏 (GONE) |
| AI-06 | DeepSeek 模型名显示 | 选中 DeepSeek | 观察"模型名称"输入框 | 显示, 默认值 "deepseek-chat" |
| AI-07 | 保存空的 API Key | 不输入 API Key | 点击保存 | 保存空字符串, 后续使用降级 |
| AI-08 | 配置后自动使用新 Provider | 配置了 Qwen Key | 返回扫描页识别物体 | AI 调用 Qwen API (需网络) |
| AI-09 | Provider 失败后降级 Gemini | DeepSeek Key 无效 | 扫描识别, DeepSeek 失败 | 尝试 BuildConfig.GEMINI_API_KEY |
| AI-10 | 全部 AI 失败后使用内置描述 | 所有 API Key 无效 | 触发识别 | 播放内置中文描述 |

### 8.7 AI 导游讲解测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| AI-11 | 本地匹配 → TTS 朗读 | 检测到映射标签 | 停留 2 秒 | 浮层显示文物信息 + TTS 播放 brief |
| AI-12 | 本地匹配 → 3秒后 AI 追加 | AI 已配置 | 等待 3 秒 | 自动追加 AI 描述 |
| AI-13 | 无匹配 → AI 生成完整介绍 | AI 已配置, 检测到 "person" | 停留 2 秒 | 显示"识别中" → AI 结果 + TTS |
| AI-14 | 无匹配 + 无 AI Key → 内置描述 | 无 AI Key | 检测到未映射标签 | 播放内置降级描述 |
| AI-15 | API 超时 → 降级 | 网络慢/断网 | 触发 AI 调用 | 超时后返回降级描述 |

### 8.8 相册图片识别测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| GAL-01 | 打开相册选择器 | 扫描页正常 | 点击右上角相册按钮 | 打开系统图片选择器 |
| GAL-02 | 选择图片显示遮罩 | 选择一张图片 | 确认选择 | 全屏遮罩显示图片 |
| GAL-03 | AI 识别成功 | 已配置 AI Key | 选择图片后等待 | 结果显示在遮罩底部 |
| GAL-04 | AI 识别失败→TFLite 降级 | 无 AI Key | 选择包含物体的图片 | 显示 TFLite 标签+置信度 |
| GAL-05 | 图片无物体可检测 | 纯色/模糊图片 | 选择后等待 | 显示"未检测到物体" |
| GAL-06 | 关闭遮罩恢复预览 | Gallery 遮罩打开 | 点击关闭按钮 | 遮罩消失, 预览恢复 |
| GAL-07 | Gallery 模式暂停实时检测 | Gallery 打开中 | 在遮罩后移走物体 | 实时检测不触发 (isGalleryMode) |

### 8.9 TTS 语音测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| TTS-01 | 识别后自动播报 | 触发讲解 | 等待 1 秒 | 听到中文语音讲解 |
| TTS-02 | 播报中文正确 | 本地匹配 | 听取语音 | 中文, 语速正常 (1.0) |
| TTS-03 | 新识别中断旧播报 | 正在播报文 A | 触发文物 B | 立即切换到文物 B 的讲解 |
| TTS-04 | 文物详情页播报 | 在详情页 | 点击"播放语音讲解" | 播报完整内容 |
| TTS-05 | 详情页停止播报 | 正在播报 | 点击"停止播放" | 语音停止 |
| TTS-06 | 详情页再次播报 | 已停止 | 再次点击"播放语音讲解" | 从头开始播报 |
| TTS-07 | 博物馆详情全播报 | 在博物馆详情页 | 点击"播报博物馆全部介绍" | 播报博物馆+所有展品 |
| TTS-08 | 单件展品播报 | 博物馆详情页 | 点击展品卡片 | 播报该展品介绍 |
| TTS-09 | 播报/停止切换 | 正在播报 | 点击停止按钮 | 切换为"停止播报"文字 |
| TTS-10 | App 退出时停止 TTS | TTS 正在播放 | 退出 App (onDestroy) | TTS 引擎 shutdown |
| TTS-11 | 博物馆详情离开页面停止 | 正在播报 | 按返回键离开详情页 | TTS 停止 |

### 8.10 博物馆地图测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| MAP-01 | 地图页加载 | 已配置 Maps API Key | 切换到地图页 | Google Maps 正常显示 |
| MAP-02 | 文物标记显示 | 种子数据已写入 | 观察地图 | 6 个橙色标记, 对应 6 件文物 |
| MAP-03 | 标记内容正确 | 标记已加载 | 点按标记 | 显示名称 (如"青花瓷瓶") + 年代 |
| MAP-04 | 标记点击跳转 | 点击标记 | 点击标记 | 跳转到文物详情页 |
| MAP-05 | 缩放控制 | 地图已加载 | 双指缩放 | 范围 15f–22f |
| MAP-06 | 默认位置北京 | 地图已加载 | 观察 | 中心在北京区域 (39.9, 116.4) |
| MAP-07 | 无 Maps Key 地图异常 | 未配置 Key | 切到地图页 | 地图可能加载失败 |

### 8.11 文物详情页测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| DTL-01 | 从扫描页进入详情 | 已识别到文物 | 点击"文物详情" | 显示对应文物全部信息 |
| DTL-02 | 详情页信息完整 | 进入 "青花瓷瓶" 详情 | 查看页面 | 名称/年代/描述/意义均显示 |
| DTL-03 | 详情页图片占位 | 进入详情页 | 查看图片区域 | 灰色背景 CardView |
| DTL-04 | 从地图进入详情 | 从地图标记跳转 | 查看 id | 传参正常, 显示正确文物 |

### 8.12 博物馆数据与 TTS 测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| MUS-01 | 首页加载十大博物馆 | 首页正常显示 | 滚动到博物馆列表 | 10 个博物馆卡片, 含排名/城市/简介 |
| MUS-02 | 博物馆详情完整 | 点击任一博物馆 | 进入详情页 | 名称/城市/简介/描述/意义/展品列表完整 |
| MUS-03 | 展品卡片显示 | 博物馆详情页 | 滚动到展品区 | 每件展品含名称/年代/描述 |
| MUS-04 | 故宫博物院数据正确 | 点击故宫 | 查看 | 5 件镇馆之宝, 含清明上河图等 |
| MUS-05 | 三星堆数据正确 | 点击三星堆 | 查看 | 4 件展品, 含青铜大立人像 |
| MUS-06 | 博物馆 TTS 完整播报 | 博物馆详情页 | 点击"播报全部" | 从博物馆名开始到所有展品完整朗读 |
| MUS-07 | 博物馆 TTS 可停止 | 正在播报 | 点击"停止播报" | 语音停止, 按钮切换 |
| MUS-08 | 国家博物馆 5 件展品 | 查看国博 | 展品列表 | 后母戊鼎, 四羊方尊等 5 件 |
| MUS-09 | 湖南博物院 4 件展品 | 查看湘博 | 展品列表 | 辛追夫人, 素纱襌衣等 4 件 |
| MUS-10 | 浙江省博物馆 3 件展品 | 查看浙博 | 展品列表 | 良渚玉琮等 3 件 |

### 8.13 博物馆位置设置测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| LOC-01 | 手动输入城市 | 首页"当前博物馆"区 | 输入"西安" → 保存设置 | 显示"当前定位：西安 · 陕西历史博物馆" |
| LOC-02 | 保存后持久化 | 已保存"西安" | 重启 App | 城市仍显示"西安" |
| LOC-03 | GPS 定位成功 | 已开启 GPS + 位置权限 | 点击"📡 自动定位" | 获取当前位置, 自动填入城市 |
| LOC-04 | GPS 定位失败处理 | 关闭 GPS | 点击"📡 自动定位" | Toast "定位失败" |
| LOC-05 | 空城市输入 | 输入框为空 | 点击"保存设置" | Toast "请输入城市名称" |
| LOC-06 | 城市 → 博物馆自动匹配 | 输入"北京" | 保存 | 显示"当前定位：北京 · 故宫博物院" |
| LOC-07 | 城市 → 博物馆自动匹配(多个) | 输入"郑州" | 保存 | 显示"当前定位：郑州 · 河南博物院" |

### 8.14 权限测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| PERM-01 | 首次启动应用 | 全新安装, 无权限 | 打开 App, 切换到扫描 | 弹出相机权限请求 |
| PERM-02 | 相机权限拒绝后体验 | 相机权限被拒 | 在扫描页操作 | 无预览, 其他功能正常 |
| PERM-03 | GPS 权限拒绝后体验 | 位置权限被拒 | 点击"📡 自动定位" | 定位失败, 可手动输入 |
| PERM-04 | 全部权限允许 | 全部允许 | 使用全部功能 | 正常 |
| PERM-05 | 权限再次请求 | 拒绝后 | 重启 App → 扫描页 | 再次弹出权限请求 |

### 8.15 降级与异常测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| FALL-01 | 无网络 + 已映射标签 | 开启飞行模式 | 扫描花瓶 2 秒 | 本地数据 + TTS 成功, 无 AI 追加 |
| FALL-02 | 无网络 + 未映射标签 | 飞行模式, 无 AI Key | 扫描未映射物体 | 内置降级描述 + TTS |
| FALL-03 | 无网络 + AI 已配置 | 飞行模式, 有 Key | 触发 AI 调用 | AI 超时(15s) → 降级描述 |
| FALL-04 | 模型文件缺失 | 删除 detect.tflite | 打开扫描页 | "AI检测不可用" 提示 |
| FALL-05 | 所有 Provider 均失败 | 全部 API Key 无效 | 触发识别 | 最终返回内置描述 |
| FALL-06 | 相册选图加载失败 | 选损坏图片 | 等待处理 | Toast "无法加载图片" |

### 8.16 性能测试

| # | 用例名称 | 前置条件 | 操作步骤 | 预期结果 |
|---|---------|---------|---------|---------|
| PERF-01 | 帧率达标 | 扫描页流畅运行 | 持续 1 分钟 | 无明显卡顿, 帧率 ≥ 10 FPS |
| PERF-02 | 检测延迟 | TFLite 已加载 | 快速移动镜头 | 检测结果实时更新, 延迟 <500ms |
| PERF-03 | AI 调用不阻塞 UI | 触发 AI 生成 | AI 加载中操作 App | UI 不卡顿, 可正常操作 |
| PERF-04 | 内存稳定 | 持续扫描 10 分钟 | 观察内存 | 无明显内存泄漏, 不 OOM |

---

## 附录 A: 测试环境要求

| 项目 | 要求 |
|------|------|
| Android 设备 | API 24+ (推荐 API 30+) |
| 摄像头 | 后置摄像头 |
| Google Play Services | 已安装 (用于 Maps) |
| TFLite 模型 | app/src/main/assets/detect.tflite (MobileNet SSD COCO) |
| Maps API Key | local.properties 中配置 |
| AI API Key (可选) | local.properties 或 首页设置 |
| 网络 | 部分功能需要 (AI/GPS/Maps) |

## 附录 B: 测试命令

```bash
# 运行所有单元测试
./gradlew app:test

# 运行指定测试类
./gradlew app:test --tests "com.example.museumguide.detection.ObjectDetectorTest"
./gradlew app:test --tests "com.example.museumguide.exhibit.ExhibitRepositoryTest"
./gradlew app:test --tests "com.example.museumguide.ai.AiTourGuideServiceTest"
./gradlew app:test --tests "com.example.museumguide.ai.AiTourGuideServiceMultimodalTest"

# 运行插桩测试 (需设备/模拟器)
./gradlew connectedAndroidTest

# 查看测试报告
open app/build/reports/tests/testDebugUnitTest/index.html
```

## 附录 C: 测试覆盖矩阵

| 模块 | 单元测试数 | 功能测试数 | 关键风险 |
|------|----------|----------|---------|
| 导航系统 | - | 9 | 状态丢失 |
| 摄像头扫描 | - | 6 | 权限拒绝 |
| TFLite 检测 | 19 | 6 | 模型缺失 |
| 2秒停留检测 | - | 5 | 计时不准 |
| 文物数据库 | 7+5(插桩) | 4 | 数据种子 |
| AI 导游 | 13 | 8 | API 失败 |
| 相册识别 | - | 7 | 大图 OOM |
| TTS 语音 | - | 11 | 引擎不可用 |
| 博物馆地图 | - | 7 | Key 缺失 |
| 文物详情 | - | 4 | 数据不完整 |
| AI 多模型 | 9 | 10 | Key 配置 |
| 首页设置 | - | 7 | GPS 故障 |
| 博物馆数据 | 6 | 10 | 数据错误 |
| 权限 | - | 5 | 拒绝影响 |
| 降级 | - | 6 | 多级失败 |
| 性能 | - | 4 | 内存泄漏 |
| **合计** | **59** | **109** | |

---

> 本文档基于实际代码 v1.2 版本生成。所有功能均已实现并通过代码审查。
> 测试用例分为: 已有自动化测试 (34 个单元测试 + 5 个插桩测试) + 新增推荐单元测试 (16 个) + 功能测试用例 (109 个)
