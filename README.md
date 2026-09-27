<h1 align="center">ECHO Android</h1>

<p align="center"><strong>把自己的音乐带在身边，也把手机变成桌面 ECHO 的遥控器。</strong></p>

<p align="center">
  原生 Android 音乐播放器 · 本地与远程曲库 · 动态歌词 · Echo Link 电脑联动
</p>

<p align="center">
  <a href="https://github.com/Moekotori/echoandroid/releases/latest">下载 Android 版</a>
  &nbsp;·&nbsp;
  <a href="https://store.steampowered.com/app/5105090/ECHO/">PC ECHO · Steam</a>
  &nbsp;·&nbsp;
  <a href="#与-pc-echo-联动">Echo Link 联动</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/Moekotori/echoandroid/issues">反馈问题</a>
</p>

---

ECHO Android 为自己的音乐库而生。你可以直接播放手机上的音乐，连接 NAS 或音乐服务器，也可以通过 **Echo Link 与 PC ECHO（ECHOSteam）联动**，在手机上浏览电脑曲库、遥控电脑播放，或把电脑里的音乐串流到手机。

Android 版可以独立使用；电脑联动需要运行支持 Echo Link 的 PC ECHO。

## 下载与开始使用

1. 前往 [GitHub Releases](https://github.com/Moekotori/echoandroid/releases/latest)，下载最新发布的 APK 并安装。
2. 首次打开时授予音乐访问权限，选择音乐目录并扫描曲库。
3. 从歌曲、专辑、艺术家或歌单开始播放；需要远程曲库或电脑联动时，打开「连接」页。

**系统要求：Android 8.0（API 26）及以上。** 发布说明会列出对应版本的变化；本 README 介绍当前源码中的能力，已发布 APK 的功能以对应版本为准。

## 你可以用它做什么

| 场景 | 功能 |
| --- | --- |
| 整理自己的音乐 | 本地目录扫描，歌曲、专辑、艺术家与歌单浏览，封面与元数据管理 |
| 专心听歌 | 播放队列、随机与循环、后台播放、动态歌词和睡眠定时 |
| 连接远程曲库 | Subsonic、WebDAV、Jellyfin / Emby 音乐来源 |
| 调整声音 | 均衡器、OPRA 耳机校正、ReplayGain，以及可选的播放过渡设置 |
| 使用外置设备 | USB 独占输出与严格整数直通模式，具体支持取决于音源格式和 DAC 能力 |
| 按喜好布置界面 | 主题、背景、外观设置与多语言界面 |
| 连接电脑 | Echo Link 配对、PC 曲库浏览、播放遥控与串流 |

声音功能的适用范围见 [均衡器与 OPRA](./docs/eq-opra.md)、[播放过渡](./docs/playback-transitions.md) 和 [USB 输出说明](./docs/usb-bit-perfect.md)。

## 与 PC ECHO 联动

**手机与电脑，共用你的音乐体验。** Echo Link 让 Android 成为 PC ECHO 的随身控制端：电脑连接音箱或 DAC，手机负责选歌和控制；也可以在手机上收听电脑曲库。

👉 **[前往 Steam 获取 PC ECHO](https://store.steampowered.com/app/5105090/ECHO/)**

### 联动能做什么

- **浏览电脑曲库**：连接后读取 PC ECHO 曲库，独立展示，保留手机本地曲库。
- **遥控电脑播放**：在手机上播放 / 暂停、切换上一首 / 下一首、调整进度与音量，并查看电脑队列和输出状态。
- **电脑音乐在手机听**：从 PC 曲库选择音乐，通过局域网串流到 Android 播放。
- **手机音乐投送到电脑**：在双方版本及音源支持时，把手机当前歌曲或队列交给 ECHOSteam 播放。
- **保存配对**：完成首次授权后保存连接信息，后续可以选择已配对的电脑重新连接。

### 首次连接

1. 让手机与电脑处于**同一局域网**，并在电脑上打开 PC ECHO。
2. 在 PC ECHO 的 **Echo Link / 联动页面**开启服务，生成并复制完整配对链接。
3. 在 Android 的「连接」页进入 PC 配对入口，将完整链接粘贴进去，并在生成后的 **两分钟内**完成配对。链接过期时，在电脑上重新生成即可。
4. 连接成功后浏览 PC 曲库，按需要使用遥控或串流功能。

首次连接需要配对授权；仅发现电脑或输入 IP 地址不代表已经配对。如果无法连接，请检查局域网是否互通、电脑防火墙是否允许 ECHO，以及两端是否已更新到支持相应功能的版本。串流和投送期间请保持网络连接。

更多连接细节见 [Echo Link 配对说明](./docs/echo-link-direct.md)。PC 端实现位于独立仓库 [moekotori/echosteam](https://github.com/moekotori/echosteam)。

## 开发与构建

项目采用 **Kotlin + Jetpack Compose**，使用 Media3 播放、Room 管理数据，按功能划分 Gradle 模块。

构建环境：**JDK 21、Android SDK 36、NDK 27.2.12479018、CMake 3.22.1**。FFmpeg 构建还需要 Python 3 与 `make`；Windows 使用 WSL 构建 FFmpeg，需要在 WSL 内安装 Python 3、`make` 和对应版本的 Linux NDK。详细行为见 [构建脚本](./core/playback/scripts/build-ffmpeg-wsl.sh)。

```bash
git clone https://github.com/Moekotori/echoandroid.git
cd echoandroid
git config core.hooksPath .githooks
git config core.autocrlf false
```

配置本机 Android SDK 路径后，在 Windows PowerShell 执行：

```powershell
.\gradlew.bat assembleDebug
```

Linux / macOS 使用 `./gradlew assembleDebug`。APK 输出到 `app/build/outputs/apk/debug/`。首次构建需要下载依赖和构建原生音频库。

提交前按改动范围运行检查，完整 CI 使用：

```powershell
.\gradlew.bat checkModules checkLocalization --no-configuration-cache
.\gradlew.bat testDebugUnitTest assembleDebug
```

| 模块 | 职责 |
| --- | --- |
| `app` | 应用入口、导航、权限与模块接线 |
| `feature/*` | 首页、曲库、播放器、连接和设置界面 |
| `core/model` | 共享模型与 Echo Link 协议形状 |
| `core/data`、`core/connect` | 曲库、设置、远程来源与联动传输 |
| `core/playback`、`core/usb-audio` | 播放、声音处理与 USB 音频 |
| `core/design`、`core/lyrics`、`core/i18n` | 设计组件、歌词与国际化 |

开发约定见 [AGENTS.md](./AGENTS.md)，翻译贡献见 [本地化指南](./docs/localization.md)。

## 反馈与支持

欢迎通过 [Issues](https://github.com/Moekotori/echoandroid/issues) 报告问题或提出建议。反馈时请附上 Android 版本、设备型号、应用版本和复现步骤；联动问题请同时注明 PC ECHO 版本及使用场景。分享日志或截图前请隐去配对链接、Token 和账号凭据。

喜欢 ECHO 的话，欢迎给项目一个 Star、参与翻译与开发，也可以通过 [Steam 版 ECHO](https://store.steampowered.com/app/5105090/ECHO/) 支持后续开发。

## 许可

Copyright (c) 2026 Moekotori.

本仓库采用 [GNU Lesser General Public License v3.0（LGPL-3.0）](./LICENSE)，并附带其引用的 [GNU GPL v3.0 全文](./LICENSE-GPL)。第三方组件遵循各自的许可，随附说明见 [音频组件许可证目录](./core/playback/third_party/licenses)。
