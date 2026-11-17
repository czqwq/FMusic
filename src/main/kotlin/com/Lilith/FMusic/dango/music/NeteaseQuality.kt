package com.Lilith.FMusic.dango.music

/**
 * 网易云音质档位。顺序就是从低到高：请求某一档时，从这一档开始往下逐档回退，
 * 所以高音质档位拿不到时会自动退到低一档，而不是直接放不出来。
 *
 * 网易云网页版自己把这些叫「标准 / 较高 / 极高 / 无损 / Hi-Res / 超清母带」，
 * 有损档给 mp3，无损及以上要 flac。实际拿到的档位以音频文件本身为准
 * （见 [AudioQuality.fromAudio]），请求的高档位没给到时播放栏会提示。
 */
enum class NeteaseQuality(
    val key: String,
    val label: String,
    /** 网易云播放地址接口的 level 参数 */
    val level: String,
    val lossless: Boolean,
    /** 老码率接口（br）的兜底值 */
    val legacyBitrate: Int,
) {
    STANDARD("standard", "Standard", "standard", false, 128_000),
    HIGHER("higher", "Higher", "higher", false, 192_000),
    EXHIGH("exhigh", "High quality", "exhigh", false, 320_000),
    LOSSLESS("lossless", "Lossless", "lossless", true, 320_000),
    HIRES("hires", "Hi-Res", "hires", true, 320_000),
    MASTER("master", "Master", "jymaster", true, 320_000),
    ;

    /** 请求它时取流用的扩展名：无损档明确要 flac */
    val encodeType: String get() = if (lossless) "flac" else "mp3"

    /**
     * 用来做缓存 / 回退提示的归类：无损三档都算无损，极高算高音质，
     * 标准和较高都算标准档（标准档不落磁盘缓存）。
     */
    val audioQuality: AudioQuality
        get() = when (this) {
            STANDARD, HIGHER -> AudioQuality.STANDARD
            EXHIGH -> AudioQuality.HIGH
            LOSSLESS, HIRES, MASTER -> AudioQuality.LOSSLESS
        }

    /** 有损 / 无损的简短说明（播放栏音质面板右侧那一列） */
    val hint: String
        get() = when (this) {
            STANDARD -> "128 kbps"
            HIGHER -> "192 kbps"
            EXHIGH -> "320 kbps"
            LOSSLESS -> "FLAC"
            HIRES -> "Hi-Res"
            MASTER -> "Master"
        }

    companion object {
        fun fromSetting(value: Int): NeteaseQuality = values().getOrElse(value) { LOSSLESS }
    }
}

/** 其它音源的通用三档设置换算成网易云档位（只有走通用设置的旧路径会用到）。 */
internal fun AudioQuality.asNeteaseQuality(): NeteaseQuality = when (this) {
    AudioQuality.STANDARD -> NeteaseQuality.STANDARD
    AudioQuality.HIGH -> NeteaseQuality.EXHIGH
    AudioQuality.LOSSLESS -> NeteaseQuality.LOSSLESS
}
