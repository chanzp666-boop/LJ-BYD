#!/bin/bash
# ============================================================
# 垃圾比亚迪（com.ljbyd.launcher3）ADB 权限一键授予脚本
# 使用方式（车机开启 ADB 调试后，电脑连接车机）：
#   bash grant_permissions.sh
# 或指定 adb 路径：
#   ADB=/path/to/adb bash grant_permissions.sh
# ============================================================

PKG=com.ljbyd.launcher3
ADB=${ADB:-adb}

echo ">>> 目标包名: $PKG"
echo ">>> 使用 adb: $ADB"
echo

# 运行时权限（pm grant）
echo "--- 授予 pm grant 权限 ---"
for perm in \
    android.permission.WRITE_SECURE_SETTINGS \
    android.permission.READ_PRIVILEGED_PHONE_STATE \
    android.permission.READ_PHONE_STATE \
    android.permission.READ_EXTERNAL_STORAGE \
    android.permission.WRITE_EXTERNAL_STORAGE \
    android.permission.ACCESS_FINE_LOCATION \
    android.permission.ACCESS_COARSE_LOCATION \
    android.permission.ACCESS_BACKGROUND_LOCATION \
    android.permission.READ_MEDIA_IMAGES \
    android.permission.READ_MEDIA_AUDIO \
    android.permission.READ_MEDIA_VIDEO
do
    $ADB shell pm grant $PKG "$perm" 2>/dev/null && echo "  [OK] $perm" || echo "  [--] $perm (非运行时权限/无需授予)"
done

echo
echo "--- 授予 appops 特殊权限 ---"
for op in \
    SYSTEM_ALERT_WINDOW \
    WRITE_SETTINGS \
    MANAGE_EXTERNAL_STORAGE \
    REQUEST_INSTALL_PACKAGES \
    GET_USAGE_STATS
do
    $ADB shell appops set $PKG "$op" allow 2>/dev/null && echo "  [OK] $op" || echo "  [--] $op"
done

echo
echo "--- 无障碍保活服务（自愈） ---"
$ADB shell settings put secure enabled_accessibility_services "$PKG/.service.AutoService"
$ADB shell settings put secure accessibility_enabled 1
echo "  [OK] 已开启无障碍服务 AutoService（被杀自动回桌面 + 三指下滑画中画）"

echo
echo "--- 设为默认桌面（可选） ---"
$ADB shell cmd package set-home-activity "$PKG/.MainActivity" 2>/dev/null \
    && echo "  [OK] 已设为默认桌面" \
    || echo "  [--] 设置默认桌面失败（可在桌面设置-系统管理-默认桌面 手动设置）"

echo
echo ">>> 全部完成。建议重启车机桌面验证自愈与画中画。"
