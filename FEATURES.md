# 已实现功能清单

## 项目总览
博物馆导游应用 (Museum Guide) — Android (Kotlin) 应用，通过摄像头识别文物并自动语音讲解，附带博物馆导游图。

- **版本**: v1.2 (versionCode=3)
- **最低 SDK**: API 24 (Android 7.0)
- **架构**: MVVM + Navigation Component + ViewBinding
- **构建**: Gradle Kotlin DSL (AGP 8.2.0, Kotlin 1.9.20, KSP)

---

## 1. 导航系统

| 功能 | 状态 | 说明 |
|------|------|------|
| 底部导航栏 (Camera / Map) | ✅ | BottomNavigationView + NavHostFragment |
| 摄像头扫描页面 (CameraFragment) | ✅ | 应用启动页 |
| 博物馆地图页面 (MuseumMapFragment) | ✅ | Google Maps 集成 |
| 文物详情页面 (ExhibitDetailFragment) | ✅ | 从相机/地图均可跳转 |
| 导航参数传递 | ✅ | exhibit_id (Long) 传参 |

**文件**: `nav_graph.xml`, `bottom_nav_menu.xml`, `MainActivity.kt`

---

## 2. 摄像头与图像采集

| 功能 | 状态 | 说明 |
|------|------|------|
| CameraX 实时预览 | ✅ | 4:3 比例, 背面摄像头 |
| 帧捕获分析 (640×480) | ✅ | STRATEGY_KEEP_ONLY_LATEST |
| YUV_420_888 → Bitmap 转换 | ✅ | NV21 → JPEG → Bitmap, 带旋转校正 |
| 单线程分析执行器 | ✅ | 避免并发帧冲突 |
| ImageCapture 快照 | ✅ | 低延迟模式已配置, 暂未使用 |

**文件**: `CameraManager.kt` (175 行)

---

## 3. TFLite 物体检测

| 功能 | 状态 | 说明 |
|------|------|------|
| MobileNet SSD COCO 模型 | ✅ | 量化模型, 300×300 输入 |
| 91 个 COCO 标签 (80 类 + 背景) | ✅ | 含 "vase", "bottle", "scissors" 等 |
| 置信度阈值 0.5 | ✅ | 过滤低置信度检测 |
| 最多 10 个检测结果 | ✅ | 按置信度降序排列 |
| 边界框格式转换 | ✅ | TFLite [top,left,bottom,right] → RectF |
| GPU 加速支持 | ✅ | tensorflow-lite-gpu 依赖 |
| 模型加载失败降级 | ✅ | 设备不兼容时提示用户 |
| 线程安全初始化 | ✅ | AtomicBoolean 控制 |

**COCO 标签映射**: `ObjectDetector.kt` companion object 中的 `COCO_LABELS` 数组
**模型文件**: `assets/detect.tflite`

**文件**: `ObjectDetector.kt` (186 行)

---

## 4. 2秒停留检测机制

| 功能 | 状态 | 说明 |
|------|------|------|
| 帧间隔采样 | ✅ | 每 3 帧执行一次检测 (~10-15 FPS) |
| 标签持续跟踪 | ✅ | 记录 `currentDetectionLabel` + `detectionStartTime` |
| ≥2000ms 稳定触发 | ✅ | 同标签持续超 2 秒触发讲解 |
| 标签变化时重置 | ✅ | 新标签/无检测 → 重置计时器 |
| 防重复触发 | ✅ | `introductionTriggered` 标记 |
| 标签丢失后可重新触发 | ✅ | `resetDwell()` 重置所有状态 |

**文件**: `CameraFragment.kt` (432 行)

---

## 5. 文物数据 (Room 数据库)

| 功能 | 状态 | 说明 |
|------|------|------|
| Exhibit 实体 | ✅ | 10 个字段: id, code, name, era, brief, desc, significance, imageUrl, mapX/Y, hallId |
| DAO 查询 | ✅ | getAll, getByCode, getById, getByHall, insertAll, deleteAll, getCount |
| Room 数据库单例 | ✅ | `museum_guide.db`, fallbackToDestructiveMigration |
| 示例数据种子 (6件) | ✅ | 首次启动自动写入 |

**示例文物**:
| 名称 | 标签 | 展厅 |
|------|------|------|
| 青花瓷瓶 | vase → code:"vase" | Hall A |
| 粉彩花卉纹瓶 | bottle → code:"vase_2" | Hall A |
| 青铜鼎 | scissors → code:"bronze" | Hall B |
| 玉璧 | clock → code:"jade" | Hall B |
| 山水画长卷 | tv → code:"painting" | Hall C |
| 唐三彩骆驼 | potted plant → code:"ceramic" | Hall C |

