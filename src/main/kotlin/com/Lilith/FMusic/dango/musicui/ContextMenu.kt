package com.Lilith.FMusic.dango.musicui

import com.Lilith.FMusic.dango.ui.LayoutBounds
import com.Lilith.FMusic.dango.ui.ShadowStyle
import com.Lilith.FMusic.dango.ui.Theme
import com.Lilith.FMusic.dango.ui.Vec2
import com.Lilith.FMusic.dango.utils.Color

import kotlin.math.max

/**
 * 通用右键菜单（液态玻璃风）：在鼠标位置弹出几项，点一项执行一项，点别处只关掉。
 * 现在是歌曲列表行上的"发送到点歌系统"，以后要加别的项，往 [show] 里多塞几个 [Item] 就行。
 *
 * 菜单用屏幕坐标（不受页面切换动画的位移影响），画在播放栏之后，文字和其它文字一起最后统一 flush。
 */
internal class ContextMenu(private val ui: Ui) {

    /** 一项：文字、能不能点（不能点时画灰、点了没反应）、点了做什么 */
    class Item(val label: String, val enabled: Boolean = true, val action: () -> Unit)

    private var open = false
    private var items: List<Item> = emptyList()
    /** 每项这一帧的位置，跟着 [panel] 算 */
    private var rows: List<LayoutBounds> = emptyList()
    private var panel = LayoutBounds()

    val isOpen: Boolean get() = open

    /** 面板这一刻的位置；没打开时是空的 */
    val rect: LayoutBounds get() = panel

    /** 在鼠标位置 ([x], [y]) 弹出：鼠标位置当左上角，右边 / 下边放不下就翻到鼠标的左边 / 上边 */
    fun show(items: List<Item>, x: Int, y: Int) {
        if (items.isEmpty()) {
            close()
            return
        }
        this.items = items
        var textW = 0f
        for (item in items) textW = max(textW, ui.width(item.label, TEXT_SIZE, Theme.FONT_SEMIBOLD))
        val w = max(MIN_WIDTH, textW + (PAD + TEXT_INSET) * 2f)
        val h = PAD * 2f + items.size * ITEM_H
        var px = x.toFloat()
        var py = y.toFloat()
        if (px + w > ui.lw) px -= w
        if (py + h > ui.lh) py -= h
        // 翻过去还放不下（窗口特别小）就夹在窗口里
        px = px.coerceIn(0f, max(0f, ui.lw - w))
        py = py.coerceIn(0f, max(0f, ui.lh - h))
        panel = LayoutBounds(px, py, w, h)
        rows = items.indices.map { LayoutBounds(px + PAD, py + PAD + it * ITEM_H, w - PAD * 2f, ITEM_H) }
        open = true
    }

    fun close() {
        open = false
        items = emptyList()
        rows = emptyList()
        panel = LayoutBounds()
    }

    /** 画在其它东西上面；文字走 [Ui.mt]，由外面统一 flush */
    fun draw() {
        if (!open || rows.size != items.size) return
        ui.ctx.drawGlass(panel, MENU_GLASS)
        for (i in rows.indices) {
            val item = items[i]
            val row = rows[i]
            val hot = ui.hover(row)
            if (hot && item.enabled) ui.card(row, Theme.CARD_HOVER, 6f)
            val color = when {
                !item.enabled -> Theme.TEXT_TERTIARY
                hot -> Color(1f, 1f, 1f, 1f)
                else -> Theme.TEXT_PRIMARY
            }
            ui.text(item.label, row.x + TEXT_INSET, row.y + row.height / 2f - TEXT_SIZE * 0.39f - 0.5f,
                TEXT_SIZE, color, Theme.FONT_SEMIBOLD)
        }
    }

    /**
     * 按下鼠标：点在菜单里 → 点到的项能点就执行并关掉（点在菜单的边距上就什么都不做，菜单留着）；
     * 点在菜单外 → 只关掉。两种情况都返回 true：这一下不往下传给播放栏 / 列表，免得误触。
     */
    fun mouseDown(mx: Int, my: Int): Boolean {
        if (!open) return false
        if (panel.contains(mx, my)) {
            val i = rows.indices.firstOrNull { rows[it].contains(mx, my) }
            if (i != null && items[i].enabled) {
                val action = items[i].action
                close()
                action()
            }
            return true
        }
        close()
        return true
    }
}

private const val ITEM_H = 30f
private const val PAD = 6f
private const val MIN_WIDTH = 168f
private const val TEXT_SIZE = 12f
/** 文字离项左边多远 */
private const val TEXT_INSET = 12f

private val MENU_GLASS = Theme.GLASS_SIDEBAR.copy(
    radius = 10f,
    shadow = ShadowStyle(Color(0f, 0f, 0f, 0.25f), 16f, Vec2(0f, -3f)),
)
