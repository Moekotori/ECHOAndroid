<p align="center">
  <img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="ECHO Android 应用图标" width="112" height="112">
</p>

<h1 align="center">ECHO Android</h1>

<p align="center"><strong>你收藏的音乐，值得一个认真听歌的地方。</strong></p>

<p align="center">
  本地音乐 · NAS 与远程曲库 · 沉浸歌词 · USB 音频 · 手机与电脑联动
</p>

<p align="center">
  <a href="https://github.com/Moekotori/echoandroid/releases/latest"><img src="https://img.shields.io/github/v/release/Moekotori/echoandroid?style=flat-square&amp;label=APK" alt="最新 Android 发布版"></a>
  <a href="https://github.com/Moekotori/echoandroid/actions/workflows/ci.yml"><img src="https://github.com/Moekotori/echoandroid/actions/workflows/ci.yml/badge.svg" alt="CI 构建状态"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 8.0 及以上">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square" alt="AGPL-3.0 许可证"></a>
  <a href="https://store.steampowered.com/app/5105090/ECHO/"><img src="https://img.shields.io/badge/ECHO-Steam-171A21?style=flat-square&amp;logo=steam&amp;logoColor=white" alt="在 Steam 查看 ECHO"></a>
</p>

<p align="center">
  <a href="https://github.com/Moekotori/echoandroid/releases/latest"><strong>下载 Android APK</strong></a>
  &nbsp; · &nbsp;
  <a href="https://store.steampowered.com/app/5105090/ECHO/"><strong>在 Steam 获取桌面版</strong></a>
  &nbsp; · &nbsp;
  <a href="#echo-link">手机与电脑联动</a>
  &nbsp; · &nbsp;
  <a href="https://github.com/Moekotori/echoandroid/issues">反馈与建议</a>
</p>

---

**ECHO Android 是一款为个人音乐收藏打造的原生 Android 播放器。** 从手机里的一张专辑，到 NAS 上的整个音乐库，再到电脑旁的音箱与 DAC，你可以按自己的习惯组织、播放和欣赏音乐。

它可以独立使用，也可以通过 **Echo Link** 与桌面端 **ECHO（ECHOSteam）** 配合：躺在沙发上选歌，让电脑负责播放；把电脑曲库串流到手机；或者让电脑直接读取手机中的音乐。

