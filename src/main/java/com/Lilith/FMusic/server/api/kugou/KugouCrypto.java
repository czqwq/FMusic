package com.Lilith.FMusic.server.api.kugou;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;

public final class KugouCrypto {

    public static final int APP_ID = 1005;
    public static final int CLIENT_VER = 20489;
    public static final int URL_CLIENT_VER = 11430;

    private static final String ANDROID_SIGN_SALT = "OIlwieks28dk2k092lksi2UIkp";
    private static final String WEB_SIGN_SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    private static final String PLAY_KEY_SALT = "57ae12eb6890223e355ccfcb74edf70d";
    private static final char[] RANDOM_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        .toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private KugouCrypto() {}

    public static String androidSignature(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder builder = new StringBuilder(ANDROID_SIGN_SALT);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if ("signature".equals(entry.getKey())) {
                continue;
            }
            builder.append(entry.getKey())
                .append('=')
                .append(entry.getValue() == null ? "" : entry.getValue());
        }
        builder.append(ANDROID_SIGN_SALT);
        return md5(builder.toString());
    }

    public static String webSignature(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder builder = new StringBuilder(WEB_SIGN_SALT);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if ("signature".equals(entry.getKey())) {
                continue;
            }
            builder.append(entry.getKey())
                .append('=')
                .append(entry.getValue() == null ? "" : entry.getValue());
        }
        builder.append(WEB_SIGN_SALT);
        return md5(builder.toString());
    }

    public static String playKey(String hash, String mid, String userId) {
        return md5(lower(hash) + PLAY_KEY_SALT + APP_ID + safe(mid) + safe(userId));
    }

    public static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    public static String randomString(int length) {
        StringBuilder builder = new StringBuilder(Math.max(0, length));
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM_CHARS[RANDOM.nextInt(RANDOM_CHARS.length)]);
        }
        return builder.toString();
    }

    private static String lower(String value) {
        return safe(value).toLowerCase(java.util.Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
