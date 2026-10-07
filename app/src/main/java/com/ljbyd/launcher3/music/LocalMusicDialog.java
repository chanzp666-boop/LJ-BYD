package com.ljbyd.launcher3.music;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ljbyd.launcher3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地音乐列表弹窗（仿迪友本地音乐）
 *
 * 展示扫描到的本地音乐（标题/歌手/时长），点击播放。
 */
public class LocalMusicDialog {

    public interface OnSongSelected {
        void onSelect(LocalMusicManager.Song song, int index);
    }

    public static void show(Context context, List<LocalMusicManager.Song> songs,
                            int currentIndex, OnSongSelected listener) {
        if (songs == null || songs.isEmpty()) {
            Toast.makeText(context, "未扫描到本地音乐（可插 U 盘/SD 卡后重试）", Toast.LENGTH_LONG).show();
            return;
        }
        Dialog dialog = new Dialog(context, R.style.BottomDialogStyle);
        dialog.setContentView(R.layout.dialog_local_music);

        TextView title = dialog.findViewById(R.id.localMusicTitle);
        title.setText("本地音乐（" + songs.size() + " 首）");

        ListView list = dialog.findViewById(R.id.localMusicList);
        list.setAdapter(new SongAdapter(songs, currentIndex));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (listener != null) {
                listener.onSelect(songs.get(position), position);
            }
            dialog.dismiss();
        });

        Button close = dialog.findViewById(R.id.localMusicClose);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.getWindow().setLayout(700, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.show();
    }

    private static class SongAdapter extends BaseAdapter {
        private final List<LocalMusicManager.Song> songs = new ArrayList<>();
        private final int currentIndex;

        SongAdapter(List<LocalMusicManager.Song> songs, int currentIndex) {
            this.songs.addAll(songs);
            this.currentIndex = currentIndex;
        }

        @Override
        public int getCount() {
            return songs.size();
        }

        @Override
        public Object getItem(int position) {
            return songs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context ctx = parent.getContext();
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(18, 14, 18, 14);

            TextView name = new TextView(ctx);
            name.setText(songs.get(position).display());
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(14);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(name);

            String info = songs.get(position).artist == null ? "" : songs.get(position).artist;
            TextView meta = new TextView(ctx);
            meta.setText(formatDuration(songs.get(position).duration) +
                    (info.isEmpty() ? "" : " · " + info));
            meta.setTextColor(0xFF8FA3B8);
            meta.setTextSize(11);
            row.addView(meta);

            if (position == currentIndex) {
                TextView playing = new TextView(ctx);
                playing.setText("▶");
                playing.setTextColor(0xFF55D6FF);
                playing.setTextSize(14);
                playing.setPadding(10, 0, 0, 0);
                row.addView(playing);
            }
            return row;
        }

        private String formatDuration(long ms) {
            if (ms <= 0) return "--:--";
            long totalSec = ms / 1000;
            return String.format(Locale.CHINA, "%02d:%02d",
                    totalSec / 60, totalSec % 60);
        }
    }
}
