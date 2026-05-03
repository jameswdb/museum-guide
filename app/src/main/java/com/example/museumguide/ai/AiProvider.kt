package com.example.museumguide.ai

import android.graphics.Bitmap

/**
 * Common interface for AI model providers.
 * Each provider handles its own API format and parsing.
 */
interface AiProvider {
    val providerId: String
    val displayName: String

    /**
     * Generate a Chinese museum-style description for a detection label.
     * @param label The detected label (e.g. "vase")
     * @param context Optional context hint
     * @param apiKey The API key for this provider
     * @return AiResponse with title/brief/description/significance, or null on failure
     */
    suspend fun generateDescription(
        label: String,
        context: String = "文物",
        apiKey: String
    ): AiResponse?

    /**
     * Generate a description from a bitmap image (multimodal).
     * @param bitmap The image bitmap to analyze
     * @param context Optional context hint
     * @param apiKey The API key for this provider
     * @return AiResponse or null on failure
     */
    suspend fun generateDescriptionFromImage(
        bitmap: Bitmap,
        context: String = "文物",
        apiKey: String
    ): AiResponse?

    /** Whether this provider supports image-based (multimodal) recognition. */
    val supportsMultimodal: Boolean
        get() = false
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
