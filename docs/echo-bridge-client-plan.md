# Android 连接 ECHO Bridge

- 目标：同一 LAN 的 Bridge 自动发现、直连、分页浏览曲库，在手机现有播放器中播放原文件；不增加扫描、数据库写入、Bridge 遥控或手机向 Bridge 注册共享。
- 证据：Bridge 广播 `role=bridge`、`library=1`、`remoteControl=0`、`apiPath=/echo-link/peer/v1`，提供 device/catalog/stream/lyrics；Android 原来只请求 `/echo-link/v1`。
- 范围：core:model 为 endpoint/device 增加默认关闭的 libraryOnly；core:connect 独立 Bridge 协议 adapter，现有 transport 路由与 session 接入；feature:connect 按能力展示；app 接线限制播放目标及 phone share。无新模块、依赖和 schema。
- 契约：PC/Bridge 服务端无需修改。分页固定 50，传 opaque ID；stream 嵌套 track，GET 解析后沿既有 Media3/播放 URI owner；能力必须握手确认。手动地址只在无凭据 status 404 时探测 Bridge，鉴权错误不得降级。
- 性能：网络可取消、解析计算线程；复用 OkHttp 池；Bridge 只做可见期间低频连接检查，不轮询虚构 PC 播放状态；列表不复制整库，文件夹查询有页数上限。无 PCM 修改。
- 步骤：协议模型/adapter → transport/client → UI/session 能力接线 → 针对 discovery、catalog 翻页与真实 HTTP stream Range 的回归 → Android 增量构建。
- 风险/回滚：默认参数维持 PC 行为；回退仅删除本次 adapter 与条件接线，不改保存凭据、Room 或曲库。缺真机时明确只完成 JVM/构建验证。
- 验证：core:connect 指定测试及 app 增量 debug 构建；与本次 Windows 界面检查共用最多 120 秒预算，单命令 60 秒。

## 当前实施与验证边界

协议 adapter、client 能力、Connect UI 与 phone playback/share 接线已准备。测试覆盖 Bridge 广播/动态端口、手动握手、50+1条翻页、不可播放项、stream/lyrics、禁止遥控、401不能降级；可选 interop 用真实原生 Bridge 和合成 SQLite/WAV 验证 Range，不读取用户媒体。

connect 定向回归16项全部通过（零跳过），包含真实原生 Bridge SQLite查询、点播 URL 与 WAV Range/206 字节验收。Android app 和 Connect UI 编译、最终Debug APK构建完成。产物为 `app/build/outputs/apk/debug/ECHOAndroid-26.10.15-debug.apk`，applicationId=`app.echo.android`，versionCode=`26101500`，50,589,966字节；签名有效，最终manifest匹配，DEX包含Bridge协议adapter、endpoint与手机播放router。没有真机声音/实际 Wi-Fi mDNS 验收。

前两次预算超时均停止自己的进程，没有将超时视为通过。用户最后追加90秒后，缓存APK打包35.5秒通过；签名、manifest、类存在和SHA-256检查1.7秒，总计37.2秒。没有重跑通过的测试或Windows构建，也没有安装至手机、发布应用商店或Steam。构建记录与SHA-256留在PC工作区忽略的 `misc/echo-bridge-refined/android-build-manifest.json`。

此前的剩余项是缩略图与仅含结构化缓存的增强歌词，下节记录补齐结果。目录旧UI原有的500子目录/500曲目限制现已改为有界分页，见[文件夹分页计划与验证](echo-bridge-folder-pagination-plan.md)。歌曲、专辑、歌单列表沿现有翻页状态继续加载。

自动共享复验增强了现有真实互通用例：最新Bridge真实窗口默认启动共享，client.connect完成libraryOnly握手和catalog，playTracksOnPhone生成选中歌曲/原文件URL队列，WAV Range/206可读。点击Bridge真实停止按钮后catalog和旧stream不可用，保存关闭状态；发现移除策略保留其他设备。用例1项通过、0跳过，运行于JVM。当前ADB无手机或模拟器，因此Android NsdManager实际发现/丢失回调、Media3声音与后台行为未实测。本次Android生产代码未变化，不重复APK构建。

