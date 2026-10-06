package com.ljbyd.launcher3;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.view.GestureDetector;
import android.view.MotionEvent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.RotateAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ljbyd.launcher3.adb.UsbDebugConnection;
import com.ljbyd.launcher3.bridge.WebViewBridge;
import com.ljbyd.launcher3.database.AppDatabaseHelper;
import com.ljbyd.launcher3.database.ComponentConfigDatabaseHelper;
import com.ljbyd.launcher3.database.ConfigAppDatabaseHelper;
import com.ljbyd.launcher3.database.QuickAppDatabaseHelper;
import com.ljbyd.launcher3.database.WallpaperCategoryDatabaseHelper;
import com.ljbyd.launcher3.database.WallpaperSettingsDatabaseHelper;
import com.ljbyd.launcher3.service.MediaSessionService;
import com.ljbyd.launcher3.ui.MusicVisualizerView;
import com.ljbyd.launcher3.utils.AppUtils;
import com.ljbyd.launcher3.utils.InitManager;
import com.ljbyd.launcher3.utils.MusicVisualizer;
import com.ljbyd.launcher3.utils.QuoteApiUtils;
import com.ljbyd.launcher3.utils.ServiceManager;
import com.ljbyd.launcher3.utils.TaskManager;
import com.ljbyd.launcher3.utils.WallpaperDownloadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 迪伙伴车载桌面 - 纯安卓原生版主界面
 *
 * 由原 WebView 混合版（index.html + JS）1:1 转换为原生 View 实现，
 * 适配骁龙 690 / 8G 内存 / Android 10（minSdk 29）。
 *
 * 功能：
 * 1. 桌面主体：时间/日期/农历 + 壁纸背景
 * 2. 底部卡片区（横向滚动）：地图/音乐/快速App/胎压/天气
 * 3. 底部控制栏：应用列表、多任务、空调控制（温度/风量/开关/除霜）
 * 4. 设置弹窗（壁纸设置/组件配置/系统管理）
 * 5. 应用列表弹窗（搜索 + 字母索引）
 * 6. 壁纸轮播、TTS 语音、音乐可视化、ADB 授权等服务逻辑
 */
public class MainActivity extends AppCompatActivity {
    // 广播Action常量
    public static final String ACTION_WALLPAPER_SETTINGS_CHANGED = "com.ljbyd.launcher3.WALLPAPER_SETTINGS_CHANGED";

    // 权限请求码常量
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_SET_DEFAULT_LAUNCHER = 1003;
    private static final int REQUEST_OVERLAY_PERMISSION = 1004;
    private static final int REQUEST_STORAGE_PERMISSION = 1005;
    private static final int REQUEST_MANAGE_STORAGE = 1006;
    private static final int REQUEST_TTS_PERMISSION = 1008;

    // ==================== 网络状态（蓝牙/WiFi 图标，对应原版 checkBluetoothStatus/checkWifiStatus） ====================
    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;
    private NetworkStateReceiver networkStateReceiver;

    // ==================== 壁纸手势（对应原版 wallpaper-swipe-patch / initWallpaperDoubleClick / handleWallpaperLongPress） ====================
    private GestureDetector wallpaperGestureDetector;
    private final List<String> wallpaperHistory = new ArrayList<>();
    private static final int WALLPAPER_HISTORY_LIMIT = 20;

    // ==================== UI 组件（原生 View） ====================
    public ImageView backgroundImageView;
    public ImageView wallpaperPreview;
    public TextView topLeftTime;
    public ImageView bluetoothIcon;
    public ImageView wifiIcon;
    public TextView timeDisplay;
    public TextView dateDisplay;
    public TextView lunarDisplay;
    public View cardView;
    public LinearLayout widgetsContainer;

    // 地图组件
    public View goHomeBtn;
    public View goCompanyBtn;

    // 音乐组件
    public ImageView vinylRecord;
    public View tonearm;
    public TextView currentSongName;
    public TextView musicCurrentTime;
    public ProgressBar musicProgressBar;
    public TextView musicTotalTime;
    public ImageButton prevBtn;
    public ImageButton playPauseBtn;
    public ImageButton nextBtn;
    public MusicVisualizerView musicVisualizerView;

    // 快速启动 App
    public GridView quickAppsContainer;

    // 胎压组件
    public TextView tireFrontLeft;
    public TextView tireFrontRight;
    public TextView tireRearLeft;
    public TextView tireRearRight;

    // 天气组件
    public ImageView weatherIcon;
    public TextView weatherTemperature;
    public TextView weatherCondition;

    // 底部控制栏
    public ImageButton appsBtn;
    public ImageButton tasksBtn;
    public ImageView acControl;
    public ImageView acMinus;
    public TextView airTempText;
    public ImageView acPlus;
    public ImageView acArrow1;
    public ImageView windLevel;
    public ImageView acArrow2;
    public ImageView acWindMinus;
    public TextView airWindTempText;
    public ImageView acWindPlus;
    public ImageView acHcs;
    public ImageButton bydBtn;
    public ImageButton settingsBtn;

    public static MainActivity instance;

    // 数据库辅助类
    public AppDatabaseHelper dbHelper;
    public QuickAppDatabaseHelper quickAppDbHelper;
    public WallpaperCategoryDatabaseHelper wallpaperDbHelper;
    public WallpaperSettingsDatabaseHelper wallpaperSettingsDbHelper;
    public ConfigAppDatabaseHelper configAppDbHelper;
    public ComponentConfigDatabaseHelper componentConfigDbHelper;

    // 音乐相关组件
    public MusicVisualizer musicVisualizer;
    public MediaSessionService mediaSessionService;
    public AudioManager audioManager;
    private MusicPlaybackReceiver musicPlaybackReceiver;
    public boolean isMusicPlaying = false;
    public boolean isMediaSessionServiceBound = false;

    // 桥接器（保留业务方法，不再走 WebView）
    public WebViewBridge webViewBridge;

    // TTS语音合成
    private TextToSpeech textToSpeech;

    // API工具类
    public QuoteApiUtils quoteApiUtils;

    // ADB连接
    public UsbDebugConnection usbDebugConnection;

    // 应用列表预加载
    private String preloadedAppList;

    // 壁纸轮播相关
    public Runnable wallpaperRunnable;
    public boolean isWallpaperCarouselEnabled = false;
    public int wallpaperSwitchInterval = 15000;
    public boolean isAppInForeground = false;

    // 延迟启动任务
    public boolean bydAutoStartEnabled = false;
    public boolean bootGreetingEnabled = false;
    public Runnable bydAutoStartRunnable;
    public Runnable bootGreetingRunnable;

    // 壁纸状态
    public boolean isUsingDefaultWallpaper = true;
    public String currentWallpaperPath = "";

    // 时间更新
    private Handler timeUpdateHandler = new Handler();
    private Handler delayedStartHandler = new Handler();

