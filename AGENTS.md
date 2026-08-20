# ECHOAndroid — Agent Guide

本文件是给编码代理和贡献者的仓库约定，必须纳入版本控制，禁止写入 `.gitignore`。

CI 会检查：`AGENTS.md` 未被忽略、模块依赖图合法、单元测试通过、`assembleDebug` 成功。

本地等价命令：

```bat
gradlew checkModules --no-configuration-cache
gradlew testDebugUnitTest assembleDebug
```

首次克隆后启用共享 hook：

```bat
git config core.hooksPath .githooks
git config core.autocrlf false
```

## 模块化开发（强制）

工程已经是 Gradle 多模块。新功能按层放置，禁止把实现塞进错误的模块，禁止用“先写在 `:app` 里再说”的方式膨胀 Application 模块。

依赖方向只允许向下：

```
:app
  → :feature:*
  → :core:*
      → :core:model
```

源文件里的 `project(":…")` 必须是 `gradle/allowed-module-graph.txt` 的子集。`./gradlew checkModules` 和 CI 都会卡住违规边。改依赖时先改该文件和本表，再改各模块 `build.gradle.kts`。

### 硬性规则

1. **禁止** `:feature:*` 依赖另一个 `:feature:*`。功能之间通过 `:app` 编排，或把共享类型放到 `:core:model` / 共享 UI 放到 `:core:design`。
2. **禁止** `:core:*` 依赖 `:feature:*` 或 `:app`。
3. **禁止** 任何模块依赖 `:app`。`:app` 只做组合根。
4. **禁止** `:feature:*` 依赖 `:core:data`、`:core:playback`、`:core:connect`、`:core:lyrics`、`:core:usb-audio`。Feature 只依赖 `:core:model` 与 `:core:design`，状态和操作由 `:app` 注入。
5. **禁止** `:core:model` 依赖其它工程模块。共享数据类、枚举、协议 DTO 放这里。
6. **禁止** 在 `app/src/main/java/app/echo/android/ui/` 下新增页面。那是历史路径；新 UI 进对应 `:feature:*`。
7. **禁止** 跨模块复制 model。播放、曲库、连接、歌词的类型以 `:core:model` 为准。
8. 新模块必须同时改：`settings.gradle.kts`、`gradle/allowed-module-graph.txt`、本文件模块表、以及需要它的 `build.gradle.kts`。

### 模块表

| 模块 | 职责 | 可以依赖 |
| --- | --- | --- |
| `:app` | 组合根：`Application` / `Activity`、导航与权限、ViewModel 与 Controller 接线、把 core 能力注入 feature UI | 任意 `:feature:*`、需要的 `:core:*`（不要直接依赖 `:core:usb-audio`，走 `:core:playback`） |
| `:feature:home` | 主页、搜索 UI | `:core:model`、`:core:design` |
| `:feature:library` | 曲库、专辑/艺术家/文件夹、本地播放列表 UI | `:core:model`、`:core:design` |
| `:feature:player` | 正在播放、迷你播放条、队列 UI | `:core:model`、`:core:design` |
| `:feature:connect` | Echo Link 配对与遥控 UI | `:core:model`、`:core:design` |
| `:feature:settings` | 设置、诊断 UI | `:core:model`、`:core:design` |
| `:core:model` | 跨模块共享的不可变模型与协议形状 | 无工程模块 |
| `:core:design` | 主题、表面、封面组件、共享视觉 token | `:core:model` |
| `:core:data` | Room、扫描、设置存储、远程曲库源、仓储 | `:core:model` |
| `:core:playback` | Media3 播放服务、均衡器、通知封面、播放状态机 | `:core:model`、`:core:usb-audio` |
| `:core:usb-audio` | USB 独占 PCM / 描述符 / 能力探测 | 无工程模块 |
| `:core:connect` | Echo Link 传输、配对解析、远程客户端 | `:core:model` |
| `:core:lyrics` | LRC/歌词解析、在线歌词解析 | `:core:model` |

### 代码应该写在哪

| 要做的事 | 放哪里 |
| --- | --- |
| 新屏幕、列表、面板、Compose 控件（某功能私有） | 对应 `:feature:<name>` |
| 主题、可复用表面、封面加载 | `:core:design` |
| 曲目/专辑/播放状态/远程协议字段 | `:core:model` 对应子包（`library` / `playback` / `connect` / `lyrics` / `settings`） |
| 扫描、数据库、DataStore、网易云/Subsonic/WebDAV | `:core:data` |
| ExoPlayer、Notification、音频焦点、USB 独占播放 | `:core:playback`（USB 细节进 `:core:usb-audio`） |
| Echo Link socket / 配对码 / 远程命令收发 | `:core:connect`；字段进 `:core:model.connect` |
| 歌词文件解析 | `:core:lyrics`；歌词模型进 `:core:model.lyrics` |
| 权限、导航、把 Repository/Player/Remote 接到 UI | `:app` 的 Controller / ViewModel / `EchoAppRoot` |

Feature 屏幕保持“状态进、事件出”：接收 `core:model` 数据类和 lambda，不直接拿 `Context` 去查 Room 或操作 `MediaController`。

### 新增 feature 模块

1. `feature/<name>/`，`namespace = app.echo.android.feature.<name>`。
2. `settings.gradle.kts` 增加 `include(":feature:<name>")`。
3. `build.gradle.kts` 只 `implementation(project(":core:model"))` 和 `implementation(project(":core:design"))`。
4. 在 `gradle/allowed-module-graph.txt` 增加 `:feature:<name> -> :core:design :core:model`，并把它列入 `:app` 的允许依赖。
5. `:app` 的 `build.gradle.kts` 增加 `implementation(project(":feature:<name>"))`，由组合根接线。
6. 更新本表。

### 新增 core 模块

仅当现有 core 无法承载、且会有两个以上消费者时才拆。默认仍优先放进语义最接近的现有 core。新 core 只能依赖 `:core:model`（或其它更底层的 core，例如 playback → usb-audio）。Feature 默认仍然不准依赖它；由 `:app` 使用。

## Git

- 不要提交 `local.properties`、`*.jks`、`*.keystore`、`keystore.properties`。
- 历史误配置曾用 `app/` 忽略规则，它会匹配**任意**名为 `app` 的目录（包括 `java/app/echo/...`），导致新源文件进不了库。不要再加这类规则。
- `AGENTS.md` 必须被跟踪。Pre-commit 和 CI 都会检查它没有被 ignore。
- 文本默认 LF；`*.bat` 为 CRLF。本地 `core.autocrlf=false`，以 `.gitattributes` 为准。
- 提交范围跟模块走：一次 PR 只动相关模块；跨层改动（先 `core:model`，再实现，再 UI）在 PR 描述里写清顺序。

## CI

工作流：`.github/workflows/ci.yml`（JDK 21、Android API 36）。

PR / `main` 会跑：

1. `AGENTS.md` 未被 gitignore
2. `checkModules`
3. `testDebugUnitTest`
4. `assembleDebug`

不要在默认 CI 里加需要模拟器的 `androidTest`。仪器测试留在 `core/data/src/androidTest`，本机或单独作业再跑。

改模块边界却不更新 `gradle/allowed-module-graph.txt` 会使 CI 失败。这是预期行为。
