# Android 低版本兼容

最低版本保持 Android 8.0（API 26）。compileSdk / targetSdk 为 37 不代表可以在旧系统直接调用新 API。

## 版本边界

| 系统 | 当前代码的兼容路径 |
| --- | --- |
| Android 8–9 / API 26–28 | 音乐读取走 `READ_EXTERNAL_STORAGE`；删除 MediaStore 原文件前按需请求 `WRITE_EXTERNAL_STORAGE`。SAF 文档仍走用户授予的 URI 权限，不额外索取全盘写权限。 |
| Android 10 / API 29 | 写入、删除 MediaStore 文件沿用逐文件 `RecoverableSecurityException` 授权；不使用 API 30 的批量请求。 |
| Android 11–12L / API 30–32 | MediaStore 写入、删除使用系统请求；读取音乐仍使用旧读取权限。 |
| Android 13+ / API 33+ | 使用音频分类权限、通知运行时权限与系统应用语言。 |
| Android 8–11 / API 26–30 | 采样率不依赖 API 31 才新增的 Retriever key：优先容器元数据，缺失时用 `MediaExtractor` 读取音轨格式。 |
| Android 12+ / API 31+ | 已有 Retriever 采样率有效时直接复用；缺失、无效或读取失败时仍可回退到格式头。 |

动态色、实时玻璃、触觉组合、USB 系统 mixer 等增强能力继续使用既有 SDK / 硬件判断。这里描述实现路径，不代表所有系统和厂商均已实机验收。

平台依据：[共享媒体文件与分版本写入授权](https://developer.android.com/training/data-storage/shared/media)、[MediaExtractor 文件描述符与资源片段接口](https://developer.android.com/reference/android/media/MediaExtractor)。

## 本轮修复

- MediaStore 采样率补读以前在 API 31 以下直接返回空，现在使用旧系统已提供的格式读取 API。仍遵守扫描设置、已有值复用和原有后台扫描调度。
- SAF 扫描、外部文件打开、重复歌曲质量分析补齐相同的采样率缺失场景。已解析的容器数据继续优先使用。
- 新的格式读取只访问 `content:` / `file:`，不为元数据查询发起远程拉流，不解码 PCM；限制检查的音轨数并释放 extractor 和文件描述符。SAF 资源片段保留 offset / length。
- 外部文件的独立元数据读取明确切到 I/O 调度器，不依赖调用方恰好在后台线程。
- 曲库和播放器共用的删除入口在 API 26–28 补请求写存储权限。拒绝授权时返回取消，不删除原文件或曲库索引；离开页面取消等待。仅移除索引与 SAF 文档删除不触发该权限。

没有改数据库 schema、模块依赖、Echo Link 契约、PC 代码或 PCM 输出。

## 短回归

```bat
gradlew :core:data:testDebugUnitTest --tests "*LocalAudioSampleRateReaderTest" --tests "*LibraryTrackDeletionPolicyTest" --tests "*LibraryScanPolicyTest"
gradlew :core:data:lintDebug :core:design:lintDebug :core:connect:lintDebug
gradlew :app:assembleDebug
```

JVM 回归检查采样率回退、有效值跳过额外读取、无效值、异常、取消传播，以及 API 26/27/28/29/30/31/33/37 的删除权限边界。JVM 测试不能验证厂商 extractor、权限弹窗或真实文件删除。

2026-11-08 本轮结果：56 个定向 JVM 用例通过，`:app:assembleDebug` 通过；设计与连接模块 lint 通过。数据模块 lint 没有报告 `NewApi` / `InlinedApi`，但被原有 `RadioBrowserMirrorDiscovery.kt:20` 的两条 `WrongConstant` 阻断，分别涉及 DNS flags 与 SRV type，未修改或压制。整包 lint 初次尝试被当时设置模块的资源 / 图标引用编译错误阻断，后续整包构建成功后没有重复跑全模块 lint。当前只有 API 36 模拟器，没有完成旧系统设备验收。

低版本实机验收仍需在 Android 8/9、10、11 各做一次短测：

1. 用 MediaStore 和 SAF 各扫描少量 MP3 / AAC / FLAC，打开外部文件，核对已知采样率；关闭采样率扫描时不新增补读。
2. Android 8/9 拒绝与允许删除写权限各一次；拒绝时文件和索引都保留。SAF 文档删除不弹全盘存储权限。
3. Android 10 验证逐文件授权的允许、拒绝；Android 11 验证批量系统删除确认。
4. 切换语言、深浅主题，前后台切换并短播一首歌，确认既有回退路径仍可用。

不把 Android 16 模拟器结果当作旧系统验收，不默认加入仪器测试或长时间压力测试。
