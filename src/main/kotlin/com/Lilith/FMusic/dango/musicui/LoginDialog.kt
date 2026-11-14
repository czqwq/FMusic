package com.Lilith.FMusic.dango.musicui

import com.Lilith.FMusic.dango.i18n.I18n

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.gfx.Texture
import com.Lilith.FMusic.dango.music.MusicScreen
import com.Lilith.FMusic.dango.music.NeteaseLogin
import com.Lilith.FMusic.dango.music.NativeLogin
import com.Lilith.FMusic.dango.music.MusicSource
import com.Lilith.FMusic.dango.music.MusicSources
import com.Lilith.FMusic.dango.platform.Keys
import com.Lilith.FMusic.dango.ui.LayoutBounds
import com.Lilith.FMusic.dango.ui.ShapeStyle
import com.Lilith.FMusic.dango.ui.Theme
import com.Lilith.FMusic.dango.utils.Color

/** 扫码登录弹窗：盖在整个播放器上面，开着时其它地方不响应 */
internal class LoginDialog(private val screen: MusicScreen) {
    private val ui = screen.ui
    private val hits = mutableListOf<Hit>()

    companion object {
        // 关掉播放器再打开还在（扫码可能还没完成）
        private var showing = false
        private var qrLoc: Texture? = null
        private var qrUploaded = -1
        private var provider = MusicSource.NETEASE
        private var uploadedProvider = MusicSource.NETEASE
        // 酷狗概念版的二维码（和普通版并排）
        private var liteLoc: Texture? = null
        private var liteUploaded = -1
    }

    val visible: Boolean get() = showing
    private val native: Boolean get() = provider != MusicSource.NETEASE
    private val state get() = if (native) NativeLogin.state else NeteaseLogin.state
    private val image get() = if (native) NativeLogin.qrImage else NeteaseLogin.qrImage
    /** 酷狗：普通版和概念版两个码并排 */
    private val dual: Boolean get() = provider == MusicSource.KUGOU

    fun open() {
        showing = true
        provider = MusicSources.selected
        if (native) NativeLogin.start(provider) else NeteaseLogin.start()
    }

    fun close() {
        showing = false
        if (native) NativeLogin.cancel() else NeteaseLogin.cancel()
    }

    /** 新二维码生成后传成贴图（每帧调用，没变化就什么都不做） */
    fun uploadQr() {
        val img = image ?: return
        val version = if (native) NativeLogin.qrVersion else NeteaseLogin.qrVersion
        if (version == qrUploaded && uploadedProvider == provider) return
        val textures = DangoClient.gfx.textures
        qrLoc?.let { textures.delete(it) }
        qrLoc = textures.upload("dango_qr", img)
        qrUploaded = version
        uploadedProvider = provider
    }

    private fun uploadLite() {
        val img = NativeLogin.liteImage ?: return
        if (NativeLogin.liteVersion == liteUploaded) return
        val textures = DangoClient.gfx.textures
        liteLoc?.let { textures.delete(it) }
        liteLoc = textures.upload("dango_qr_lite", img)
        liteUploaded = NativeLogin.liteVersion
    }

