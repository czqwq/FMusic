package com.Lilith.FMusic.server.bili.bilibili;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.Lilith.FMusic.server.bili.json.Json;
import com.Lilith.FMusic.server.bili.util.Strings;

public final class BiliPacketCodec {

    public static final int HEADER_SIZE = 16;
    public static final int OP_HEARTBEAT = 2;
    public static final int OP_HEARTBEAT_REPLY = 3;
    public static final int OP_MESSAGE = 5;
    public static final int OP_AUTH = 7;
    public static final int OP_AUTH_REPLY = 8;

    private BiliPacketCodec() {}

    public static byte[] auth(long uid, long roomId, String token, String buvid3) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("uid", uid);
        values.put("roomid", roomId);
        values.put("protover", 2);
        values.put("buvid", buvid3 == null ? "" : buvid3);
        values.put("platform", "web");
        values.put("type", 2);
        values.put("key", token == null ? "" : token);
        return encode(
            Json.stringifyObject(values)
                .getBytes(Strings.UTF_8),
            1,
            OP_AUTH,
            1);
    }

    public static byte[] heartbeat() {
        return encode("{}".getBytes(Strings.UTF_8), 1, OP_HEARTBEAT, 1);
    }

    public static byte[] encode(byte[] body, int protocolVersion, int operation, int sequence) {
        byte[] payload = body == null ? new byte[0] : body;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length)
            .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(HEADER_SIZE + payload.length);
        buffer.putShort((short) HEADER_SIZE);
        buffer.putShort((short) protocolVersion);
        buffer.putInt(operation);
        buffer.putInt(sequence);
        buffer.put(payload);
        return buffer.array();
    }

    public static List<BiliPacket> decode(byte[] data, int maxInflatedBytes) throws IOException {
        List<BiliPacket> out = new ArrayList<BiliPacket>();
        decodeInto(data, maxInflatedBytes, 0, out);
        return out;
    }

    private static void decodeInto(byte[] data, int maxInflatedBytes, int depth, List<BiliPacket> out)
        throws IOException {
        if (data == null || data.length == 0) {
            return;
        }
        if (depth > 8) {
            throw new IOException("Bilibili packet nesting is too deep");
        }
        int offset = 0;
        while (offset + HEADER_SIZE <= data.length) {
            ByteBuffer header = ByteBuffer.wrap(data, offset, HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
            int totalLength = header.getInt();
            int headerLength = header.getShort() & 0xFFFF;
            int protocolVersion = header.getShort() & 0xFFFF;
            int operation = header.getInt();
            int sequence = header.getInt();
            if (headerLength < HEADER_SIZE || totalLength < headerLength || totalLength > data.length - offset) {
                throw new IOException("Invalid Bilibili packet length");
            }
            int bodyLength = totalLength - headerLength;
            byte[] body = new byte[bodyLength];
            System.arraycopy(data, offset + headerLength, body, 0, bodyLength);
            offset += totalLength;

            if (protocolVersion == 2) {
                byte[] inflated = inflate(body, maxInflatedBytes);
                decodeInto(inflated, maxInflatedBytes, depth + 1, out);
            } else if (protocolVersion == 3) {
                // We request protover=2. Brotli is deliberately not bundled to avoid a native/library conflict.
                throw new IOException("Received unsupported Brotli packet despite requesting zlib");
            } else {
                out.add(new BiliPacket(protocolVersion, operation, sequence, body));
            }
        }
        if (offset != data.length) {
            throw new IOException("Trailing bytes after Bilibili packet stream");
        }
    }

    private static byte[] inflate(byte[] compressed, int maxBytes) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream output = new ByteArrayOutputStream(
            Math.min(maxBytes, Math.max(1024, compressed.length * 3)));
        byte[] buffer = new byte[8192];
        int total = 0;
        try {
            while (!inflater.finished()) {
                int read;
                try {
                    read = inflater.inflate(buffer);
                } catch (DataFormatException e) {
                    throw new IOException("Invalid zlib packet", e);
                }
                if (read > 0) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("Inflated packet exceeds " + maxBytes + " bytes");
                    }
                    output.write(buffer, 0, read);
                    continue;
                }
                if (inflater.needsDictionary()) {
                    throw new IOException("zlib dictionary is required");
                }
                if (inflater.needsInput()) {
                    throw new IOException("Truncated zlib packet");
                }
                throw new IOException("zlib inflater made no progress");
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
