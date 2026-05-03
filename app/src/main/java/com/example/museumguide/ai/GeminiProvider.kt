package com.example.museumguide.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.museumguide.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * AI provider using Google Gemini 2.0 Flash API.
 * Uses Gemini's proprietary API format with structured JSON prompts.
 */
class GeminiProvider : AiProvider {

    override val providerId: String = AiConfiguration.PROVIDER_GEMINI
    override val displayName: String = "Google Gemini 2.0 Flash"
    override val supportsMultimodal: Boolean = true

    companion object {
        private const val API_PATH = "/v1beta/models/gemini-2.0-flash:generateContent"
        private const val TIMEOUT_SECONDS = 15L
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
                    .url("https://generativelanguage.googleapis.com$API_PATH?key=$apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    return@withContext null
                }

                parseGeminiResponse(responseBody, label)
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
        return withContext(Dispatchers.IO) {
            try {
                val compressed = compressBitmap(bitmap)
                val base64Image = bitmapToBase64(compressed)
                val prompt = buildMultimodalPrompt(context)

                val requestBody = gson.toJson(
                    mapOf(
                        "contents" to listOf(
                            mapOf(
                                "parts" to listOf(
                                    mapOf("text" to prompt),
                                    mapOf(
                                        "inlineData" to mapOf(
                                            "mimeType" to "image/jpeg",
                                            "data" to base64Image
                                        )
                                    )
                                )
                            )
                        ),
                        "generationConfig" to mapOf(
                            "temperature" to 0.7,
                            "maxOutputTokens" to 1024,
                            "topP" to 0.9
                        )
                    )
                ).toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com$API_PATH?key=$apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    return@withContext null
                }

                parseGeminiResponse(responseBody, "图片识别")
            } catch (e: Exception) {
                null
            }
        }
    }

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

    private fun buildMultimodalPrompt(context: String): String {
        return """你是一个专业的博物馆导游AI助手。请分析这张图片中的物体，并生成一段优美的中文文物介绍。
物体类别: $context

要求：
1. 识别图片中的主要物体，给出一个博物馆中常见的文物名称
2. 用生动、专业的博物馆语言描述
3. 内容要有文化底蕴
4. 如果图片中的物体不是典型文物，请给出最接近的博物馆类别描述

请严格按照以下JSON格式回复（不要包含markdown代码块标记，只返回纯JSON）：
{
  "title": "文物中文名称",
  "brief": "一句话简介（25字以内）",
  "description": "详细的文物描述（100-200字，包括外观、材质、工艺、历史背景等），用中文叙述",
  "significance": "文物的文化历史意义（50-100字）"
}"""
    }

    private fun parseGeminiResponse(responseBody: String, label: String): AiResponse? {
        return try {
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

    private fun compressBitmap(bitmap: Bitmap, maxDimension: Int = 800): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val scale = if (width > height) maxDimension.toFloat() / width
                    else maxDimension.toFloat() / height
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
