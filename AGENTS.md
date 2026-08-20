# ECHOAndroid

Android 多模块播放器。包名 `app.echo.android`。JDK 21，compileSdk 36。

本地：

```bat
git config core.hooksPath .githooks
git config core.autocrlf false
gradlew checkModules --no-configuration-cache
gradlew testDebugUnitTest assembleDebug
```

CI（`.github/workflows/ci.yml`）跑同样的 `checkModules`、单元测试和 `assembleDebug`。不要把仪器测试塞进默认 CI。`AGENTS.md` 要入库，不要写进 `.gitignore`。

## 模块

依赖向下：`:app` → `:feature:*` → `:core:*` → `:core:model`。

`project(":…")` 写在 `gradle/allowed-module-graph.txt` 里，改依赖时一起改。`checkModules` 会核对。

真正要守住的：

- 不要依赖 `:app`
- `:core` 不要依赖 `:feature`
- `:feature` 之间不要互相依赖

其余是习惯，不是高压线：新 UI 优先进对应 feature；`:app` 做导航、权限、Controller 接线；共享类型进 `:core:model`。`app/.../ui/` 里已有的 shell 可以留，不必为了纯洁再搬一次。feature 一般只靠 `:core:model` 和 `:core:design`，真需要别的 core 就写进模块图，不要硬绕。

| 模块 | 放什么 |
| --- | --- |
| `:app` | Application、Activity、导航、权限、把 core 接到 UI |
| `:feature:home` / `library` / `player` / `connect` / `settings` | 各功能 Compose UI |
| `:core:model` | 共享模型和协议形状 |
| `:core:design` | 主题、表面、封面 |
| `:core:data` | Room、扫描、设置、远程曲库 |
| `:core:playback` | Media3、均衡器、通知 |
| `:core:usb-audio` | USB 独占 PCM |
| `:core:connect` | Echo Link 传输与配对 |
| `:core:lyrics` | 歌词解析 |

新 feature：建模块 → `settings.gradle.kts` → 模块图 → `:app` 依赖。新 core 只在现有 core 装不下、且会有两个以上消费者时再拆。

## Git

不要提交 `local.properties`、keystore。不要再用会匹配任意 `app/` 目录的 ignore 规则。文本 LF，`*.bat` 为 CRLF。

## 开发工作流

`.grok/workflows/`：

- `/echo-dev` — 按模块实现一项改动。参数 `task`。
- `/echo-review` — 看一眼当前 diff。可选 `base`，默认 `HEAD`。
