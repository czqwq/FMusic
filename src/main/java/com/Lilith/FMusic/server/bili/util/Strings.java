package com.Lilith.FMusic.server.bili.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Strings {

    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    private Strings() {}

    public static String safeTrim(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (maxLength > 0 && trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength)
                .trim();
        }
        return trimmed;
    }

    public static String normalizeKeyword(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!previousWhitespace) {
                    out.append(' ');
                    previousWhitespace = true;
                }
            } else {
                out.append(c);
                previousWhitespace = false;
            }
        }
        return out.toString()
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    public static String sanitizeRequester(String name, String fallback) {
        String value = safeTrim(name, 32);
        if (value.isEmpty()) {
            value = safeTrim(fallback, 32);
        }
        if (value.isEmpty()) {
            value = "B站观众";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c != '\u007F' && c != '§') {
                out.append(c);
            }
        }
        String result = out.toString()
            .trim();
        return result.isEmpty() ? "B站观众" : result;
    }

    public static String replace(String template, String key, String value) {
        return (template == null ? "" : template).replace("{" + key + "}", value == null ? "" : value);
    }
}
