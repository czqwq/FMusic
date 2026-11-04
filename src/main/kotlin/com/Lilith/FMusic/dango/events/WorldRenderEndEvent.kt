package com.Lilith.FMusic.dango.events

import com.Lilith.FMusic.dango.event.Event

data class WorldRenderEndEvent(
    val partialTicks: Float,
) : Event