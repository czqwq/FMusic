package com.Lilith.FMusic.dango.musicui

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.i18n.I18n
import com.Lilith.FMusic.dango.music.CommentPage
import com.Lilith.FMusic.dango.music.CoverCache
import com.Lilith.FMusic.dango.music.MusicScreen
import com.Lilith.FMusic.dango.music.MusicSource
import com.Lilith.FMusic.dango.music.NeteaseApi
import com.Lilith.FMusic.dango.music.NeteaseTrack
import com.Lilith.FMusic.dango.music.SongComment
import com.Lilith.FMusic.dango.music.SourceAccessException
import com.Lilith.FMusic.dango.platform.Keys
import com.Lilith.FMusic.dango.ui.LayoutBounds
import com.Lilith.FMusic.dango.ui.ShapeStyle
import com.Lilith.FMusic.dango.ui.Theme
import com.Lilith.FMusic.dango.utils.Color

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * 网易云歌曲评论页：只读浏览 + 发表 / 回复 / 点赞 / 删除自己的评论。
 *
 * 打开时按当前播放的歌拉第一页（不用登录），滚到底自动加载下一页；
 * 写操作要登录（Cookie 里的 MUSIC_U），没登录时输入框变灰并提示去登录。
 * 评论点赞先在界面上切过去，请求失败再切回来。
 */
internal class CommentsPage(private val screen: MusicScreen) {
    private val ui = screen.ui