    fun draw() {
        val ctx = ui.ctx
        hits.clear()
        val state = this.state
        if (state == NeteaseLogin.State.SUCCESS) {
            showing = false
            if (native) NativeLogin.cancel() else NeteaseLogin.cancel()
            Library.refreshAll()
            return
        }

        ctx.drawShape(LayoutBounds(0f, 0f, ui.lw, ui.lh), ShapeStyle(Color(0f, 0f, 0f, 0.45f)))
        if (dual) {
            drawDual(state)
            return
        }
        val w = 300f
        val h = 372f
        val box = LayoutBounds((ui.lw - w) / 2f, (ui.lh - h) / 2f, w, h)
        ctx.drawGlass(box, Theme.GLASS_WINDOW)

        val title = if (native) I18n.text("Log in") + " · " + I18n.text(provider.label) else I18n.text("Log in to NetEase")
        ui.text(title, box.x + 20f, box.y + 20f, 18f, Theme.TEXT_PRIMARY, Theme.FONT_SEMIBOLD)
        val closeB = LayoutBounds(box.right - 38f, box.y + 14f, 24f, 24f)
        ctx.drawShape(closeB, ShapeStyle(if (ui.hover(closeB)) Theme.CARD_HOVER else Theme.CARD, 12f))
        ui.text("x", closeB.x + 8.5f, closeB.y + 7f, 12f, Theme.TEXT_PRIMARY, Theme.FONT_MEDIUM)
        hits += closeB to { close() }
        ui.tip(closeB, I18n.text("Close"))

        val qs = 200f
        val qrBox = LayoutBounds(box.x + (w - qs) / 2f, box.y + 60f, qs, qs)
        ctx.drawShape(qrBox.expand(10f, 10f), ShapeStyle(Color.WHITE, 14f))
        val loc = qrLoc
        val showQr = image != null && loc != null &&
            (state == NeteaseLogin.State.WAITING || state == NeteaseLogin.State.SCANNED || state == NeteaseLogin.State.EXPIRED)
        if (showQr) {
            ctx.pushOpacity(if (state == NeteaseLogin.State.EXPIRED) 0.2f else 1f)
            ctx.drawImage(qrBox, loc!!)
            ctx.popOpacity()
        } else {
            val t = if (state == NeteaseLogin.State.ERROR) {
                I18n.text(if (image != null) "Login failed" else "获取二维码失败")
            } else I18n.text("正在获取二维码…")
            ui.text(t, qrBox.x + (qs - ui.width(t, 13f)) / 2f, qrBox.y + qs / 2f - 6f, 13f, Color(0.33f, 0.33f, 0.33f, 1f))
        }

        val msg = when (state) {
            NeteaseLogin.State.WAITING -> I18n.text(when (provider) {
                MusicSource.QQ -> "Scan with the QQ Music app"
                MusicSource.KUGOU -> "Scan with the Kugou Music app"
                else -> "打开手机网易云音乐 App 扫码"
            })
            NeteaseLogin.State.SCANNED -> if (native && NativeLogin.message.isNotEmpty()) I18n.text(NativeLogin.message)
                else I18n.text("已扫码，请在手机上确认登录")
            NeteaseLogin.State.EXPIRED -> I18n.text("二维码已过期")
            NeteaseLogin.State.ERROR -> I18n.text("出错了，请重试")
            else -> I18n.text("请稍候…")
        }
        val msgColor = when (state) {
            NeteaseLogin.State.SCANNED -> Theme.GREEN
            NeteaseLogin.State.ERROR, NeteaseLogin.State.EXPIRED -> RED
            else -> Theme.TEXT_PRIMARY
        }
        val detail = if (native && state == NeteaseLogin.State.ERROR && NativeLogin.message.isNotEmpty()) I18n.text(NativeLogin.message) else msg
        val lines = ui.wrap2(detail, w - 24f, 12f)
        lines.forEachIndexed { index, shown ->
            ui.text(shown, box.x + (w - ui.width(shown, 12f)) / 2f, qrBox.bottom + (if (lines.size > 1) 18f else 24f) + index * 14f, 12f, msgColor)
        }

        if (state == NeteaseLogin.State.EXPIRED || state == NeteaseLogin.State.ERROR) {
            val label = I18n.text("Refresh QR")
            val bw = ui.width(label, 12f, Theme.FONT_MEDIUM) + 28f
            val b = LayoutBounds(box.x + (w - bw) / 2f, qrBox.bottom + 48f, bw, 28f)
            ctx.drawShape(b, ShapeStyle(RED, 14f))
            ui.text(label, b.x + 14f, b.y + 9f, 12f, Theme.TEXT_PRIMARY, Theme.FONT_MEDIUM)
            hits += b to { if (native) NativeLogin.start(provider) else NeteaseLogin.start() }
            ui.tip(b, I18n.text("Refresh QR"))
        }
    }