## 完整接入补齐计划（2026-10-15）

目标：保留既有 Bridge 发现、直连、分页和手机原文件播放，补齐曲库/播放器/通知封面和结构化歌词；复核共享停止、断线、串流刷新。仅修改 Android，不改 PC 协议、扫描、Room、PCM 或输出引擎，不发布远端。

现状证据：Bridge catalog 提供 coverId，thumb 返回有界 raster/base64 JSON；当前 Android 没有映射 coverId。Bridge lyrics 可只有 lines，当前 adapter 只取 syncedText/plainText。既有 JSON 歌词解析器支持词时间轴和翻译，但缺少 timeMs 别名与 offsetMs。既有 Media3 通知 loader 不认识 JSON 封面响应。

范围与步骤：

1. `core:connect`：新增 Bridge 封面 URL/读取模块；曲目、专辑、歌单、文件夹只传轻量 URL，封面按需请求 320/512 既有尺寸。歌词优先保留结构化数据，兼容旧的正文响应。不新增缓存或网络依赖。
2. `core:lyrics`：在现有 JSON 解析职责内支持 ECHO timeMs、offsetMs、词时间轴与翻译。公共 connect/model API 不变化。
3. `core:design` / `app`：现有 Coil loader 接受组件装配，独立 Bridge fetcher 调用 connect 读取器；大封面按显示大小取图，复用现有有界图片缓存。桌面小组件也使用同一读取器。
4. `core:playback` / `app`：通知封面通过可装配的 BitmapLoader 适配口复用同一读取器；只在通知后台 executor 解码，不改音频数据源、会话或播放热路径。
5. 定向测试验证发现/端口/鉴权隔离、分页、歌词/封面边界、原文件 Range、关闭共享撤销；Android 增量 APK。单条最多 60 秒，累计最多 120 秒，超时停止自己的进程。检查当前 ADB 设备，缺设备时不声称真机发现或出声通过。

风险与回滚：封面 JSON <=512 KiB，解码图 <=256 KiB，歌词 <=2000 行；取消请求释放 Call，无整库/图片并行缓存。Coil 只拦截 Bridge 封面路由，其他来源和 PC 配对流程保留。通知仅装配图片 adapter。回退仅撤销本节新增的 asset/parser/loader 接线，保留工作区此前的 Bridge 和其他并行改动。

- [x] 补齐封面与歌词适配：catalog 轻量封面 URL、Coil/通知/小组件按需读取，512 KiB 响应与256 KiB raster上限；Bridge 查询全局并发2，为原文件流与seek留容量。JSON歌词保留时间戳、偏移、翻译、罗马音、逐字与背景人声；纯文本保持无时间轴。
- [x] 定向回归与真实 Bridge 互通：30项，零失败、零跳过；含实际原生Bridge启动默认共享、HTTP握手/分页/目录、320px与768px图片解码、结构化歌词、手机队列、原文件Range/206。真实停止共享按钮后目录与旧串流地址撤销；发现移除策略保留其他设备。
- [x] 增量 APK：模块图检查与`:app:assembleDebug`通过；包名`app.echo.android`，版本`26.10.15` / `26101500`，APK签名有效，DEX包含新增图片读取器、Coil factory、通知adapter与歌词parser。安装包及SHA-256记录见PC工作区忽略的`misc/echo-bridge-android-complete/apk-audit.json`。
- [ ] 手机实机验收：当前ADB授权设备与模拟器均为0。NsdManager实际Wi-Fi发现/丢失、Media3出声、通知和小组件实际显示、后台切换与网络恢复仍未实测；不将JVM互通等同真机通过。

首轮Gradle在60秒编译上限时已停止，未把超时记为通过。用户明确追加最多180秒、单条最多120秒后，定向回归51.0秒、APK102.4秒、签名/manifest/DEX检查1.9秒，累计155.4秒。保留并行改动，未安装至设备、未提交或远端发布。
