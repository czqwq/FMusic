package com.Lilith.FMusic.server.bili.request;

import com.Lilith.FMusic.server.bili.config.PluginSettings;
import com.Lilith.FMusic.server.bili.util.Strings;

public final class SongRequestParser {

    public SongRequest parse(DanmakuMessage message, PluginSettings.SongRequest settings) {
        if (message == null || settings == null) {
            return null;
        }
        String text = message.text.trim();
        if (text.isEmpty()) {
            return null;
        }
        String keyword = null;
        for (String prefix : settings.prefixes) {
            if (startsWith(text, prefix)) {
                keyword = text.substring(prefix.length())
                    .trim();
                while (!keyword.isEmpty()) {
                    char first = keyword.charAt(0);
                    if (first == ':' || first == '：' || first == '-' || first == '—') {
                        keyword = keyword.substring(1)
                            .trim();
                    } else {
                        break;
                    }
                }
                break;
            }
        }
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        keyword = Strings.safeTrim(keyword, settings.maxKeywordLength);
        if (keyword.isEmpty()) {
            return null;
        }
        String requester = "fixed".equals(settings.requesterNameMode) ? settings.requesterFixedName : message.username;
        requester = Strings.sanitizeRequester(requester, settings.requesterFixedName);
        return new SongRequest(message.uid, message.username, requester, keyword, message.receivedAtMillis);
    }

    private static boolean startsWith(String text, String prefix) {
        if (prefix == null || prefix.isEmpty() || text.length() < prefix.length()) {
            return false;
        }
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
