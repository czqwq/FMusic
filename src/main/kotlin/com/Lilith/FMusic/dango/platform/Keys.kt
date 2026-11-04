package com.Lilith.FMusic.dango.platform

/**
 * 统一的键码：数值和 LWJGL 2（Minecraft 1.8.9）完全一样，配置文件里存的就是这些数。
 * 高版本用的是 GLFW 键码，由那边的平台层转换成这里的数，所以快捷键设置在各版本之间通用。
 */
object Keys {
    const val NONE = 0
    const val ESCAPE = 1
    const val BACK = 14
    const val TAB = 15
    const val RETURN = 28
    const val A = 30
    const val F = 33
    const val L = 38
    const val V = 47
    const val M = 50
    const val SPACE = 57
    const val NUMPADENTER = 156
    const val HOME = 199
    const val UP = 200
    const val PRIOR = 201
    const val LEFT = 203
    const val RIGHT = 205
    const val END = 207
    const val DOWN = 208
    const val NEXT = 209
    const val DELETE = 211

    /** 键码 → 名字（和 LWJGL 2 的 Keyboard.getKeyName 一致） */
    private val names: Map<Int, String> = buildMap {
        put(1, "ESCAPE")
        "1234567890".forEachIndexed { i, c -> put(2 + i, c.toString()) }
        put(12, "MINUS"); put(13, "EQUALS"); put(14, "BACK"); put(15, "TAB")
        "QWERTYUIOP".forEachIndexed { i, c -> put(16 + i, c.toString()) }
        put(26, "LBRACKET"); put(27, "RBRACKET"); put(28, "RETURN"); put(29, "LCONTROL")
        "ASDFGHJKL".forEachIndexed { i, c -> put(30 + i, c.toString()) }
        put(39, "SEMICOLON"); put(40, "APOSTROPHE"); put(41, "GRAVE"); put(42, "LSHIFT"); put(43, "BACKSLASH")
        "ZXCVBNM".forEachIndexed { i, c -> put(44 + i, c.toString()) }
        put(51, "COMMA"); put(52, "PERIOD"); put(53, "SLASH"); put(54, "RSHIFT"); put(55, "MULTIPLY")
        put(56, "LMENU"); put(57, "SPACE"); put(58, "CAPITAL")
        for (i in 1..10) put(58 + i, "F$i")
        put(69, "NUMLOCK"); put(70, "SCROLL")
        put(71, "NUMPAD7"); put(72, "NUMPAD8"); put(73, "NUMPAD9"); put(74, "SUBTRACT")
        put(75, "NUMPAD4"); put(76, "NUMPAD5"); put(77, "NUMPAD6"); put(78, "ADD")
        put(79, "NUMPAD1"); put(80, "NUMPAD2"); put(81, "NUMPAD3"); put(82, "NUMPAD0"); put(83, "DECIMAL")
        put(87, "F11"); put(88, "F12")
        for (i in 13..18) put(100 + (i - 13), "F$i")
        put(112, "KANA"); put(113, "F19"); put(121, "CONVERT"); put(123, "NOCONVERT"); put(125, "YEN")
        put(141, "NUMPADEQUALS"); put(144, "CIRCUMFLEX"); put(145, "AT"); put(146, "COLON"); put(147, "UNDERLINE")
        put(148, "KANJI"); put(149, "STOP"); put(150, "AX"); put(151, "UNLABELED")
        put(156, "NUMPADENTER"); put(157, "RCONTROL"); put(167, "SECTION"); put(179, "NUMPADCOMMA")
        put(181, "DIVIDE"); put(183, "SYSRQ"); put(184, "RMENU"); put(196, "FUNCTION"); put(197, "PAUSE")
        put(199, "HOME"); put(200, "UP"); put(201, "PRIOR"); put(203, "LEFT"); put(205, "RIGHT")
        put(207, "END"); put(208, "DOWN"); put(209, "NEXT"); put(210, "INSERT"); put(211, "DELETE")
        put(218, "CLEAR"); put(219, "LMETA"); put(220, "RMETA"); put(221, "APPS"); put(222, "POWER"); put(223, "SLEEP")
    }

    fun name(key: Int): String? = if (key in -100..-1) "Mouse ${key + 101}" else names[key]

    /** 与旧版 Minecraft 一致：鼠标左键 -100，右键 -99，中键 -98。 */
    fun mouse(button: Int): Int = button - 100

    /** 能输进文本框的字符（和原版 ChatAllowedCharacters 一样：不能是 § 和控制字符） */
    fun isAllowedChar(c: Char): Boolean = c != '§' && c >= ' ' && c != '\u007f'
}
