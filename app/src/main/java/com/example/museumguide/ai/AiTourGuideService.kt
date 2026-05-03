package com.example.museumguide.ai

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Facade for the AI-powered tour guide service.
 *
 * Supports multiple AI model providers:
 * - Gemini (default, already integrated)
 * - DeepSeek (OpenAI-compatible)
 * - Qwen / 通义千问 (OpenAI-compatible)
 * - ERNIE / 文心一言 (OpenAI-compatible)
 *
 * Features:
 * - Delegates to the configured provider for AI generation
 * - Falls back to built-in Chinese descriptions when AI is unavailable
 * - In-memory cache to avoid redundant API calls
 * - Graceful degradation: no API key → built-in descriptions
 */
class AiTourGuideService(private val appContext: Context? = null) {

    companion object {
        private const val MAX_CACHE_SIZE = 100
    }

    private val cache = ConcurrentHashMap<String, AiResponse>()
    private var config: AiConfiguration = AiConfiguration()

    // Provider instances
    private val geminiProvider = GeminiProvider()
    private val deepseekProvider = OpenAiCompatibleProvider.deepSeek()
    private val qwenProvider = OpenAiCompatibleProvider.qwen()
    private val ernieProvider = OpenAiCompatibleProvider.ernie()

    /** Reload configuration from SharedPreferences. */
    fun reloadConfig() {
        appContext?.let {
            config = AiConfiguration.load(it)
        }
    }

    /** Build a cache key that includes the provider to avoid stale cross-provider hits. */
    private fun cacheKey(label: String): String = "${config.activeProviderId}:$label"

    /**
     * Generate a museum-style Chinese description for a detected label.
     *
     * Strategy:
     * 1. Check in-memory cache (scoped by provider)
     * 2. Get the active provider and its API key
     * 3. Try AI generation from the active provider
     * 4. If AI fails → return built-in fallback description
     *
     * @param label The detected label from TFLite (e.g. "vase")
     * @param context Optional context hint
     * @return AiResponse with title/brief/description/significance
     */
    suspend fun generateDescription(
        label: String,
        context: String = "文物"
    ): AiResponse {
        reloadConfig()

        val key = cacheKey(label)

        // 1. Check cache (provider-scoped)
        val cached = cache[key]
        if (cached != null) return cached

        // 2. Try the active provider
        val apiKey = AiConfiguration.getActiveApiKey(config)
        if (apiKey.isNotBlank()) {
            val provider = getActiveProvider()
            val result = provider?.generateDescription(label, context, apiKey)
            if (result != null && result.description.isNotBlank()) {
                cacheResult(key, result)
                return result
            }
        }

        // 3. Fallback: try Gemini with BuildConfig key (old behavior)
        if (com.example.museumguide.BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            val geminiResult = geminiProvider.generateDescription(
                label, context, com.example.museumguide.BuildConfig.GEMINI_API_KEY
            )
            if (geminiResult != null && geminiResult.description.isNotBlank()) {
                cacheResult(key, geminiResult)
                return geminiResult
            }
        }

        // 4. Final fallback: built-in descriptions
        val fallback = buildFallbackResponse(label)
        cacheResult(key, fallback)
        return fallback
    }

    /**
     * Generate description from a bitmap image using multimodal AI.
     * Image results are NOT cached since each input is unique.
     * Falls back gracefully if the active provider doesn't support multimodality.
     */
    suspend fun generateDescriptionFromImage(
        bitmap: Bitmap,
        context: String = "文物"
    ): AiResponse {
        reloadConfig()

        val apiKey = AiConfiguration.getActiveApiKey(config)

        // 1. Try the active provider if it supports multimodal
        if (apiKey.isNotBlank()) {
            val provider = getActiveProvider()
            if (provider?.supportsMultimodal == true) {
                val result = provider.generateDescriptionFromImage(bitmap, context, apiKey)
                if (result != null && result.title != "识别失败" && result.title != "识别出错") {
                    return result
                }
            }
        }

        // 2. Fallback: try Gemini (always supports multimodal)
        if (com.example.museumguide.BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            val geminiResult = geminiProvider.generateDescriptionFromImage(
                bitmap, context, com.example.museumguide.BuildConfig.GEMINI_API_KEY
            )
            if (geminiResult != null && geminiResult.title != "识别失败" && geminiResult.title != "识别出错") {
                return geminiResult
            }
        }

        // 3. Final fallback
        return AiResponse(
            title = "图片识别",
            brief = "AI识别不可用",
            description = "当前未配置有效的AI API密钥。请前往首页配置AI模型和API密钥。",
            significance = ""
        )
    }