**LABEL_TO_CODE 映射**: `ExhibitRepository.kt` (6 个通用 COCO 标签 → 文物编码)

**文件**: `Exhibit.kt`, `ExhibitDao.kt`, `ExhibitDatabase.kt`, `ExhibitRepository.kt`

---

## 6. AI 导游讲解 (Google Gemini)

| 功能 | 状态 | 说明 |
|------|------|------|
| Gemini 2.0 Flash API 集成 | ✅ | OkHttp + Gson |
| 文本生成描述 | ✅ | `generateDescription(label, context)` |
| 多模态图像识别 | ✅ | `generateDescriptionFromImage(bitmap)`, 压缩至 800px, Base64 JPEG |
| 结构化 JSON Prompt | ✅ | 返回 title/brief/description/significance |
| 内存缓存 (100条) | ✅ | ConcurrentHashMap, 自动淘汰 |
| API Key 缺失降级 | ✅ | 15 种内置中文描述 |
| API 错误降级 | ✅ | 返回降级文本 + 错误提示 |
| 内容被拦截处理 | ✅ | 检测 blockReason |
| 请求配置 | ✅ | temperature=0.7, maxTokens=1024, topP=0.9 |
| 超时控制 | ✅ | 15 秒 connect + read |
| 线程安全并发访问 | ✅ | ConcurrentHashMap + Dispatchers.IO |

**内置降级描述 (15种)**: vase, bottle, scissors, clock, tv, potted plant, book, bronze, jade, chair, dining table, bed, cup, bowl, knife, cell phone

**文件**: `AiTourGuideService.kt` (438 行)

---

## 7. AI 增强讲解流程

| 功能 | 状态 | 说明 |
|------|------|------|
| 本地匹配 → TTS 朗读 | ✅ | 先播放本地 brief |
| 本地匹配 → 延时 3s → AI 追加 | ✅ | `appendSpeak()` 队列追加 |
| 无匹配 → AI 全部生成 | ✅ | 调用 Gemini, 结果显示+朗读 |
| AI 生成中 Loading 状态 | ✅ | "正在获取AI导游讲解…" |
| 配置 Key → 自动使用 AI | ✅ | 从 BuildConfig.GEMINI_API_KEY 读取 |
| 未配置 → 纯本地降级 | ✅ | 仅播放本地数据 |

**文件**: `CameraFragment.kt` (onExhibitRecognised / fetchAndPlayAiNarration / showAiGeneratedContent)

---

## 8. 相册图片识别

| 功能 | 状态 | 说明 |
|------|------|------|
| 相册选择器按钮 | ✅ | 右上角 gallery 图标 |
| ActivityResultContracts.GetContent | ✅ | "image/*" |
| 全屏图片预览遮罩 | ✅ | 黑底, fitCenter 缩放 |
| 两阶段识别 | ✅ | AI 多模态 → TFLite 降级 |
| AI 多模态识别 | ✅ | Gemini 分析图片内容 |
| TFLite 离线降级 | ✅ | 显示标签 + 置信度百分比 |
| Loading 指示器 | ✅ | ProgressBar |
| 关闭遮罩返回预览 | ✅ | 停止 gallery mode, 恢复实时检测 |
| Gallery 模式暂停实时检测 | ✅ | `isGalleryMode` 标记 |
| 错误处理 | ✅ | 图片加载失败/识别出错均有提示 |

**文件**: `CameraFragment.kt` (onGalleryImagePicked / closeGalleryMode)

---

## 9. 文字转语音 (TTS)

| 功能 | 状态 | 说明 |
|------|------|------|
| Android TTS 引擎 | ✅ | TextToSpeech |
| 中文语音 | ✅ | Locale.CHINESE |
| 语速 0.9x | ✅ | setSpeechRate(0.9f) |
| 播放 (中断当前) | ✅ | `speak()` - QUEUE_FLUSH |
| 追加播放 | ✅ | `appendSpeak()` - QUEUE_ADD |
| 停止播放 | ✅ | `silence()` |
| 生命周期管理 | ✅ | onDestroy 中 stop + shutdown |

**文件**: `MainActivity.kt` (70 行)

---

## 10. 博物馆导游图 (Google Maps)

