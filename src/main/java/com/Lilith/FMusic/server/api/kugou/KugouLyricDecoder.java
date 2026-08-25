package com.Lilith.FMusic.server.api.kugou;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.objs.music.LyricItemObj;

public final class KugouLyricDecoder {

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    private KugouLyricDecoder() {}

    public static Map<Long, LyricItemObj> parse(String lyric) {
        Map<Long, LyricItemObj> result = new LinkedHashMap<>();
        if (lyric == null || lyric.trim()
            .isEmpty()) {
            return result;
        }
        String[] lines = lyric.replace("\r", "")
            .split("\n");
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            Matcher matcher = TIME.matcher(line);
            int textStart = -1;
            while (matcher.find()) {
                textStart = matcher.end();
            }
            if (textStart < 0) {
                continue;
            }
            String text = line.substring(textStart)
                .trim();
            text = FMusic.getReplacer()
                .replace(text);

            matcher.reset();
            while (matcher.find()) {
                try {
                    long minute = Long.parseLong(matcher.group(1));
                    long second = Long.parseLong(matcher.group(2));
                    long millis = parseFraction(matcher.group(3));
                    long start = minute * 60_000L + second * 1_000L + millis;
                    result.put(start, new LyricItemObj(text, null, start));
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private static long parseFraction(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        long number = Long.parseLong(value);
        if (value.length() == 1) {
            return number * 100L;
        }
        if (value.length() == 2) {
            return number * 10L;
        }
        return number;
    }
}
