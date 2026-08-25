package com.Lilith.FMusic.server.bili.util;

public final class UrlCodec {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UrlCodec() {}

    /** RFC 3986 percent encoding, equivalent to JavaScript encodeURIComponent for UTF-8 text. */
    public static String encode(String value) {
        if (value == null) {
            return "";
        }
        byte[] bytes = value.getBytes(Strings.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length * 3);
        for (byte item : bytes) {
            int b = item & 0xFF;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                || (b >= '0' && b <= '9')
                || b == '-'
                || b == '_'
                || b == '.'
                || b == '~') {
                out.append((char) b);
            } else {
                out.append('%')
                    .append(HEX[(b >>> 4) & 0x0F])
                    .append(HEX[b & 0x0F]);
            }
        }
        return out.toString();
    }
}
