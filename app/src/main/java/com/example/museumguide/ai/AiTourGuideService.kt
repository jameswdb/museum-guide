package com.example.museumguide.ai

import com.example.museumguide.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * AI-powered tour guide service that uses Google Gemini API to generate
 * rich museum-style descriptions for detected artifacts.
 *
 * ## How it works
 * 1. When the local database has an exhibit for a detected label, AI can
 *    enhance the narration with more vivid, varied descriptions.
 * 2. When NO local exhibit matches, AI generates a description on-the-fly,
 *    allowing the app to talk about any detected object.
 * 3. Responses are cached in memory to avoid redundant API calls.
 *
 * ## Setup
 * Add to `local.properties`:
 * ```
 * GEMINI_API_KEY=your_gemini_api_key_here
 * ```
 * Obtain a free API key at https://aistudio.google.com/apikey
 *
 * ## Fallback behaviour
 * - No API key configured → returns localised fallback text
 * - Network error → returns cached or fallback text
 * - API error → returns fallback with error hint
 */
class AiTourGuideService(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    /**
     * Optional OkHttpClient for dependency injection (testing).
     * Defaults to a standard client with 15s timeouts.
     */
    private val okHttpClient: OkHttpClient? = null,
    /**
     * Base URL for the Gemini API. Override for testing with MockWebServer.
     * Default: official Google API endpoint.
     */
    private val endpointBaseUrl: String = "https://generativelanguage.googleapis.com"
) {
    companion object {
        private const val API_PATH =
            "/v1beta/models/gemini-2.0-flash:generateContent"
        private const val TIMEOUT_SECONDS = 15L
        private const val MAX_CACHE_SIZE = 100
    }

    /** In-memory LRU-like cache: detected label → generated response. */
    private val cache = ConcurrentHashMap<String, AiResponse>()
    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /**
     * Generate a museum-style Chinese description for a detected label.
     *
     * @param label  The detected label from TFLite (e.g. "vase", "bronze")
     * @param context  Optional context hint (e.g. "文物", "古代艺术品")
     * @return [AiResponse] with title, brief, description, and significance
     */
    suspend fun generateDescription(
        label: String,
        context: String = "文物"
    ): AiResponse {
        // 1. Check in-memory cache
        cache[label]?.let { return it }

        // 2. No API key → return built-in fallback
        if (apiKey.isBlank()) {
            val fallback = buildFallbackResponse(label)
            cache[label] = fallback
            return fallback
        }

        // 3. Call Gemini API
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(label, context)
                val requestBody = gson.toJson(
                    mapOf(
                        "contents" to listOf(
                            mapOf("parts" to listOf(mapOf("text" to prompt)))
                        ),
                        "generationConfig" to mapOf(
                            "temperature" to 0.7,
                            "maxOutputTokens" to 1024,
                            "topP" to 0.9
                        )
                    )
                ).toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$endpointBaseUrl$API_PATH?key=$apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    return@withContext buildFallbackResponse(
                        label, "API请求失败 (HTTP ${response.code})"
                    )
                }

                val aiResponse = parseGeminiResponse(responseBody, label)

                // Cache the result (evict oldest if at capacity)
                if (cache.size >= MAX_CACHE_SIZE) {
                    val oldest = cache.keys.firstOrNull()
                    if (oldest != null) cache.remove(oldest)
                }
                cache[label] = aiResponse

                aiResponse
            } catch (e: Exception) {
                buildFallbackResponse(label, e.message ?: "未知错误")
            }
        }
    }

    /**
     * Build a structured prompt that asks Gemini to generate a
     * Chinese museum-style description in JSON format.
     */
    private fun buildPrompt(label: String, context: String): String {
        return """你是一个专业的博物馆导游AI助手。请根据检测到的物体标签，生成一段优美的中文文物介绍。
检测标签: "$label"
物体类别: $context

要求：
1. 基于标签推测一个合理的文物名称（不要直接翻译标签，给出一个博物馆中常见的文物名称）
2. 用生动、专业的博物馆语言描述
3. 内容要有文化底蕴

请严格按照以下JSON格式回复（不要包含markdown代码块标记，只返回纯JSON）：
{
  "title": "文物中文名称",
  "brief": "一句话简介（25字以内）",
  "description": "详细的文物描述（100-200字，包括外观、材质、工艺、历史背景等），用中文叙述",
  "significance": "文物的文化历史意义（50-100字）"
}"""
    }

    /**
     * Parse the Gemini API response JSON to extract the generated content.
     */
    private fun parseGeminiResponse(responseBody: String, label: String): AiResponse {
        try {
            val root: JsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val candidate = candidates[0].asJsonObject
                val content = candidate.getAsJsonObject("content")
                val parts = content?.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val text = parts[0].asJsonObject.get("text")?.asString ?: ""
                    if (text.isNotBlank()) {
                        return extractJsonFromText(text, label)
                    }
                }
            }
            // Check for blocked response
            val promptFeedback = root.getAsJsonObject("promptFeedback")
            if (promptFeedback != null) {
                val blockReason = promptFeedback.get("blockReason")?.asString
                if (blockReason != null) {
                    return buildFallbackResponse(label, "内容被拦截: $blockReason")
                }
            }
        } catch (e: Exception) {
            return buildFallbackResponse(label, "解析响应失败: ${e.message}")
        }
        return buildFallbackResponse(label, "无法解析AI响应")
    }

    /**
     * Extract structured content from the Gemini response text.
     * Tries to find and parse a JSON object within the text.
     */
    private fun extractJsonFromText(text: String, label: String): AiResponse {
        // Find outermost JSON braces to extract structured content
        val cleaned = text.trim()
        val jsonStart = cleaned.indexOf('{')
        val jsonEnd = cleaned.lastIndexOf('}')

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val jsonStr = cleaned.substring(jsonStart, jsonEnd + 1)
            try {
                val json = gson.fromJson(jsonStr, JsonObject::class.java)
                if (json != null) {
                    return AiResponse(
                        title = json.get("title")?.asString ?: label,
                        brief = json.get("brief")?.asString
                            ?: "检测到「$label」",
                        description = json.get("description")?.asString
                            ?: generateLocalDescription(label),
                        significance = json.get("significance")?.asString ?: ""
                    )
                }
            } catch (_: Exception) {
                // JSON parse failed, continue to fallback
            }
        }

        // Fallback: use raw text as description
        val truncated = if (text.length > 500) text.take(500) + "…" else text
        return AiResponse(
            title = label,
            brief = "检测到「$label」",
            description = truncated.ifBlank { generateLocalDescription(label) },
            significance = ""
        )
    }

    /**
     * Build a fallback response when AI is unavailable.
     */
    private fun buildFallbackResponse(
        label: String,
        reason: String? = null
    ): AiResponse {
        val desc = generateLocalDescription(label)
        val note = if (reason != null) "\n\n（AI提示: $reason）" else ""
        return AiResponse(
            title = formatTitle(label),
            brief = "检测到「${formatTitle(label)}」",
            description = desc + note,
            significance = ""
        )
    }

    /**
     * Generate a basic localised description without AI, based on the label.
     */
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

    /** Clear the in-memory cache. */
    fun clearCache() {
        cache.clear()
    }
}

/**
 * Structured response from the AI tour guide service.
 *
 * @property title  Chinese display name for the detected object
 * @property brief  Short one-line summary for overlay display
 * @property description  Full narrative description for TTS narration
 * @property significance  Cultural / historical significance context
 */
data class AiResponse(
    val title: String,
    val brief: String,
    val description: String,
    val significance: String
)
