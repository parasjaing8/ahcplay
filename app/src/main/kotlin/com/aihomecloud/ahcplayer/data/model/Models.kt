package com.aihomecloud.ahcplayer.data.model

enum class SourceType { SMB, AHC }

data class MediaSource(
    val id: Long = 0,
    val name: String,
    val host: String,
    val share: String,
    val port: Int = 445,
    val sourceType: SourceType = SourceType.SMB,
    val username: String = "",
    val hasPin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val smbBaseUri: String get() = "smb://$host/$share"
    val browseRootUri: String get() = when (sourceType) {
        SourceType.SMB -> "smb://$host/$share"
        SourceType.AHC -> buildString {
            append("ahc://$host:$port/srv/nas?share=$share")
            if (username.isNotEmpty()) append("&user=$username")
        }
    }
}

data class BrowseItem(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val mimeType: String = ""
) {
    val isVideo: Boolean get() {
        if (isDirectory) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS || mimeType.startsWith("video/")
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "avi", "mov", "wmv", "m4v", "ts", "flv",
            "webm", "ogv", "3gp", "hevc", "m2ts", "mts"
        )
    }
}

data class WatchHistory(
    val id: Long = 0,
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val sourceId: Long,
    val lastWatchedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}
