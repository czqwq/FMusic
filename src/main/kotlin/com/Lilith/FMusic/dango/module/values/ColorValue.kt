package com.Lilith.FMusic.dango.module.values

/** 一个颜色（0xRRGGBB，不带透明度），配置里存成 "#RRGGBB" */
class ColorValue(val default: Int) {
    var value: Int = default and 0xFFFFFF
        private set

    fun setValue(rgb: Int) {
        value = rgb and 0xFFFFFF
    }

    fun reset() {
        value = default and 0xFFFFFF
    }

    /** "#RRGGBB" */
    fun hex(): String = "#" + String.format("%06X", value)

    companion object {
        /** 解析 "#RRGGBB" / "RRGGBB" / "#RGB"，不对就返回 null */
        fun parse(text: String): Int? {
            var t = text.trim().removePrefix("#")
            if (t.length == 3 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                t = t.map { "$it$it" }.joinToString("")
            }
            if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return t.toInt(16)
        }
    }
}
