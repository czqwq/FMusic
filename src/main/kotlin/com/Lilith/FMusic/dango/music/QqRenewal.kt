package com.Lilith.FMusic.dango.music

import com.google.gson.JsonObject

/**
 * QQ 音乐登录的续期。
 *
 * 扫码登录拿到的 musickey 只能用三天左右。换票（LoginServer.Login）的回包里另外带着 refresh_key / refresh_token，
 * 拿它们再调一次 Login 就能换到一把新的 musickey，不用重新扫码。这里只存续期需要的几项，不存别的。
 */
internal object QqRenewal {
    /** 剩下不到这么久就续（musickey 一般能用 3 天，留出一天半：游戏隔天打开也赶得上） */
    private const val RENEW_BEFORE_MS = 36L * 60 * 60 * 1000
    /** 回包没说什么时候到期时，按登录后多久算该续了 */
    private const val UNKNOWN_AGE_MS = 24L * 60 * 60 * 1000
    /** 续期失败后隔多久再试 */
    const val RETRY_MS = 10L * 60 * 1000

    private val KEPT = listOf("refresh_key", "refresh_token", "openid", "unionid", "str_musicid")

    /** 从换票回包里挑出续期要用的字段；没有 refresh_key 时返回 null（这种登录续不了） */
    fun fields(data: JsonObject): JsonObject? {
        if (data.text("refresh_key").isEmpty()) return null
        val now = System.currentTimeMillis()
        return JsonObject().apply {
            for (name in KEPT) data.text(name).takeIf { it.isNotEmpty() }?.let { addProperty(name, it) }
            val created = data.number("musickeyCreateTime")
            val lifetime = data.number("keyExpiresIn")
            // 到期时间是回包自己说的；不合理（已经过了，或超过一周）就当不知道
            val expires = if (created > 0 && lifetime > 0) (created + lifetime) * 1000 else 0L
            addProperty("expiresAt", if (expires in now..now + 7L * 24 * 60 * 60 * 1000) expires else 0L)
            addProperty("issuedAt", now)
        }
    }

    fun due(account: NativeAccount): Boolean {
        val renewal = account.renewal ?: return false
        if (account.source != MusicSource.QQ || renewal.text("refresh_key").isEmpty()) return false
        if (java.lang.Boolean.getBoolean("dangomusic.qqRenewNow")) return true
        val now = System.currentTimeMillis()
        val expires = renewal.number("expiresAt")
        return if (expires > 0) now > expires - RENEW_BEFORE_MS else now > renewal.number("issuedAt") + UNKNOWN_AGE_MS
    }

    /** 续期请求的参数（Login 接口，loginMode 2 = 用 refresh_key 续） */
    fun params(account: NativeAccount, key: String): JsonObject {
        val renewal = account.renewal ?: JsonObject()
        return JsonObject().apply {
            addProperty("openid", renewal.text("openid"))
            addProperty("refresh_token", renewal.text("refresh_token"))
            addProperty("str_musicid", renewal.text("str_musicid").ifEmpty { account.userId })
            addProperty("musickey", key)
            addProperty("unionid", renewal.text("unionid"))
            addProperty("refresh_key", renewal.text("refresh_key"))
            addProperty("loginMode", 2)
        }
    }
}
