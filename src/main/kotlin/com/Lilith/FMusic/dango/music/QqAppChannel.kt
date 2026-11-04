package com.Lilith.FMusic.dango.music

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A bounded MQTT 5 / WebSocket connection for one QQ Music App QR session, Java 8 compatible. */
internal class QqAppChannel : Closeable {
    class Event(val type: String, val payload: ByteArray)
    private val random = SecureRandom()
    private val socketLock = Any()
    @Volatile private var socket: Socket? = null
    @Volatile private var closed = false
    private var input: InputStream? = null
    private var pending = ByteArray(0)
    private var fragment: ByteArrayOutputStream? = null
    private var topic = ""
    private var lastPing = 0L
    private var pingInterval = 20000L

    fun connect(qrId: String) {
        require(qrId.matches(Regex("[A-Za-z0-9_-]{1,256}")))
        topic = "management.qrcode_login/$qrId"
        var path = "/ws/handshake"
        val deadline = System.currentTimeMillis() + 30000
        for (attempt in 0..3) {
            openWebSocket(path)
            val properties = ByteArrayOutputStream().apply {
                write(0x15); utf8("pass")
                user("tmeAppID", "qqmusic"); user("business", "management"); user("hashTag", qrId)
                user("clientTag", "management.user"); user("userID", qrId)
            }.toByteArray()
            sendPacket(0x10, ByteArrayOutputStream().apply {
                utf8("MQTT"); write(5); write(2); u16(45)
                variable(properties.size); write(properties)
                utf8("${System.currentTimeMillis()}${1000 + random.nextInt(9000)}")
            }.toByteArray())
            val ack = nextPacket(deadline) ?: throw IOException("QR channel connect timeout")
            check(ack.header ushr 4 == 2) { "Expected MQTT connection acknowledgement" }
            val cursor = Cursor(ack.body)
            cursor.byte()
            val reason = cursor.byte()
            val server = cursor.properties()
            if (reason == 0x9c || reason == 0x9d) {
                val reference = server["server"] ?: throw IOException("Missing QR channel route")
                require(reference.matches(Regex("[A-Za-z0-9_.:-]{1,128}")))
                if (attempt == 3) throw IOException("Too many QR channel routes")
                // A broker reference is a route suffix on the SAME verified TLS host, never a new host.
                path = "/ws/handshake/$reference"
                socket?.close(); pending = ByteArray(0); fragment = null
                continue
            }
            check(reason == 0) { "QR channel connection rejected" }
            server["keepalive"]?.toLongOrNull()?.let { pingInterval = (it * 500).coerceIn(1000, 20000) }
            val props = ByteArrayOutputStream().apply { user("authorization", "tmelogin"); user("pubsub", "unicast") }.toByteArray()
            sendPacket(0x82, ByteArrayOutputStream().apply {
                u16(1); variable(props.size); write(props); utf8(topic); write(0)
            }.toByteArray())
            val subscribed = nextPacket(deadline) ?: throw IOException("QR channel subscription timeout")
            check(subscribed.header ushr 4 == 9) { "Expected MQTT subscription acknowledgement" }
            val sub = Cursor(subscribed.body)
            check(sub.u16() == 1)
            sub.properties()
            check(sub.byte() < 128) { "QR channel subscription rejected" }
            lastPing = System.currentTimeMillis()
            return
        }
    }

    fun next(deadline: Long): Event? {
        while (!closed && System.currentTimeMillis() < deadline) {
            if (System.currentTimeMillis() - lastPing >= pingInterval) {
                sendPacket(0xc0, ByteArray(0)); lastPing = System.currentTimeMillis()
            }
            val packet = nextPacket(minOf(deadline, System.currentTimeMillis() + 1000)) ?: continue
            when (packet.header ushr 4) {
                3 -> {
                    val cursor = Cursor(packet.body)
                    val incomingTopic = cursor.string()
                    val qos = (packet.header ushr 1) and 3
                    require(qos <= 1)
                    val id = if (qos == 1) cursor.u16() else 0
                    val properties = cursor.properties()
                    if (qos == 1) sendPacket(0x40, byteArrayOf((id ushr 8).toByte(), id.toByte()))
                    if (incomingTopic == topic) return Event(properties["type"].orEmpty(), cursor.rest())
                }
                13 -> {} // PINGRESP
                14 -> throw IOException("QR channel disconnected")
                else -> throw IOException("Unexpected QR channel packet")
            }
        }
        return null
    }

