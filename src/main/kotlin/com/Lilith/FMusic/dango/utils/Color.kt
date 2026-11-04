package com.Lilith.FMusic.dango.utils

data class Color(
    var r: Float,
    var g: Float,
    var b: Float,
    var a: Float = 1f
) {
    companion object {
        fun fromARGB(argb: Long): Color {
            return fromARGB(argb.toInt())
        }

        fun fromARGB(argb: Int): Color {
            val a = (argb ushr 24 and 255) / 255f
            val r = (argb shr 16 and 255) / 255f
            val g = (argb shr 8 and 255) / 255f
            val b = (argb and 255) / 255f
            return Color(r, g, b, a)
        }

        fun fromRGB(rgb: Int): Color {
            val r = (rgb shr 16 and 255) / 255f
            val g = (rgb shr 8 and 255) / 255f
            val b = (rgb and 255) / 255f
            return Color(r, g, b)
        }

        fun fromHex(hex: String): Color {
            val clean = hex.trim().removePrefix("#").removePrefix("0x")

            return when (clean.length) {
                6 -> {
                    // RRGGBB
                    val r = clean.substring(0, 2).toInt(16)
                    val g = clean.substring(2, 4).toInt(16)
                    val b = clean.substring(4, 6).toInt(16)
                    Color(r / 255f, g / 255f, b / 255f, 1f)
                }

                8 -> {
                    // AARRGGBB
                    val a = clean.substring(0, 2).toInt(16)
                    val r = clean.substring(2, 4).toInt(16)
                    val g = clean.substring(4, 6).toInt(16)
                    val b = clean.substring(6, 8).toInt(16)
                    Color(r / 255f, g / 255f, b / 255f, a / 255f)
                }

                else -> {
                    throw IllegalArgumentException("Invalid hex color: $hex")
                }
            }
        }

        val WHITE = Color(1f, 1f, 1f, 1f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val RED = Color(1f, 0f, 0f, 1f)
        val GREEN = Color(0f, 1f, 0f, 1f)
        val BLUE = Color(0f, 0f, 1f, 1f)
        val TRANSPARENT = Color(0f, 0f, 0f, 0f)
    }

    fun brighter(factor: Float = 1.2f): Color {
        return Color(
            (r * factor).coerceIn(0f, 1f),
            (g * factor).coerceIn(0f, 1f),
            (b * factor).coerceIn(0f, 1f),
            a
        )
    }

    fun brighterIf(factor: Float = 1.2f, p: Boolean): Color {
        return if (p) brighter(factor) else this
    }

    fun toARGB(): Int {
        val ia = (a * 255).toInt() and 255
        val ir = (r * 255).toInt() and 255
        val ig = (g * 255).toInt() and 255
        val ib = (b * 255).toInt() and 255

        return (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
    }

    fun lerp(other: Color, t: Float): Color {
        return Color(
            r + (other.r - r) * t,
            g + (other.g - g) * t,
            b + (other.b - b) * t,
            a + (other.a - a) * t
        )
    }
}