    // 音乐播放状态检查
    public Runnable musicCheckRunnable;
    private Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // 格式化时间、日期和农历日期
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss",
                        java.util.Locale.getDefault());
                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy年MM月dd日",
                        java.util.Locale.getDefault());
                java.util.Date now = new java.util.Date();

                String time = timeFormat.format(now);
                String date = dateFormat.format(now) + " " + getWeekDay(now);
                String lunarDate = com.ljbyd.launcher3.utils.LunarCalendarUtils.lunarCalendar();

                // 直接更新原生 View
                updateTimeDisplayUI(time, date, lunarDate);
            } catch (Exception e) {
                Log.e("MainActivity", "更新时间时出错", e);
            } finally {
                timeUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    // 壁纸设置更改广播接收器
    private WallpaperSettingsChangedReceiver wallpaperSettingsChangedReceiver;

    // 工具类
    private InitManager initManager;
    private TaskManager taskManager;
    private ServiceManager serviceManager;

    // 黑胶唱片旋转动画
    private RotateAnimation vinylRotation;
    private boolean vinylIsRotating = false;

    // 设置弹窗
    private Dialog settingsDialog;
    // 应用列表弹窗
    private Dialog appsDialog;
    private List<Map<String, Object>> allAppsList = new ArrayList<>();
    private List<Map<String, Object>> filteredAppsList = new ArrayList<>();
    private SimpleAdapter appsAdapter;

    /**
     * 活动创建方法
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置屏幕方向为用户横向
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        super.onCreate(savedInstanceState);
        instance = this;

        // 初始化工具类
        initManager = new InitManager(this);
        taskManager = new TaskManager(this);
        serviceManager = new ServiceManager(this);

        // 初始化核心组件
        initManager.initCoreComponents();

        // 初始化数据库辅助类
        initManager.initDatabaseHelpers();

        // 初始化配置（只执行一次）
        initSettings();

        // 初始化原生界面
        initNativeUI();

        // 预加载应用列表
        preloadAppList();

        // 初始化广播接收器
        initManager.initReceivers();

        // 初始化组件
        initManager.initComponents();

        // 请求权限
        initManager.initPermissions();

        // 注册音乐播放广播接收器
        registerMusicPlaybackReceiver();

        // 初始化服务
        initManager.initServices();

        // 启动各项任务
        initManager.startTasks();
    }

    /**
     * 初始化原生 UI：绑定布局控件、设置点击事件、启动动画与数据加载
     */
    private void initNativeUI() {
        setContentView(R.layout.activity_main);

        // 绑定主界面控件
        backgroundImageView = findViewById(R.id.backgroundImage);
        wallpaperPreview = findViewById(R.id.wallpaperPreview);
        topLeftTime = findViewById(R.id.topLeftTime);
        bluetoothIcon = findViewById(R.id.bluetoothIcon);
        wifiIcon = findViewById(R.id.wifiIcon);
        timeDisplay = findViewById(R.id.timeDisplay);
        dateDisplay = findViewById(R.id.dateDisplay);
        lunarDisplay = findViewById(R.id.lunarDisplay);
        cardView = findViewById(R.id.cardView);
        widgetsContainer = findViewById(R.id.widgetsContainer);

        // 地图组件
        goHomeBtn = findViewById(R.id.goHomeBtn);
        goCompanyBtn = findViewById(R.id.goCompanyBtn);

        // 音乐组件
        vinylRecord = findViewById(R.id.vinylRecord);
        tonearm = findViewById(R.id.tonearm);
        currentSongName = findViewById(R.id.currentSongName);
        musicCurrentTime = findViewById(R.id.musicCurrentTime);
        musicProgressBar = findViewById(R.id.musicProgressBar);
        musicTotalTime = findViewById(R.id.musicTotalTime);
        prevBtn = findViewById(R.id.prevBtn);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        nextBtn = findViewById(R.id.nextBtn);
        musicVisualizerView = findViewById(R.id.musicVisualizerView);

        // 快速启动 App
        quickAppsContainer = findViewById(R.id.quickAppsContainer);

        // 胎压组件
        tireFrontLeft = findViewById(R.id.tireFrontLeft);
        tireFrontRight = findViewById(R.id.tireFrontRight);
        tireRearLeft = findViewById(R.id.tireRearLeft);
        tireRearRight = findViewById(R.id.tireRearRight);

        // 天气组件
        weatherIcon = findViewById(R.id.weatherIcon);
        weatherTemperature = findViewById(R.id.weatherTemperature);
        weatherCondition = findViewById(R.id.weatherCondition);

        // 底部控制栏
        appsBtn = findViewById(R.id.appsBtn);
        tasksBtn = findViewById(R.id.tasksBtn);
        acControl = findViewById(R.id.acControl);
        acMinus = findViewById(R.id.acMinus);
        airTempText = findViewById(R.id.airTempText);
        acPlus = findViewById(R.id.acPlus);
        acArrow1 = findViewById(R.id.acArrow1);
        windLevel = findViewById(R.id.windLevel);
        acArrow2 = findViewById(R.id.acArrow2);
        acWindMinus = findViewById(R.id.acWindMinus);
        airWindTempText = findViewById(R.id.airWindTempText);
        acWindPlus = findViewById(R.id.acWindPlus);
        acHcs = findViewById(R.id.acHcs);
        bydBtn = findViewById(R.id.bydBtn);
        settingsBtn = findViewById(R.id.settingsBtn);

        // ==================== 绑定点击事件 ====================
        // 地图：回家 / 公司
        goHomeBtn.setOnClickListener(v -> navigateHome());
        goCompanyBtn.setOnClickListener(v -> navigateCompany());

        // 音乐控制
        prevBtn.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.playPrevious(); });
        playPauseBtn.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.playPause(); });
        nextBtn.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.playNext(); });

        // 快速启动 App 点击
        quickAppsContainer.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, Object> app = (Map<String, Object>) parent.getItemAtPosition(position);
            String pkg = (String) app.get("packageName");
            if (webViewBridge != null) webViewBridge.launchApp(pkg);
        });

        // 底部控制栏
        appsBtn.setOnClickListener(v -> showAppsDialog());
        tasksBtn.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.openRecents(); });
        acControl.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.toggleAirConditioning(); });
        acMinus.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.adjustTemperature(-1); });
        acPlus.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.adjustTemperature(1); });
        acWindMinus.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.adjustWindLevel(-1); });
        acWindPlus.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.adjustWindLevel(1); });
        acHcs.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.toggleDefrost(); });
        bydBtn.setOnClickListener(v -> { if (webViewBridge != null) webViewBridge.launchBydHome(); });
        settingsBtn.setOnClickListener(v -> showSettingsDialog());

        // 启动黑胶唱片旋转动画（默认缓慢旋转，播放时正常速度）
        setupVinylAnimation();
        // 启动时间更新
        startTimeUpdate();
        // 加载壁纸背景
        loadInitialWallpaper();
        // 加载快速启动应用
        loadQuickApps();
        // 初始化空调状态
        if (webViewBridge != null) {
            webViewBridge.initializeAcStatus();
        }
        // 加载天气
        loadWeather();

        // ==================== 补齐原版功能 ====================
        // 网络状态图标（蓝牙/WiFi）
        initNetworkStatus();
        // 壁纸手势：滑动切换 / 双击恢复默认 / 长按删除
        initWallpaperGestures();
        // 快速启动 App 长按移除
        initQuickAppLongPress();
        // 回家/公司长按：选择地图应用
        initMapAppSelection();
    }

    /**
     * 黑胶唱片旋转动画
     */
    private void setupVinylAnimation() {
        vinylRotation = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        vinylRotation.setDuration(4000);
        vinylRotation.setRepeatCount(Animation.INFINITE);
        vinylRotation.setRepeatMode(Animation.RESTART);
    }

    /**
     * 控制黑胶唱片旋转（播放时旋转，暂停时停止）
     */
    public void setVinylRotating(boolean rotating) {
        if (vinylRecord == null) return;
        if (rotating && !vinylIsRotating) {
            vinylRecord.startAnimation(vinylRotation);
            vinylIsRotating = true;
        } else if (!rotating && vinylIsRotating) {
            vinylRecord.clearAnimation();
            vinylIsRotating = false;
        }
    }

    /**
     * 原生更新时间显示
     */
    public void updateTimeDisplayUI(String time, String date, String lunarDate) {
        if (timeDisplay != null) timeDisplay.setText(time);
        if (dateDisplay != null) dateDisplay.setText(date);
        if (lunarDisplay != null) lunarDisplay.setText(lunarDate);
        if (topLeftTime != null) topLeftTime.setText(time);
    }

    /**
     * 加载初始壁纸（默认壁纸）
     */
    private void loadInitialWallpaper() {
        try {
            backgroundImageView.setImageResource(R.drawable.default_bg_1);
            isUsingDefaultWallpaper = true;
            currentWallpaperPath = "";
        } catch (Exception e) {
            Log.e("MainActivity", "加载默认壁纸失败", e);
        }
    }

    /**
     * 设置壁纸背景（支持本地路径与资源）
     */
    public void setWallpaperBackground(String pathOrResource) {
        try {
            if (pathOrResource == null || pathOrResource.isEmpty()) {
                return;
            }
            if (pathOrResource.startsWith("file://")) {
                String filePath = pathOrResource.substring(7);
                Bitmap bmp = BitmapFactory.decodeFile(filePath);
                if (bmp != null) {
                    backgroundImageView.setImageBitmap(bmp);
                    isUsingDefaultWallpaper = false;
                    currentWallpaperPath = filePath;
                }
            } else if (pathOrResource.startsWith("/") || pathOrResource.contains(".jpg") ||
                    pathOrResource.contains(".jpeg") || pathOrResource.contains(".png")) {
                // 绝对/相对文件路径（原版 getRandomWallpaper 返回本地绝对路径，不带 file:// 前缀）
                Bitmap bmp = BitmapFactory.decodeFile(pathOrResource);
                if (bmp != null) {
                    backgroundImageView.setImageBitmap(bmp);
                    isUsingDefaultWallpaper = false;
                    currentWallpaperPath = pathOrResource;
                } else {
                    Log.e("MainActivity", "解码本地壁纸失败: " + pathOrResource);
                }
            } else if (pathOrResource.startsWith("http")) {
                // 在线壁纸异步加载
                String url = pathOrResource;
                new Thread(() -> {
                    try {
                        Bitmap bmp = WallpaperDownloadUtils.downloadBitmap(url);
                        if (bmp != null) {
                            runOnUiThread(() -> {
                                backgroundImageView.setImageBitmap(bmp);
                                isUsingDefaultWallpaper = false;
                            });
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "加载在线壁纸失败", e);
                    }
                }).start();
            } else {
                // 视为资源 ID
                try {
                    int resId = Integer.parseInt(pathOrResource);
                    backgroundImageView.setImageResource(resId);
                    isUsingDefaultWallpaper = true;
                } catch (Exception e) {
                    // 忽略
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "设置壁纸失败", e);
        }
    }

    /**
     * 加载快速启动应用
     */
    public void loadQuickApps() {
        try {
            List<Map<String, Object>> apps = quickAppDbHelper.getAllQuickApps();
            if (apps == null || apps.isEmpty()) {
                return;
            }
            List<Map<String, Object>> data = new ArrayList<>();
            for (Map<String, Object> app : apps) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", app.get("name"));
                item.put("packageName", app.get("packageName"));
                String iconBase64 = (String) app.get("iconBase64");
                if (iconBase64 != null && !iconBase64.isEmpty()) {
                    byte[] bytes = Base64.decode(iconBase64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        item.put("icon", bmp);
                    }
                }
                data.add(item);
            }
            if (data.isEmpty()) return;

            String[] from = { "name", "icon" };
            int[] to = { R.id.quick_app_name, R.id.quick_app_icon };
            SimpleAdapter adapter = new SimpleAdapter(this, data,
                    R.layout.item_quick_app, from, to);
            quickAppsContainer.setAdapter(adapter);
        } catch (Exception e) {
            Log.e("MainActivity", "加载快速启动应用失败", e);
        }
    }

    /**
     * 加载天气（简化：调用天气接口）
     */
    private void loadWeather() {
        try {
            // 使用原天气逻辑（QuoteApiUtils 或直接 HTTP）
            new Thread(() -> {
                try {
                    String apiUrl = "https://wttr.in/?format=j1";
                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        JSONObject root = new JSONObject(sb.toString());
                        JSONObject current = root.getJSONArray("current_condition").getJSONObject(0);
                        String temp = current.getString("temp_C") + "°";
                        String desc = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value");
                        runOnUiThread(() -> {
                            weatherTemperature.setText(temp);
                            weatherCondition.setText(desc);
                            try {
                                updateWeatherIcon(current.getString("weatherCode"));
                            } catch (Exception ex) {
                                Log.e("MainActivity", "更新天气图标失败", ex);
                            }
                        });
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e("MainActivity", "加载天气失败", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e("MainActivity", "天气初始化失败", e);
        }
    }

    /**
     * 更新天气图标（按天气代码）
     */
    private void updateWeatherIcon(String weatherCode) {
        try {
            int code = Integer.parseInt(weatherCode);
            int resId;
            if (code == 113) resId = R.drawable.nav_weather_sunny;
            else if (code == 116 || code == 119) resId = R.drawable.nav_weather_cloudy;
            else if (code >= 122 && code <= 260) resId = R.drawable.nav_weather_thunderstorm;
            else if (code >= 266 && code <= 329) resId = R.drawable.nav_weather_rainy;
            else if (code >= 338 && code <= 368) resId = R.drawable.nav_weather_snowy;
            else if (code == 248 || code == 260) resId = R.drawable.nav_weather_foggy;
            else resId = R.drawable.nav_weather;
            weatherIcon.setImageResource(resId);
        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== 弹窗：应用列表 ====================

    /**
     * 显示应用列表弹窗
     */
    private void showAppsDialog() {
        if (appsDialog != null && appsDialog.isShowing()) {
            return;
        }
        appsDialog = new Dialog(this, R.style.BottomDialogStyle);
        appsDialog.setContentView(R.layout.dialog_apps);
        // 对应原前端 apps-modal-content: width 75% / height 75%，居中
        android.graphics.Point size = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        appsDialog.getWindow().setLayout((int) (size.x * 0.75),
                (int) (size.y * 0.75));
        appsDialog.getWindow().setGravity(android.view.Gravity.CENTER);

        ImageView closeApps = appsDialog.findViewById(R.id.closeApps);
        EditText appSearch = appsDialog.findViewById(R.id.appSearch);
        ListView appsList = appsDialog.findViewById(R.id.appsList);
        TextView alphabetList = appsDialog.findViewById(R.id.alphabetList);

        closeApps.setOnClickListener(v -> appsDialog.dismiss());

        // 加载应用数据
        loadAppsIntoDialog(appSearch, appsList, alphabetList);

        // 搜索过滤
        appSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString(), appsList, alphabetList);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });

        // 长按应用：添加到快速启动（已在快速启动则移除）—— 对应原版 showAddToQuickAppsDialog
        appsList.setOnItemLongClickListener((parent, view, position, id) -> {
            try {
                Map<String, Object> app = (Map<String, Object>) parent.getItemAtPosition(position);
                showAddOrRemoveQuickAppDialog(app);
            } catch (Exception e) {
                Log.e("MainActivity", "长按应用列表项失败", e);
            }
            return true;
        });

        appsDialog.show();
    }

    // ==================== 补齐：网络状态图标（蓝牙/WiFi） ====================

    private void initNetworkStatus() {
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            networkStateReceiver = new NetworkStateReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(networkStateReceiver, filter);
            updateNetworkStatus();
        } catch (Exception e) {
            Log.e("MainActivity", "初始化网络状态失败", e);
        }
    }

    private void updateNetworkStatus() {
        try {
            boolean btOn = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
            bluetoothIcon.setVisibility(btOn ? View.VISIBLE : View.GONE);

            boolean wifiConnected = false;
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    Network network = cm.getActiveNetwork();
                    if (network != null) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                        wifiConnected = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    }
                }
            }
            wifiIcon.setVisibility(wifiConnected ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.e("MainActivity", "更新网络状态失败", e);
        }
    }

    private class NetworkStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateNetworkStatus();
        }
    }

    // ==================== 补齐：壁纸手势（滑动切换/双击恢复默认/长按删除） ====================

    private void initWallpaperGestures() {
        if (backgroundImageView == null) return;
        wallpaperGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // 对应原版 WallpaperSwipeManager：左右滑动切换壁纸
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX < 0) {
                        nextWallpaper();
                    } else {
                        previousWallpaper();
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 对应原版 restoreDefaultWallpaper：双击恢复默认壁纸
                restoreDefaultWallpaper();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // 对应原版 handleWallpaperLongPress：长按删除当前壁纸
                if (deleteCurrentWallpaper()) {
                    Toast.makeText(MainActivity.this, "壁纸已删除", Toast.LENGTH_SHORT).show();
                    nextWallpaper();
                } else {
                    Toast.makeText(MainActivity.this, "无法删除当前壁纸", Toast.LENGTH_SHORT).show();
                }
            }
        });
        backgroundImageView.setOnTouchListener((v, event) -> wallpaperGestureDetector.onTouchEvent(event));
    }

    private void nextWallpaper() {
        try {
            if (currentWallpaperPath != null && !currentWallpaperPath.isEmpty()) {
                wallpaperHistory.add(currentWallpaperPath);
                if (wallpaperHistory.size() > WALLPAPER_HISTORY_LIMIT) {
                    wallpaperHistory.remove(0);
                }
            }
            if (webViewBridge != null) {
                String path = webViewBridge.getRandomWallpaper();
                if (path != null && !path.isEmpty()) {
                    setWallpaperBackground(path);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "切换下一张壁纸失败", e);
        }
    }

    private void previousWallpaper() {
        try {
            if (!wallpaperHistory.isEmpty()) {
                String prev = wallpaperHistory.remove(wallpaperHistory.size() - 1);
                setWallpaperBackground(prev);
            } else if (webViewBridge != null) {
                String path = webViewBridge.getRandomWallpaper();
                if (path != null && !path.isEmpty()) {
                    setWallpaperBackground(path);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "切换上一张壁纸失败", e);
        }
    }

    private void restoreDefaultWallpaper() {
        try {
            backgroundImageView.setImageResource(R.drawable.default_bg_1);
            isUsingDefaultWallpaper = true;
            currentWallpaperPath = null;
            Toast.makeText(this, "已恢复默认壁纸", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("MainActivity", "恢复默认壁纸失败", e);
        }
    }

    // ==================== 补齐：快速启动 App 长按移除 ====================

    private void initQuickAppLongPress() {
        quickAppsContainer.setOnItemLongClickListener((parent, view, position, id) -> {
            try {
                Map<String, Object> app = (Map<String, Object>) parent.getItemAtPosition(position);
                showRemoveFromQuickAppsDialog(app);
            } catch (Exception e) {
                Log.e("MainActivity", "长按快速应用失败", e);
            }
            return true;
        });
    }

    // ==================== 补齐：应用长按 添加/移除 快速启动 ====================

    private void showAddOrRemoveQuickAppDialog(Map<String, Object> app) {
        String pkg = String.valueOf(app.get("packageName"));
        boolean isQuick = webViewBridge != null && webViewBridge.isQuickApp(pkg);
        if (isQuick) {
            showRemoveFromQuickAppsDialog(app);
        } else {
            showAddToQuickAppsDialog(app);
        }
    }

    private void showAddToQuickAppsDialog(Map<String, Object> app) {
        String name = String.valueOf(app.get("name"));
        String pkg = String.valueOf(app.get("packageName"));
        final String iconBase64;
        Object icon = app.get("icon");
        if (icon instanceof Bitmap) {
            iconBase64 = bitmapToBase64((Bitmap) icon);
        } else {
            iconBase64 = "";
        }
        new AlertDialog.Builder(this, R.style.BottomDialogStyle)
                .setTitle("添加到快速启动")
                .setMessage("确定要将「" + name + "」添加到快速启动吗？")
                .setPositiveButton("确定", (d, w) -> {
                    if (webViewBridge != null) {
                        webViewBridge.addQuickApp(name, pkg, iconBase64);
                        loadQuickApps();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRemoveFromQuickAppsDialog(Map<String, Object> app) {
        String name = String.valueOf(app.get("name"));
        String pkg = String.valueOf(app.get("packageName"));
        new AlertDialog.Builder(this, R.style.BottomDialogStyle)
                .setTitle("移除快速启动")
                .setMessage("确定要从快速启动中移除「" + name + "」吗？")
                .setPositiveButton("确定", (d, w) -> {
                    if (webViewBridge != null) {
                        webViewBridge.removeQuickApp(pkg);
                        loadQuickApps();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String bitmapToBase64(Bitmap bmp) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 补齐：回家/公司长按选择地图应用 ====================

    private void initMapAppSelection() {
        goHomeBtn.setOnLongClickListener(v -> { showMapAppSelectionDialog("goHomeBtn"); return true; });
        goCompanyBtn.setOnLongClickListener(v -> { showMapAppSelectionDialog("goCompanyBtn"); return true; });
    }

    private void showMapAppSelectionDialog(String buttonId) {
        String title = "goHomeBtn".equals(buttonId) ? "选择回家地图应用" : "选择公司地图应用";
        try {
            List<Map<String, Object>> apps = AppUtils.getInstalledApps(this);
            List<String> names = new ArrayList<>();
            List<Map<String, Object>> mapApps = new ArrayList<>();
            for (Map<String, Object> app : apps) {
                String pkg = String.valueOf(app.get("packageName")).toLowerCase(Locale.ROOT);
                if (pkg.contains("map") || pkg.contains("navi") || pkg.contains("amap")
                        || pkg.contains("baidu") || pkg.contains("autonavi") || pkg.contains("gaode")) {
                    names.add(String.valueOf(app.get("name")));
                    mapApps.add(app);
                }
            }
            if (mapApps.isEmpty()) {
                Toast.makeText(this, "未检测到地图应用，请先安装高德/百度地图", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this, R.style.BottomDialogStyle)
                    .setTitle(title)
                    .setItems(names.toArray(new String[0]), (d, which) -> {
                        Map<String, Object> app = mapApps.get(which);
                        String name = String.valueOf(app.get("name"));
                        String pkg = String.valueOf(app.get("packageName"));
                        String iconBase64 = "";
                        Object icon = app.get("icon");
                        if (icon instanceof Bitmap) {
                            iconBase64 = bitmapToBase64((Bitmap) icon);
                        }
                        if (webViewBridge != null) {
                            webViewBridge.saveConfigApp(buttonId, name, pkg, iconBase64);
                            Toast.makeText(this, "已配置「" + name + "」", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e("MainActivity", "显示地图应用选择失败", e);
        }
    }


    /**
     * 加载应用列表到弹窗
     */
    private void loadAppsIntoDialog(EditText appSearch, ListView appsList, TextView alphabetList) {
        new Thread(() -> {
            try {
                List<Map<String, Object>> apps = AppUtils.getInstalledApps(this);
                allAppsList.clear();
                allAppsList.addAll(apps);
                runOnUiThread(() -> {
                    filteredAppsList.clear();
                    filteredAppsList.addAll(allAppsList);
                    bindAppsList(appsList, alphabetList);
                });
            } catch (Exception e) {
                Log.e("MainActivity", "加载应用列表失败", e);
            }
        }).start();
    }

    /**
     * 绑定应用列表适配器与字母索引
     */
    private void bindAppsList(ListView appsList, TextView alphabetList) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> app : filteredAppsList) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", app.get("name"));
            item.put("packageName", app.get("packageName"));
            item.put("letter", getFirstLetter(String.valueOf(app.get("name"))));
            Object icon = app.get("icon");
            if (icon != null) {
                item.put("icon", icon);
            }
            data.add(item);
        }

        String[] from = { "name", "packageName", "letter", "icon" };
        int[] to = { R.id.app_name, R.id.app_package, R.id.app_letter, R.id.app_icon };
        appsAdapter = new SimpleAdapter(this, data, R.layout.item_app, from, to) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                TextView letterView = view.findViewById(R.id.app_letter);
                String letter = String.valueOf(data.get(position).get("letter"));
                if (position == 0 || !letter.equals(String.valueOf(data.get(position - 1).get("letter")))) {
                    letterView.setVisibility(View.VISIBLE);
                } else {
                    letterView.setVisibility(View.GONE);
                }
                return view;
            }
        };
        appsList.setAdapter(appsAdapter);

        // 点击启动应用
        appsList.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, Object> app = data.get(position);
            String pkg = (String) app.get("packageName");
            if (webViewBridge != null) webViewBridge.launchApp(pkg);
            if (appsDialog != null) appsDialog.dismiss();
        });

        // 字母索引
        StringBuilder sb = new StringBuilder();
        String lastLetter = "";
        for (Map<String, Object> app : data) {
            String letter = (String) app.get("letter");
            if (!letter.equals(lastLetter)) {
                sb.append(letter).append("\n");
                lastLetter = letter;
            }
        }
        alphabetList.setText(sb.toString());
    }

    /**
     * 按关键字过滤应用
     */
    private void filterApps(String keyword, ListView appsList, TextView alphabetList) {
        filteredAppsList.clear();
        if (keyword == null || keyword.isEmpty()) {
            filteredAppsList.addAll(allAppsList);
        } else {
            for (Map<String, Object> app : allAppsList) {
                String name = String.valueOf(app.get("name"));
                String pkg = String.valueOf(app.get("packageName"));
                if (name.contains(keyword) || pkg.contains(keyword)) {
                    filteredAppsList.add(app);
                }
            }
        }
        bindAppsList(appsList, alphabetList);
    }

    // ==================== 弹窗：设置 ====================

    /**
     * 显示设置弹窗
     */
    private void showSettingsDialog() {
        if (settingsDialog != null && settingsDialog.isShowing()) {
            return;
        }
        settingsDialog = new Dialog(this, R.style.BottomDialogStyle);
        settingsDialog.setContentView(R.layout.dialog_settings);
        settingsDialog.getWindow().setLayout(560, ViewGroup.LayoutParams.WRAP_CONTENT);

        ImageView closeSettings = settingsDialog.findViewById(R.id.closeSettings);
        Button tabWallpaper = settingsDialog.findViewById(R.id.tabWallpaper);
        Button tabComponents = settingsDialog.findViewById(R.id.tabComponents);
        Button tabApps = settingsDialog.findViewById(R.id.tabApps);
        LinearLayout wallpaperTab = settingsDialog.findViewById(R.id.wallpaperTabContent);
        LinearLayout componentsTab = settingsDialog.findViewById(R.id.componentsTabContent);
        LinearLayout appsTab = settingsDialog.findViewById(R.id.appsTabContent);

        // Tab 切换
        tabWallpaper.setOnClickListener(v -> switchSettingTab(tabWallpaper, tabComponents, tabApps,
                wallpaperTab, componentsTab, appsTab));
        tabComponents.setOnClickListener(v -> switchSettingTab(tabComponents, tabWallpaper, tabApps,
                componentsTab, wallpaperTab, appsTab));
        tabApps.setOnClickListener(v -> switchSettingTab(tabApps, tabWallpaper, tabComponents,
                appsTab, wallpaperTab, componentsTab));

        // 壁纸设置控件
        Switch wallpaperCarouselCheckbox = settingsDialog.findViewById(R.id.wallpaperCarouselCheckbox);
        Switch randomModeCheckbox = settingsDialog.findViewById(R.id.randomModeCheckbox);
        Switch specifiedModeCheckbox = settingsDialog.findViewById(R.id.specifiedModeCheckbox);
        EditText switchIntervalInput = settingsDialog.findViewById(R.id.switchIntervalInput);
        Button saveWallpaperSettingsBtn = settingsDialog.findViewById(R.id.saveWallpaperSettingsBtn);

        // 组件配置控件
        Switch musicComponentCheckbox = settingsDialog.findViewById(R.id.musicComponentCheckbox);
        Switch mapComponentCheckbox = settingsDialog.findViewById(R.id.mapComponentCheckbox);
        Switch appComponentCheckbox = settingsDialog.findViewById(R.id.appComponentCheckbox);
        Switch tirePressureComponentCheckbox = settingsDialog.findViewById(R.id.tirePressureComponentCheckbox);
        Switch weatherComponentCheckbox = settingsDialog.findViewById(R.id.weatherComponentCheckbox);
        Button saveComponentsBtn = settingsDialog.findViewById(R.id.saveComponentsBtn);

        // 系统管理控件
        Switch bydAutoStartCheckbox = settingsDialog.findViewById(R.id.bydAutoStartCheckbox);
        Switch bootGreetingCheckbox = settingsDialog.findViewById(R.id.bootGreetingCheckbox);
        Button restartAppBtn = settingsDialog.findViewById(R.id.restartAppBtn);
        Button setDefaultDesktopBtn = settingsDialog.findViewById(R.id.setDefaultDesktopBtn);
        Button adbAuthorizationBtn = settingsDialog.findViewById(R.id.adbAuthorizationBtn);

        closeSettings.setOnClickListener(v -> settingsDialog.dismiss());

        // 加载壁纸设置
        Map<String, Object> settings = wallpaperSettingsDbHelper.getAllSettings();
        wallpaperCarouselCheckbox.setChecked((boolean) settings.getOrDefault("wallpaper_carousel", false));
        randomModeCheckbox.setChecked((boolean) settings.getOrDefault("random_mode", false));
        specifiedModeCheckbox.setChecked((boolean) settings.getOrDefault("specified_mode", false));
        switchIntervalInput.setText(String.valueOf(settings.getOrDefault("switch_interval", 15)));

        // 加载组件配置
        musicComponentCheckbox.setChecked(componentConfigDbHelper.isComponentEnabled("music"));
        mapComponentCheckbox.setChecked(componentConfigDbHelper.isComponentEnabled("map"));
        appComponentCheckbox.setChecked(componentConfigDbHelper.isComponentEnabled("app"));
        tirePressureComponentCheckbox.setChecked(componentConfigDbHelper.isComponentEnabled("tirePressure"));
        weatherComponentCheckbox.setChecked(componentConfigDbHelper.isComponentEnabled("weather"));

        // 加载系统设置
        bydAutoStartCheckbox.setChecked(bydAutoStartEnabled);
        bootGreetingCheckbox.setChecked(bootGreetingEnabled);

        // 保存壁纸设置
        saveWallpaperSettingsBtn.setOnClickListener(v -> {
            wallpaperSettingsDbHelper.updateWallpaperCarousel(wallpaperCarouselCheckbox.isChecked());
            wallpaperSettingsDbHelper.updateRandomMode(randomModeCheckbox.isChecked());
            wallpaperSettingsDbHelper.updateSpecifiedMode(specifiedModeCheckbox.isChecked());
            try {
                int interval = Integer.parseInt(switchIntervalInput.getText().toString());
                if (interval < 5) interval = 5;
                if (interval > 300) interval = 300;
                wallpaperSettingsDbHelper.updateSwitchInterval(interval);
            } catch (Exception e) {
                // 非法输入忽略
            }
            sendWallpaperSettingsChangedBroadcast();
            restartWallpaperCarousel();
            Toast.makeText(this, "壁纸设置已保存", Toast.LENGTH_SHORT).show();
        });

        // 保存组件配置
        saveComponentsBtn.setOnClickListener(v -> {
            componentConfigDbHelper.saveOrUpdateComponentConfig("music", musicComponentCheckbox.isChecked());
            componentConfigDbHelper.saveOrUpdateComponentConfig("map", mapComponentCheckbox.isChecked());
            componentConfigDbHelper.saveOrUpdateComponentConfig("app", appComponentCheckbox.isChecked());
            componentConfigDbHelper.saveOrUpdateComponentConfig("tirePressure", tirePressureComponentCheckbox.isChecked());
            componentConfigDbHelper.saveOrUpdateComponentConfig("weather", weatherComponentCheckbox.isChecked());
            applyComponentVisibility();
            Toast.makeText(this, "组件配置已保存", Toast.LENGTH_SHORT).show();
        });

        // 系统管理
        bydAutoStartCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (webViewBridge != null) webViewBridge.saveBydAutoStartSetting(isChecked);
        });
        bootGreetingCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (webViewBridge != null) webViewBridge.saveBootGreetingSetting(isChecked);
        });
        restartAppBtn.setOnClickListener(v -> {
            if (webViewBridge != null) webViewBridge.restartApp();
        });
        setDefaultDesktopBtn.setOnClickListener(v -> {
            if (webViewBridge != null) webViewBridge.setDefaultDesktop();
        });
        adbAuthorizationBtn.setOnClickListener(v -> {
            if (webViewBridge != null) webViewBridge.triggerWirelessAdbAuthorization();
        });

        settingsDialog.show();
    }

    /**
     * 设置弹窗 Tab 切换
     */
    private void switchSettingTab(Button activeTab, Button tab2, Button tab3,
                                  LinearLayout activeContent, LinearLayout content2, LinearLayout content3) {
        activeTab.setBackgroundResource(R.drawable.tab_background_selected_new);
        tab2.setBackgroundResource(R.drawable.tab_background_unselected_new);
        tab3.setBackgroundResource(R.drawable.tab_background_unselected_new);
        activeContent.setVisibility(View.VISIBLE);
        content2.setVisibility(View.GONE);
        content3.setVisibility(View.GONE);
    }

    /**
     * 应用组件显隐（组件配置）
     */
    private void applyComponentVisibility() {
        try {
            if (widgetsContainer == null) return;
            boolean music = componentConfigDbHelper.isComponentEnabled("music");
            boolean map = componentConfigDbHelper.isComponentEnabled("map");
            boolean app = componentConfigDbHelper.isComponentEnabled("app");
            boolean tire = componentConfigDbHelper.isComponentEnabled("tirePressure");
            boolean weather = componentConfigDbHelper.isComponentEnabled("weather");
            findViewById(R.id.musicWidget).setVisibility(music ? View.VISIBLE : View.GONE);
            findViewById(R.id.mapWidget).setVisibility(map ? View.VISIBLE : View.GONE);
            findViewById(R.id.quickAppsWidget).setVisibility(app ? View.VISIBLE : View.GONE);
            findViewById(R.id.carWidget).setVisibility(tire ? View.VISIBLE : View.GONE);
            findViewById(R.id.weatherWidget).setVisibility(weather ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.e("MainActivity", "应用组件配置失败", e);
        }
    }

    // ==================== 导航 ====================

    private void navigateHome() {
        if (webViewBridge != null) webViewBridge.navigateToHome();
    }

    private void navigateCompany() {
        if (webViewBridge != null) webViewBridge.navigateToCompany();
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onStart() {
        super.onStart();
        isAppInForeground = true;
        resumeWallpaperCarousel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFullscreenMode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        isAppInForeground = false;
        taskManager.pauseWallpaperCarousel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimeUpdate();
        if (networkStateReceiver != null) {
            try { unregisterReceiver(networkStateReceiver); } catch (Exception ignored) { }
        }
        taskManager.stopWallpaperCarousel();
        taskManager.stopMusicPlaybackCheck();
        taskManager.cancelDelayedStartupTasks();
        serviceManager.unbindAllServices();
        unregisterReceivers();

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        instance = null;
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限未授予", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    createDirectoriesInRoot();
                } else {
                    Toast.makeText(this, "请授予所有文件访问权限", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1007) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "录音权限已授予");
            } else {
                Log.d("MainActivity", "录音权限被拒绝");
            }
        }
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length >= 2 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                createDirectoriesInRoot();
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法操作根目录", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 初始化（沿用原逻辑） ====================

    public void initWallpaperSettingsReceiver() {
        wallpaperSettingsChangedReceiver = new WallpaperSettingsChangedReceiver();
        IntentFilter wallpaperSettingsFilter = new IntentFilter(ACTION_WALLPAPER_SETTINGS_CHANGED);
        registerReceiver(wallpaperSettingsChangedReceiver, wallpaperSettingsFilter);
    }

    public void initRestartAppReceiver() {
        RestartAppReceiver restartAppReceiver = new RestartAppReceiver();
        IntentFilter restartAppFilter = new IntentFilter("com.ljbyd.launcher3.RESTART_APP");
        registerReceiver(restartAppReceiver, restartAppFilter);
    }

    /**
     * 初始化 TTS 语音合成
     */
    public void initTextToSpeech() {
        Log.d("MainActivity", "开始初始化TTS");
        try {
            checkTTSEngines();
            createTTSInstance();
        } catch (Exception e) {
            Log.e("MainActivity", "TTS初始化异常", e);
        }
    }

    private void checkTTSEngines() {
        Intent checkTTSIntent = new Intent();
        checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ttsEngines = pm.queryIntentActivities(checkTTSIntent, 0);
        Log.d("MainActivity", "可用的TTS引擎数量: " + ttsEngines.size());
        for (ResolveInfo info : ttsEngines) {
            Log.d("MainActivity", "TTS引擎: " + info.activityInfo.packageName);
        }
    }

    private void createTTSInstance() {
        String xiaomiTtsPackage = "com.xiaomi.mibrain.speech";
        if (isTtsEngineAvailable(xiaomiTtsPackage)) {
            Log.d("MainActivity", "使用小米小爱TTS引擎: " + xiaomiTtsPackage);
            textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        setupTTSLanguage();
                    } else {
                        createDefaultTtsInstance();
                    }
                }
            }, xiaomiTtsPackage);
        } else {
            Log.d("MainActivity", "小米小爱TTS引擎不可用，使用系统默认引擎");
            createDefaultTtsInstance();
        }
    }

    private void createDefaultTtsInstance() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    setupTTSLanguage();
                } else {
                    handleTTSInitFailure(status);
                }
            }
        });
    }

    private boolean isTtsEngineAvailable(String packageName) {
        try {
            Intent checkTTSIntent = new Intent();
            checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            checkTTSIntent.setPackage(packageName);
            PackageManager pm = getPackageManager();
            List<ResolveInfo> ttsEngines = pm.queryIntentActivities(checkTTSIntent, 0);
            return !ttsEngines.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void setupTTSLanguage() {
        int result = textToSpeech.setLanguage(Locale.CHINESE);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            setupSimplifiedChineseLanguage();
        } else {
            configureTTSParams();
        }
    }

    private void setupSimplifiedChineseLanguage() {
        int simplifiedResult = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
        if (simplifiedResult == TextToSpeech.LANG_MISSING_DATA || simplifiedResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            setupEnglishLanguage();
        } else {
            configureTTSParams();
        }
    }

    private void setupEnglishLanguage() {
        int englishResult = textToSpeech.setLanguage(Locale.ENGLISH);
        if (englishResult == TextToSpeech.LANG_AVAILABLE || englishResult == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            configureTTSParams();
        } else {
            Toast.makeText(MainActivity.this, "TTS不支持任何语言", Toast.LENGTH_SHORT).show();
        }
    }

    private void configureTTSParams() {
        textToSpeech.setSpeechRate(0.8f);
        textToSpeech.setPitch(1.0f);
        Log.d("MainActivity", "TTS全部初始化完成");
    }

    private void handleTTSInitFailure(int status) {
        Log.e("MainActivity", "TTS初始化失败，状态码: " + status);
        try {
            Intent installTTSIntent = new Intent();
            installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installTTSIntent);
        } catch (Exception e) {
            Log.e("MainActivity", "启动TTS安装界面失败", e);
        }
    }

    // ==================== 音乐相关 ====================

    public void initMusicVisualizer() {
        try {
            musicVisualizer = new MusicVisualizer(this);
            musicVisualizer.setOnVisualizerUpdateListener((waveform, frequency) -> {
                if (frequency != null && musicVisualizerView != null) {
                    runOnUiThread(() -> musicVisualizerView.setFrequencyData(frequency));
                }
            });
            Log.d("MainActivity", "音乐可视化初始化成功");
        } catch (Exception e) {
            Log.e("MainActivity", "初始化音乐可视化时出错", e);
        }
    }

    private void registerMusicPlaybackReceiver() {
        musicPlaybackReceiver = new MusicPlaybackReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.android.music.metachanged");
        filter.addAction("com.android.music.playstatechanged");
        filter.addAction("com.android.music.playbackcomplete");
        filter.addAction("com.android.music.queuechanged");
        filter.addAction("com.htc.music.metachanged");
        filter.addAction("fm.last.android.metachanged");
        filter.addAction("com.sec.android.Music.metachanged");
        filter.addAction("com.nullsoft.winamp.metachanged");
        filter.addAction("com.amazon.mp3.metachanged");
        filter.addAction("com.miui.player.metachanged");
        filter.addAction("com.real.IMP.metachanged");
        filter.addAction("com.sonyericsson.music.metachanged");
        filter.addAction("com.rdio.android.metachanged");
        filter.addAction("com.samsung.MusicPlayer.metachanged");
        filter.addAction("com.andrew.apollo.metachanged");

        registerReceiver(musicPlaybackReceiver, filter);
    }

    public void startMusicPlaybackCheck() {
        taskManager.startMusicPlaybackCheck();
    }

    public void checkMusicPlaybackState() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                boolean isMusicActive = am.isMusicActive();
                updateMusicPlaybackState(isMusicActive);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "检查音乐播放状态时出错", e);
        }
    }

    /**
     * 更新音乐播放状态（原生 UI + 可视化）
     */
    private void updateMusicPlaybackState(boolean isPlaying) {
        if (isPlaying != isMusicPlaying) {
            isMusicPlaying = isPlaying;

            // 原生 UI：切换播放按钮图标
            runOnUiThread(() -> {
                if (playPauseBtn != null) {
                    playPauseBtn.setImageResource(isPlaying ? R.drawable.nav_music_pause : R.drawable.nav_music_play);
                }
                setVinylRotating(isPlaying);
                if (musicVisualizerView != null) {
                    musicVisualizerView.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
                }
            });

            if (isPlaying && musicVisualizer != null) {
                try {
                    musicVisualizer.startVisualizer();
                } catch (Exception e) {
                    Log.e("MainActivity", "启动音乐可视化时出错", e);
                }
            } else if (!isPlaying && musicVisualizer != null) {
                try {
                    musicVisualizer.stopVisualizer();
                } catch (Exception e) {
                    Log.e("MainActivity", "停止音乐可视化时出错", e);
                }
            }
        }
    }

    /**
     * 静态方法：更新音乐状态到原生 UI（供 MediaSessionService 调用）
     */
    public static void updateMusicStatus(String musicName, boolean isPlaying, long currentPosition, long duration) {
        if (instance != null) {
            instance.runOnUiThread(() -> {
                try {
                    if (instance.currentSongName != null) {
                        instance.currentSongName.setText(musicName != null && !musicName.isEmpty()
                                ? musicName : instance.getString(R.string.music_idle_text));
                    }
                    if (instance.musicProgressBar != null && duration > 0) {
                        instance.musicProgressBar.setMax((int) duration);
                        instance.musicProgressBar.setProgress((int) currentPosition);
                    }
                    if (instance.musicCurrentTime != null) {
                        instance.musicCurrentTime.setText(formatMusicTime(currentPosition));
                    }
                    if (instance.musicTotalTime != null) {
                        instance.musicTotalTime.setText(formatMusicTime(duration));
                    }
                    if (instance.playPauseBtn != null) {
                        instance.playPauseBtn.setImageResource(isPlaying
                                ? R.drawable.nav_music_pause : R.drawable.nav_music_play);
                    }
                    instance.setVinylRotating(isPlaying);
                } catch (Exception e) {
                    Log.e("MainActivity", "更新音乐状态时出错", e);
                }
            });
        }
    }

    private static String formatMusicTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    // ==================== 壁纸轮播 ====================

    public void startWallpaperCarousel() {
        try {
            Map<String, Object> settings = wallpaperSettingsDbHelper.getAllSettings();
            isWallpaperCarouselEnabled = (boolean) settings.get("wallpaper_carousel");
            wallpaperSwitchInterval = (int) settings.get("switch_interval");
        } catch (Exception e) {
            Log.e("MainActivity", "获取壁纸设置时出错", e);
            isWallpaperCarouselEnabled = false;
            wallpaperSwitchInterval = 15000;
        }
        taskManager.startWallpaperCarousel();
    }

    public void pauseWallpaperCarousel() {
        taskManager.pauseWallpaperCarousel();
    }

    public void resumeWallpaperCarousel() {
        if (isWallpaperCarouselEnabled) {
            startWallpaperCarousel();
        }
    }

    public boolean deleteCurrentWallpaper() {
        try {
            if (!canDeleteCurrentWallpaper()) return false;
            File deletedDir = prepareDeletedDirectory();
            if (deletedDir == null) return false;
            return performWallpaperDeletion(deletedDir);
        } catch (Exception e) {
            Log.e("MainActivity", "删除壁纸时出错", e);
            return false;
        }
    }

    private boolean canDeleteCurrentWallpaper() {
        if (isUsingDefaultWallpaper) return false;
        if (currentWallpaperPath == null || currentWallpaperPath.isEmpty()) return false;
        File currentWallpaperFile = new File(currentWallpaperPath);
        if (!currentWallpaperFile.exists()) return false;
        String fstartPath = Environment.getExternalStorageDirectory() + "/ljbyd";
        if (!currentWallpaperPath.startsWith(fstartPath)) return false;
        return true;
    }

    private File prepareDeletedDirectory() {
        File deletedDir = new File(Environment.getExternalStorageDirectory(), "fstart_deleted");
        if (!deletedDir.exists()) {
            if (!deletedDir.mkdirs()) return null;
        }
        return deletedDir;
    }

    private boolean performWallpaperDeletion(File deletedDir) {
        File currentWallpaperFile = new File(currentWallpaperPath);
        String fileName = System.currentTimeMillis() + "_" + currentWallpaperFile.getName();
        File deletedFile = new File(deletedDir, fileName);
        boolean success = currentWallpaperFile.renameTo(deletedFile);
        if (success) {
            currentWallpaperPath = "";
            return true;
        }
        return false;
    }

    public void startTimeUpdate() {
        timeUpdateHandler.post(timeUpdateRunnable);
    }

    private void initSettings() {
        try {
            Map<String, Object> settings = wallpaperSettingsDbHelper.getAllSettings();
            bydAutoStartEnabled = (boolean) settings.getOrDefault("byd_auto_start", false);
            bootGreetingEnabled = (boolean) settings.getOrDefault("boot_greeting", false);
        } catch (Exception e) {
            Log.e("MainActivity", "初始化配置时出错", e);
            bydAutoStartEnabled = false;
            bootGreetingEnabled = false;
        }
    }

    /**
     * 预加载应用列表
     */
    private void preloadAppList() {
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                List<Map<String, Object>> apps = AppUtils.getInstalledApps(MainActivity.this);
                preloadedAppList = apps.toString();
                Log.d("MainActivity", "应用列表预加载完成，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
            } catch (Exception e) {
                Log.e("MainActivity", "预加载应用列表时出错", e);
            }
        }).start();
    }

    public void scheduleDelayedStartupTasks() {
        taskManager.scheduleDelayedStartupTasks();
    }

    public void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO }, 1007);
        }
    }

    public void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog();
            } else {
                createDirectoriesInRoot();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE }, REQUEST_STORAGE_PERMISSION);
            } else {
                createDirectoriesInRoot();
            }
        } else {
            createDirectoriesInRoot();
        }
    }

    private void showManageStorageDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            createDirectoriesInRoot();
            return;
        }

        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("存储权限必要")
                .setMessage("需要存储权限以保存应用配置和壁纸")
                .setPositiveButton("继续申请", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                        } catch (Exception e) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                        }
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.READ_EXTERNAL_STORAGE },
                                REQUEST_STORAGE_PERMISSION);
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "未获得存储权限，部分功能可能无法正常使用", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show());
    }

    private void createDirectoriesInRoot() {
        File rootDir = Environment.getExternalStorageDirectory();
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "外部存储不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        File targetDir = new File(rootDir, "ljbyd");
        File parentDir = targetDir.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Toast.makeText(this, "父目录创建失败", Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (!targetDir.exists()) {
            if (targetDir.mkdirs()) {
                createTestFile(targetDir);
            } else {
                Toast.makeText(this, "目录创建失败，请检查权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void createTestFile(File parentDir) {
        File testFile = new File(parentDir, "test.txt");
        try {
            if (testFile.createNewFile()) {
                try (FileWriter writer = new FileWriter(testFile)) {
                    writer.write("This is a test file in root directory.");
                }
            }
        } catch (IOException e) {
            Log.e("Storage", "测试文件创建失败", e);
        }
    }

    private void speakText(String text) {
        if (textToSpeech == null) return;
        if (textToSpeech.isSpeaking()) return;
        try {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } catch (Exception e) {
            Log.e("MainActivity", "TTS播放异常", e);
        }
    }

    public void playRandomGreeting() {
        if (quoteApiUtils != null) {
            quoteApiUtils.getDailyQuote(new QuoteApiUtils.QuoteListener() {
                @Override
                public void onQuoteReceived(String quote) {
                    if (quote != null && !quote.isEmpty()) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, quote, Toast.LENGTH_LONG).show();
                            speakText(quote);
                        });
                    }
                }
            });
        }
    }

    public void launchBydHome() {
        new Thread(() -> {
            try {
                String packageName = "com.android.launcher3";
                String activityName = "com.android.launcher3.Launcher";
                String command = "am start -n " + packageName + "/" + activityName
                        + " -f 0x00000004 -f 0x00008000 -f 0x00000080 && input keyevent KEYCODE_HOME";
                com.ljbyd.launcher3.adb.AdbCommandProcessor processor = new com.ljbyd.launcher3.adb.AdbCommandProcessor(this);
                boolean success = processor.executeCommand(command);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        success ? "已启动原桌面" : "启动原桌面失败，无法连接ADB", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "启动原桌面失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setFullscreenMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void stopTimeUpdate() {
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
    }

    public void stopWallpaperCarousel() {
        taskManager.stopWallpaperCarousel();
    }

    public void restartWallpaperCarousel() {
        startWallpaperCarousel();
    }

    private void stopMusicCheck() {
        taskManager.stopMusicPlaybackCheck();
    }

    private void cancelDelayedStartupTasks() {
        taskManager.cancelDelayedStartupTasks();
    }

    private void unbindServices() {
        serviceManager.unbindAllServices();
    }

    private void unregisterReceivers() {
        if (musicPlaybackReceiver != null) {
            try {
                unregisterReceiver(musicPlaybackReceiver);
            } catch (Exception e) {
                Log.e("MainActivity", "注销音乐播放接收器时出错", e);
            }
        }
        if (wallpaperSettingsChangedReceiver != null) {
            try {
                unregisterReceiver(wallpaperSettingsChangedReceiver);
            } catch (Exception e) {
                Log.e("MainActivity", "注销壁纸设置更改接收器时出错", e);
            }
        }
    }

    public void rescheduleDelayedStartupTasks() {
        if (delayedStartHandler != null) {
            if (bydAutoStartRunnable != null) {
                delayedStartHandler.removeCallbacks(bydAutoStartRunnable);
            }
            if (bootGreetingRunnable != null) {
                delayedStartHandler.removeCallbacks(bootGreetingRunnable);
            }
        }
        scheduleDelayedStartupTasks();
    }

    public void sendWallpaperSettingsChangedBroadcast() {
        try {
            Intent intent = new Intent(ACTION_WALLPAPER_SETTINGS_CHANGED);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e("MainActivity", "发送壁纸设置更改广播时出错", e);
        }
    }

    private String getWeekDay(java.util.Date date) {
        String[] weekdays = { "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六" };
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        int weekIndex = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
        return weekdays[weekIndex];
    }

    private String getSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class);
            return (String) method.invoke(null, key);
        } catch (Exception e) {
            return "";
        }
    }

    private void initAppListToDatabase() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> apps = AppUtils.getInstalledApps(MainActivity.this);
                dbHelper.clearApps();
                dbHelper.bulkInsertOrUpdateApps(apps);
                Log.d("MainActivity", "应用列表已初始化到数据库");
            } catch (Exception e) {
                Log.e("MainActivity", "初始化应用列表到数据库时出错", e);
            }
        }).start();
    }

    public boolean triggerUsbDebugAuthorization() {
        if (usbDebugConnection != null) {
            return usbDebugConnection.triggerUsbDebugAuthorization();
        }
        Log.e("MainActivity", "USB调试连接对象未初始化");
        return false;
    }

    public ServiceConnection mediaSessionServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaSessionService.LocalBinder binder = (MediaSessionService.LocalBinder) service;
            mediaSessionService = binder.getService();
            isMediaSessionServiceBound = true;
            webViewBridge.setMediaSessionService(mediaSessionService, true);
            Log.d("MainActivity", "媒体会话服务已绑定");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isMediaSessionServiceBound = false;
            webViewBridge.setMediaSessionService(null, false);
            Log.d("MainActivity", "媒体会话服务已断开");
        }
    };

    public class MusicPlaybackReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("MainActivity", "收到音乐播放广播: " + action);
            checkMusicPlaybackState();
        }
    }

    public static class AppChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (instance != null) {
                instance.runOnUiThread(() -> Toast.makeText(context, "应用列表已更新", Toast.LENGTH_SHORT).show());
            }
        }
    }

    public class WallpaperSettingsChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (instance != null) {
                instance.restartWallpaperCarousel();
                instance.rescheduleDelayedStartupTasks();
            }
        }
    }

    public class RestartAppReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.ljbyd.launcher3.RESTART_APP".equals(intent.getAction())) {
                Intent restartIntent = new Intent(context, MainActivity.class);
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(restartIntent);
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }

    /**
     * 从壁纸设置刷新壁纸（供 WebViewBridge.notifyWallpaperUpdate 调用）
     */
    public void refreshWallpaperFromSettings() {
        try {
            if (webViewBridge != null) {
                String path = webViewBridge.getCurrentWallpaperPathFromDb();
                if (path != null && !path.isEmpty()) {
                    setWallpaperBackground(path);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "刷新壁纸时出错", e);
        }
    }

    /**
     * 原生更新空调状态 UI（供 WebViewBridge.initializeAcStatus 调用）
     */
    public void updateAcStatusUI(org.json.JSONObject acData) {
        try {
            if (acData == null) return;
            boolean acOn = acData.optBoolean("acOn", false);
            int temperature = acData.optInt("temperature", 22);
            int windLevelValue = acData.optInt("windLevel", 6);
            boolean defrostOn = acData.optBoolean("defrostOn", false);

            runOnUiThread(() -> {
                if (airTempText != null) airTempText.setText(temperature + "°");
                if (airWindTempText != null) airWindTempText.setText(temperature + "°");
                if (windLevel != null) {
                    int resId = getWindLevelResId(windLevelValue);
                    windLevel.setImageResource(resId);
                }
                if (acControl != null) {
                    acControl.setImageResource(acOn ? R.drawable.nav_air : R.drawable.nav_air);
                    acControl.setAlpha(acOn ? 1.0f : 0.4f);
                }
                if (acHcs != null) {
                    acHcs.setAlpha(defrostOn ? 1.0f : 0.4f);
                }
            });
        } catch (Exception e) {
            Log.e("MainActivity", "更新空调状态UI时出错", e);
        }
    }

    private int getWindLevelResId(int level) {
        switch (level) {
            case 0: return R.drawable.wind_level_00;
            case 1: return R.drawable.wind_level_01;
            case 2: return R.drawable.wind_level_02;
            case 3: return R.drawable.wind_level_03;
            case 4: return R.drawable.wind_level_04;
            case 5: return R.drawable.wind_level_05;
            case 6: return R.drawable.wind_level_06;
            case 7: return R.drawable.wind_level_07;
            default: return R.drawable.wind_level_06;
        }
    }

    /**
     * 获取应用名首字母（拼音）
     */
    private String getFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return "#";
        }
        char firstChar = str.charAt(0);
        if ((firstChar >= 'A' && firstChar <= 'Z') || (firstChar >= 'a' && firstChar <= 'z')) {
            return String.valueOf(Character.toUpperCase(firstChar));
        } else if (isChineseChar(firstChar)) {
            return getChineseFirstLetter(firstChar);
        } else {
            return "#";
        }
    }

    private boolean isChineseChar(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF) || (c >= 0x20000 && c <= 0x2A6DF)
                || (c >= 0x2A700 && c <= 0x2B73F) || (c >= 0x2B740 && c <= 0x2B81F)
                || (c >= 0x2B820 && c <= 0x2CEAF);
    }

    private String getChineseFirstLetter(char ch) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        try {
            String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(ch, format);
            if (pinyinArray != null && pinyinArray.length > 0) {
                String pinyin = pinyinArray[0];
                if (pinyin != null && pinyin.length() > 0) {
                    return String.valueOf(pinyin.charAt(0));
                }
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            Log.e("MainActivity", "拼音转换出错", e);
        }
        return "#";
    }
}
