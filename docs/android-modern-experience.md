# 新版 Android 播放体验

## 本轮能力

- 播放组件同时声明 `home_screen|keyguard`。锁屏入口是否提供由系统和厂商决定；打开主页面走普通 Activity，不绕过锁屏认证。
- 组件提供 180×56、300×72、250×110、250×180 dp 四种布局。窄条保留播放/暂停；其余尺寸展示完整控制，大号组件展示单行歌词。背景跟随系统动态色和深浅模式，按钮使用中性前景色。
- Android 16 支持包络触感的设备使用不同的确认、刻度、抓取和落位反馈；依据硬件时长/控制点限制选择效果，不支持时回退原语或短振动。尊重应用触感开关及系统触感设置。
- 进度拖动只在用户跨越刻度时反馈，最短间隔 65 ms；播放时钟不会触发振动。队列完成实际移动后才发出落位反馈，取消拖动不会发出完成反馈。
- 播放页面通过 Jetpack WindowManager 监听分隔或遮挡屏幕的铰链，离开页面或进入后台时停止收集。展开时左右分栏；支持的半折叠宽屏上方歌词、下方封面/控制。计算会扣除页面在窗口中的真实偏移，避让铰链；普通短横屏沿用现有布局。

## 深化后的边界处理

- 冷启动组件被动读取已有保存队列，恢复标题/封面并明确显示暂停；读取限时 1.5 秒，不启动播放服务、不自动播放、不另存一份队列。实时播放状态始终优先。
- 组件与快捷磁贴各自收集所需状态；歌词变化不刷新磁贴。组件合并刷新突发，忽略播放进度 tick，取消异常继续向外传播。
- 组件封面最多缓存一张，解码/下载上限 2 MiB；失败缓存 30 秒后可重试，成功缓存 5 分钟。冷启动只读本地封面；HTTP 连接在所有结束路径关闭。
- 组件回调等待控制器连接和实际播放确认，整个操作限时 8 秒、同时最多一个控制器。取消/超时释放 Future、监听器和控制器。空队列的播放按钮打开应用选歌，大字号减少次要文字以保留触摸区域。
- 补充官方 Media3 `MediaButtonReceiver`，配合已有 `onPlaybackResumption` 提供系统/耳机用户触发的恢复入口。
- 轻量档关闭连续拖动触感并禁用包络效果，保留点击确认；队列按实际行号反馈，硬件异常自动回退。
- 分栏依据播放器实际可用区域和字号判断；不足时将完整翻页播放器放在较大可用区域。标题栏也避让铰链，RTL 调整封面/歌词位置。页面通过 movable content 与 saveable state 保持滚动和局部状态；分栏状态下的歌词跳转不等待已移除的 Pager 布局。

## 验证边界

- 深化轮的相关模块及 App Kotlin 编译、`checkModules`、Debug APK 打包通过。上一轮 `checkLocalization` 通过；本轮没有修改语言资源。
- 深化轮仅运行 `EchoSeekHapticGateTest`、`PlayerPanePlacementTest`、`EchoWidgetPlaybackSnapshotTest`，共 8 个用例通过；上一轮另有组件尺寸选择用例。
- Android 16 模拟器实测：宽屏双栏显示、恢复窄屏无崩溃；播放服务未运行时，桌面组件显示上次封面/标题和暂停状态，窄条/大号尺寸切换可用。截图为真实运行画面。
- 冷启动播放按钮已拉起服务，但采样时尚未确认播放，随后测试模拟器被关闭。已将控制器释放条件收紧到实际播放确认；该修正已编译打包，尚未完成设备复验。
- 模拟器安装空间门槛、显示尺寸和密度均已恢复原值；没有删除曲库或应用数据。
- Android 17 代码检查：当前播放服务为 Media3 `MediaLibraryService`；组件经 MediaController 响应用户点击，系统续播走 `onPlaybackResumption`。这不等于 Android 17 设备验收。
- 仍需在支持锁屏组件的设备、Android 16 包络触感硬件、真实折叠屏和 Android 17 上分别验收。编译/几何单测不能证明真机触感、铰链姿态或后台音频可靠性。

