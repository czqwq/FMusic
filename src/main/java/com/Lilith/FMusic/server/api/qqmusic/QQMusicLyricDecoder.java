package com.Lilith.FMusic.server.api.qqmusic;

import java.util.LinkedHashMap;
import java.util.Map;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.objs.music.LyricItemObj;
import com.Lilith.FMusic.server.core.utils.Function;

public class QQMusicLyricDecoder {

    public static Map<Long, LyricItemObj> parse(String lyric) {
        Map<Long, LyricItemObj> res = new LinkedHashMap<>();
        if (lyric == null || lyric.isEmpty()) {
            return res;
        }
        String[] lines = lyric.split("\\n");
        for (String line : lines) {
            if (line == null || !line.startsWith("[") || !line.contains("]")) {
                continue;
            }
            try {
                String timeText = Function.getString(line, "[", "]");
                if (timeText == null || !timeText.contains(":") || !timeText.contains(".")) {
                    continue;
                }
                String[] a = timeText.split(":", 2);
                if (a.length != 2) {
                    continue;
                }
                String[] b = a[1].split("\\.", 2);
                if (b.length != 2) {
                    continue;
                }
                String min = a[0];
                String sec = b[0];
                String mil = b[1];
                if (!Function.isInteger(min) || !Function.isInteger(sec) || !Function.isInteger(mil)) {
                    continue;
                }
                long ms = Long.parseLong(min) * 60 * 1000 + Long.parseLong(sec) * 1000;
                long m = Long.parseLong(mil);
                if (mil.length() == 2) {
                    ms += m * 10;
                } else if (mil.length() == 3) {
                    ms += m;
                } else {
                    ms += m * 10;
                }
                String text = Function.getString(line, "]", null);
                text = FMusic.getReplacer()
                    .replace(text);
                res.put(ms, new LyricItemObj(text, null, ms));
            } catch (Exception ignored) {}
        }
        return res;
    }
}