| 功能 | 状态 | 说明 |
|------|------|------|
| Google Maps SDK 集成 | ✅ | SupportMapFragment |
| 标准坐标 → LatLng 转换 | ✅ | 标准化坐标 (0.0-1.0) → 北京占位坐标 |
| 橙色文物标记 | ✅ | BitmapDescriptorFactory.HUE_ORANGE |
| 标记标题 + 片段 | ✅ | 名称 + 年代 |
| 标记点击 → 详情页 | ✅ | OnMarkerClickListener |
| 缩放范围限制 | ✅ | min=15f, max=22f |
| 默认缩放 | ✅ | 17f |
| 顶部标题卡片 | ✅ | MaterialCardView |
| 地图类型 | ✅ | MAP_TYPE_NORMAL (占位) |

**文件**: `MuseumMapFragment.kt` (135 行)

---

## 11. 文物详情页面

| 功能 | 状态 | 说明 |
|------|------|------|
| 名称显示 | ✅ | 24sp bold |
| 年代显示 | ✅ | 16sp gray |
| 详细描述 | ✅ | 16sp + 行间距 |
| 文化意义 | ✅ | 灰底卡片 14sp |
| 图片占位 | ✅ | CardView + ImageView, 灰色背景 |
| TTS 播放/暂停按钮 | ✅ | 组合朗读: 名称 + 年代 + 描述 + 意义 |
| ScrollView 布局 | ✅ | 适合长内容滚动 |

**文件**: `ExhibitDetailFragment.kt` (82 行)

---

## 12. 权限管理

| 权限 | 状态 | 说明 |
|------|------|------|
| CAMERA | ✅ | 运行时申请, 用于物体检测 |
| ACCESS_FINE_LOCATION | ✅ | 清单声明, 用于地图定位 |
| ACCESS_COARSE_LOCATION | ✅ | 清单声明 |
| INTERNET | ✅ | 清单声明, 用于地图/AI |
| ACCESS_NETWORK_STATE | ✅ | 清单声明, 地图瓦片加载 |

**文件**: `AndroidManifest.xml`

---

## 13. UI / 主题

