package com.example.museumguide.ai

import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Generic AI provider for OpenAI-compatible APIs.
 * Supports DeepSeek, Qwen (阿里云百炼), and ERNIE (百度千帆)
 * which all implement the OpenAI chat completions format.
 *
 * All three providers use the same /v1/chat/completions endpoint pattern
 * but differ in base URL and model name.
 */
class OpenAiCompatibleProvider(
    override val providerId: String,
    override val displayName: String,
    private val baseUrl: String,
    private val defaultModelName: String,
    override val supportsMultimodal: Boolean = false
) : AiProvider {

    companion object {
        private const val TIMEOUT_SECONDS = 15L

        /** Pre-configured DeepSeek provider. */
        fun deepSeek(): OpenAiCompatibleProvider = OpenAiCompatibleProvider(
            providerId = AiConfiguration.PROVIDER_DEEPSEEK,
            displayName = "DeepSeek (深度求索)",
            baseUrl = "https://api.deepseek.com",
            defaultModelName = "deepseek-chat",
            supportsMultimodal = false
        )

        /** Pre-configured Qwen provider (阿里云百炼). */
        fun qwen(): OpenAiCompatibleProvider = OpenAiCompatibleProvider(
            providerId = AiConfiguration.PROVIDER_QWEN,
            displayName = "通义千问 Qwen (阿里云)",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModelName = "qwen-turbo",
            supportsMultimodal = false
        )

        /** Pre-configured ERNIE provider (百度千帆, OpenAI-compatible endpoint). */
        fun ernie(): OpenAiCompatibleProvider = OpenAiCompatibleProvider(
            providerId = AiConfiguration.PROVIDER_ERNIE,
            displayName = "文心一言 ERNIE (百度)",
            baseUrl = "https://qianfan.baidubce.com/v2",
            defaultModelName = "ernie-speed-8k",
            supportsMultimodal = false
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun generateDescription(
        label: String,
        context: String,
        apiKey: String
    ): AiResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(label, context)
                val requestBody = gson.toJson(
                    mapOf(
                        "model" to defaultModelName,
                        "messages" to listOf(
                            mapOf(
                                "role" to "user",
                                "content" to prompt
                            )
                        ),
                        "temperature" to 0.7,
                        "max_tokens" to 1024
                    )
                ).toRequestBody("application/json".toMediaType())

                val url = when (providerId) {
                    AiConfiguration.PROVIDER_ERNIE -> "$baseUrl/$defaultModelName"
                    else -> "$baseUrl/chat/completions"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    return@withContext null
                }

                parseResponse(responseBody, label)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun generateDescriptionFromImage(
        bitmap: Bitmap,
        context: String,
        apiKey: String
    ): AiResponse? {
        // Multimodal not supported by basic OpenAI-compatible providers
        return null
    }

    private fun buildPrompt(label: String, context: String): String {
        return """你是一个专业的博物馆导游AI助手。请根据检测到的物体标签，生成一段优美的中文文物介绍。

检测标签: "$label"
物体类别: $context

要求：
1. 基于标签推测一个合理的文物中文名称
2. 用生动、专业的博物馆语言描述
3. 内容要有文化底蕴

请严格按照以下JSON格式回复（只返回纯JSON，不要包含markdown代码块标记）：
{
  "title": "文物中文名称",
  "brief": "一句话简介（25字以内）",
  "description": "详细的文物描述（100-200字）",
  "significance": "文物的文化历史意义（50-100字）"
}"""
    }

    private fun parseResponse(responseBody: String, label: String): AiResponse? {
        return try {
            val root: JsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            // OpenAI format: choices[0].message.content
            val choices = root.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject.getAsJsonObject("message")
                val content = message?.get("content")?.asString ?: ""
                if (content.isNotBlank()) {
                    return extractJsonFromText(content, label)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJsonFromText(text: String, label: String): AiResponse? {
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
                        brief = json.get("brief")?.asString ?: "检测到「$label」",
                        description = json.get("description")?.asString ?: "",
                        significance = json.get("significance")?.asString ?: ""
                    )
                }
            } catch (_: Exception) {}
        }
        val truncated = if (text.length > 500) text.take(500) + "…" else text
        return AiResponse(
            title = label,
            brief = "检测到「$label」",
            description = truncated.ifBlank { "" },
            significance = ""
        )
    }
}
