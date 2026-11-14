package com.Lilith.FMusic.dango.musicui

import com.Lilith.FMusic.dango.i18n.I18n
import com.Lilith.FMusic.dango.module.impls.music.NeteaseMusicModule
import com.Lilith.FMusic.dango.music.AudioQuality
import com.Lilith.FMusic.dango.ui.LayoutBounds
import com.Lilith.FMusic.dango.ui.ShadowStyle
import com.Lilith.FMusic.dango.ui.ShapeStyle
import com.Lilith.FMusic.dango.ui.Theme
import com.Lilith.FMusic.dango.ui.Vec2
import com.Lilith.FMusic.dango.utils.Color
import kotlin.math.max
import kotlin.math.min

/**
 * 播放栏上的音质切换：徽章显示当前选择的档位（均衡器按钮左边），点开是从徽章上方展开的弹出列表。
 * 只影响本机播放器自己的取流（不影响服务端点歌和其它播放器）；切换后按新档位重新加载当前歌曲。
 */
internal class QualityPanel(private val ui: Ui, private val module: NeteaseMusicModule) {

    var open = false
    /** 打开时调用（播放栏用它关掉别的面板） */
    var onOpen: (() -> Unit)? = null
    private val openAnim = Spring(320f, 26f)
    private val hoverA = Spring(300f, 26f)
    private val pressA = Spring(420f, 13f)

    var panelRect = LayoutBounds()
        private set
    private var buttonRect = LayoutBounds()

    /** 徽章宽度：按最长的档位名算，中英文都能放下；窗口太窄时调用方不画它 */
    fun badgeWidth(): Float {
        var widest = 0f
        for (quality in AudioQuality.values()) widest = max(widest, ui.width(I18n.text(quality.label), 10f, Theme.FONT_SEMIBOLD))
        return (widest + 18f).coerceIn(46f, 76f)
    }

    fun drawButton(b: LayoutBounds) {
        buttonRect = b
        if (!open) ui.tip(b, I18n.text("Audio quality"), above = true)
        val h = hoverA.tick(if (ui.hover(b) || open) 1f else 0f).coerceIn(0f, 1f)
        val press = pressA.tick(0f)
        val color = if (open || h > 0.35f) RED else Theme.TEXT_PRIMARY
        ui.ctx.drawShape(b, ShapeStyle(Theme.TRACK.copy(a = 0.45f + 0.40f * h), 8f, 1f, color.copy(a = 0.42f + 0.25f * h)))
        val label = ui.trim(I18n.text(module.preferredQuality().label), b.width - 12f, 10f, Theme.FONT_SEMIBOLD)
        ui.text(label, b.x + (b.width - ui.width(label, 10f, Theme.FONT_SEMIBOLD)) / 2f,
            b.y + b.height / 2f - 10f * 0.39f - 0.5f - press, 10f, color, Theme.FONT_SEMIBOLD)
        ui.hits += b to {
            pressA.v -= 7f
            val opening = !open
            if (opening) onOpen?.invoke()
            open = opening
        }
    }

    fun drawPanel(bar: LayoutBounds) {
        val a = openAnim.tick(if (open) 1f else 0f).coerceIn(0f, 1f)
        if (a < 0.01f) {
            panelRect = LayoutBounds()
            return
        }
        val tiers = AudioQuality.values().reversed() // 无损 / 高音质 / 标准：高的在上面
        val rowH = 34f
        val w = 196f
        val h = 40f + tiers.size * rowH + 26f
        val anchor = if (buttonRect.width > 0f) buttonRect else LayoutBounds(bar.right - 60f, bar.y, 50f, 26f)
        val right = min(bar.right - 6f, anchor.right + 6f)
        val p = LayoutBounds(
            max(bar.x + 6f, right - w),
            max(8f, bar.y - 10f - h + (1f - a) * 10f),
            w, h,
        )
        panelRect = p
        val ctx = ui.ctx
        ctx.pushOpacity(a)
        ctx.drawGlass(p, QUALITY_GLASS)
        val x0 = p.x + 14f
        val x1 = p.right - 14f
        ui.text(I18n.text("Audio quality"), x0, p.y + 13f, 12f, Theme.TEXT_SECONDARY, Theme.FONT_MEDIUM)
        for ((i, tier) in tiers.withIndex()) {
            val row = LayoutBounds(x0 - 6f, p.y + 36f + i * rowH, (x1 - x0) + 12f, rowH - 4f)
            val selected = tier == module.preferredQuality()
            if (selected) ui.card(row, RED.copy(a = 0.16f), 8f)
            else if (ui.hover(row)) ui.card(row, Theme.CARD_HOVER, 8f)
            if (selected) {
                // 选中：左边一道小红条，名字用红色（不依赖字体里的勾字符号）
                ctx.drawShape(LayoutBounds(row.x + 5f, row.y + 7f, 2.5f, row.height - 14f), ShapeStyle(RED, 1.25f))
            }
            ui.text(I18n.text(tier.label), row.x + 14f, row.y + row.height / 2f - 11.5f * 0.39f - 0.5f, 11.5f,
                if (selected) RED else Theme.TEXT_PRIMARY,
                if (selected) Theme.FONT_SEMIBOLD else Theme.FONT_MEDIUM)
            // 右侧只写一个最短的档位说明，不做成图里那种复杂描述
            val tag = tier.shortHint
            ui.text(tag, row.right - 10f - ui.width(tag, 9.5f), row.y + row.height / 2f - 9.5f * 0.39f - 0.5f, 9.5f, Theme.TEXT_TERTIARY)
            ui.hits += row to {
                open = false
                module.selectQuality(tier)
            }
        }
        val hint = I18n.text("Switching reloads the current song")
        ui.text(ui.trim(hint, x1 - x0, 9f), x0, p.y + h - 19f, 9f, Theme.TEXT_TERTIARY)
        ui.hits += p to {}
        ctx.popOpacity()
    }

    /** 面板开着时点到外面就关掉；返回 true = 这次点击被吃掉 */
    fun mouseDown(mx: Int, my: Int): Boolean {
        if (!open) return false
        if (panelRect.contains(mx, my) || buttonRect.contains(mx, my)) return false
        open = false
        return true
    }
}

/** 列表右侧的简短说明（不带账号/会员信息，只说这一档是什么） */
private val AudioQuality.shortHint: String
    get() = when (this) {
        AudioQuality.STANDARD -> "128 kbps"
        AudioQuality.HIGH -> "320 kbps"
        AudioQuality.LOSSLESS -> "FLAC"
    }

private val QUALITY_GLASS = Theme.GLASS_SIDEBAR.copy(radius = 20f, shadow = ShadowStyle(Color(0f, 0f, 0f, 0.22f), 20f, Vec2(0f, -4f)))
