package com.Lilith.FMusic.server.bili.request;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.Lilith.FMusic.server.bili.config.PluginSettings;
import com.Lilith.FMusic.server.bili.util.Strings;

/** Atomic cooldown and duplicate checks. */
public final class RequestGate {

    public enum Decision {
        ACCEPTED,
        USER_COOLDOWN,
        GLOBAL_COOLDOWN,
        DUPLICATE
    }

    private final Map<String, Long> users = new LinkedHashMap<String, Long>();
    private final Map<String, Long> keywords = new LinkedHashMap<String, Long>();
    private long lastGlobal;
    private long lastCleanup;

    public synchronized Decision checkAndMark(SongRequest request, PluginSettings.SongRequest settings, long now) {
        if (settings.globalCooldownMillis > 0L && now - lastGlobal < settings.globalCooldownMillis) {
            return Decision.GLOBAL_COOLDOWN;
        }
        String userKey = request.uid > 0L ? "uid:" + request.uid : "name:" + request.username.toLowerCase(Locale.ROOT);
        long userWindow = settings.perUserCooldownSeconds * 1000L;
        Long previousUser = users.get(userKey);
        if (userWindow > 0L && previousUser != null && now - previousUser < userWindow) {
            return Decision.USER_COOLDOWN;
        }
        String keywordKey = Strings.normalizeKeyword(request.keyword);
        long duplicateWindow = settings.duplicateWindowSeconds * 1000L;
        Long previousKeyword = keywords.get(keywordKey);
        if (duplicateWindow > 0L && previousKeyword != null && now - previousKeyword < duplicateWindow) {
            return Decision.DUPLICATE;
        }
        lastGlobal = now;
        users.put(userKey, now);
        keywords.put(keywordKey, now);
        if (now - lastCleanup > 60000L || users.size() + keywords.size() > 8192) {
            cleanup(now, Math.max(userWindow, duplicateWindow));
            lastCleanup = now;
        }
        return Decision.ACCEPTED;
    }

    public synchronized void clear() {
        users.clear();
        keywords.clear();
        lastGlobal = 0L;
        lastCleanup = 0L;
    }

    private void cleanup(long now, long keepMillis) {
        long threshold = now - Math.max(keepMillis, 60000L) * 2L;
        removeOlder(users, threshold);
        removeOlder(keywords, threshold);
    }

    private static void removeOlder(Map<String, Long> map, long threshold) {
        Iterator<Map.Entry<String, Long>> iterator = map.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            if (iterator.next()
                .getValue() < threshold) {
                iterator.remove();
            }
        }
    }
}
