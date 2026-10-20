# Afterglow 引擎（echosteam Jizura）

Afterglow · 雾景 / 夜航 默认由 echosteam 的 Jizura 引擎渲染，与 PC 同一份 JS，运行在离线 `WebView` 里。原生 Canvas 实现（`afterglow/AfterglowStage.kt` 余下部分）保留为回退。

- 轻量档（`framesPerSecond == 0`）、歌词超过 512 行 / 32768 字、WebView 不可用、引擎报错、12 秒内未 ready 或渲染进程崩溃，都会回到原生舞台。
- 引擎资源：`feature/player/src/main/assets/jizura/engine.html`，由 `scripts/jizura/build-engine.mjs` 从本地 echosteam 检出生成，`SOURCE.md` 记录 echosteam 提交与 JIZURA 上游。不要手改。更新：`node scripts/jizura/build-engine.mjs <echosteam 路径>`。
- 引擎文件本身不改；页面顶部只有一个 shim，把引擎对 `parent.postMessage` 的输出转给 `EchoJizuraHost.post`，并让宿主以 `window` message 事件投递输入。消息协议与 PC 一致：`project` / `clock` / `render-settings` / `palette` / `randomize` / `dispose`。
- 宿主侧：`AfterglowEngineHost`（WebView，无网络、无导航、触摸穿透）、`AfterglowEngineStage`（时钟、设置、生命周期）、`AfterglowEngineProject`（歌词→项目 JSON，纯函数，有单测）。
- 时钟只在状态变化、跳转（>320 ms）和 500 ms 心跳时发送，引擎自行外推；页面不可见时 `WebView.onPause()`。
- 画质由引擎的自适应档位决定（上限 1080 px 高）。慢设备会自动降档，画面随之变糊；模拟器的软件画布会降到最低档，不能代表真机。
- 场景选择菜单在引擎模式下隐藏（场景由引擎规划）；配色、随机、上一句 / 下一句保留。
- 未覆盖：真机帧率与耗电、歌名 / 歌手装饰（舞台目前只传歌名）、字体与 PC 的差异（引擎用系统回退字体）。
