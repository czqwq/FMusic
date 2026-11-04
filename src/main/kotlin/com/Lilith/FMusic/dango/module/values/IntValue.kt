package com.Lilith.FMusic.dango.module.values

class IntValue(
    val minRange: Int,
    val maxRange: Int,
    value: Int = minRange,
) {

    var value: Int = minRange
        private set

    init {
        setValue(value)
    }

    fun setValue(value: Int) {
        this.value = value.coerceIn(minRange, maxRange)
    }

    fun normalize(): Float {
        return (value - minRange).toFloat() /
                (maxRange - minRange)
    }
}