package com.Lilith.FMusic.dango.platform

import com.Lilith.FMusic.dango.module.values.IntValue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum class PlayerAction(val id: String, val defaultKey: Int = Keys.NONE) {
    OPEN("open_player", Keys.M), PAUSE("play_pause"), PREVIOUS("previous"), NEXT("next"),
    VOLUME_UP("volume_up"), VOLUME_DOWN("volume_down"), LYRICS("toggle_lyrics");

    val translationKey: String get() = "key.fmusic_dango.$id"
}

class PlayerKeyBinding(val action: PlayerAction, val value: IntValue)

/**
 * 鼠标左键 / 右键不能当快捷键：绑上以后界面里每次点击都被当成快捷键吃掉，什么都点不了。
 * 原版按键设置里绑的、旧配置里存的，都当成没绑定。
 */
fun allowedPlayerKey(key: Int): Int = if (key == Keys.mouse(0) || key == Keys.mouse(1)) Keys.NONE else key

/** 对接实际原版按键；首次优先采用原版已保存值，没有时迁移模组已有绑定。 */
class KeyBindingLink(
    private val setting: PlayerKeyBinding,
    private val readNative: () -> Int,
    private val writeNative: (Int) -> Unit,
    savedNative: Int?,
) {
    private var previous: Int

    init {
        if (savedNative != null) setting.value.setValue(savedNative)
        setting.value.setValue(allowedPlayerKey(setting.value.value))
        previous = setting.value.value
        writeNative(previous)
    }

    /** 有修改时返回 true，调用方批量刷新原版映射并保存。 */
    fun sync(): Boolean {
        val configured = allowedPlayerKey(setting.value.value)
        if (configured != setting.value.value) setting.value.setValue(configured)
        val native = readNative()
        return when {
            configured != previous -> {
                writeNative(configured)
                previous = configured
                true
            }
            native != previous -> {
                setting.value.setValue(allowedPlayerKey(native))
                previous = setting.value.value
                if (previous != native) writeNative(previous)
                true
            }
            else -> false
        }
    }
}

/** 只读 Dango Music 的原版绑定项，不重新加载整个 options.txt。 */
fun savedPlayerKeys(file: Path): Map<String, String> = try {
    if (!Files.exists(file)) emptyMap() else Files.readAllLines(file, StandardCharsets.UTF_8)
        .filter { it.startsWith("key_key.fmusic_dango.") }
        .mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon < 0) null else line.substring(4, colon) to line.substring(colon + 1)
        }.toMap()
} catch (_: Exception) {
    emptyMap()
}
