package com.Lilith.FMusic.server.bili.bilibili;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.Lilith.FMusic.server.bili.json.Json;
import com.Lilith.FMusic.server.bili.json.JsonValue;
import com.Lilith.FMusic.server.bili.util.Strings;

/** Reads browser-exported cookies without ever logging their values. */
public final class CookieStore {

    private final File cookieFile;
    private final File deviceFile;
    private final Map<String, String> cookies = new LinkedHashMap<String, String>();

    public CookieStore(File dataFolder, String relativeCookiePath) {
        File base = dataFolder == null ? new File("plugins/BiliMusicBridge") : dataFolder;
        this.cookieFile = resolveInside(
            base,
            relativeCookiePath == null ? "cookie.json" : relativeCookiePath,
            "cookie.json");
        this.deviceFile = resolveInside(base, "device.properties", "device.properties");
    }

    public synchronized void ensureTemplate() throws IOException {
        File parent = cookieFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create cookie directory: " + parent);
        }
        if (!cookieFile.exists()) {
            FileOutputStream output = new FileOutputStream(cookieFile);
            try {
                output.write("[]\n".getBytes(Strings.UTF_8));
            } finally {
                output.close();
            }
        }
    }

    public synchronized int reload() throws IOException {
        ensureTemplate();
        cookies.clear();
        String text = readText(cookieFile, 4 * 1024 * 1024).trim();
        if (!text.isEmpty()) {
            if (text.startsWith("[") || text.startsWith("{")) {
                parseJson(text);
            } else {
                parseCookieHeader(text);
            }
        }
        loadDeviceCookies();
        return cookies.size();
    }

    private void parseJson(String text) {
        JsonValue root = Json.parse(text);
        if (root.type() == JsonValue.Type.ARRAY) {
            for (JsonValue item : root.asArray()) {
                if (item.type() != JsonValue.Type.OBJECT) {
                    continue;
                }
                String name = item.get("name")
                    .asString("")
                    .trim();
                String value = item.get("value")
                    .asString("");
                put(name, value);
            }
            return;
        }
        if (root.type() == JsonValue.Type.OBJECT) {
            for (Map.Entry<String, JsonValue> entry : root.asObject()
                .entrySet()) {
                JsonValue value = entry.getValue();
                if (value.type() == JsonValue.Type.STRING || value.type() == JsonValue.Type.NUMBER
                    || value.type() == JsonValue.Type.BOOLEAN) {
                    put(entry.getKey(), value.asString(""));
                }
            }
        }
    }

    private void parseCookieHeader(String header) {
        String normalized = header.replace('\r', ';')
            .replace('\n', ';');
        String[] parts = normalized.split(";");
        for (String part : parts) {
            int split = part.indexOf('=');
            if (split <= 0) {
                continue;
            }
            put(
                part.substring(0, split)
                    .trim(),
                part.substring(split + 1)
                    .trim());
        }
    }

    private void put(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        String cleanName = name.trim();
        if (cleanName.isEmpty() || cleanName.indexOf(';') >= 0 || cleanName.indexOf('=') >= 0) {
            return;
        }
        String cleanValue = value.trim();
        if (containsHeaderControl(cleanName) || containsHeaderControl(cleanValue)) {
            return;
        }
        cookies.put(cleanName, cleanValue);
    }

    private void loadDeviceCookies() throws IOException {
        if (!deviceFile.isFile()) {
            return;
        }
        Properties properties = new Properties();
        FileInputStream input = new FileInputStream(deviceFile);
        try {
            properties.load(input);
        } finally {
            input.close();
        }
        if (!cookies.containsKey("buvid3")) put("buvid3", properties.getProperty("buvid3", ""));
        if (!cookies.containsKey("buvid4")) put("buvid4", properties.getProperty("buvid4", ""));
    }

    public synchronized void saveDeviceIds(String buvid3, String buvid4) throws IOException {
        Properties properties = new Properties();
        if (buvid3 != null && !buvid3.trim()
            .isEmpty()) {
            properties.setProperty("buvid3", buvid3.trim());
            cookies.put("buvid3", buvid3.trim());
        }
        if (buvid4 != null && !buvid4.trim()
            .isEmpty()) {
            properties.setProperty("buvid4", buvid4.trim());
            cookies.put("buvid4", buvid4.trim());
        }
        File parent = deviceFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream output = new FileOutputStream(deviceFile);
        try {
            properties.store(output, "Generated Bilibili device identifiers. Do not publish this file.");
        } finally {
            output.close();
        }
    }

    public synchronized String header() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getValue() == null || entry.getValue()
                .isEmpty()) {
                continue;
            }
            if (out.length() > 0) out.append("; ");
            out.append(entry.getKey())
                .append('=')
                .append(entry.getValue());
        }
        return out.toString();
    }

    public synchronized String get(String key) {
        String value = cookies.get(key);
        return value == null ? "" : value;
    }

    public synchronized long uid() {
        String explicit = get("DedeUserID");
        if (!explicit.isEmpty()) {
            try {
                return Long.parseLong(explicit);
            } catch (NumberFormatException ignored) {
                // Try SESSDATA below.
            }
        }
        String sessdata = get("SESSDATA");
        if (sessdata.isEmpty()) {
            return 0L;
        }
        try {
            String decoded = URLDecoder.decode(sessdata, "UTF-8");
            int comma = decoded.indexOf(',');
            String first = comma >= 0 ? decoded.substring(0, comma) : decoded;
            return Long.parseLong(first);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(cookies));
    }

    public File file() {
        return cookieFile;
    }

    private static File resolveInside(File base, String relative, String fallbackName) {
        File fallback = new File(base, fallbackName).getAbsoluteFile();
        try {
            File canonicalBase = base.getCanonicalFile();
            File candidate = new File(canonicalBase, relative).getCanonicalFile();
            String basePath = canonicalBase.getPath();
            String candidatePath = candidate.getPath();
            if (candidatePath.equals(basePath) || !candidatePath.startsWith(basePath + File.separator)) {
                return fallback;
            }
            return candidate;
        } catch (IOException e) {
            return fallback;
        }
    }

    private static boolean containsHeaderControl(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static String readText(File file, int maxBytes) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Cookie file is too large");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), Strings.UTF_8);
        } finally {
            input.close();
        }
    }
}
