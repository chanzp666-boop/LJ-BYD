package com.ljbyd.launcher3.music;

import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地音乐管理器（仿迪友 LocalMusicItemBean + MusicControlService）
 *
 * 扫描 MediaStore 本地音乐（含 USB/SD 卡），支持播放/暂停/上一首/下一首，
 * 歌词：优先读取同目录同名 .lrc 文件解析（LyricParser），无则仅显示歌名。
 */
public class LocalMusicManager {

    private static final String TAG = "LocalMusicManager";
    private static LocalMusicManager sInstance;

    /** 本地音乐条目 */
    public static class Song {
        public long id;
        public String title;
        public String artist;
        public String album;
        public String path;
        public long duration;
        public String lyricPath;   // 同目录 .lrc（可为空）

        public String display() {
            return (title == null || title.isEmpty()) ? "未知歌曲" : title;
        }
    }

    /** 歌词行 */
    public static class LyricLine {
        public long timeMs;
        public String text;
        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private final Context mContext;
    private final List<Song> mSongs = new ArrayList<>();
    private MediaPlayer mPlayer;
    private int mCurrentIndex = -1;
    private boolean mIsPlaying = false;
    private List<LyricLine> mLyrics = new ArrayList<>();
    private Listener mListener;

    public interface Listener {
        void onSongChanged(Song song, boolean isPlaying);
        void onProgress(long positionMs, long durationMs);
        void onLyricLine(String line);
    }

    public static synchronized LocalMusicManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LocalMusicManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private LocalMusicManager(Context context) {
        mContext = context;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** 扫描本地音乐（含 U 盘/SD 卡） */
    public List<Song> scanMusic() {
        mSongs.clear();
        try {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            try (Cursor cursor = mContext.getContentResolver().query(
                    uri, projection, selection, null,
                    MediaStore.Audio.Media.TITLE + " ASC")) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                    int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                    int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                    int durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                    while (cursor.moveToNext()) {
                        Song s = new Song();
                        s.id = cursor.getLong(idCol);
                        s.title = cursor.getString(titleCol);
                        s.artist = cursor.getString(artistCol);
                        s.album = cursor.getString(albumCol);
                        s.path = cursor.getString(dataCol);
                        s.duration = cursor.getLong(durCol);
                        s.lyricPath = findLrc(s.path);
                        mSongs.add(s);
                    }
                }
            }
            Log.i(TAG, "扫描到本地音乐 " + mSongs.size() + " 首");
        } catch (Exception e) {
            Log.e(TAG, "扫描音乐失败: " + e.getMessage(), e);
        }
        return mSongs;
    }

    /** 找同目录同名 .lrc 歌词文件 */
    private String findLrc(String audioPath) {
        if (audioPath == null) return null;
        try {
            File f = new File(audioPath);
            File dir = f.getParentFile();
            if (dir == null) return null;
            String base = audioPath.substring(0, audioPath.lastIndexOf('.'));
            File lrc1 = new File(base + ".lrc");
            if (lrc1.exists()) return lrc1.getAbsolutePath();
            String name = f.getName().substring(0, f.getName().lastIndexOf('.'));
            File lrc2 = new File(dir, name + ".lrc");
            if (lrc2.exists()) return lrc2.getAbsolutePath();
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<Song> getSongs() {
        return mSongs;
    }

    public Song getCurrentSong() {
        if (mCurrentIndex >= 0 && mCurrentIndex < mSongs.size()) {
            return mSongs.get(mCurrentIndex);
        }
        return null;
    }

    /** 播放指定曲目 */
    public void play(int index) {
        if (index < 0 || index >= mSongs.size()) return;
        try {
            stopInternal();
            mCurrentIndex = index;
            Song song = mSongs.get(index);
            mPlayer = new MediaPlayer();
            mPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mPlayer.setDataSource(song.path);
            mPlayer.prepare();
            mPlayer.setOnCompletionListener(mp -> playNext());
            mPlayer.setOnPreparedListener(mp -> {
                mIsPlaying = true;
                mPlayer.start();
                notifySongChanged();
            });
            mPlayer.prepareAsync();
            mLyrics = LyricParser.parse(song.lyricPath);
            if (mListener != null) mListener.onSongChanged(song, true);
        } catch (Exception e) {
            Log.e(TAG, "播放失败: " + e.getMessage(), e);
            mIsPlaying = false;
        }
    }

    public void playPause() {
        if (mPlayer == null) return;
        try {
            if (mIsPlaying) {
                mPlayer.pause();
                mIsPlaying = false;
            } else {
                mPlayer.start();
                mIsPlaying = true;
            }
            notifySongChanged();
        } catch (Exception e) {
            Log.e(TAG, "播放暂停失败", e);
        }
    }

    public void playNext() {
        if (mSongs.isEmpty()) return;
        int next = (mCurrentIndex + 1) % mSongs.size();
        play(next);
    }

    public void playPrevious() {
        if (mSongs.isEmpty()) return;
        int prev = mCurrentIndex <= 0 ? mSongs.size() - 1 : mCurrentIndex - 1;
        play(prev);
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    public long getPosition() {
        try {
            return mPlayer == null ? 0 : mPlayer.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public long getDuration() {
        try {
            return mPlayer == null ? 0 : mPlayer.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 每 200ms 由外部轮询调用，推进进度与歌词 */
    public void tick() {
        if (mPlayer == null || !mIsPlaying) return;
        long pos = getPosition();
        long dur = getDuration();
        if (mListener != null) {
            mListener.onProgress(pos, dur);
        }
        String line = LyricParser.currentLyric(mLyrics, pos);
        if (line != null && mListener != null) {
            mListener.onLyricLine(line);
        }
    }

    private void stopInternal() {
        if (mPlayer != null) {
            try {
                mPlayer.release();
            } catch (Exception ignored) {
            }
            mPlayer = null;
        }
        mIsPlaying = false;
    }

    public void release() {
        stopInternal();
        mCurrentIndex = -1;
        mSongs.clear();
        mLyrics.clear();
    }

    private void notifySongChanged() {
        if (mListener != null) {
            mListener.onSongChanged(getCurrentSong(), mIsPlaying);
        }
    }
}
