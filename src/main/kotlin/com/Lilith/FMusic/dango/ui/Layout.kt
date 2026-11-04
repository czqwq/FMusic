package com.Lilith.FMusic.dango.ui

import kotlin.math.sqrt

data class Size(val width: Float, val height: Float) {
    companion object {
        val ZERO = Size(0f, 0f)
    }

    fun expand(width: Float, height: Float): Size {
        return Size(this.width + width, this.height + height)
    }

    fun distance(other: Size): Float {
        val dw = width - other.width
        val dh = height - other.height
        return sqrt(dw * dw + dh * dh)
    }
}

data class SizeSpec(val value: Float, val type: Type) {
    companion object {
        fun px(value: Int): SizeSpec {
            return SizeSpec(value.toFloat(), Type.PX)
        }

        fun auto(): SizeSpec {
            return SizeSpec(0f, Type.AUTO)
        }

        fun percent(value: Float): SizeSpec {
            return SizeSpec(value, Type.PERCENTAGE)
        }
    }

    fun toValue(parentSize: Float): Float {
        return when (type) {
            Type.PX -> value
            Type.PERCENTAGE -> parentSize * value
            Type.AUTO -> parentSize
        }
    }

    enum class Type {
        AUTO, PX, PERCENTAGE
    }
}

data class LayoutBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    val left: Float get() = x
    val top: Float get() = y
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(x1: Int, y1: Int): Boolean {
        return x1 >= x &&
                x1 < x + width &&
                y1 >= y &&
                y1 < y + height
    }

    fun expand(ex: Float, ey: Float): LayoutBounds {
        return LayoutBounds(
            x - ex,
            y - ey,
            width + ex * 2,
            height + ey * 2,
        )
    }

    fun intersect(other: LayoutBounds): LayoutBounds {
        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(right, other.right)
        val bottom = minOf(bottom, other.bottom)

        return LayoutBounds(
            left,
            top,
            (right - left).coerceAtLeast(0f),
            (bottom - top).coerceAtLeast(0f)
        )
    }
}