> **开始听歌：** [下载 APK](https://github.com/Moekotori/echoandroid/releases/latest) → 授予音乐访问权限 → 选择目录并扫描 → 打开一张喜欢的专辑。
>
> 需要 **Android 8.0（API 26）及以上**。下文介绍当前源码中的能力，下载版本的功能与兼容性以对应 [发布说明](https://github.com/Moekotori/echoandroid/releases) 为准。

<a id="echo-steam"></a>

## 也在电脑上听歌？来试试 ECHO Steam 版

<p align="center">
  <a href="https://store.steampowered.com/app/5105090/ECHO/">
    <img src="https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/5105090/50397235271904776f2fd7edccbeb6a5c4e2ff9b/header.jpg?t=1792231814" alt="ECHO Steam 版：点击前往商店" width="460">
  </a>
</p>

**手机上随身听，回到电脑前也有 ECHO。** 如果你喜欢整理本地收藏、边工作边听歌，或者想为自己的音乐搭配不同的歌词场景和播放器外观，欢迎看看我们持续维护的 Steam 版。

| 桌面上的 ECHO | 带来的体验 |
| --- | --- |
| 适合日常听歌的桌面播放器 | 歌曲、专辑、艺术家与文件夹浏览，歌单、收藏、历史记录，迷你播放器与托盘播放 |
| 桌面歌词与沉浸歌词 | 工作时留一小块位置给歌词，想专心听歌时再切换场景 |
| Steam 创意工坊 | 订阅主题、皮肤、歌词场景、背景、可视化与功能模组，搭配自己的外观 |
| Steam 安装与更新 | 通过 Steam 管理安装和更新，支持成就与好友状态；可选云同步部分设置，音乐文件不上传 |
| Echo Link 设备工作流 | 与 Android 配合，按所用版本和授权使用曲库浏览、遥控及串流等联动能力 |

**[前往 Steam 获取 ECHO →](https://store.steampowered.com/app/5105090/ECHO/)** &nbsp; [逛逛创意工坊](https://steamcommunity.com/app/5105090/workshop/)

Steam 本体覆盖日常本地播放、桌面歌词和普通创意工坊外观；**Pro 是可选的付费 DLC**，提供进阶声音处理、远程曲库和 Connect 设备工作流等能力。购买前可在 [商店说明](https://store.steampowered.com/app/5105090/ECHO/) 核对自己需要的功能。支持 Steam 版，也是在支持 ECHO 的后续开发。

Android 与 ECHOSteam 是独立产品；本仓库维护 Android，桌面端代码与问题请前往官方仓库 [moekotori/echosteam](https://github.com/moekotori/echosteam)。

## 从一张专辑，到自己的音乐世界

### 让收藏更容易找到

按歌曲、专辑、艺术家、文件夹和歌单浏览，也可以使用统一搜索，查找曲库、歌单、片段与已连接的 PC 内容。把常听的内容固定到首页，用智能歌单整理收藏，或通过多选、批量标签编辑、封面管理与失效文件修复维护曲库。

同一首歌的多个来源可以关联并指定首选版本。对古典与录音收藏，还可以沿着**作曲家 → 作品 → 乐章 → 指挥／演奏者 → 录音版本**查找音乐；这些关系读取文件中已有的明确标签，详见 [音乐关系浏览](./docs/music-relationships.md)。

### 歌词也值得认真看

动态歌词、逐字高亮、翻译与音译显示，配合可调整的字体、对齐、背景和歌词场景。可以手动导入歌词，编辑内容、逐句打轴、调整偏移，也能把喜欢的一句做成歌词卡片分享。

离开播放页后，按需要开启桌面悬浮歌词、通知栏歌词或单行悬浮窗口。不同系统的显示能力有差异，见 [歌词来源与显示位置](./docs/lyrics-surfaces.md)。

<details>
<summary>支持哪些歌词格式？</summary>

支持同名旁置文件与手动导入：SPL、LRC / Enhanced LRC（`.lrc/.elrc/.lrcx`）、TTML（`.ttml/.dfxp/.xml`）、YRC、QRC（含 XML 包装）、KRC、SRT、WebVTT、ASS/SSA、SBV、SAMI（`.smi/.sami`）与纯文本。系统权限可能限制旁置文件读取，无法自动找到时可手动选择。

JSON 支持 ECHO 歌词存储文件、网易云及 LRCLIB 响应、逐行对象数组，或带 `lines` 数组的对象。逐行对象可使用 `startMs/endMs/text`，逐字 `words` 使用相同字段；时间单位为毫秒，支持 `translation` 与 `romanization`。未识别的 JSON/XML 不会直接显示为歌词正文。

有逐字时间的歌词保留原始时间轴，包括 ASS 卡拉 OK 标签和 WebVTT 行内时间戳；只有整句时间时，可以开启「估算逐字高亮」。格式细节见 [歌词格式兼容说明](./docs/lyrics-cue-format-compatibility.md)。

</details>

### 手机、NAS 和音乐服务器，都有入口

除了本地目录，还可以连接自己的 **Subsonic / Navidrome、WebDAV、SMB、Jellyfin / Emby、OneDrive（Beta）和 SSHFS 挂载目录**。Subsonic / Navidrome 支持服务器歌单编辑和收藏写回。OneDrive 登录及 SSHFS 目录授权见[远程曲库来源](./docs/remote-library-sources.md)。

想换一种听法，也可以收听网络电台，或添加公开 RSS 播客，按单集继续收听、加入队列。电台与播客的支持范围见 [使用说明](./docs/radio-podcasts.md)。

### 调整声音，也看清输出链路

使用均衡器、参数均衡器、ReplayGain，以及 **AutoEQ 离线耳机校正 / OPRA** 调整听感；按设备保存声音配置，切换输出时使用对应设置。播放过渡也有独立选项。

连接兼容 DAC 后，可选择 **USB 独占输出**；严格 bit-perfect 模式按音源与设备能力检查格式和时钟，支持范围内也可使用 DSD DoP。严格模式会旁路 EQ 等处理，具体格式、限制与操作见 [USB 输出说明](./docs/usb-bit-perfect.md)，声音处理见 [均衡器与耳机校正](./docs/eq-opra.md) 和 [播放过渡](./docs/playback-transitions.md)。

### 留住喜欢的片段和听歌记录

播放队列、随机与循环、A–B 片段循环、片段收藏、睡眠定时与后台播放，覆盖日常听歌和反复聆听的需要。查看听歌统计，也可以连接 Last.fm / ListenBrainz，离线记录在恢复联网后补报。

### 把播放器布置成自己喜欢的样子

调整主题、背景与播放器外观，用桌面小组件和专辑／歌单快捷方式更快开始播放。界面支持简体中文、繁体中文、英语、日语、韩语、西班牙语、德语、法语和俄语，缺失译文按系统资源规则回退。

Android 还支持导入带权限声明的沙箱脚本插件，详见 [Android 插件说明](./docs/plugins.md)。Android 插件与 Steam 创意工坊内容使用各自的机制。

在播放器外观页可以导入、预览和导出**场景与外观包**，将内置样式、歌词排版、配色、静态背景和歌词字体一起分享，并恢复应用前的外观。插件教程可安装 LRCLIB 歌词源示例，授权后在歌词管理中主动搜索、预览和选用。

信号页还提供默认关闭的 **HRTF 耳机声场**与**动态等响补偿**：HRTF 使用实测响应模拟双音箱，支持不超过 96 kHz 的普通立体声 PCM；动态等响跟随应用 PCM 音量。USB 位完美继续旁路，使用方式和边界见 [外观包与个人听音](./docs/appearance-packs-and-personal-audio.md)。

<a id="与-pc-echo-联动"></a>
<a id="echo-link"></a>

## Echo Link：音乐在两台设备之间继续

**电脑接音箱，手机负责选歌；电脑里收藏的专辑，也能在手机上播放。** Echo Link 把两个播放器接在一起，同时保留各自的曲库与播放入口。

| 想做什么 | 怎么使用 |
| --- | --- |
| 用手机控制电脑 | 浏览 PC 曲库，播放／暂停、切歌、跳转进度，查看队列与输出状态；电脑允许音量控制时可调整音量 |
| 把电脑音乐拿到手机上听 | 从已连接的 PC 曲库选择歌曲，串流到 Android 播放 |
| 把手机音乐交给电脑 | 投送支持的当前歌曲或队列，或开启手机曲库共享，让电脑浏览并播放手机的本地原文件 |
| 在两端传歌 | 打开双方的「传歌」面板，选择音频文件和接收位置，按原文件传输后再添加到曲库 |
| 整理两边的收藏和歌单 | 配对后预览歌曲匹配，再手动同步收藏与普通歌单；追加合并保留目标已有名称和顺序 |
| 换设备继续听 | 对应版本支持个人听歌状态预览与主动续听 |

以上能力需要两端版本及 PC 授权支持。**串流是在网络上播放，传歌会复制文件，收藏／歌单同步处理的是元数据**，可以按自己的需要选择。

### 第一次连接

1. 手机和电脑接入同一局域网，在电脑端 ECHO 开启 **Echo Link**。
2. 打开 Android「连接」页，选择附近电脑；支持局域网直连的 PC 可以直接连接，也可输入 IP 地址尝试连接。
3. 需要配对授权时，在 PC 生成完整配对链接，复制到 Android，并在生成后的**两分钟内**完成配对。收藏／歌单同步等写入操作需要已配对凭据。
4. 连接成功后浏览 PC 曲库，选择遥控、串流或其他设备功能；后续可从已保存的电脑重新连接。

找不到电脑时，检查两端网络是否互通、电脑防火墙是否允许 ECHO，以及两端版本是否支持对应能力。配置好的 Tailscale / FRP 等网络也可通过 IP、域名或配对链接连接，具体配置见 [配对与地址连接](./docs/echo-link-direct.md)。

手机共享曲库时，可在连接页关闭共享，或通过共享通知停止服务；持续共享会增加耗电。支持范围与后台行为见 [手机曲库共享](./docs/echo-link-phone-library.md)，原文件收发见 [Echo Link 传歌](./docs/echo-link-song-transfer.md)。

## 安装与常见问题

### 从哪里下载？

前往 [GitHub Releases](https://github.com/Moekotori/echoandroid/releases/latest)，展开 Assets 下载 APK，在 Android 上允许对应来源安装后打开。更新时以发布说明为准。

### 不用电脑，能单独使用吗？

可以。手机本地播放、歌词、曲库管理和已配置的远程来源均可独立使用。Echo Link 是连接电脑时使用的功能。

### 为什么扫描后没有找到歌曲或歌词？

先检查音乐访问权限，以及文件所在目录是否已授权并完成扫描。通过系统文件选择器授权的目录和普通媒体库权限覆盖范围不同；歌曲可播放也不代表旁置歌词一定能读取，歌词可再手动导入。

### Android 和 Steam 版的功能一样吗？

两端分别按手机和桌面场景开发，功能与发布节奏各自独立。Steam 创意工坊、桌面音频输出与 Pro 授权以 [Steam 商店说明](https://store.steampowered.com/app/5105090/ECHO/) 为准；Android 的功能以本仓库和 APK 发布说明为准。

### 换手机时怎么带走设置？

使用应用内迁移包，恢复前可以查看预览。迁移内容包括历史、歌词选择与偏移、首页与播放器设置、外观素材等；音乐原文件请另外迁移，远程来源仍需保持可访问。

## 开发与贡献

ECHO Android 使用 **Kotlin + Jetpack Compose** 构建界面，**Media3** 负责播放，**Room** 管理曲库，音频与 USB 能力由所属 core 模块提供。

### 本地构建

准备 **JDK 21、Android SDK 37（CI 使用 `platforms;android-37.0`）、NDK 27.2.12479018、CMake 3.22.1**。FFmpeg 编译还需要 Python 3 和 `make`；Windows 通过 WSL 编译 FFmpeg，WSL 内需安装同版本的 **Linux NDK** 及脚本所需工具，见 [WSL 构建脚本](./core/playback/scripts/build-ffmpeg-wsl.sh)。

```bash
git clone https://github.com/Moekotori/echoandroid.git
cd echoandroid
git config core.hooksPath .githooks
git config core.autocrlf false
```

在本机 `local.properties` 配置 Android SDK 路径，再执行：

```powershell
# Windows PowerShell
.\gradlew.bat assembleDebug
```

Linux / macOS 使用 `./gradlew assembleDebug`。APK 输出到 `app/build/outputs/apk/debug/`；首次构建会下载依赖并构建原生音频库。`local.properties`、签名配置和 keystore 留在本机。

Windows 生成正式签名 Release APK：在本机 `keystore.properties` 配置 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，使用已分发正式版的原签名密钥，密钥文件可放在仓库外。然后运行：

```powershell
.\scripts\build-release.cmd
```

脚本只在本地执行 `:app:assembleRelease`，自动递增本机已有产物的当日构建号，并核验正式签名证书；不触发云端构建或发布。APK 输出到 `app/build/outputs/apk/release/`。可用 `-Version 26.11.8 -Revision 2` 指定版本和修订号；若同日另有云端或其他机器构建，需手动选择更大的修订号。

<details>
<summary>开发时自动构建与安装</summary>

macOS 可双击根目录的 `打开Echo.command`，或执行：

```bash
./scripts/hot-reload.sh
./scripts/hot-reload.sh --avd ECHO_API_36
./scripts/hot-reload.sh --once
```

需要 Python 3、Android SDK、JDK 21 和已创建的 AVD。脚本启动模拟器，构建并安装，随后监听源码、资源和 Gradle 配置；连续保存会合并，构建期间的改动会在下一轮更新。Ctrl+C 停止监听。Windows 入口为 `scripts/hot-reload.cmd`。

自动刷新采用增量构建、覆盖安装再重启，保留应用数据，但会中断播放并丢失临时页面状态。需要不重启的 Compose UI 更新时，使用 [Android Studio Live Edit](https://developer.android.com/develop/ui/compose/tooling/iterative-development)。

</details>

### 检查与模块边界

按改动范围运行检查，完整 CI 使用：

```powershell
.\gradlew.bat checkModules checkLocalization --no-configuration-cache
.\gradlew.bat testDebugUnitTest assembleDebug
```

文档修改只检查 diff；局部逻辑运行相关模块／用例，语言资源改动加跑 `checkLocalization`。收尾时可运行相关模块的 `lintDebug` 作为参考，lint 不作为 CI 门禁；默认 CI 不运行仪器测试。

从[项目地图与 CI](./docs/project-map.md)查看各模块的入口、依赖图和构建产物。开发前阅读 [AGENTS.md](./AGENTS.md)，新增依赖同步维护[模块依赖白名单](./gradle/allowed-module-graph.txt)，参与翻译请看[本地化指南](./docs/localization.md)。

## 文档导航

| 想进一步了解 | 文档 |
| --- | --- |
| 手机与电脑连接 | [配对、IP 与跨网连接](./docs/echo-link-direct.md) · [手机曲库共享](./docs/echo-link-phone-library.md) · [双向传歌](./docs/echo-link-song-transfer.md) |
| 音频与输出 | [均衡器 / AutoEQ / OPRA](./docs/eq-opra.md) · [USB 与 bit-perfect](./docs/usb-bit-perfect.md) · [播放过渡](./docs/playback-transitions.md) |
| 歌词与播放器 | [歌词显示位置](./docs/lyrics-surfaces.md) · [格式兼容](./docs/lyrics-cue-format-compatibility.md) · [播放器手势](./docs/player-gestures.md) |
| 曲库与更多玩法 | [音乐关系](./docs/music-relationships.md) · [电台与播客](./docs/radio-podcasts.md) · [快捷入口与小组件](./docs/android-quick-access.md) · [Android 插件](./docs/plugins.md) |

## 反馈与支持

欢迎在 [Issues](https://github.com/Moekotori/echoandroid/issues) 报告问题或提出建议。请附上 **应用版本、Android 版本、设备型号、复现步骤**；音频问题补充音源格式与输出设备，联动问题补充 PC ECHO 版本和网络场景。日志与截图中请隐去配对链接、Token 和账号凭据。

喜欢 ECHO，欢迎给仓库一个 **Star**、参与翻译、提交改进，也欢迎通过 [Steam 版 ECHO](https://store.steampowered.com/app/5105090/ECHO/) 支持持续开发。

## 许可

Copyright (c) 2026 Moekotori.

本仓库采用 [GNU Affero General Public License v3.0（AGPL-3.0）](./LICENSE)。第三方组件遵循各自许可，随附声明见 [音频组件许可证目录](./core/playback/third_party/licenses)；应用内「设置 → 关于 → 开源协议」可查看协议与第三方声明。
