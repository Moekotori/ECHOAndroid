# ECHO Android 动效系统

统一入口在 `core:design` 的 `EchoMotion.kt` 与 `EchoInteractions.kt`。动效表达操作反馈、层级和状态，不改变播放状态的真实来源。

## 节奏与组件

| 场景 | 入口 | 行为 |
| --- | --- | --- |
| 点按卡片 / 图标 | `echoClickable` | 按下缩至 97%，松开弹簧回位；保留 ripple |
| 歌曲长按 | `echoCombinedClickable` | 共用按压反馈，保留长按、双击、滚动取消语义 |
| Material 按钮 | `echoPressFeedback(source)` | 与按钮传入同一个 interactionSource，不额外注册点击 |
| 状态图标 / 标签 | `EchoStateContent(state)` | 180ms 淡入、120ms 淡出；不要传播放进度 tick |
| 内容展开收起 | `EchoExpand` | 360 / 260 弹簧调校，退出结束后移除内容 |
| 内容高度变化 | `echoAnimateContentSize` | 在容器尺寸变化时平滑重排 |
| 展开箭头 | `echoExpandIndicator` | 向下箭头连续旋转 180° |
| 有稳定 key 的歌曲列表 | `echoItemMotion` | 移动弹簧与删除淡出，不在分页加载时逐项飞入 |
| 曲库层级 / 分类 | `rememberEchoContentMotion` | 深入、返回和左右切换共用转场；轻量模式只淡入淡出 |
| 播放器 / 弹层 | `EchoMotion.nowPlaying* / overlay* / dialog*` | 沿用现有纵向层级、淡入和缩放 |

`silkFloat / Offset / Dp / Size` 是临界阻尼弹簧，参数 ms 是调校量而非保证完成时间。颜色与透明度使用 tween；手指跟随直接映射位移，松手才做回弹。

## 接入规则

- 新的可点按自定义组件优先用共享 clickable。全屏遮罩保留普通 clickable，不给整屏做按压缩放。
- 一个操作只绑定一个点击事件源，不叠加 pointerInput 模拟按压。禁用状态不缩放，滚动取消后回位。
- `echoPressFeedback` 在 graphicsLayer 中读取动画状态，避免每帧重组整行。
- 同一次展开不要同时套 `EchoExpand` 和父级 `echoAnimateContentSize`，避免两个尺寸动画追赶。
- 列表 key 必须代表条目身份，不能用位置。播放队列允许重复歌曲，目前仍使用原有队列实现；引入独立队列条目 ID 后再加重排动画。
- 不为所有列表项添加入场延迟，不新增常驻无限动画，也不把进度 tick 做成内容转场。
- 轻量模式关闭新增按压缩放、列表移动和尺寸插值，保留短淡变。Compose 动画沿用系统 animator duration scale，不自建帧循环覆盖系统关闭动画的设置。

## 当前覆盖和验收边界

覆盖首页与搜索卡片、曲库歌曲与长按菜单、曲库层级/分类转场、远程曲库展开与箭头、设置操作、迷你播放器状态和队列按钮、底部导航、共享文本按钮和分段标签。

全屏播放器已有独立手势和歌词动效，本次不改其正在进行的代码修改。共享封面转场、预测性返回、队列条目身份属于后续专项，当前实现不声称已经覆盖这些功能。

快速验收：点按与滑动取消歌曲 → 长按菜单 → 曲库分类/详情返回 → 远程曲库连续展开收起 → 播放暂停 → 切换轻量模式与系统动画关闭。真机帧时间及各刷新率体验需要设备验收，编译不能证明流畅度。

参考：Android Developers 的 [交互事件源](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions) 与 [动画 API 选择](https://developer.android.com/develop/ui/compose/animation/choose-api)。

### 本次验证

- `checkModules assembleDebug --no-configuration-cache`：通过。
- 新构建安装到现有模拟器成功，启动后检查首页、导航到连接页、远程曲库展开和收起。
- 模拟器曲库为空，未实测歌曲长按、列表重排或播放状态切换；未进行真机帧率测试。
- 共享工作区的设置模块拆分及全屏播放器修改一并存在于构建中，不属于本次动效改动的独立验证结论。
