package com.example.museumguide.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a museum exhibit/artifact.
 *
 * Each exhibit has a unique ID, a human-readable label used for recognition
 * matching, and rich metadata for display and audio narration.
 */
@Entity(tableName = "exhibits")
data class Exhibit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Unique exhibit identifier within the museum (e.g. "A12"). */
    val exhibitCode: String,
    /** Display name in Chinese (e.g. "青铜鼎"). */
    val name: String,
    /** Historical period / dynasty (e.g. "商代"). */
    val era: String,
    /** Brief one-line description for the camera overlay. */
    val brief: String,
    /** Full text description for the detail screen. */
    val description: String,
    /** Cultural significance / historical background. */
    val significance: String,
    /** Relative path or URL to the exhibit image. */
    val imageUrl: String = "",
    /** X coordinate on the museum map (normalized 0.0–1.0). */
    val mapPositionX: Float = 0f,
    /** Y coordinate on the museum map (normalized 0.0–1.0). */
    val mapPositionY: Float = 0f,
    /** Floor / hall section identifier. */
    val hallId: String = "main",
)
