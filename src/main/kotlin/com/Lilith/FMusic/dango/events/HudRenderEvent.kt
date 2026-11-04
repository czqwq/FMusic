package com.Lilith.FMusic.dango.events

import com.Lilith.FMusic.dango.event.Event

/**
 * 画屏幕上的 HUD（Song card、灵动岛、左上角 Now playing）。
 * 在原版暗角之后、快捷栏之前发出，所以暗角不会把它们压暗；隐藏 HUD（F1）时不画。
 */
object HudRenderEvent : Event
