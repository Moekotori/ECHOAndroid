# 开源播放器源码参考

核对日期：2026-10-31。本轮阅读 Symphony、Auxio、Gramophone 的关键实现，并对照 ECHOAndroid 当前工作区。这里记录的是源码行为和采用判断，不是实机性能或完整产品验收。

## Symphony：页面与队列

快照：`dd04b872b8b4e6dd56172c053a5776c4d56ad080`，AGPL-3.0。

- [Queue.kt](https://github.com/zyrouge/symphony/blob/dd04b872b8b4e6dd56172c053a5776c4d56ad080/app/src/main/java/io/github/zyrouge/symphony/ui/view/Queue.kt)：队列使用惰性列表，首次打开定位到当前项，并能保存为歌单。值得参考操作入口与列表职责的划分。
- [RadioQueue.kt](https://github.com/zyrouge/symphony/blob/dd04b872b8b4e6dd56172c053a5776c4d56ad080/app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioQueue.kt)：分别保存原始与随机队列。以歌曲 ID 查找当前位置的方式不适合直接移植到允许同曲重复入队的场景；ECHO 现有队列条目的 entryId 应保留。
- [LyricsText.kt](https://github.com/zyrouge/symphony/blob/dd04b872b8b4e6dd56172c053a5776c4d56ad080/app/src/main/java/io/github/zyrouge/symphony/ui/components/LyricsText.kt)：歌词组件用 50ms Timer 获取进度，在组件销毁时取消。本轮不采用这种轮询；ECHO 的 `feature/player/.../LyricsDisplayClock.kt` 已限制生命周期、窗口焦点和有效性能档。

对照：`feature/player/src/main/java/app/echo/android/feature/player/NextUpQueueList.kt` 已有当前、下一首、继续播放、已播放分组及条目身份。本轮保留现有语义。

## Auxio：播放事实与恢复

快照：`c05cebc52fe2393bf3a988068e4060fd6dba408f`，GPL-3.0。

- [PlaybackStateManager.kt](https://github.com/OxygenCobalt/Auxio/blob/c05cebc52fe2393bf3a988068e4060fd6dba408f/app/src/main/java/org/oxycblt/auxio/playback/state/PlaybackStateManager.kt)：把播放状态、队列命令、UI 消费方和播放器接入方分开；状态应由播放所有者维护，页面展示状态并发送操作。
- [PersistenceRepository.kt](https://github.com/OxygenCobalt/Auxio/blob/c05cebc52fe2393bf3a988068e4060fd6dba408f/app/src/main/java/org/oxycblt/auxio/playback/persist/PersistenceRepository.kt)：恢复时按稳定歌曲身份重建曲库引用，并保存原队列和随机映射。这个身份与播放顺序分离的思路值得保留。

对照：`core/playback/src/main/java/app/echo/android/playback/EchoPlaybackSessionRestorer.kt` 已有专门的恢复所有者、远程鉴权检查和位置分桶保存。本轮没有更换恢复流程；其磁盘写入顺序与失败恢复没有做完整审计，不据此声称可靠性已验证。

## Gramophone：歌词语义与时间归属

快照：`55b5ba30abb9dc551f5460f6acfd3fd1e763fd00`，GPL-3.0。

- [SemanticLyrics.kt](https://github.com/FoedusProgramme/Gramophone/blob/55b5ba30abb9dc551f5460f6acfd3fd1e763fd00/app/src/main/java/org/akanework/gramophone/logic/utils/SemanticLyrics.kt)：区分主唱、伴唱、翻译以及行级和逐字时间。TTML 处理特别提示不能把伴唱子节点的时间当作主唱逐字时间。
- [NewLyricsView.kt](https://github.com/FoedusProgramme/Gramophone/blob/55b5ba30abb9dc551f5460f6acfd3fd1e763fd00/app/src/main/java/org/akanework/gramophone/ui/components/NewLyricsView.kt)：展示层通过播放器接口读取进度、播放状态和速度。ECHO 继续使用现有 Compose 展示和服务进度，不引入该项目的 View 实现。

### 本轮采用并修复

ECHO 的 `core/lyrics/src/main/java/app/echo/android/lyrics/EchoTtmlParser.kt` 原先扫描所有后代元素判断是否有逐字时间。主唱只有行级时间时，嵌套伴唱、翻译、音译或空 span 的时间也会触发这个判断，导致主唱生成并不存在的逐字数据。

修复后仅在实际读取当前声部的有时间歌词文本时确认逐字数据，独立保留伴唱、翻译、音译及原有时间。实现按 ECHO 现有解析流程编写，没有复制外部代码。

回归用例放在 `core/lyrics/src/test/java/app/echo/android/lyrics/EchoTtmlTimingOwnershipTest.kt`：

1. 逐行主唱与逐字伴唱混合时，主唱不生成逐字时间，伴唱保留每字起止时间及说话者。
2. 有时间的翻译、音译及空 span 不改变主唱同步等级，注释文本仍保留。

两个用例均先在旧实现上复现失败，再修正实现。验证命令：

```bat
gradlew.bat :core:lyrics:testDebugUnitTest --tests app.echo.android.lyrics.EchoTtmlTimingOwnershipTest --tests app.echo.android.lyrics.LyricsRegressionTest --tests app.echo.android.lyrics.EchoLrcParserTest
gradlew.bat :core:lyrics:lintDebug
```

结果：上述三个测试类共 25 项通过，歌词模块 `lintDebug` 通过；合并执行约 19 秒。

范围只包含歌词解析、必要回归测试和本文档；无公共模型、Room schema、模块依赖或 Echo Link 协议变化。未做实机逐字动画、整包构建或性能采样。

## 第二轮：曲库排序与播放队列

继续对照 Auxio 的 [Sort.kt](https://github.com/OxygenCobalt/Auxio/blob/c05cebc52fe2393bf3a988068e4060fd6dba408f/app/src/main/java/org/oxycblt/auxio/list/sort/Sort.kt)、上面的队列状态实现，以及 ECHO 已有的队列协议与分区。重点是排序依据一致、重复入队的条目独立、手动插队不参与打乱；本轮没有复制外部实现。

### 缺失碟号导致重排不能保持

`LibraryAlbumTrackOrderPolicy.discKey` 把缺失、零及负碟号归为第 1 碟，SQL 原先把缺失碟号排在第 1 碟之前。例如将碟号 1 的歌曲移到无碟号歌曲前，重排会写入曲序 1、2，但重载时 SQL 仍把无碟号歌曲放回最前面。

统一 `core:data` 六个查询文件中的碟号排序：仅正数保留，其他值按 1 排序，随后沿用原有曲序和其他排序条件。覆盖 DAO 专辑/艺术家/目录等列表、曲库歌曲排序、专辑艺术家查询、专辑播放查询及歌单按专辑排序。不改标签、数据库字段或 schema。

短验证直接读取源码中的 9 条 DAO SQL，在临时内存 SQLite 中执行缺失、0、负数、1、2 碟号的排序；另检查集合队列的完整排序和从点击歌曲开始的锚点后缀。旧 SQL 已复现错误，修复后的 11 项 SQL 检查通过。

### 歌手分散留下本可避免的连续尾段

原算法轮流取歌手桶。当前歌手为 Intro，后续 A 有 4 首、B/C 各 2 首时，会排成 `A B C A B C A A`；这一配置可以完整错开，末尾的 `A A` 可以避免。

`core:playback/PseudoRandomQueuePolicy.kt` 改为优先取剩余歌曲较多且不同于上一位歌手的桶。桶内保持原打乱顺序，相同大小按原桶顺序取；已经分散的队列直接返回。当前曲目、队列成员和手动插队的接线不变，仅调整开启歌手分散时的原队列排序。

用两个回归用例覆盖不均匀桶和当前歌手约束，其中前者先在旧实现上复现失败。新增计算为 O(n log k)，内存 O(n)，k 为歌手桶数；只在队列排序事件运行，不新增轮询、I/O 或 PCM 回调工作。没有测量设备耗时，不据此声称性能提升。

### 本轮验证结果

- 曲库定向单测 10 项通过：`LibraryAlbumTrackOrderPolicyTest`、`LibraryTrackLocatorQueryTest`、`PlaylistOrderingQueryTest`。
- 队列定向单测 16 项通过：`PseudoRandomQueuePolicyTest`、`PureRandomQueuePolicyTest`、`NextUpQueuePolicyTest`。
- 实际 DAO SQL 及集合队列排序/锚点的 11 项临时内存 SQLite 检查通过；这是 PC 上的 SQL 行为验证，未替代 Android Room/真机操作验收。
- `:core:playback:lintDebug` 通过，保留既有 15 条警告，本轮修改的排序文件没有 lint 报告项。
- `:core:data:lintDebug` 被未修改的 `RadioBrowserMirrorDiscovery.kt:20` 的两条 `WrongConstant` 阻挡，另有 46 条警告；没有为通过 lint 添加抑制或修改这些范围外代码。
- 曲库首次 Gradle 检查遇到 KSP 增量缓存损坏，单次关闭增量后完成编译与测试。该次测试加两个模块 lint 约 69 秒，未继续跑全套或压力测试。

```bat
gradlew.bat :core:playback:testDebugUnitTest --tests app.echo.android.playback.PseudoRandomQueuePolicyTest --tests app.echo.android.playback.PureRandomQueuePolicyTest --tests app.echo.android.playback.NextUpQueuePolicyTest
gradlew.bat :core:data:testDebugUnitTest --tests app.echo.android.data.LibraryAlbumTrackOrderPolicyTest --tests app.echo.android.data.LibraryTrackLocatorQueryTest --tests app.echo.android.data.PlaylistOrderingQueryTest
```

未改队列命令、条目身份、手动插队和随机循环策略，未做整包构建、设备播放或运行时性能测量。保留工作区其他未提交改动。
