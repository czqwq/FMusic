package com.Lilith.FMusic.dango.music

enum class AudioQuality(val key: String, val label: String) {
    STANDARD("standard", "Standard"),
    HIGH("high", "High quality"),
    LOSSLESS("lossless", "Lossless");

    val cacheEnabled: Boolean get() = this != STANDARD
    val fallbacks: List<AudioQuality> get() = values().take(ordinal + 1).reversed()
    val neteaseLevel: String get() = when (this) {
        STANDARD -> "standard"
        HIGH -> "exhigh"
        LOSSLESS -> "lossless"
    }
    val kugouQuality: String get() = when (this) {
        STANDARD -> "128"
        HIGH -> "320"
        LOSSLESS -> "flac"
    }
    val qqFile: Pair<String, String> get() = when (this) {
        STANDARD -> "M500" to "mp3"
        HIGH -> "M800" to "mp3"
        LOSSLESS -> "F000" to "flac"
    }

    companion object {
        fun fromSetting(value: Int): AudioQuality = values().getOrElse(value) { STANDARD }

        internal fun fromAudio(format: String, bitrate: Long, fallback: AudioQuality = STANDARD): AudioQuality = when {
            format.equals("flac", true) -> LOSSLESS
            bitrate >= 256000 -> HIGH
            bitrate > 0 -> STANDARD
            fallback == LOSSLESS -> STANDARD
            else -> fallback
        }
    }
}
