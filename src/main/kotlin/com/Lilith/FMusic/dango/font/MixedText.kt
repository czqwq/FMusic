package com.Lilith.FMusic.dango.font

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.gfx.CjkRun
import com.Lilith.FMusic.dango.gfx.CjkStyle
import com.Lilith.FMusic.dango.ui.LayoutBounds
import com.Lilith.FMusic.dango.ui.RenderContext
import com.Lilith.FMusic.dango.ui.Theme
import com.Lilith.FMusic.dango.utils.Color

/**
 * 中英混排：把一串文字按"拉丁字符 / 其它字符"切成几段，
 *   拉丁段用菜单字体（Inter，跟着液态玻璃一起画），
 *   中日韩和符号段用平滑字体（先记下来，玻璃画完后 [flush] 统一画）。
 * 坐标都是调用方的逻辑坐标，y 是文字顶部。
 */
class MixedText {

    companion object {
        /** 顶部 → 基线：和 Theme.drawText 的大写字高居中方式对齐 */
        private const val BASELINE = 0.39f + 0.727f / 2f
        /** 中文字比同字号的 Inter 显得大一点，稍微缩一下 */
        private const val CJK_SCALE = 0.96f

        private fun isLatin(c: Char) = c.code in 32..126
        fun bold(font: String) = font == Theme.FONT_SEMIBOLD || font == Theme.FONT_DISPLAY
        private fun style(bold: Boolean) = if (bold) CjkStyle.BOLD else CjkStyle.PLAIN
    }

    private val gfx get() = DangoClient.gfx

    private val runs = ArrayList<CjkRun>()

    /** 当前裁剪区域（逻辑坐标），之后记下的文字都按它裁剪 */
    var clip: LayoutBounds? = null

    /** 整体平移（和 ctx.pushTranslate 同步，做入场动画用）：之后记下的中文段都加上这个偏移 */
    var shiftX = 0f
    var shiftY = 0f

    fun clear() {
        runs.clear()
        clip = null
        shiftX = 0f
        shiftY = 0f
    }

    private inline fun segments(s: String, f: (String, Boolean) -> Unit) {
        var i = 0
        while (i < s.length) {
            val latin = isLatin(s[i])
            var j = i + 1
            while (j < s.length && isLatin(s[j]) == latin) j++
            f(s.substring(i, j), latin)
            i = j
        }
    }

    fun width(s: String, size: Float, font: String = Theme.FONT_REGULAR): Float {
        var w = 0f
        segments(s) { seg, latin ->
            w += if (latin) Theme.textWidth(seg, size, font) else gfx.cjkWidth(seg, size * CJK_SCALE, style(bold(font)))
        }
        return w
    }

    fun text(ctx: RenderContext, s: String, x: Float, y: Float, size: Float, color: Color, font: String = Theme.FONT_REGULAR) {
        if (s.isEmpty()) return
        val opacity = ctx.getOpacity()
        var pen = x
        segments(s) { seg, latin ->
            if (latin) {
                Theme.drawText(ctx, seg, pen, y + size * 0.39f, size, font, color)
                pen += Theme.textWidth(seg, size, font)
            } else {
                val cs = size * CJK_SCALE
                runs += CjkRun(seg, pen + shiftX, y + size * BASELINE + shiftY, cs, color.r, color.g, color.b, color.a * opacity, style(bold(font)), clip, false)
                pen += gfx.cjkWidth(seg, cs, style(bold(font)))
            }
        }
    }

    /** 图标符号（⌂ ≡ ♫ ♥ ↻ ⇄ ▶ …），按可见部分居中在 (cx, cy) */
    fun icon(ctx: RenderContext, s: String, cx: Float, cy: Float, size: Float, color: Color, bold: Boolean = false) {
        s.forEach { gfx.cjkPreload(it, style(bold)) }
        runs += CjkRun(s, cx + shiftX, cy + shiftY, size, color.r, color.g, color.b, color.a * ctx.getOpacity(), style(bold), clip, true)
    }

    fun trim(s: String, maxW: Float, size: Float, font: String = Theme.FONT_REGULAR): String {
        if (width(s, size, font) <= maxW) return s
        var end = s.length
        while (end > 0 && width(s.substring(0, end) + "...", size, font) > maxW) end--
        return s.substring(0, end) + "..."
    }

    /** 最多折成两行，第二行放不下就省略 */
    fun wrap2(s: String, maxW: Float, size: Float, font: String = Theme.FONT_REGULAR): List<String> {
        if (width(s, size, font) <= maxW) return listOf(s)
        var end = 1
        while (end < s.length && width(s.substring(0, end + 1), size, font) <= maxW) end++
        val sp = s.lastIndexOf(' ', end)
        if (sp > end / 2) end = sp
        val first = s.substring(0, end).trimEnd()
        val rest = s.substring(end).trimStart()
        return if (rest.isEmpty()) listOf(first) else listOf(first, trim(rest, maxW, size, font))
    }

    /** 画出记下的中文段。k = 逻辑坐标 → 窗口像素的缩放 */
    fun flush(k: Float) {
        if (runs.isEmpty()) return
        gfx.drawCjkRuns(runs, k)
        runs.clear()
    }
}
