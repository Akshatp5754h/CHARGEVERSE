package com.akshat.chargeverse.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Media kinds the animation engine can play. */
enum class MediaType { VIDEO, GIF, IMAGE, WEBP, LOTTIE }

/** Original CHARGEVERSE animation categories. */
enum class AnimationCategory {
    CINEMATIC_3D, ANIME, CYBERPUNK, GAMING, RGB_NEON, LUXURY,
    AMOLED_MINIMAL, SPACE, NATURE, ABSTRACT, HOLOGRAPHIC, ELECTRIC_ENERGY,
}

@Entity(
    tableName = "media_items",
    indices = [Index("category"), Index("addedAtMs")],
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MediaType,
    /** content:// or file:// URI of the media on device. */
    val uri: String,
    val title: String,
    val category: AnimationCategory,
    val addedAtMs: Long,
    val sizeBytes: Long,
    val isBuiltIn: Boolean,
    /** 0 for still images. */
    val durationMs: Long,
    /** Width / height in px, 0 when unknown. */
    val widthPx: Int,
    val heightPx: Int,
)

@Entity(
    tableName = "animation_profiles",
    indices = [Index("isActive")],
)
data class AnimationProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** FK -> media_items.id */
    val mediaItemId: Long,
    val scale: Float = 1f,
    /** Normalized offset, -1..1 relative to screen size. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val playbackSpeed: Float = 1f,
    val backgroundHex: Long = 0xFF050510,
    val textOverlay: String = "",
    val showBatteryOverlay: Boolean = true,
    val showPercent: Boolean = true,
    val isActive: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/** Roles a sound can play in the sound studio. */
enum class SoundRole { CONNECT, DISCONNECT, FULL, LOW }

@Entity(tableName = "sound_items", indices = [Index("role")])
data class SoundItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** content://, file:// or asset:// uri. */
    val uri: String,
    val role: SoundRole,
    val isBuiltIn: Boolean,
    val addedAtMs: Long,
)

/** Automation triggers supported by the rules engine. */
enum class AutomationTrigger {
    CHARGER_CONNECTED, CHARGER_DISCONNECTED, FULL_BATTERY, LOW_BATTERY,
    PERCENT_ABOVE, PERCENT_BELOW,
}

/** Actions a rule can perform. */
enum class AutomationAction {
    APPLY_PROFILE, PLAY_CONNECT_SOUND, PLAY_DISCONNECT_SOUND,
    MUTE_SOUNDS, UNMUTE_SOUNDS,
}

@Entity(tableName = "automation_rules", indices = [Index("enabled")])
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: AutomationTrigger,
    /** Percent threshold for PERCENT_ABOVE / PERCENT_BELOW. */
    val triggerValue: Int = 0,
    val action: AutomationAction,
    /** Target profile id for APPLY_PROFILE. */
    val targetProfileId: Long? = null,
    val enabled: Boolean = true,
    val createdAtMs: Long,
    /** Set after the rule fired once for one-shot triggers (FULL, LOW). */
    val lastFiredMs: Long? = null,
)

/** History of generations from the optional local AI studio. */
@Entity(tableName = "ai_history", indices = [Index("createdAtMs")])
data class AiHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** TXT2IMG or IMG2VID */
    val workflow: String,
    val prompt: String,
    val serverUrl: String,
    val outputUri: String?,
    val createdAtMs: Long,
    /** OK / ERROR / RUNNING */
    val status: String,
    val errorMessage: String? = null,
)
