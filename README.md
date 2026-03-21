# 迪伙伴 车载桌面系统

一个基于 Android 的开源车载桌面启动器，专为车载娱乐系统设计。
车机桌面不方便，基于自己的使用习惯瞎折腾出来的，小白新学技术有限，希望大佬出手指导，感激不尽。
- 代码仅限学习交流使用，请下载后及时删除，如有侵权请联系删除。
## 功能特性

- 音乐控制组件 - 支持主流音乐播放器的控制和显示
- 天气显示 - 实时天气信息展示
- 地图导航快捷入口
- 车辆信息展示
- 空调控制面板
- 壁纸切换系统
- 快速启动应用
- 胎压监测显示

## 技术栈

- **前端**: HTML5 + CSS3 + JavaScript
- **后端**: Android Java (API 25+)
- **通信**: WebView Bridge
- **数据库**: SQLite

## 系统要求

- Android 7.1+ (API 25)
- 建议 Android 9+ 以获得最佳体验

## 快速开始

### 1. 克隆项目

```bash
git clone https://gitee.com/hex_code/DiPartner.git
cd DiPartner
```

### 2. 配置环境

- Android Studio 4.2+
- JDK 11
- Android SDK 30

### 3. 构建项目

```bash
./gradlew assembleDebug
```

### 4. 安装到设备

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
DiPartner/
├── app/
│   ├── src/main/
│   │   ├── assets/          # Web 前端资源
│   │   │   ├── css/         # 样式文件
│   │   │   ├── js/          # JavaScript 文件
│   │   │   ├── images/      # 图片资源
│   │   │   └── index.html   # 主页面
│   │   ├── java/            # Android Java 代码
│   │   │   ├── service/     # 后台服务
│   │   │   ├── utils/       # 工具类
│   │   │   └── MainActivity.java
│   │   └── res/             # Android 资源
│   └── build.gradle         # 模块构建配置
├── build.gradle             # 项目构建配置
└── README.md
```

## 核心功能说明

### 音乐控制
- 通过 MediaSession API 获取播放信息
- 支持播放/暂停/上一首/下一首控制
- 实时显示播放进度
- 黑胶唱片动画效果

### WebView 通信
- Android 与 JavaScript 双向通信
- 通过 WebViewBridge 实现数据交换
- 支持 ADB 命令执行

## 贡献指南

- 欢迎提交 Issue 和 Pull Request！
- 目前车载控制功能没有实现，希望大佬指导贡献者参与，感激不尽。

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 开源协议

本项目采用 [MIT](LICENSE) 协议开源。

## 致谢

感谢所有为这个项目做出贡献的开发者。

## 联系方式

如有问题或建议，欢迎提交 Issue。