    /** 酷狗：左边普通版、右边概念版，扫哪个就按哪个版本登录 */
    private fun drawDual(state: NeteaseLogin.State) {
        val ctx = ui.ctx
        uploadLite()
        val lite = NativeLogin.liteState
        val w = 520f
        val h = 392f
        val box = LayoutBounds((ui.lw - w) / 2f, (ui.lh - h) / 2f, w, h)
        ctx.drawGlass(box, Theme.GLASS_WINDOW)
        ui.text(I18n.text("Log in") + " · " + I18n.text(provider.label), box.x + 20f, box.y + 20f, 18f, Theme.TEXT_PRIMARY, Theme.FONT_SEMIBOLD)
        val closeB = LayoutBounds(box.right - 38f, box.y + 14f, 24f, 24f)
        ctx.drawShape(closeB, ShapeStyle(if (ui.hover(closeB)) Theme.CARD_HOVER else Theme.CARD, 12f))
        ui.text("x", closeB.x + 8.5f, closeB.y + 7f, 12f, Theme.TEXT_PRIMARY, Theme.FONT_MEDIUM)
        hits += closeB to { close() }
        ui.tip(closeB, I18n.text("Close"))

        val qs = 180f
        val gap = 70f
        val left = box.x + (w - qs * 2f - gap) / 2f
        fun slot(x: Float, loc: Texture?, img: Any?, slotState: NeteaseLogin.State, label: String, note: String) {
            val qrBox = LayoutBounds(x, box.y + 66f, qs, qs)
            ctx.drawShape(qrBox.expand(10f, 10f), ShapeStyle(Color.WHITE, 14f))
            val visible = img != null && loc != null &&
                (slotState == NeteaseLogin.State.WAITING || slotState == NeteaseLogin.State.SCANNED || slotState == NeteaseLogin.State.EXPIRED)
            if (visible) {
                ctx.pushOpacity(if (slotState == NeteaseLogin.State.EXPIRED) 0.2f else 1f)
                ctx.drawImage(qrBox, loc!!)
                ctx.popOpacity()
            } else {
                val t = I18n.text(if (slotState == NeteaseLogin.State.ERROR) "获取二维码失败" else "正在获取二维码…")
                ui.text(t, qrBox.x + (qs - ui.width(t, 13f)) / 2f, qrBox.y + qs / 2f - 6f, 13f, Color(0.33f, 0.33f, 0.33f, 1f))
            }
            val title = I18n.text(label)
            ui.text(title, x + (qs - ui.width(title, 13f, Theme.FONT_MEDIUM)) / 2f, qrBox.bottom + 20f, 13f, Theme.TEXT_PRIMARY, Theme.FONT_MEDIUM)
            val sub = I18n.text(if (slotState == NeteaseLogin.State.SCANNED) "已扫码，请在手机上确认登录" else note)
            if (sub.isNotEmpty()) ui.text(sub, x + (qs - ui.width(sub, 11f)) / 2f, qrBox.bottom + 38f, 11f,
                if (slotState == NeteaseLogin.State.SCANNED) Theme.GREEN else Theme.TEXT_SECONDARY)
        }
        slot(left, qrLoc, image, state, "Scan with the Kugou Music app", "")
        slot(left + qs + gap, liteLoc, NativeLogin.liteImage, lite, "Scan with Kugou Lite", "Free VIP claimed for you every day")

        // 底部：出错的说明；两个码都不能用了才给刷新按钮
        val dead = { s: NeteaseLogin.State -> s == NeteaseLogin.State.EXPIRED || s == NeteaseLogin.State.ERROR }
        val y = box.y + 66f + qs + 66f
        if (state == NeteaseLogin.State.ERROR && NativeLogin.message.isNotEmpty()) {
            val lines = ui.wrap2(I18n.text(NativeLogin.message), w - 40f, 12f)
            lines.forEachIndexed { index, shown -> ui.text(shown, box.x + (w - ui.width(shown, 12f)) / 2f, y + index * 14f, 12f, RED) }
        }
        if (dead(state) && dead(lite)) {
            val label = I18n.text("Refresh QR")
            val bw = ui.width(label, 12f, Theme.FONT_MEDIUM) + 28f
            val b = LayoutBounds(box.x + (w - bw) / 2f, box.bottom - 44f, bw, 28f)
            ctx.drawShape(b, ShapeStyle(RED, 14f))
            ui.text(label, b.x + 14f, b.y + 9f, 12f, Theme.TEXT_PRIMARY, Theme.FONT_MEDIUM)
            hits += b to { NativeLogin.start(provider) }
            ui.tip(b, I18n.text("Refresh QR"))
        }
    }

    fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button == 0) hits.firstOrNull { it.first.contains(mx, my) }?.second?.invoke()
    }

    fun keyTyped(keyCode: Int) {
        if (keyCode == Keys.ESCAPE) close()
    }
}
