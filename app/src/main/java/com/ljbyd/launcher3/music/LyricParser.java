package com.ljbyd.launcher3.music;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析（仿迪友 LyricBean）
 *
 * 支持标准 [mm:ss.xx] 格式；多时间戳行拆分；元信息行忽略。
 */
public class LyricParser {

    private static final String TAG = "LyricParser";
    // [mm:ss.xx] 或 [mm:ss:xx]
    private static final Pattern TIME_PATTERN =
            Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]");

    /** 解析 .lrc 文件，返回按时间排序的歌词行 */
    public static List<LocalMusicManager.LyricLine> parse(String lrcPath) {
        List<LocalMusicManager.LyricLine> lines = new ArrayList<>();
        if (lrcPath == null) return lines;
        File f = new File(lrcPath);
        if (!f.exists()) return lines;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f),
                        Charset.forName("UTF-8")))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = TIME_PATTERN.matcher(line);
                List<Long> times = new ArrayList<>();
                while (m.find()) {
                    long min = Long.parseLong(m.group(1));
                    long sec = Long.parseLong(m.group(2));
                    long ms = 0;
                    if (m.group(3) != null) {
                        String frac = m.group(3);
                        if (frac.length() == 1) ms = Long.parseLong(frac) * 100;
                        else if (frac.length() == 2) ms = Long.parseLong(frac) * 10;
                        else ms = Long.parseLong(frac);
                    }
                    times.add(min * 60_000 + sec * 1000 + ms);
                }
                if (times.isEmpty()) continue;
                String text = TIME_PATTERN.matcher(line).replaceAll("").trim();
                for (long t : times) {
                    lines.add(new LocalMusicManager.LyricLine(t, text));
                }
            }
            Collections.sort(lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
            Log.i(TAG, "解析歌词 " + lines.size() + " 行: " + lrcPath);
        } catch (Exception e) {
            Log.e(TAG, "歌词解析失败: " + e.getMessage());
        }
        return lines;
    }

    /** 取当前时间对应的歌词行（无则 null） */
    public static String currentLyric(List<LocalMusicManager.LyricLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return null;
        String current = null;
        for (LocalMusicManager.LyricLine ll : lines) {
            if (ll.timeMs <= positionMs) {
                current = ll.text;
            } else {
                break;
            }
        }
        return current;
    }
}