| 功能 | 状态 | 说明 |
|------|------|------|
| Material Design 3 | ✅ | Theme.MaterialComponents.Light.NoActionBar |
| 主题色: 蓝色 (#1565C0) | ✅ | Primary |
| 强调色: 琥珀色 (#FF8F00) | ✅ | Secondary |
| 背景: 米色 (#FFF8E1) | ✅ | Background |
| 扫描区域指示框 | ✅ | CardView, 250dp 方框, 琥珀色描边 |
| 暗色遮罩 | ✅ | 扫描区外半透明黑色 |
| 文物信息底部浮层 | ✅ | 半透明白底, 展示名称/年代/简介/详情按钮 |
| Gallery 全屏遮罩 | ✅ | 黑色, 带关闭按钮 |
| 圆形半透明按钮 | ✅ | drawable/round_button_bg.xml |
| 底部导航 labeled 模式 | ✅ | 始终显示文字标签 |

**文件**: `themes.xml`, `colors.xml`, `strings.xml`, `round_button_bg.xml`

---

## 14. 测试覆盖

| 测试 | 数量 | 框架 | 类型 |
|------|------|------|------|
| ObjectDetectorTest | 19 | JUnit 4 + Robolectric + Mockito | 单元测试 |
| ExhibitRepositoryTest | 7 | JUnit 4 + Mockito | 单元测试 |
| AiTourGuideServiceTest | 10 | JUnit 4 + MockWebServer | 单元测试 |
| AiTourGuideServiceMultimodalTest | 4 | Robolectric + MockWebServer | 单元测试 (需模拟器框架) |
| ExhibitDaoTest | 5 | JUnit 4 + Room Testing | 插桩测试 (需模拟器) |
| **合计** | **45** | | |

**测试覆盖内容**:
- Detection 数据类、COCO 标签、常量、后处理逻辑 (置信度过滤、排序、边界框转换)
- Repository 标签映射、数据种子、缓存逻辑
- AI 服务：空 key 降级、MockWebServer 模拟 API、缓存、并发安全
- 多模态：图片 base64 编码验证、API 错误处理
- Room DAO：CRUD 操作、数据库完整性

**文件**: `src/test/` (4 个测试文件), `src/androidTest/` (1 个测试文件)

---

## 15. 构建配置

| 项目 | 值 |
|------|-----|
| applicationId | com.example.museumguide |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| versionCode | 2 |
| versionName | 1.1 |
| Java | 17 |
| Kotlin | 1.9.20 |
| AGP | 8.2.0 |
| KSP | 1.9.20-1.0.14 |
| ProGuard | release (minify=false) |

---

## 16. 第三方依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| CameraX (core/camera2/lifecycle/view) | 1.3.1 | 摄像头预览与帧捕获 |
| TensorFlow Lite (core/support/gpu) | 2.16.1 | 设备端物体检测 |
| Google Maps + Location | 18.2.0 / 21.0.1 | 博物馆导游图 |
| Room (runtime/ktx/compiler) | 2.6.1 | 本地文物数据库 |
| Lifecycle (viewmodel/livedata/runtime) | 2.7.0 | MVVM 架构 |
| Navigation (fragment/ui) | 2.7.6 | 页面导航 |
| Material Design | 1.11.0 | UI 组件库 |
| OkHttp | 4.12.0 | Gemini API 网络请求 |
| Gson | 2.10.1 | JSON 解析 |
| Coroutines | 1.7.3 | 异步操作 |

---

## 17. 异常处理与降级策略

| 场景 | 处理方式 |
|------|----------|
| TFLite 模型加载失败 | 显示"AI检测不可用"提示 |
| 未授权相机权限 | 运行时申请, 拒绝后不启动相机 |
| 未配置 Maps API Key | 地图页不可用, 其他功能正常 |
| 未配置 Gemini API Key | AI 讲解自动降级为内置本地描述 |
| Gemini API 请求失败 (HTTP 4xx/5xx) | 返回降级文本 + 错误提示 |
| 内容被 AI 安全过滤拦截 | 检测 blockReason, 返回降级文本 |
| 相册图片加载失败 | 显示"无法加载图片" |
| 相册识别 AI 失败 | 自动降级为 TFLite 离线检测 |
| 无检测结果 | 显示"未检测到物体" |

---

---

## 18. AI 多模型支持 (v1.2 新增)

| 功能 | 状态 | 说明 |
|------|------|------|
| AI Provider 接口抽象 | ✅ | AiProvider 接口, 支持多后端切换 |
| Gemini 2.0 Flash | ✅ | 默认, 支持多模态图像识别 |
| DeepSeek (深度求索) | ✅ | OpenAI 兼容, 超低价, 注册送 500 万 Token |
| 通义千问 Qwen (阿里云百炼) | ✅ | OpenAI 兼容, 新用户 7000 万免费 Token, 有效期 90 天 |
| 文心一言 ERNIE (百度千帆) | ✅ | ERNIE-Speed-8K 永久免费, 不限量 |
| 用户配置持久化 | ✅ | SharedPreferences 保存 Provider + API Key + 模型名 |
| 跨 Provider 缓存 | ✅ | 统一缓存, 减少重复调用 |
| 降级链: AI → 内置描述 | ✅ | 任何 Provider 失败均自动降级 |

**免费 API 汇总**:
| 提供商 | 免费策略 | 获取地址 |
|--------|---------|---------|
| Google Gemini | 免费额度 (不限量免费层有限) | https://aistudio.google.com/apikey |
| DeepSeek | 注册送 500 万 Token | https://platform.deepseek.com |
| 阿里云百炼 (Qwen) | 新用户 7000 万免费 Token (70+ 模型各 100 万) | https://bailian.console.aliyun.com |
| 百度千帆 (ERNIE) | ERNIE-Speed-8K 永久免费 | https://console.bce.baidu.com/qianfan |

**文件**: `ai/AiProvider.kt`, `ai/GeminiProvider.kt`, `ai/OpenAiCompatibleProvider.kt`, `ai/AiConfiguration.kt`

---

## 19. 首页 / 设置中心 (v1.2 新增)

| 功能 | 状态 | 说明 |
|------|------|------|
| AI 模型选择器 | ✅ | 4 种 Provider RadioButton 选择 |
| API Key 输入 (密码隐藏) | ✅ | 根据选中 Provider 动态切换 |
| 模型名称自定义 | ✅ | DeepSeek/Qwen/ERNIE 支持自定义模型名 |
| API 获取地址提示 | ✅ | 动态显示各 Provider 申请地址 |
| 博物馆城市手动设置 | ✅ | TextInput 输入城市名 |
| GPS 自动定位博物馆 | ✅ | FusedLocationProviderClient + Geocoder 反向地理编码 |
| 定位城市 → 博物馆匹配 | ✅ | 自动匹配中国十大博物馆 |
| 中国十大博物馆列表 | ✅ | RecyclerView 卡片列表 |

**文件**: `ui/HomeFragment.kt`, `fragment_home.xml`

---

## 20. 中国十大博物馆内置数据 (v1.2 新增)

| 博物馆 | 城市 | 镇馆之宝数量 |
|--------|------|------------|
| 故宫博物院 | 北京 | 5 件 (清明上河图, 金瓯永固杯, 千里江山图, 翡翠白菜, 太和殿) |
| 中国国家博物馆 | 北京 | 5 件 (后母戊鼎, 四羊方尊, 人面鱼纹彩陶盆, 金缕玉衣, 红山玉龙) |
| 陕西历史博物馆 | 西安 | 4 件 (镶金兽首玛瑙杯, 铜车马, 鎏金银竹节铜熏炉, 唐三彩骆驼载乐俑) |
| 上海博物馆 | 上海 | 3 件 (大克鼎, 晋侯稣钟, 淳化阁帖) |
| 南京博物院 | 南京 | 3 件 (金缕玉衣, 竹林七贤砖画, 釉里红梅瓶) |
| 河南博物院 | 郑州 | 3 件 (贾湖骨笛, 妇好鸮尊, 莲鹤方壶) |
| 湖北省博物馆 | 武汉 | 3 件 (曾侯乙编钟, 越王勾践剑, 曾侯乙尊盘) |
| 湖南博物院 | 长沙 | 4 件 (辛追夫人遗体, 素纱襌衣, T形帛画, 皿方罍) |
| 三星堆博物馆 | 广汉 | 4 件 (青铜大立人像, 青铜神树, 黄金权杖, 纵目面具) |
| 浙江省博物馆 | 杭州 | 3 件 (良渚玉琮, 越王者旨於睗剑, 龙泉窑青瓷舟形砚滴) |

**TTS 播报功能**:
| 功能 | 状态 | 说明 |
|------|------|------|
| 博物馆完整播报 | ✅ | 点击"播报博物馆全部介绍"按钮 |
| 单件展品播报 | ✅ | 点击展品卡片单独播放语音 |
| 播报/停止切换 | ✅ | 播放中可停止 |
| 中文语音 | ✅ | Locale.CHINESE, 语速 1.0 |

**文件**: `model/MuseumInfo.kt`, `ui/MuseumDetailFragment.kt`, `fragment_museum_detail.xml`

---

## 21. 导航更新 (v1.2)

| 功能 | 状态 | 说明 |
|------|------|------|
| 3 个底部导航标签 | ✅ | 首页 \| 扫描 \| 地图 |
| 首页 (HomeFragment) | ✅ | 启动目的地, 含设置和博物馆列表 |
| 博物馆详情 (MuseumDetailFragment) | ✅ | 从首页跳转, 含 TTS 播报 |
| 设置持久化 | ✅ | SharedPreferences 存储所有配置 |

**导航结构**: `首页(Home)` → `扫描(Camera)` / `地图(Map)` | `首页` → `博物馆详情`

**文件**: `nav_graph.xml`, `bottom_nav_menu.xml`

---

## 22. 版本更新记录

| 版本 | 新增内容 |
|------|---------|
| v1.0 | 初始版本: CameraX + TFLite + Room + Google Maps |
| v1.1 | Gemini AI 导游 + 相册图片识别 + 多模态 + TTS + 测试覆盖 |
| v1.2 | **多 AI 模型支持** (DeepSeek/Qwen/ERNIE) + **首页设置中心** (AI配置/博物馆定位/GPS) + **中国十大博物馆内置数据** (37 件镇馆之宝) + **博物馆 TTS 播报** + 3 标签导航 |

---

## 汇总统计

| 类别 | v1.1 | v1.2 |
|------|------|------|
| Kotlin 源文件 | 12 个 | 20 个 |
| 布局 XML | 4 个 | 8 个 |
| 资源 XML | 5 个 (values) + 3 个 (drawable) + 导航/菜单 | 不变 |
| 构建脚本 | 3 个 | 不变 |
| 单元测试 | 36 个 (4 文件) | 34 个 (4 文件) |
| 插桩测试 | 5 个 (1 文件) | 不变 |
| TFLite 模型 | 1 个 | 不变 |
| 内置文物示例 | 6 条 | 6 条 |
| AI 内置降级描述 | 15 种标签 + 通用兜底 | 不变 |
| AI 模型提供商 | 1 (Gemini) | 4 (Gemini + DeepSeek + Qwen + ERNIE) |
| 屏幕页面 | 3 个 (Camera/Map/Detail) | 5 个 (Home + Camera + Map + Detail + Museum) |
| 博物馆内置数据 | 无 | 10 座博物馆, 37 件镇馆之宝 |
| 底部导航标签 | 2 个 | 3 个 (首页 + 扫描 + 地图) |
