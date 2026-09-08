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

### 模块化开发要求

- 动手前确认改动归属、调用方和现有依赖；优先在负责这项能力的模块内完成，不把新业务逻辑持续堆进 Activity、根 Composable 或总 ViewModel。
- feature 对外暴露必要的页面入口、状态和事件；跨 feature 跳转、共享播放状态由 `:app` 接线，不能通过互相引用页面或 ViewModel 绕过边界。
- `:core:model` 只放共享模型和协议，不塞数据库、网络、播放器实现或页面状态。实现细节留在所属 core，避免把 Room Entity、Media3 实现类型泄露为通用 UI 协议。
- core 之间的依赖也要有明确职责、保持无环；模块图是依赖白名单，不是把违规依赖写进去就算通过。新增依赖优先用 `implementation`，只有公共 API 确实需要传递类型时才用 `api`。
- 接口用于明确边界、替换实现或隔离测试；不要给每个类机械添加接口、Repository、UseCase，也不要为一次调用新拆模块。
- 修改公共模型或接口时同步检查所有调用方。不要靠复制模型、全局单例或隐式可变状态解决模块通信。
- 现有 shell 和接线代码可留在 `:app`；按本次需求逐步收敛职责，不做与任务无关的大规模搬迁或重命名。

## 性能要求

性能与功能一起考虑，重点守住播放连续性、界面响应、内存和后台耗电。以下是开发约束，不代表现有代码已全部满足；发现问题时结合本次范围修正，不顺手扩大重构。

### 主线程与并发

- 主线程只做轻量 UI 和必要的线程绑定调用。文件读写、网络、曲库扫描、数据库重操作、封面解码、歌词解析及大集合排序不能阻塞主线程。
- `suspend` 本身不保证切换线程：阻塞 I/O 使用合适的 I/O 调度器，CPU 密集计算使用计算调度器；遵守 Media3 等组件自己的线程约束，不机械地把所有调用移到后台。
- 协程绑定明确生命周期，页面退出、查询替换、连接断开时取消无用任务。不要吞掉取消异常，不用无归属的 `GlobalScope` 启动常驻任务。
- 扫描、下载、解码等并发要有上限；合并重复请求，搜索等连续输入按需防抖并取消过期结果，避免每次状态变化都启动一轮全量工作。

### 播放与高频状态

- 音频回调和 PCM/USB 数据热路径避免磁盘、网络、阻塞锁、频繁对象分配和逐帧日志；复用缓冲区，并明确容量、所有权及释放时机。
- 播放服务/引擎提供实际播放状态，UI 只负责展示和必要插值；不要让 UI 动画或重复计时器成为播放进度的事实来源。
- 进度、频谱、逐字歌词等高频更新限制在使用它们的局部 UI，不能因为每次 tick 重建整个页面状态、播放队列或曲库列表。
- UI 不可见时停止无用动画、频谱计算和 UI 轮询；后台播放仍需保留必要的音频、通知和会话更新，不能为省电破坏播放。

### Compose 与列表

- Composable 函数体内不执行 I/O、解析、大集合过滤排序或带副作用的业务操作；把工作放到所属状态持有者，副作用使用具有正确 key 和清理逻辑的 effect。
- 按职责拆分状态读取范围；缓存昂贵派生结果时使用完整依赖 key。`remember`、`derivedStateOf` 应解决具体重复工作，不机械套用；不要用不真实的 `@Stable` / `@Immutable` 掩盖可变状态。
- 长列表使用惰性布局和稳定、唯一的 item key；避免嵌套同方向无界滚动，避免每次重组复制整份列表或重新解码封面。
- 封面按展示尺寸加载，动画、模糊、阴影等效果控制作用范围；发现滚动掉帧时先定位重组、布局、绘制或解码开销，不直接删功能当作优化。

### 数据、内存与资源

- 大曲库查询优先在数据层过滤、排序和分页；避免全量加载后在 UI 处理、逐条查询造成 N+1，以及频繁全表扫描。索引与实际查询条件匹配。
- 批量写入使用合理批次和事务，扫描及同步优先增量更新；不要在一个超大事务中夹带网络请求或长时间计算。
- 图片、歌词、音频缓冲和远程结果缓存必须有容量或生命周期边界、淘汰及失效策略；禁止无上限 Map/列表和重复持有大对象。
- 不长期持有 Activity、View 或页面 Context；监听器、Flow 收集、播放器、USB 连接、文件句柄等由明确的所有者管理，在对应生命周期结束时释放。
- 后台任务优先事件驱动，确需轮询时设置合理间隔和停止条件；失败重试要有退避和次数/时间边界，避免循环唤醒、持续抢占 CPU 或刷日志。

### 性能问题处理与验证

- 先说明复现场景和可观察问题，再定位瓶颈；不要仅凭代码观感声称“更快”“省内存”。围绕问题选择启动耗时、掉帧、查询耗时、内存、音频中断或后台 CPU 等指标。
- 前后对比尽量保持设备、构建类型、曲库规模和操作一致；需要时再用 Profiler、Perfetto 等工具。Debug 或模拟器结果不能直接当作真机 Release 性能结论。
- 优化不能改变排序、歌词时序、播放队列和音频正确性。报告已验证内容及未覆盖条件，没有测量时明确只完成了实现层面的改进。
- 不过度测试：文档修改只检查 diff；局部逻辑改动运行相关模块/用例；依赖变更运行 `checkModules`；涉及打包或跨模块接口时再做相应构建和必要回归。完整 CI 保持原有检查，不为小改动反复跑全套，也不默认加入仪器测试、长时间压力测试或全量性能采样。

## Git

不要提交 `local.properties`、keystore。不要再用会匹配任意 `app/` 目录的 ignore 规则。文本 LF，`*.bat` 为 CRLF。

保留工作区已有的其他改动，只修改本次任务涉及的文件；不要擅自 reset、覆盖或提交他人的未提交工作。服务器凭据不得写入文档或仓库；需要 SSH 操作时先告知用户。

## 开发工作流

`.grok/workflows/`：

- `/echo-dev` — 按模块实现一项改动。参数 `task`。
- `/echo-review` — 看一眼当前 diff。可选 `base`，默认 `HEAD`。