    private fun openWebSocket(path: String) {
        check(!closed)
        val raw = Socket()
        install(raw)
        raw.connect(InetSocketAddress("mu.y.qq.com", 443), 8000)
        check(!closed)
        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, "mu.y.qq.com", 443, true) as SSLSocket
        install(tls)
        tls.soTimeout = 8000
        tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        tls.startHandshake()
        val key = Base64.getEncoder().encodeToString(ByteArray(16).apply { random.nextBytes(this) })
        val request = "GET $path HTTP/1.1\r\nHost: mu.y.qq.com\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: mqtt\r\n" +
            "Origin: https://y.qq.com\r\nReferer: https://y.qq.com/\r\nUser-Agent: Mozilla/5.0\r\n\r\n"
        tls.outputStream.write(request.toByteArray(Charsets.US_ASCII)); tls.outputStream.flush()
        val stream = tls.inputStream.buffered()
        val header = ByteArrayOutputStream()
        var complete = false
        while (header.size() < 16384) {
            val byte = stream.read()
            if (byte < 0) throw EOFException()
            header.write(byte)
            val bytes = header.toByteArray()
            if (bytes.size >= 4 && bytes.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) { complete = true; break }
        }
        check(complete) { "Incomplete WebSocket handshake" }
        val lines = String(header.toByteArray(), Charsets.US_ASCII).split("\r\n")
        check(lines.firstOrNull()?.split(' ')?.getOrNull(1) == "101") { "WebSocket upgrade rejected" }
        val headers = lines.drop(1).filter { it.contains(':') }.associate {
            it.substringBefore(':').lowercase(java.util.Locale.ROOT) to it.substringAfter(':').trim()
        }
        val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
            .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
        check(headers["sec-websocket-accept"] == expected && headers["sec-websocket-protocol"] == "mqtt") { "Invalid WebSocket handshake" }
        check(headers["upgrade"]?.equals("websocket", true) == true)
        input = stream
        tls.soTimeout = 1000
    }

    private class Packet(val header: Int, val body: ByteArray)

    private fun nextPacket(deadline: Long): Packet? {
        while (!closed && System.currentTimeMillis() < deadline) {
            if (pending.size >= 2) {
                var length = 0
                var multiplier = 1
                var end = 1
                while (end < pending.size && end <= 4) {
                    val value = pending[end++].toInt() and 255
                    length += (value and 127) * multiplier
                    require(length <= 1024 * 1024)
                    if (value and 128 == 0) {
                        if (pending.size >= end + length) {
                            val packet = Packet(pending[0].toInt() and 255, pending.copyOfRange(end, end + length))
                            pending = pending.copyOfRange(end + length, pending.size)
                            return packet
                        }
                        break
                    }
                    multiplier *= 128
                    require(end <= 4) { "Invalid MQTT length" }
                }
            }
            val data = readMessage() ?: continue
            require(pending.size + data.size <= 1024 * 1024)
            pending += data
        }
        return null
    }

    private fun readMessage(): ByteArray? {
        val stream = input ?: throw IOException("QR channel not connected")
        val first = try { stream.read() } catch (_: SocketTimeoutException) { return null }
        if (first < 0) throw EOFException()
        val current = socket ?: throw IOException("QR channel closed")
        current.soTimeout = 8000
        try {
            val second = stream.byte()
            require(first and 0x70 == 0 && second and 0x80 == 0) { "Invalid server WebSocket frame" }
            var length = (second and 127).toLong()
            if (length == 126L) length = (stream.byte() * 256 + stream.byte()).toLong()
            else if (length == 127L) {
                length = 0
                repeat(8) { length = (length shl 8) or stream.byte().toLong() }
            }
            require(length in 0..1024 * 1024)
            val opcode = first and 15
            val fin = first and 128 != 0
            if (opcode >= 8) require(fin && length <= 125)
            val payload = ByteArray(length.toInt())
            var offset = 0
            while (offset < payload.size) {
                val count = stream.read(payload, offset, payload.size - offset)
                if (count < 0) throw EOFException()
                offset += count
            }
            when (opcode) {
                8 -> throw IOException("QR WebSocket closed")
                9 -> { frame(10, payload); return null }
                10 -> return null
                2 -> {
                    check(fragment == null)
                    if (fin) return payload
                    fragment = ByteArrayOutputStream().apply { write(payload) }
                }
                0 -> {
                    val partial = fragment ?: throw IOException("Unexpected continuation")
                    require(partial.size() + payload.size <= 1024 * 1024)
                    partial.write(payload)
                    if (fin) { fragment = null; return partial.toByteArray() }
                }
                else -> throw IOException("Unexpected WebSocket data")
            }
            return null
        } finally { if (!current.isClosed) current.soTimeout = 1000 }
    }

    private fun sendPacket(header: Int, body: ByteArray) = frame(2, ByteArrayOutputStream().apply {
        write(header); variable(body.size); write(body)
    }.toByteArray())

    @Synchronized private fun frame(opcode: Int, payload: ByteArray) {
        check(!closed)
        require(payload.size <= 1024 * 1024)
        val mask = ByteArray(4).apply { random.nextBytes(this) }
        val bytes = ByteArrayOutputStream().apply {
            write(0x80 or opcode)
            if (payload.size < 126) write(0x80 or payload.size)
            else { require(payload.size <= 65535); write(0x80 or 126); u16(payload.size) }
            write(mask)
            payload.forEachIndexed { index, byte -> write((byte.toInt() xor mask[index % 4].toInt()) and 255) }
        }.toByteArray()
        val output = socket?.getOutputStream() ?: throw IOException("QR channel closed")
        output.write(bytes); output.flush()
    }

    private fun install(next: Socket) = synchronized(socketLock) {
        if (closed) { next.close(); throw IOException("QR channel cancelled") }
        socket = next
    }

    override fun close() {
        synchronized(socketLock) { closed = true; try { socket?.close() } catch (_: IOException) {} }
    }

    private class Cursor(private val bytes: ByteArray) {
        private var position = 0
        fun byte(): Int { require(position < bytes.size); return bytes[position++].toInt() and 255 }
        fun u16() = byte() * 256 + byte()
        fun variable(): Int {
            var result = 0; var multiplier = 1
            repeat(4) {
                val digit = byte(); result += (digit and 127) * multiplier
                if (digit and 128 == 0) return result
                multiplier *= 128
            }
            throw IOException("Invalid MQTT length")
        }
        fun string(): String {
            val size = u16(); require(position + size <= bytes.size)
            val value = String(bytes, position, size, Charsets.UTF_8); position += size; return value
        }
        fun rest() = bytes.copyOfRange(position, bytes.size)
        fun properties(): Map<String, String> {
            val size = variable(); val end = position + size
            require(end <= bytes.size)
            val values = mutableMapOf<String, String>()
            while (position < end) {
                when (byte()) {
                    0x26 -> { val name = string(); values[name] = string() }
                    0x1c -> values["server"] = string()
                    0x13 -> values["keepalive"] = u16().toString()
                    0x01, 0x17, 0x19, 0x24, 0x25, 0x28, 0x29, 0x2a -> byte()
                    0x21, 0x22, 0x23 -> u16()
                    0x02, 0x11, 0x18, 0x27 -> repeat(4) { byte() }
                    0x0b -> variable()
                    0x03, 0x08, 0x12, 0x15, 0x1a, 0x1f -> string()
                    0x09, 0x16 -> { val length = u16(); repeat(length) { byte() } }
                    else -> throw IOException("Unknown MQTT property")
                }
            }
            require(position == end)
            return values
        }
    }
}

private fun InputStream.byte(): Int = read().also { if (it < 0) throw EOFException() }
private fun ByteArrayOutputStream.u16(value: Int) { write(value ushr 8); write(value and 255) }
private fun ByteArrayOutputStream.utf8(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); require(bytes.size <= 65535); u16(bytes.size); write(bytes) }
private fun ByteArrayOutputStream.user(name: String, value: String) { write(0x26); utf8(name); utf8(value) }
private fun ByteArrayOutputStream.variable(value: Int) {
    require(value in 0..268435455)
    var remaining = value
    do { var digit = remaining % 128; remaining /= 128; if (remaining > 0) digit = digit or 128; write(digit) } while (remaining > 0)
}
