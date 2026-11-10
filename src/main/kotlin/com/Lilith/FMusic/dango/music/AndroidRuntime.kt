package com.Lilith.FMusic.dango.music

import java.io.File
import java.util.Locale

/**
 * 是不是在安卓上跑（启动器里的 Java）：安卓上的 Java Sound 是模拟出来的，放歌会掉帧，自动改用游戏的 OpenAL。
 *
 * 对应上游的 `AndroidRuntime.kt`。上游 1.7 之前这里是只弹一条聊天提示的 `AndroidPlaybackHint.kt`
 * （提示玩家自己加 `-Ddangomusic.openal=true`）；上游后来改成直接检测、自动换输出，
 * 本模组跟着改成一样的做法（见 `MusicPlayer.preferGameAudio`）。
 * 文件沿用原来的名字，只是里面换了实现。
 */
object AndroidRuntime {

    val detected: Boolean by lazy { detect() }

    /** Linux 桌面也可能装 Android SDK，不能只凭 Linux 或 ANDROID_HOME 就认定是安卓。 */
    fun isAndroid(osName: String, runtimeName: String, vmName: String, env: Map<String, String>, androidSystem: Boolean): Boolean {
        val os = osName.lowercase(Locale.ROOT)
        if (os.contains("android")) return true
        if (!os.contains("linux")) return false
        val runtime = (runtimeName + " " + vmName).lowercase(Locale.ROOT)
        return runtime.contains("android") || runtime.contains("dalvik") || androidSystem ||
            (env["ANDROID_ROOT"] == "/system" && env["ANDROID_DATA"] == "/data") ||
            !env["POJAV_NATIVEDIR"].isNullOrBlank()
    }

    private fun detect(): Boolean = try {
        val env = listOf("ANDROID_ROOT", "ANDROID_DATA", "POJAV_NATIVEDIR").mapNotNull { key ->
            System.getenv(key)?.let { key to it }
        }.toMap()
        isAndroid(System.getProperty("os.name", ""), System.getProperty("java.runtime.name", ""),
            System.getProperty("java.vm.name", ""), env,
            listOf("/system/build.prop", "/system/bin/app_process", "/system/bin/app_process64").any { File(it).isFile })
    } catch (_: SecurityException) { false }
}