    private companion object {
        val worker = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "Dango-Comments").apply { isDaemon = true }
        }
        /** 一页评论在界面上大概多高（用于滚动范围） */
        const val ROW_BASE = 66f
        const val LINE_H = 19f
        /** 点赞 / 回复 / 删除按钮占的右侧宽度 */
        const val SIDE_W = 86f
        /** 评论输入的长度上限（和服务端一致） */
        const val MAX_COMMENT = NeteaseApi.COMMENT_MAX_LENGTH
    }

    private class Feed(val track: NeteaseTrack) {
        @Volatile var page: CommentPage? = null
        @Volatile var loading = false
        @Volatile var failed = false
        @Volatile var sending = false
        @Volatile var status = ""
        @Volatile var statusAt = 0L
        /** 写操作成功：下一帧把输入框和回复目标清空（输入框是渲染线程的状态） */
        @Volatile var sent = false
    }

    /** 本帧画了评论列表的区域（滚轮用）；没画就是空的 */
    var area = LayoutBounds()

    /** 正在输入评论（平台层据此打开系统输入法） */
    var editing = false
        private set

    private var track: NeteaseTrack? = null
    private var trackKey = ""

    @Volatile private var feed: Feed? = null

    private var scroll = 0f
    private var target = 0f
    private var maxScroll = 0f
    private var layoutPage: CommentPage? = null
    private var layoutWidth = 0f
    private var lines: List<List<String>> = emptyList()

    private var inputText = ""
    private var replyTo: SongComment? = null
    private var inputRect = LayoutBounds()
    private var sendRect = LayoutBounds()
    private var backRect = LayoutBounds()
    private var replyCloseRect = LayoutBounds()

    /** 删除要二次确认：第一次点先记下 id，3 秒内再点才真删 */
    private var armedDeleteId = 0L
    private var armedDeleteAt = 0L

    private val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    /** 每首歌的评论总数（播放栏上的评论按钮显示用） */
    private val counts = ConcurrentHashMap<String, Long>()
    private val countRequests = ConcurrentHashMap.newKeySet<String>()

    // ---------------------------------------------------------------- 打开 / 关闭

    fun open(track: NeteaseTrack) {
        if (this.trackKey == track.key && feed != null) {
            // 还是这首歌：保留已加载的评论，只把输入状态收起来
            editing = false
            replyTo = null
            return
        }
        this.track = track
        trackKey = track.key
        feed = Feed(track)
        scroll = 0f
        target = 0f
        maxScroll = 0f
        layoutPage = null
        lines = emptyList()
        editing = false
        replyTo = null
        inputText = ""
        armedDeleteId = 0L
        load(feed!!)
    }

    fun close() {
        editing = false
        replyTo = null
    }

    /** 播放栏上的评论数：还没拿到时返回 null（按钮上显示 …） */
    fun countFor(track: NeteaseTrack): Long? {
        if (track.source != MusicSource.NETEASE) return null
        val current = feed
        if (current != null && current.track.key == track.key) return current.page?.total
        counts[track.key]?.let { return it }
        if (countRequests.add(track.key)) {
            worker.execute {
                try {
                    counts[track.key] = NeteaseApi.songComments(track.id, 0).total
                } catch (_: Exception) {
                } finally {
                    countRequests.remove(track.key)
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- 加载

    private fun load(feed: Feed) {
        if (feed.loading) return
        feed.loading = true
        feed.failed = false
        worker.execute {
            try {
                val page = NeteaseApi.songComments(feed.track.id, 0)
                if (this.feed === feed) {
                    feed.page = page
                    counts[feed.track.key] = page.total
                }
            } catch (e: Exception) {
                if (this.feed === feed) feed.failed = true
                DangoLogger.warn("Comments load failed: {}", e.javaClass.simpleName)
            } finally {
                feed.loading = false
            }
        }
    }

    private fun loadMore() {
        val feed = feed ?: return
        val page = feed.page ?: return
        if (feed.loading || !page.more || page.nextOffset < 0) return
        feed.loading = true
        val offset = page.nextOffset
        worker.execute {
            try {
                val next = NeteaseApi.songComments(feed.track.id, offset)
                if (this.feed === feed) {
                    val have = feed.page?.comments.orEmpty()
                    feed.page = CommentPage(
                        have + next.comments.filter { c -> have.none { it.id == c.id } },
                        max((have.size + next.comments.size).toLong(), feed.page?.total ?: next.total),
                        next.more,
                        next.nextOffset,
                    )
                }
            } catch (e: Exception) {
                if (this.feed === feed) feed.failed = true
            } finally {
                feed.loading = false
            }
        }
    }

    /** 写完评论后重新拉第一页（新评论在最前面） */
    private fun reload(feed: Feed) {
        worker.execute {
            try {
                val page = NeteaseApi.songComments(feed.track.id, 0)
                if (this.feed === feed) {
                    feed.page = page
                    counts[feed.track.key] = page.total
                    feed.failed = false
                }
            } catch (_: Exception) {
            } finally {
                feed.loading = false
            }
        }
    }

    private fun showStatus(feed: Feed, message: String) {
        feed.status = message
        feed.statusAt = System.nanoTime()
    }

    // ---------------------------------------------------------------- 写操作

    private fun send() {
        val feed = feed ?: return
        val track = track ?: return
        if (feed.sending) return
        if (!NeteaseApi.loggedIn) {
            showStatus(feed, "Log in to NetEase first.")
            return
        }
        val text = inputText.trim()
        if (text.isEmpty()) return
        if (text.length > MAX_COMMENT) {
            showStatus(feed, "Comment too long (max 140 characters).")
            return
        }
        feed.sending = true
        val parent = replyTo
        worker.execute {
            try {
                if (parent != null) NeteaseApi.replyComment(track.id, parent.id, text)
                else NeteaseApi.addComment(track.id, text)
                if (this.feed !== feed) return@execute
                feed.sent = true
                showStatus(feed, if (parent != null) "Reply sent" else "Comment sent")
                reload(feed)
            } catch (e: Exception) {
                if (this.feed === feed) showStatus(feed, publicError(e))
            } finally {
                feed.sending = false
            }
        }
    }

    private fun toggleLike(comment: SongComment) {
        val feed = feed ?: return
        val track = track ?: return
        if (!NeteaseApi.loggedIn) {
            showStatus(feed, "Log in to NetEase first.")
            return
        }
        val like = !comment.liked
        updateComment(feed, comment.copy(
            liked = like,
            likedCount = (comment.likedCount + if (like) 1 else -1).coerceAtLeast(0L),
        ))
        worker.execute {
            try {
                NeteaseApi.likeComment(track.id, comment.id, like)
            } catch (e: Exception) {
                updateComment(feed, comment)
                if (this.feed === feed) showStatus(feed, publicError(e))
            }
        }
    }

    private fun deleteComment(comment: SongComment) {
        val feed = feed ?: return
        val track = track ?: return
        if (!NeteaseApi.loggedIn) return
        val now = System.currentTimeMillis()
        if (armedDeleteId != comment.id || now - armedDeleteAt > 3_000L) {
            armedDeleteId = comment.id
            armedDeleteAt = now
            return
        }
        armedDeleteId = 0L
        worker.execute {
            try {
                NeteaseApi.deleteComment(track.id, comment.id)
                if (this.feed === feed) {
                    val page = feed.page ?: return@execute
                    feed.page = CommentPage(
                        page.comments.filter { it.id != comment.id },
                        (page.total - 1).coerceAtLeast(0L),
                        page.more,
                        page.nextOffset,
                    )
                    showStatus(feed, "Comment deleted")
                }
            } catch (e: Exception) {
                if (this.feed === feed) showStatus(feed, publicError(e))
            }
        }
    }

    private fun updateComment(feed: Feed, updated: SongComment) {
        val page = feed.page ?: return
        feed.page = CommentPage(
            page.comments.map { if (it.id == updated.id) updated else it },
            page.total, page.more, page.nextOffset,
        )
    }

    private fun startReply(comment: SongComment) {
        replyTo = comment
        editing = true
        armedDeleteId = 0L
    }

    private fun publicError(e: Exception): String =
        if (e is SourceAccessException) e.message.orEmpty() else "Could not send the comment. Please retry."

    // ---------------------------------------------------------------- 每帧 / 滚轮

    fun tick() {
        val feed = feed ?: return
        if (feed.sent) {
            feed.sent = false
            editing = false
            replyTo = null
            inputText = ""
        }
        if (armedDeleteId != 0L && System.currentTimeMillis() - armedDeleteAt > 3_000L) armedDeleteId = 0L
    }

    fun scroll(wheel: Int) {
        target = (target + if (wheel > 0) -96f else 96f).coerceIn(0f, maxScroll)
        if (wheel < 0 && target >= maxScroll - 120f) loadMore()
    }

    /** 点到输入框 / 发表按钮以外的地方时收起输入法焦点 */
    fun onClickElsewhere(hit: Hit?) {
        if (!editing) return
        val rect = hit?.first
        if (rect == null || (rect !== inputRect && rect !== sendRect)) editing = false
    }

    // ---------------------------------------------------------------- 绘制

    fun draw(page: LayoutBounds) {
        val track = track ?: return
        val feed = feed ?: return
        area = LayoutBounds()

        // 顶部：返回 + 封面 + 歌名 / 歌手 + 评论总数
        backRect = LayoutBounds(page.x, page.y + 4f, 30f, 30f)
        ui.ctx.drawShape(backRect, ShapeStyle(if (ui.hover(backRect)) Theme.CARD_HOVER else Theme.CARD, 15f))
        ui.icon("◀", backRect.x + 14.5f, backRect.y + 15f, 10f, Theme.TEXT_PRIMARY)
        ui.hits += backRect to { screen.goBack() }

        val cover = LayoutBounds(page.x + 44f, page.y + 1f, 40f, 40f)
        ui.drawCover(cover, CoverCache.trackRounded(track, 0.5f), 10f)
        ui.text(ui.trim(track.name, page.width - 150f, 15f, Theme.FONT_SEMIBOLD),
            cover.right + 12f, page.y + 5f, 15f, Theme.TEXT_PRIMARY, Theme.FONT_SEMIBOLD)
        val artists = track.artists.joinToString(" / ")
        ui.text(ui.trim(artists, page.width - 150f, 11f), cover.right + 12f, page.y + 26f, 11f, Theme.TEXT_SECONDARY)
        val total = feed.page?.total
        val label = I18n.text("All comments") + if (total != null) "  $total" else ""
        ui.text(label, page.x, page.y + 50f, 11f, Theme.TEXT_TERTIARY)

        // 输入区固定在列表下面、播放栏上面
        val bottom = page.bottom - ui.bottomInset - 4f
        val inputH = 44f
        val replyH = if (replyTo != null) 24f else 0f
        inputRect = LayoutBounds(page.x, bottom - inputH, page.width - 92f, inputH)
        sendRect = LayoutBounds(inputRect.right + 10f, bottom - inputH, 82f, inputH)
        if (replyTo != null) {
            val r = replyTo!!
            val y = inputRect.y - 24f
            val banner = LayoutBounds(page.x, y, page.width, 22f)
            ui.card(banner, Theme.CARD, 11f)
            val text = I18n.format("Reply to %s", r.nickname)
            ui.text(ui.trim(text, page.width - 40f, 11f), banner.x + 10f, y + 6f, 11f, Theme.TEXT_SECONDARY)
            replyCloseRect = LayoutBounds(banner.right - 20f, y + 2f, 18f, 18f)
            ui.icon("×", replyCloseRect.x + 9f, replyCloseRect.y + 9f, 12f, Theme.TEXT_TERTIARY)
            ui.hits += replyCloseRect to {
                replyTo = null
                editing = true
            }
        } else {
            replyCloseRect = LayoutBounds()
        }

        drawInput(feed, bottom - inputH)

        // 评论列表
        val listTop = page.y + 66f
        val listBottom = bottom - inputH - 8f - replyH
        val list = LayoutBounds(page.x, listTop, page.width, (listBottom - listTop).coerceAtLeast(0f))
        area = list
        drawList(feed, list)
    }

    private fun drawInput(feed: Feed, top: Float) {
        val loggedIn = NeteaseApi.loggedIn
        val focused = editing && loggedIn
        ui.card(inputRect, if (focused) Theme.CARD_HOVER else Theme.CARD, 12f)
        if (focused) ui.ctx.drawShape(inputRect, ShapeStyle(Color(0f, 0f, 0f, 0f), 12f, 1f, RED.copy(a = 0.8f)))
        val placeholder = if (loggedIn) "Write a comment…" else "Log in to comment"
        val size = 12f
        val maxW = inputRect.width - 24f
        val text = if (inputText.isEmpty()) I18n.text(placeholder) else inputText
        val color = if (inputText.isEmpty()) Theme.TEXT_TERTIARY else Theme.TEXT_PRIMARY
        if (inputText.isEmpty()) {
            ui.text(ui.trim(text, maxW, size), inputRect.x + 12f, top + 15f, size, color)
        } else {
            // 长评论显示尾巴：从末尾往前留能放下的部分，光标在最后
            var shown = inputText
            while (shown.length > 1 && ui.width(shown, size) > maxW - 8f) shown = shown.substring(1)
            val caret = if (focused && caretVisible()) "|" else ""
            ui.text(shown + caret, inputRect.x + 12f, top + 15f, size, color)
        }
        ui.hits += inputRect to {
            if (loggedIn) {
                editing = true
            } else {
                showStatus(feed, "Log in to NetEase first.")
            }
        }
        // 字数（接近上限时变红）
        val count = I18n.format("%d/%d", inputText.length, MAX_COMMENT)
        ui.text(count, inputRect.right - ui.width(count, 9f) - 10f, top + 31f, 9f,
            if (inputText.length >= MAX_COMMENT) RED else Theme.TEXT_TERTIARY)

        val enabled = loggedIn && !feed.sending && inputText.isNotBlank()
        val fill = if (enabled) RED else Theme.TRACK
        ui.card(sendRect, fill, 12f)
        val label = I18n.text(if (feed.sending) "Sending…" else "Send")
        ui.text(label, sendRect.x + (sendRect.width - ui.width(label, 12f, Theme.FONT_SEMIBOLD)) / 2f,
            sendRect.y + sendRect.height / 2f - 12f * 0.39f - 0.5f, 12f,
            if (enabled) Theme.TEXT_PRIMARY else Theme.TEXT_TERTIARY, Theme.FONT_SEMIBOLD)
        ui.hits += sendRect to { if (enabled) send() }
    }

    private fun drawList(feed: Feed, list: LayoutBounds) {
        val page = feed.page
        val comments = page?.comments.orEmpty()
        val textWidth = (list.width - 52f - SIDE_W).coerceAtLeast(20f)
        if (layoutPage !== page || layoutWidth != textWidth) {
            layoutPage = page
            layoutWidth = textWidth
            lines = comments.map { wrap(it, textWidth) }
        }
        val height = lines.sumOf { (ROW_BASE + it.size * LINE_H).toDouble() }.toFloat()
        maxScroll = (height + 40f - list.height).coerceAtLeast(0f)
        target = target.coerceIn(0f, maxScroll)
        scroll = ui.approach(scroll, target, 85f)
        var y = list.y - scroll
        ui.beginClip(list)
        val clip = list
        for ((index, comment) in comments.withIndex()) {
            val body = lines.getOrElse(index) { listOf(comment.content) }
            val rowHeight = ROW_BASE + body.size * LINE_H
            if (y + rowHeight >= list.y && y < list.bottom) {
                drawComment(feed, comment, body, list, y, rowHeight, clip)
            }
            y += rowHeight
        }
        // 状态 / 加载 / 空列表
        val status = when {
            feed.page == null && feed.loading -> I18n.text("Loading comments…")
            feed.failed -> I18n.text("Could not load comments · Retry")
            feed.page != null && comments.isEmpty() -> I18n.text("No comments yet")
            feed.loading && page != null -> I18n.text("Loading comments…")
            else -> null
        }
        if (status != null && y + 8f < list.bottom) {
            val b = LayoutBounds(list.x, y + 4f, list.width, 32f)
            ui.text(status, list.x + 8f, y + 12f, 12f, Theme.TEXT_SECONDARY)
            if (feed.failed) ui.hits += b.intersect(clip) to { load(feed) }
        }
        if (feed.status.isNotEmpty() && System.nanoTime() - feed.statusAt < 4_000_000_000L) {
            val text = I18n.message(feed.status)
            val h = 30f
            val b = LayoutBounds(list.x, list.bottom - h - 4f, list.width, h)
            ui.ctx.drawGlass(b, STATUS_GLASS)
            ui.text(ui.trim(text, b.width - 20f, 11f), b.x + 10f, b.y + h / 2f - 11f * 0.39f - 0.5f, 11f, Theme.TEXT_PRIMARY)
        }
        ui.endClip()
        if (target >= maxScroll - 80f) loadMore()
    }

    private fun drawComment(
        feed: Feed,
        comment: SongComment,
        body: List<String>,
        list: LayoutBounds,
        y: Float,
        rowHeight: Float,
        clip: LayoutBounds,
    ) {
        val avatar = CoverCache.rounded("comment:${comment.avatarUrl}", comment.avatarUrl, 0.5f)
        ui.drawCover(LayoutBounds(list.x, y + 6f, 34f, 34f), avatar, 17f)
        val textX = list.x + 44f
        val textW = list.width - 52f - SIDE_W
        val own = NeteaseApi.userId != 0L && comment.userId == NeteaseApi.userId
        ui.text(ui.trim(comment.nickname.ifEmpty { "?" }, textW, 12f, Theme.FONT_MEDIUM), textX, y + 5f, 12f, Theme.TEXT_SECONDARY, Theme.FONT_MEDIUM)
        body.forEachIndexed { n, line -> ui.text(line, textX, y + 24f + n * LINE_H, 12f, Theme.TEXT_PRIMARY) }
        if (comment.time > 0) {
            ui.text(date.format(Date(comment.time)), textX, y + rowHeight - 18f, 9.5f, Theme.TEXT_TERTIARY)
        }

        // 右侧：点赞 / 回复 / 删除
        val sideX = list.right - SIDE_W
        val likeB = LayoutBounds(sideX, y + 6f, 34f, 22f)
        if (ui.hover(likeB)) ui.card(likeB, Theme.CARD_HOVER, 11f)
        ui.icon("♥", likeB.x + 11f, likeB.y + 11f, 11f, if (comment.liked) RED else Theme.TEXT_TERTIARY)
        val countText = if (comment.likedCount > 9999) "9999+" else comment.likedCount.toString()
        ui.text(countText, likeB.x + 20f, likeB.y + 5.5f, 9.5f, Theme.TEXT_TERTIARY)
        ui.hits += likeB.intersect(clip) to { toggleLike(comment) }

        val replyB = LayoutBounds(sideX, y + 30f, 40f, 18f)
        ui.text(I18n.text("Reply"), replyB.x, replyB.y + 3f, 10f, if (ui.hover(replyB)) RED else Theme.TEXT_TERTIARY)
        ui.hits += replyB.intersect(clip) to { startReply(comment) }

        if (own) {
            val armed = armedDeleteId == comment.id
            val deleteB = LayoutBounds(sideX + 42f, y + 30f, 44f, 18f)
            val label = I18n.text(if (armed) "Click again" else "Delete")
            ui.text(label, deleteB.x, deleteB.y + 3f, 10f, if (armed) RED else Theme.TEXT_TERTIARY)
            ui.hits += deleteB.intersect(clip) to { deleteComment(comment) }
        }

        ui.card(LayoutBounds(list.x, y + rowHeight - 1f, list.width, 1f), Theme.SEPARATOR, 0f)
    }

    /** 把评论正文按宽度折行；回复别人的评论在最前面加一行「回复 @某人」 */
    private fun wrap(comment: SongComment, width: Float): List<String> {
        val out = ArrayList<List<String>>()
        if (comment.replyToNickname.isNotEmpty()) {
            val title = I18n.format("Reply to %s", comment.replyToNickname)
            out += wrapText(title, width)
        }
        out += wrapText(comment.content, width)
        return out.flatten()
    }

    private fun wrapText(text: String, width: Float): List<String> = text.replace("\r", "").split('\n').flatMap { paragraph ->
        if (paragraph.isEmpty()) return@flatMap listOf("")
        buildList {
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                var end = 1
                while (end < remaining.length && ui.width(remaining.substring(0, end + 1), 12f) <= width) end++
                if (end < remaining.length) {
                    val space = remaining.lastIndexOf(' ', end - 1)
                    if (space > end / 2) end = space + 1
                    if (Character.isHighSurrogate(remaining[end - 1])) end = if (end > 1) end - 1 else 2
                }
                add(remaining.substring(0, end).trimEnd())
                remaining = remaining.substring(end)
            }
        }
    }

    // ---------------------------------------------------------------- 键盘

    /** 输入框有焦点时的按键；返回 true 表示按键被吃掉了 */
    fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!editing) return false
        when {
            keyCode == Keys.ESCAPE -> {
                if (replyTo != null) replyTo = null else editing = false
            }
            keyCode == Keys.RETURN || keyCode == Keys.NUMPADENTER -> send()
            keyCode == Keys.BACK -> if (inputText.isNotEmpty()) inputText = inputText.dropLast(1)
            DangoClient.platform.ctrlDown && keyCode == Keys.V -> {
                val clip = DangoClient.platform.clipboard.replace(Regex("[\\r\\n]"), " ")
                inputText = (inputText + clip).take(MAX_COMMENT)
            }
            DangoClient.platform.ctrlDown && keyCode == Keys.A -> inputText = ""
            Keys.isAllowedChar(typedChar) -> if (inputText.length < MAX_COMMENT) inputText += typedChar
        }
        return true
    }
}

private val STATUS_GLASS = Theme.GLASS_SIDEBAR.copy(radius = 10f)
