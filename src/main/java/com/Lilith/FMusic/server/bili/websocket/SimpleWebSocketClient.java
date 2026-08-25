package com.Lilith.FMusic.server.bili.websocket;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.Lilith.FMusic.server.bili.http.SimpleHttpClient;
import com.Lilith.FMusic.server.bili.util.Strings;

/** Minimal RFC 6455 WSS client with masking, fragmentation and ping/pong support. */
public final class SimpleWebSocketClient implements AutoCloseable {

    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final URI uri;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int maxFrameBytes;
    private final String cookieHeader;
    private SSLSocket socket;
    private InputStream input;
    private OutputStream output;
    private boolean closeSent;
    private int fragmentedOpcode = -1;
    private ByteArrayOutputStream fragmented;

    public SimpleWebSocketClient(String url, int connectTimeoutMillis, int readTimeoutMillis, int maxFrameBytes,
        String cookieHeader) throws IOException {
        try {
            this.uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid WebSocket URL", e);
        }
        if (!"wss".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Only wss:// is supported");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxFrameBytes = maxFrameBytes;
        this.cookieHeader = cookieHeader == null ? "" : cookieHeader;
    }

    public void connect() throws IOException {
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        if (host == null || host.isEmpty()) {
            throw new IOException("WebSocket host is empty");
        }
        SSLSocket created = (SSLSocket) SSLSocketFactory.getDefault()
            .createSocket();
        try {
            created.connect(new java.net.InetSocketAddress(host, port), connectTimeoutMillis);
            SSLParameters parameters = created.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            try {
                List<SNIServerName> serverNames = Collections.<SNIServerName>singletonList(new SNIHostName(host));
                parameters.setServerNames(serverNames);
            } catch (IllegalArgumentException ignored) {
                // A literal IP address cannot be used as an SNI host name.
            }
            created.setSSLParameters(parameters);
            created.setSoTimeout(Math.max(connectTimeoutMillis, 5000));
            created.startHandshake();
            socket = created;
            input = created.getInputStream();
            output = created.getOutputStream();
            handshake(host, port);
            created.setSoTimeout(readTimeoutMillis);
        } catch (IOException e) {
            clearFailedConnection(created);
            throw e;
        } catch (RuntimeException e) {
            clearFailedConnection(created);
            throw e;
        }
    }

    private void clearFailedConnection(SSLSocket created) {
        try {
            created.close();
        } catch (IOException ignored) {
            // Preserve the original connection failure.
        }
        socket = null;
        input = null;
        output = null;
    }

    private void handshake(String host, int port) throws IOException {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String key = Base64.getEncoder()
            .encodeToString(nonce);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getRawQuery() != null && !uri.getRawQuery()
            .isEmpty()) path += "?" + uri.getRawQuery();
        String hostHeader = port == 443 ? host : host + ':' + port;
        StringBuilder request = new StringBuilder();
        request.append("GET ")
            .append(path)
            .append(" HTTP/1.1\r\n")
            .append("Host: ")
            .append(hostHeader)
            .append("\r\n")
            .append("Upgrade: websocket\r\n")
            .append("Connection: Upgrade\r\n")
            .append("Sec-WebSocket-Key: ")
            .append(key)
            .append("\r\n")
            .append("Sec-WebSocket-Version: 13\r\n")
            .append("Origin: https://live.bilibili.com\r\n")
            .append("User-Agent: ")
            .append(SimpleHttpClient.USER_AGENT)
            .append("\r\n");
        if (!cookieHeader.isEmpty()) {
            request.append("Cookie: ")
                .append(cookieHeader)
                .append("\r\n");
        }
        request.append("\r\n");
        output.write(
            request.toString()
                .getBytes(Strings.UTF_8));
        output.flush();

        String headers = readHeaders(32768);
        String[] lines = headers.split("\\r\\n");
        if (lines.length == 0 || !lines[0].contains(" 101 ")) {
            throw new IOException("WebSocket upgrade failed: " + (lines.length == 0 ? "empty response" : lines[0]));
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                map.put(
                    lines[i].substring(0, colon)
                        .trim()
                        .toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1)
                        .trim());
            }
        }
        String expected = websocketAccept(key);
        String actual = map.get("sec-websocket-accept");
        if (!expected.equals(actual)) {
            throw new IOException("Invalid Sec-WebSocket-Accept header");
        }
    }

    public WebSocketMessage readMessage() throws IOException {
        while (true) {
            Frame frame = readFrame();
            switch (frame.opcode) {
                case 0x0:
                    if (fragmentedOpcode < 0 || fragmented == null) {
                        throw new IOException("Unexpected continuation frame");
                    }
                    appendFragment(frame.payload);
                    if (frame.fin) {
                        byte[] combined = fragmented.toByteArray();
                        boolean text = fragmentedOpcode == 0x1;
                        fragmentedOpcode = -1;
                        fragmented = null;
                        return new WebSocketMessage(text, combined);
                    }
                    break;
                case 0x1:
                case 0x2:
                    if (fragmentedOpcode >= 0) {
                        throw new IOException("New data frame before fragmented message completed");
                    }
                    if (frame.fin) {
                        return new WebSocketMessage(frame.opcode == 0x1, frame.payload);
                    }
                    fragmentedOpcode = frame.opcode;
                    fragmented = new ByteArrayOutputStream(frame.payload.length + 1024);
                    appendFragment(frame.payload);
                    break;
                case 0x8:
                    if (!closeSent) {
                        sendFrame(0x8, frame.payload);
                        closeSent = true;
                    }
                    throw new EOFException("WebSocket peer closed the connection");
                case 0x9:
                    sendFrame(0xA, frame.payload);
                    break;
                case 0xA:
                    break;
                default:
                    throw new IOException("Unsupported WebSocket opcode: " + frame.opcode);
            }
        }
    }

    public void sendBinary(byte[] payload) throws IOException {
        sendFrame(0x2, payload == null ? new byte[0] : payload);
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        if (output == null) {
            throw new IOException("WebSocket is not connected");
        }
        byte[] data = payload == null ? new byte[0] : payload;
        if (data.length > maxFrameBytes) {
            throw new IOException("Outgoing frame exceeds limit");
        }
        ByteArrayOutputStream frame = new ByteArrayOutputStream(data.length + 14);
        frame.write(0x80 | (opcode & 0x0F));
        if (data.length <= 125) {
            frame.write(0x80 | data.length);
        } else if (data.length <= 0xFFFF) {
            frame.write(0x80 | 126);
            frame.write((data.length >>> 8) & 0xFF);
            frame.write(data.length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            long length = data.length;
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.write((int) ((length >>> shift) & 0xFF));
            }
        }
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < data.length; i++) {
            frame.write((data[i] ^ mask[i & 3]) & 0xFF);
        }
        output.write(frame.toByteArray());
        output.flush();
    }

    private Frame readFrame() throws IOException {
        int first = readByte();
        int second = readByte();
        boolean fin = (first & 0x80) != 0;
        if ((first & 0x70) != 0) {
            throw new IOException("WebSocket extensions are not supported");
        }
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte() << 8) | readByte();
        } else if (length == 127) {
            length = 0L;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
            if (length < 0L) throw new IOException("Negative WebSocket payload length");
        }
        boolean control = opcode >= 0x8;
        if (control && (!fin || length > 125L)) {
            throw new IOException("Invalid WebSocket control frame");
        }
        if (length > maxFrameBytes || length > Integer.MAX_VALUE) {
            throw new IOException("WebSocket frame exceeds limit: " + length);
        }
        byte[] mask = masked ? readFully(4) : null;
        byte[] payload = readFully((int) length);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i & 3]);
            }
        }
        return new Frame(fin, opcode, payload);
    }

    private void appendFragment(byte[] payload) throws IOException {
        if (fragmented == null) throw new IOException("Fragment buffer is missing");
        if ((long) fragmented.size() + payload.length > maxFrameBytes) {
            throw new IOException("Fragmented WebSocket message exceeds limit");
        }
        fragmented.write(payload);
    }

    private String readHeaders(int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int matched = 0;
        while (output.size() < maxBytes) {
            int value = input.read();
            if (value < 0) throw new EOFException("EOF during WebSocket handshake");
            output.write(value);
            if ((matched == 0 || matched == 2) && value == '\r') matched++;
            else if ((matched == 1 || matched == 3) && value == '\n') matched++;
            else matched = value == '\r' ? 1 : 0;
            if (matched == 4) return new String(output.toByteArray(), Strings.UTF_8);
        }
        throw new IOException("WebSocket response headers are too large");
    }

    private int readByte() throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException("Unexpected WebSocket EOF");
        return value;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] out = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(out, offset, length - offset);
            if (read < 0) throw new EOFException("Unexpected WebSocket EOF");
            offset += read;
        }
        return out;
    }

    private static String websocketAccept(String key) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + ACCEPT_GUID).getBytes(Strings.UTF_8));
            return Base64.getEncoder()
                .encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 is unavailable", e);
        }
    }

    @Override
    public synchronized void close() {
        if (output != null && !closeSent) {
            try {
                sendFrame(0x8, new byte[0]);
                closeSent = true;
            } catch (IOException ignored) {
                // Socket may already be gone.
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing else to do.
            }
        }
        socket = null;
        input = null;
        output = null;
    }

    private static final class Frame {

        private final boolean fin;
        private final int opcode;
        private final byte[] payload;

        private Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }
}