    /** Get the currently selected provider instance. */
    private fun getActiveProvider(): AiProvider? {
        return when (config.activeProviderId) {
            AiConfiguration.PROVIDER_GEMINI -> geminiProvider
            AiConfiguration.PROVIDER_DEEPSEEK -> deepseekProvider
            AiConfiguration.PROVIDER_QWEN -> qwenProvider
            AiConfiguration.PROVIDER_ERNIE -> ernieProvider
            else -> null
        }
    }

    /** Cache a result, evicting oldest if at capacity. */
    private fun cacheResult(label: String, response: AiResponse) {
        if (cache.size >= MAX_CACHE_SIZE) {
            val oldest = cache.keys.firstOrNull()
            if (oldest != null) cache.remove(oldest)
        }
        cache[label] = response
    }

    /** Clear the in-memory cache. */
    fun clearCache() {
        cache.clear()
    }

    // ── Built-in fallback descriptions ──────────────────────────────

    private fun buildFallbackResponse(label: String, reason: String? = null): AiResponse {
        val desc = generateLocalDescription(label)
        val note = if (reason != null) "\n\n（AI提示: $reason）" else ""
        return AiResponse(
            title = formatTitle(label),
            brief = "检测到「${formatTitle(label)}」",
            description = desc + note,
            significance = ""
        )
    }

    private fun generateLocalDescription(label: String): String {
        return when (label.lowercase()) {
            "vase" -> "这是一件精美的陶瓷器皿。瓶身线条流畅，釉色温润，可能出自中国古代官窑。瓷器是中国古代最重要的发明之一，通过丝绸之路远销海外，成为中华文明的象征。"
            "bottle" -> "这是一个具有历史价值的容器。古代瓶类器物种类繁多，有瓷瓶、玉瓶、铜瓶等，不同材质和形制反映了不同时期的工艺水平与审美追求。"
            "scissors" -> "这是一件古代金属工具。剪刀的发明极大便利了人类生活，从青铜时代到铁器时代，剪刀的形制不断演变，体现了古人智慧。"
            "clock" -> "这是一件精密计时仪器。从日晷到漏刻，从机械钟到石英钟，人类对时间的精确计量追求推动了科学技术的进步。"
            "tv" -> "这是一件与传播媒介相关的文物。它可能是一幅古代画卷，或是一件记载信息的器物，见证了人类信息传播方式的演变。"
            "potted plant" -> "这是一件融合自然元素的艺术品。植物纹样在历代工艺品中广泛应用，寄托了人们对自然的敬畏与对美好生活的向往。"
            "book" -> "这是一件珍贵的古籍。书籍承载着人类文明的智慧结晶，古代典籍是研究历史文化不可替代的重要资料。"
            "bronze" -> "这是一件古代青铜器。中国青铜文明举世闻名，青铜礼器是权力与地位的象征，其铸造工艺代表了当时最先进的技术水平。"
            "jade" -> "这是一件精美的玉器。中国玉文化源远流长，玉被视为君子之德的象征，雕琢工艺精湛，承载着深厚的文化内涵。"
            "chair" -> "这是一件古代家具。中式家具设计体现了＂天人合一＂的哲学思想，造型简洁而富有韵味。"
            "dining table" -> "这是一件古代家具作品。桌案类家具在中国传统居室中占据重要地位，做工考究，体现了古代匠人的智慧。"
            "bed" -> "这是一件古代卧具。中国传统床榻设计注重舒适与私密，雕刻精美，是古代生活方式的缩影。"
            "cup" -> "这是一件古代饮器。从陶杯到瓷杯，从玉杯到金银器，饮器的演变反映了不同时代的饮食文化与社会风尚。"
            "bowl" -> "这是一件古代容器。碗是日常生活中最常用的器皿之一，不同窑口的碗类器物各具特色，是研究古代陶瓷的重要对象。"
            "knife" -> "这是一件古代刀具。刀是人类最早使用的工具之一，从石刀到金属刀，见证了人类文明的演进历程。"
            "cell phone" -> "这是一件现代通讯设备的早期形态。这座展品展示了人类通讯技术从有线到无线的革命性变革。"
            else -> "检测到「$label」。这是一件博物馆藏品，具有独特的文化价值。请配置Gemini API密钥以获取AI生成的专业文物介绍。"
        }
    }

    private fun formatTitle(label: String): String {
        return label.replaceFirstChar { it.uppercase() }
    }
}
