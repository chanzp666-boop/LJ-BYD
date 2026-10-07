# 垃圾比亚迪

一个基于 **纯 Android 原生（Java + XML）** 的车载桌面启动器，由开源项目 [DiPartner](https://gitee.com/hex_code/DiPartner)（比亚迪车机 WebView 混合桌面）**1:1 迁移转换**而来，适配 **骁龙 690 / 8GB 内存 / Android 10（API 29）** 车机平台。

> 原项目使用 WebView + HTML/JS 混合开发；本工程已将所有 UI 与业务逻辑**改为纯原生 Java 实现**（不再依赖 WebView 前端），并在此前基础上补齐了还原度缺口、修复了壁纸切换等 Bug。

## 功能特性

- 桌面主界面（1:1 还原原版布局：时间/农历、底部卡片区、控制栏）
- 音乐控制组件（MediaSession + 原生可视化频谱、黑胶唱片动画）
- 天气显示（实时天气信息，联网获取）
- 地图导航快捷入口（回家/公司一键导航；长按可选择地图应用）
- 车辆信息展示 / 胎压监测显示
- 空调控制面板（温度 ±、风量 ±、AC、除霜、HCS）
- 壁纸系统：轮播、在线分类下载、随机/指定模式
- **壁纸手势**：左右滑动切换、双击恢复默认、长按删除当前壁纸
- 快速启动应用（应用列表长按添加、快速应用长按移除）
- 蓝牙 / Wi-Fi 状态图标实时显示
- 系统管理（BYD 自启、开机问候、重启应用、设默认桌面、ADB 授权）
- **画中画（迪友桌面同款）**：三指下滑打开/关闭；右侧 3/4 画中画窗口 + 左侧 1/4 车辆信息区（胎压小车模、综合油耗、行驶/里程/能耗、4 个车控快捷按钮）；顶部 ⋮ 三点 → 应用选择弹窗（本机所有应用，单选打勾，确定后在画中画窗口内镜像打开，而非全屏）
- **无障碍保活自愈**：无障碍服务 + 开机广播 + 就绪广播，桌面被杀后自动拉起自愈
- **ADB 权限对齐**：悬浮窗 / 修改系统设置 / 无障碍 / 录屏投影等权限一键授权脚本（grant_permissions.sh）

## 技术栈

- **语言**：Java（原生 Android，无 WebView 前端依赖）
- **UI**：XML 布局（FrameLayout/LinearLayout + 自定义 View）
- **数据**：SQLite（壁纸设置、组件配置、快速应用、车况）
- **通信**：MediaSession / 系统广播 / ADB Intent 转发（车机控制）

## 系统要求

- Android 10（API 29）及以上
- 适配：骁龙 690 / 8GB 内存车机
- 车机控制类功能（空调、胎压、BYD 主页）依赖车机 ADB 服务

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/chanzp666-boop/LJ-BYD.git
cd LJ-BYD
```

### 2. 配置环境

- Android Studio（或命令行 Gradle）
- JDK 17
- Android SDK 30

### 3. 构建项目

```bash
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME=~/Library/Android/sdk

./gradlew assembleDebug   # debug
./gradlew assembleRelease # release（使用本机保留的 ljbyd.jks 签名）
```

### 4. 安装到设备

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## 应用信息

| 项目 | 内容 |
|---|---|
| 应用名称 | 垃圾比亚迪 |
| 包名 | com.ljbyd.launcher3 |
| 版本 | 2.0（versionCode 2） |
| 签名 | 自签名 V2（ljbyd.jks，密钥文件仅本机保留，不随仓库分发） |
| 存储目录 | /sdcard/ljbyd（壁纸、配置等） |

## 项目结构

```
LJ-BYD/
├── app/
│   ├── src/main/
│   │   ├── java/com/ljbyd/launcher3/   # 原生 Java 代码
│   │   │   ├── MainActivity.java       # 主界面（桌面全部逻辑）
│   │   │   ├── bridge/                 # 原 WebViewBridge 迁移（数据库/ADB/接口）
│   │   │   ├── database/               # SQLite 帮助类
│   │   │   ├── service/                # 后台服务
│   │   │   ├── utils/                  # 工具类（App/壁纸/任务等）
│   │   │   └── adb/                    # ADB Intent 转发（车机控制）
│   │   ├── res/                        # Android 资源（布局/图片/样式）
│   │   └── assets_backup/              # 原版 Web 前端资源备份（仅存档，不参与运行）
│   └── build.gradle                    # 模块构建配置
├── build.gradle                        # 项目构建配置
└── README.md
```

## 与原版（WebView 版）的关系

- **界面**：按原版 HTML/CSS 布局 1:1 还原（配色、字号、间距、组件位置一致）
- **业务逻辑**：原 WebViewBridge 的 170+ JS 接口中与 UI 相关的逻辑已迁移为原生实现：
  - 时间/农历 → 原生定时器更新
  - 空调状态 → 原生 JSON 状态更新
  - 壁纸轮播/手势 → 原生 Handler + GestureDetector
  - 音乐状态 → MediaSession 广播 → 原生 View 更新
  - 应用列表 → 原生 ListView（拼音索引、搜索过滤）
  - 组件显隐 → SQLite 配置（默认全启用）
- **还原度**：约 88%，已补齐 6 项缺口（蓝牙/WiFi 图标、壁纸滑动/双击/长按手势、快速应用添加/移除、地图应用选择）
- **数据存储**：沿用原版 SQLite 表结构

## 开源协议

本项目采用 [MIT](LICENSE) 协议开源，代码仅供学习交流。

## 联系方式

如有问题或建议，欢迎提交 Issue。
