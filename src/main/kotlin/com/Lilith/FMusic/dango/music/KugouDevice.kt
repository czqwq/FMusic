package com.Lilith.FMusic.dango.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.Lilith.FMusic.dango.DangoMusic
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 这台电脑在酷狗那边的"设备身份"：第一次用时随机生成，存在配置目录里，之后一直用同一套。
 * 酷狗对每次都换设备的请求更容易风控（新账号尤其明显），所以登录和概念版的请求都带上它。
 * 里面只有随机数，不含任何真实的硬件信息。
 */
internal object KugouDevice {
    class Identity(val guid: String, val dev: String, val mac: String) {
        /** 酷狗用的设备号：guid 的 MD5 当成一个大整数，写成十进制 */
        val mid: String = BigInteger(1, MessageDigest.getInstance("MD5").digest(guid.toByteArray(Charsets.US_ASCII))).toString()
    }

    @Volatile private var cached: Identity? = null

    fun get(): Identity = cached ?: synchronized(this) {
        cached ?: (load() ?: create()).also { cached = it }
    }

    private fun file() = DangoMusic.instance.clientDir.resolve("kugou_device.json")

    private fun load(): Identity? = try {
        val path = file()
        if (!Files.isRegularFile(path)) null else {
            val root = JsonParser().parse(String(Files.readAllBytes(path), Charsets.UTF_8)).asJsonObject
            val identity = Identity(root.text("guid"), root.text("dev"), root.text("mac"))
            identity.takeIf { it.guid.matches(Regex("[0-9A-F]{32}")) && it.dev.isNotEmpty() && it.mac.isNotEmpty() }
        }
    } catch (_: Exception) { null }

    private fun create(): Identity {
        val random = SecureRandom()
        fun hex(bytes: Int) = ByteArray(bytes).also { random.nextBytes(it) }.joinToString("") { "%02X".format(it.toInt() and 255) }
        val identity = Identity(hex(16), hex(5), hex(6).chunked(2).joinToString(":"))
        try {
            val path = file()
            Files.createDirectories(path.parent)
            val root = JsonObject().apply {
                addProperty("guid", identity.guid); addProperty("dev", identity.dev); addProperty("mac", identity.mac)
            }
            val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
            Files.write(temporary, root.toString().toByteArray(Charsets.UTF_8))
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) { /* 存不下来也能用，只是下次启动会换一套 */ }
        return identity
    }
}