平台参考：[Media3 后台播放与恢复](https://developer.android.com/media/media3/session/background-playback#resumption)。

## 小组件视觉与系统岛（2026-10-31）

- 小组件改用 `glance-material3` 主题：Android 12+ 随壁纸取 Material You 动态色，低版本回退 Material 3 基线色。圆角在 Android 12+ 读取系统小组件圆角/内圆角，封面和歌单入口使用内圆角。选择器预览的颜色同样在 v31 取系统色，与实际渲染一致。
- 播放键为强调色实心圆，上一首/下一首为色调圆底；封面样式上用固定的半透明白色玻璃按钮和更深的底部遮罩，保证任意封面可读。绑定歌单的入口改为圆角色调条。触摸目标仍不小于 44 dp。
- 刷新策略不变：没有新增定时器或进度轮询。

### 各厂商“灵动岛”能力边界

| 系统 | 做法 | 状态 |
| --- | --- | --- |
| Android Live Updates | 支持的样式不含 MediaStyle | 保持标准媒体通知，不伪装为进度/导航活动 |
| 三星 One UI Now Bar、vivo/iQOO 原子岛、OPPO/一加/realme 流体云、小米/Redmi/POCO 超级岛、荣耀媒体胶囊 | 向系统提供 Media3 会话、媒体通知和媒体展示字段，是否展示由设备、ROM 和系统设置决定 | 已实现公共媒体接入路径；尚未逐品牌真机验收，不保证所有 ROM 都开放第三方音乐入口 |
| 小米 HyperOS 超级岛/焦点通知 | 通知 extras 写 `miui.focus.param`，需向小米申请焦点通知权限，文档为公测 | 未实现：无权限、无真机，字段表未核对 |
| OPPO 流体云、vivo 原子通知 | 走厂商 Push 通道并申请凭据 | 未实现 |
| 魅族 Flyme | 通知 ticker 状态栏歌词 | 已有 |

### 系统音乐岛兼容补齐（2026-11-08）

- 普通曲目同时提供标准 `title/artist/albumTitle` 和 `displayTitle/subtitle/description`。设置展示标题时也显式设置歌手，避免旧版媒体描述不再自动补出歌手；封面、时长、会话 token、打开应用与控制动作沿用 Media3。
- 电台 ICY 曲名变化时同步更新会话的展示标题，展示副标题为电台名；原始电台名和主机字段保留，避免影响电台身份、保存队列和曲名解析。只更新同 URI 的媒体项，不为上岛重开音频流。
- 会话预留上一首/下一首槽位，向遵循标准布局的系统表达位置偏好；队列边界仍按实际可用命令显示，不能操作的按钮不会伪装可用。系统可以忽略该布局偏好。
- 通知歌词必须属于当前曲目，空白或不匹配时回退歌手；Flyme ticker 也检查曲目身份。切歌后不会把上一首歌词带到新曲通知。
- 不新增悬浮窗、无障碍服务、通知读取权限、云推送 SDK、第二条通知或进度轮询。仅在现有媒体项/通知更新时处理展示信息，不修改音频处理与 Echo Link。

这里的“媒体接入”不等于厂商专有卡片授权。小米焦点通知仍需申请；OPPO/vivo 的业务实况通知也不能由标准媒体会话替代。未接入未经核实的私有 extras，也未获得任何厂商白名单或授权。

参考：[Android 系统媒体控件与元数据](https://developer.android.com/media/implement/surfaces/mobile)、[Live Updates 支持条件](https://developer.android.com/develop/ui/views/notifications/live-update)、[小米焦点通知申请及客户端接入说明](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2146)、[三星 Now Bar 设置](https://www.samsung.com/us/support/answer/ANS10000753/)、[vivo 原子岛功能说明](https://www.vivo.com.cn/service/questions/all?categoryId=158&questionId=1744)。

真机验收应覆盖：后台/锁屏播放，播放与暂停，队列首尾的切歌，拖动进度，异步封面完成，切歌时歌词清理，以及电台曲名更新。关闭系统音乐岛入口时，应继续提供正常媒体通知。当前未连接各品牌真机，代码与单测不能代替这些设备验收。

本轮验证：`:core:playback:testDebugUnitTest` 定向运行 `EchoSystemMediaPresentationTest`、`EchoRadioNowPlayingTest`、`EchoMediaSessionControllerGateTest`，共 21 个用例通过；播放模块 Kotlin 编译和 `checkModules --no-configuration-cache` 通过。模块 `lintDebug` 检查无错误，保留 15 条现有警告；没有跑完整 App 打包或长时间测试。

### 验证范围

在 HEAD 的干净 worktree 叠加本次改动：`:app:compileDebugKotlin`、`checkModules`、`:app:processDebugResources`（含 v31 系统色引用）和组件单测通过。**没有在真机或模拟器上看过实际渲染**，也没有完成 APK 打包（该 worktree 路径过长导致原生库 CMake 失败，与本次改动无关）。
