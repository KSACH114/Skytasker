# Skytasker

解决游玩《光·遇》时，每次查看任务都要先等蜡烛收完并消失的痛点。

**不修改游戏本体，免 root。**

## 原理

PC 版《光·遇》中，键盘的 `Shift` 键可以在不影响蜡烛的情况下独立调出任务面板。

本 App 通过 `/dev/uhid` 创建一个**虚拟物理键盘**，并且只保留 `Shift` 一个按键，
让游戏误认为当前处于键盘模式，从而复现与 PC 版一致的「一键查看任务」效果。

App 自身运行在 `untrusted_app` 域，被 SELinux 拦截，无权访问 `/dev/uhid`。
因此借助 [Shizuku](https://shizuku.rikka.app/)（或 SUI）以 `shell` 域身份，
启动一个常驻的 `libvkbd.so` 守护进程，由它持有虚拟键盘并接收按键指令。

键盘**常驻挂载**（而非每次按下时创建再销毁），以避免游戏因输入设备反复
「插入 → 移除」重新枚举而卡顿。

## 环境要求

- Android 12（API 30）及以上
- **arm64-v8a** 设备
- [Shizuku](https://shizuku.rikka.app/zh-hans/download/)（推荐，需无线调试配对）或 SUI

## 使用方法

1. 安装 Shizuku 并启动服务（首次需通过无线调试完成配对），或使用 SUI
2. 打开 Skytasker，按引导授予 Shizuku 权限与悬浮窗权限
3. 回到主界面，悬浮窗开关会自动开启
4. 进入游戏并**先领取一个任务**，点击悬浮的 ⇧ 按钮即可调出任务面板

## 构建

### 1. 编译 vkbd 守护进程

需要 Android NDK（本项目使用 NDK 28.2.13676358）：

```bash
aarch64-linux-android30-clang -O2 -o libvkbd.so vkbd.c
```

产物放到 `app/src/main/jniLibs/arm64-v8a/libvkbd.so`。

> 注意：`libvkbd.so` 是**可执行文件**而非共享库，因此 `build.gradle.kts` 中
> 需开启 `useLegacyPackaging = true`，确保它被解压到 `nativeLibraryDir`。

### 2. 编译 APK

用 Android Studio 直接 Run，或命令行：

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Shizuku API 13.1.5
- C（`/dev/uhid` 虚拟 HID 设备）
- AGP 9.3.0 / Gradle 9.5.0 / compileSdk 37 / minSdk 30

## 项目结构

```
├── vkbd.c                          # 虚拟键盘守护进程源码
├── app/src/main/
│   ├── java/com/Skyhelp/tasker/
│   │   ├── core/                   # 日志、偏好设置、服务状态总线
│   │   ├── permission/             # 权限状态封装
│   │   └── ui/                     # Compose 界面（引导页 / 主页 / 教程 / 关于）
│   └── jniLibs/arm64-v8a/
│       └── libvkbd.so              # 预编译的守护进程
```

## 已知限制

- 仅支持 arm64-v8a
- 需先领取游戏任务，任务面板才有内容可显示

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku) —— 免 root 调用系统（shell / ADB）能力
