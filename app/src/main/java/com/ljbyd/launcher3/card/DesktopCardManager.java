package com.ljbyd.launcher3.card;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.ljbyd.launcher3.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 桌面卡片管理器（仿迪友桌面卡片式布局）
 *
 * 桌面底部卡片区由多个功能卡片组成（地图/音乐/快速应用/车况/天气），
 * 支持：设置里开关显示、顺序调整（保存到 SharedPreferences，重启生效）。
 */
public class DesktopCardManager {

    private static final String PREFS_NAME = "ljbyd_cards";
    private static final String KEY_ORDER = "card_order";
    private static final String KEY_ENABLED_PREFIX = "card_enabled_";

    /** 卡片 key 与名称 */
    public static final String CARD_MAP = "map";
    public static final String CARD_MUSIC = "music";
    public static final String CARD_QUICK_CTRL = "quick_ctrl";
    public static final String CARD_ENERGY = "energy";
    public static final String CARD_APPS = "apps";
    public static final String CARD_CAR = "car";
    public static final String CARD_WEATHER = "weather";

    public static final String[] ALL_CARDS = {CARD_MAP, CARD_MUSIC, CARD_QUICK_CTRL, CARD_ENERGY, CARD_APPS, CARD_CAR, CARD_WEATHER};

    public static String cardName(Context ctx, String key) {
        switch (key) {
            case CARD_MAP: return "地图导航";
            case CARD_MUSIC: return "音乐";
            case CARD_QUICK_CTRL: return "便捷车控";
            case CARD_ENERGY: return "能耗环";
            case CARD_APPS: return "快速应用";
            case CARD_CAR: return "车况胎压";
            case CARD_WEATHER: return "天气";
            default: return key;
        }
    }

    /** 卡片对应的布局 View id */
    public static int cardViewId(String key) {
        switch (key) {
            case CARD_MAP: return R.id.mapWidget;
            case CARD_MUSIC: return R.id.musicWidget;
            case CARD_QUICK_CTRL: return R.id.quickControlWidget;
            case CARD_ENERGY: return R.id.energyWidget;
            case CARD_APPS: return R.id.quickAppsWidget;
            case CARD_CAR: return R.id.carWidget;
            case CARD_WEATHER: return R.id.weatherWidget;
            default: return 0;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 读取卡片顺序（未配置时用默认顺序） */
    public static List<String> getOrder(Context ctx) {
        String saved = prefs(ctx).getString(KEY_ORDER, null);
        List<String> order = new ArrayList<>();
        if (saved != null) {
            for (String k : saved.split(",")) {
                if (!k.isEmpty() && !order.contains(k)) {
                    order.add(k);
                }
            }
        }
        for (String k : ALL_CARDS) {
            if (!order.contains(k)) {
                order.add(k);
            }
        }
        return order;
    }

    public static void saveOrder(Context ctx, List<String> order) {
        prefs(ctx).edit().putString(KEY_ORDER, String.join(",", order)).apply();
    }

    /** 卡片开关 */
    public static boolean isEnabled(Context ctx, String key) {
        return prefs(ctx).getBoolean(KEY_ENABLED_PREFIX + key, true);
    }

    public static void setEnabled(Context ctx, String key, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED_PREFIX + key, on).apply();
    }

    /**
     * 按配置重排 + 显隐桌面卡片。
     * 调用时机：启动时 / 设置里修改卡片配置后。
     */
    public static void apply(LinearLayout container) {
        if (container == null) return;
        Context ctx = container.getContext();
        List<String> order = getOrder(ctx);

        // 先缓存所有卡片 View（必须在移除之前 findViewById，否则脱离视图树后找不到）
        java.util.Map<String, View> cards = new java.util.HashMap<>();
        for (String key : ALL_CARDS) {
            int viewId = cardViewId(key);
            if (viewId == 0) continue;
            View card = container.findViewById(viewId);
            if (card != null) {
                cards.put(key, card);
            }
        }
        // 清空容器
        container.removeAllViews();
        // 按配置顺序重新添加 + 显隐
        for (String key : order) {
            View card = cards.get(key);
            if (card == null) continue;
            if (card.getParent() != null) {
                ((ViewGroup) card.getParent()).removeView(card);
            }
            container.addView(card);
            card.setVisibility(isEnabled(ctx, key) ? View.VISIBLE : View.GONE);
        }
    }

    /** 交换卡片顺序（设置里上移/下移） */
    public static void moveCard(Context ctx, String key, int direction) {
        List<String> order = getOrder(ctx);
        int idx = order.indexOf(key);
        if (idx < 0) return;
        int newIdx = idx + direction;
        if (newIdx < 0 || newIdx >= order.size()) return;
        String tmp = order.get(idx);
        order.set(idx, order.get(newIdx));
        order.set(newIdx, tmp);
        saveOrder(ctx, order);
    }
}
