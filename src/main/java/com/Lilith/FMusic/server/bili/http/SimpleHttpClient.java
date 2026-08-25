package com.Lilith.FMusic.server.bili.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import com.Lilith.FMusic.server.bili.util.Strings;

public final class SimpleHttpClient {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int maxResponseBytes;

    public SimpleHttpClient(int connectTimeoutMillis, int readTimeoutMillis, int maxResponseBytes) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxResponseBytes = maxResponseBytes;
    }

    public HttpResult get(String url, String cookieHeader) throws IOException {
        final URL target;
        try {
            target = new URI(url).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid HTTPS URL", e);
        }
        if (!"https".equalsIgnoreCase(target.getProtocol())) {
            throw new IOException("Only HTTPS is allowed: " + target.getProtocol());
        }
        HttpsURLConnection connection = (HttpsURLConnection) target.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        connection.setRequestProperty("Referer", "https://www.bilibili.com/");
        connection.setRequestProperty("Origin", "https://www.bilibili.com");
        connection.setRequestProperty("Connection", "close");
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            connection.setRequestProperty("Cookie", cookieHeader);
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= HttpURLConnection.HTTP_BAD_REQUEST ? connection.getErrorStream()
                : connection.getInputStream();
            String body = stream == null ? "" : read(stream, maxResponseBytes);
            Map<String, List<String>> headers = connection.getHeaderFields();
            return new HttpResult(status, body, headers);
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input, int maxBytes) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("HTTP response exceeds " + maxBytes + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), Strings.UTF_8);
        } finally {
            input.close();
        }
    }
}
