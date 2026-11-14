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

        /**
         * 只按音源实际返回的格式/码率判定，码率未知时按标准档处理：
         * 宁可少标，也不能把 128k 说成 320k（回退结果不冒充所选音质）。
         */
        internal fun fromAudio(format: String, bitrate: Long): AudioQuality = when {
            format.equals("flac", true) -> LOSSLESS
            bitrate >= 256000 -> HIGH
            else -> STANDARD
        }
    }
}
