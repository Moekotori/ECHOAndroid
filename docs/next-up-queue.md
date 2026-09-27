# 接下来播放

手机播放队列区分原队列与手动插播。音乐下载、PC 遥控和 Echo Link 协议不在本次改动范围内。

- “加入接下来播放”追加到插播区；“下一首播放”插到插播区最前面；“追加到原队列末尾”保留原行为。
- 当前歌曲继续播放，结束后依次播放插播，再接回原专辑或歌单。随机开关只改变原队列的遍历，手动插播顺序不变。
- 插播离开当前播放位置后移除；同一首歌多次加入时用不同的队列项身份标识，互不影响，也不删除原队列里的同曲。
- 单曲循环维持当前歌曲；主动切下一首进入插播。列表循环不重新播放已消费的插播。
- 清空插播只移除待播项。直接播放另一份曲库队列会替换整份队列。
- 队列面板按正在播放、接下来播放、继续原队列分区，先前曲目可展开。列表循环时继续区域包含将回绕到的原队列歌曲。
- 会话保存包含每次入队的身份、插播标记、来源名称和随机顺序。旧会话没有这些字段时按普通队列恢复。

`NextUpQueueController` 归播放服务所有，沿用一个 ExoPlayer 和原音频输出链路；调度在队列操作或播放事件时更新，不依赖页面或进度计时。UI 编辑命令携带队列项身份，避免自动切歌后旧索引操作了别的歌曲。

## 定向验证

```text
gradlew :core:playback:testDebugUnitTest --tests *NextUpQueuePolicyTest
gradlew :core:model:testDebugUnitTest --tests *NextUpQueueStateTest
gradlew :core:data:testDebugUnitTest --tests *NextUpSessionPersistenceTest --tests *EchoSavedPlaybackSessionTest
gradlew checkModules checkLocalization :app:assembleDebug --no-configuration-cache
```

可选模拟器验证使用 `:core:playback:connectedDebugAndroidTest`，限定
`-Pandroid.testInstrumentationRunnerArguments.class=app.echo.android.playback.NextUpQueueControllerTest`。
测试静音运行，不加入默认 CI。真机 USB、远程网络欠载和具体音乐文件的无缝表现仍需对应设备验证。
