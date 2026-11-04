package com.Lilith.FMusic.dango.ui

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.utils.Color

/**
 * 液态玻璃主题：颜色、字体、玻璃材质预设，以及常用绘制辅助。
 * 所有尺寸都是"逻辑像素"，整个菜单会按屏幕大小统一缩放。
 */
object Theme {
    // ---- 布局 ----
    const val MENU_WIDTH = 880f
    const val MENU_HEIGHT = 560f

    // ---- 字体 ----
    const val FONT_REGULAR = "Inter-Regular-cut.ttf"
    const val FONT_MEDIUM = "Inter-Medium-cut.ttf"
    const val FONT_SEMIBOLD = "Inter-SemiBold-cut.ttf"
    const val FONT_DISPLAY = "InterDisplay-Bold-cut.ttf"

    /** Inter 的大写字高 / 字号，用于文字垂直居中 */
    private const val CAP_HEIGHT = 0.727f

    // ---- 颜色（iOS 深色模式系统色） ----
    val TEXT_PRIMARY = Color(1f, 1f, 1f, 0.96f)
    val TEXT_SECONDARY = Color(0.92f, 0.92f, 0.96f, 0.62f)
    val TEXT_TERTIARY = Color(0.92f, 0.92f, 0.96f, 0.38f)

    val ACCENT = Color.fromHex("#0A84FF")
    val GREEN = Color.fromHex("#30D158")
    val SWITCH_OFF = Color(0.47f, 0.47f, 0.50f, 0.36f)
    val TRACK = Color(0.47f, 0.47f, 0.50f, 0.30f)

    val CARD = Color(1f, 1f, 1f, 0.055f)
    val CARD_HOVER = Color(1f, 1f, 1f, 0.085f)
    val HAIRLINE = Color(1f, 1f, 1f, 0.08f)
    val SEPARATOR = Color(1f, 1f, 1f, 0.07f)

    val ICON_DISABLED = Color(0.38f, 0.38f, 0.42f, 1f)

    // ---- 玻璃材质：越靠前的层越亮 ----
    val GLASS_WINDOW = GlassStyle(
        radius = 30f,
        tint = Color(0.10f, 0.10f, 0.13f, 0.18f),
        maxLuma = 0.30f,
        saturation = 1.45f,
        frost = 0.85f,
        refraction = 22f,
        bevel = 26f,
        dispersion = 0.30f,
        highlight = 1f,
        shadow = ShadowStyle(Color(0f, 0f, 0f, 0.30f), 36f, Vec2(0f, -10f)),
    )

    val GLASS_SIDEBAR = GlassStyle(
        radius = 22f,
        tint = Color(1f, 1f, 1f, 0.05f),
        maxLuma = 0.36f,
        saturation = 1.4f,
        frost = 0.55f,
        refraction = 16f,
        bevel = 18f,
        dispersion = 0.25f,
        highlight = 0.9f,
        shadow = ShadowStyle(Color(0f, 0f, 0f, 0.18f), 18f, Vec2(0f, -4f)),
    )

    val GLASS_SELECTION = GlassStyle(
        radius = 12f,
        tint = Color(1f, 1f, 1f, 0.13f),
        maxLuma = 0.48f,
        saturation = 1.5f,
        frost = 0.2f,
        refraction = 10f,
        bevel = 12f,
        dispersion = 0.35f,
        highlight = 1.1f,
    )

    // ---- 绘制辅助 ----

    fun textWidth(text: String, size: Float, fontPath: String): Float =
        DangoClient.gfx.uiTextWidth(text, size, fontPath)

    /** 以 centerY 为视觉中线绘制文字（按大写字高居中） */
    fun drawText(
        context: RenderContext,
        text: String,
        x: Float,
        centerY: Float,
        size: Float,
        fontPath: String,
        color: Color,
    ) {
        context.drawText(
            text,
            x,
            centerY + size * CAP_HEIGHT / 2f,
            FontStyle(fontPath, color, size)
        )
    }

    /** 细 chevron（">"），angle 为顺时针旋转角度 */
    fun drawChevron(context: RenderContext, cx: Float, cy: Float, size: Float, angle: Float, color: Color) {
        val thickness = size * 0.22f
        val length = size * 0.62f
        context.pushTranslate(cx, cy, 0f)
        context.pushRotate(angle, 0f, 0f, 1f)
        // 尖端在原点、两臂向左展开；右移让图形视觉居中
        context.pushTranslate(length * 0.35f, 0f, 0f)
        for (dir in intArrayOf(-1, 1)) {
            context.pushRotate(45f * dir, 0f, 0f, 1f)
            context.drawShape(
                LayoutBounds(-length + thickness / 2f, -thickness / 2f, length, thickness),
                ShapeStyle(color, thickness / 2f)
            )
            context.popTransform()
        }
        context.popTransform()
        context.popTransform()
        context.popTransform()
    }
}
