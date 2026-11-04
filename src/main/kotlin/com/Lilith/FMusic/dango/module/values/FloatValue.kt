package com.Lilith.FMusic.dango.module.values

import kotlin.math.pow

class FloatValue(
    val minRange: Float,
    val maxRange: Float,
    value: Float = minRange,
    val decimals: Int = 1,
) {
    var value: Float = minRange
        private set

    init {
        setValue(value)
    }

    fun setValue(value: Float) {
        // 不是数（比如拿 0 宽度的滑条算出来的）就不要，否则之后显示 / 保存都会出错
        if (value.isNaN()) return
        this.value = round(value.coerceIn(minRange, maxRange))
    }

    fun normalize(): Float {
        return (value - minRange) /
                (maxRange - minRange)
    }

    private fun round(value: Float): Float {
        val scale = 10.0.pow(decimals).toFloat()
        return kotlin.math.round(value * scale) / scale
    }
}