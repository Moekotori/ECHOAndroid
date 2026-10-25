# 电台与播客

曲库的音源菜单提供独立的「电台」与「播客」入口，不需要连接 PC。

本次对照的 PC 实现位于 [moekotori/echosteam](https://github.com/moekotori/echosteam)：
[电台目录说明](https://github.com/moekotori/echosteam/blob/main/docs/radio-browser-directory.md)、
[电台页面](https://github.com/moekotori/echosteam/blob/main/src/renderer/components/radio/RadioWorkspace.tsx)、
[播客说明](https://github.com/moekotori/echosteam/blob/main/docs/podcasts.md)。
Android 使用自身的数据层、Compose 与 Media3 实现，没有改动 PC 或 Echo Link 协议，也没有引入 PC 的 TypeScript 实现。

## 电台

- 我的电台：搜索、收藏、编辑、移除与清空；保留已有 Android 电台及 ECHO Radio 默认项的删除状态。
- 全球电台：热门、最近收听、音乐、新闻/谈话、体育与地区；音乐分类包含 J-Pop、J-Rock、Anime、VOCALOID、东方、同人、游戏音乐及 Chiptune。
- 名称、国家代码、语言和标签组合查询服务端，每页 20 台并读取一个后续项。离开目录、页面进入后台或替换查询时取消请求并释放结果。没有启动预取、全目录缓存、位置推断或播放点击上报。
- Android 10 及以上按需查询官方 DNS SRV 镜像，旧系统或发现失败使用官方 HTTPS 后备镜像。查询总时限 14 秒、最多两个镜像、响应体上限 256 KiB。
- 手动流探测读取 ICY / 内容类型信息；支持从 M3U、PLS、文本列表和 JSON 导入。保存本身不会探测或播放。M3U 可与 PC 交换；JSON 备份还保留封面、收藏、简介、备用线路等字段。
- 电台详情支持封面、简介、网站、类型/地区跳转筛选、最多 15 条备用线路及默认线路。正在收听时显示已有 ICY 标题；可选直播视频仅在详情可见时播放，禁用其音轨，退出或进入后台释放视频播放器。
- 只有音频服务报告实际播放后才保存新台和更新最近收听。保存的用户编辑字段优先于目录元数据。直播沿用现有不可定位的播放路径和电台播放页。

## 播客

- 添加创作者公开发布的 RSS 2.0 地址，浏览/搜索订阅及单集；支持手动刷新、取消订阅、简介展开、原网站链接。
- 支持全部、未听完、继续收听、已听完筛选，每页 40 集。节目主播放按钮跨全部已存单集选择续播目标，不受当前搜索或页码限制。
- 单集支持续播、从头播放、下一首播放、加入队列、已播/未播标记；页面小播放器提供暂停/继续、后退 15 秒及前进 30 秒。原有全局进度、倍速、队列、睡眠定时器、通知和后台会话继续适用。
- RSS 及每级重定向仅接受无嵌入凭据的 HTTP(S)。响应最多 16 MiB、时限 12 秒、单个刷新任务；支持 XML 声明/HTTP 字符集、Unicode BOM、命名空间与相对 URL 的 `xml:base`。拒绝 DTD 和外部实体，限制 XML 深度和字段长度，不渲染来源 HTML。
- 最多 100 个订阅，每节目最多保留 500 集。RSS 有效音频 enclosure 使用稳定 SHA-256 身份；按发布日期及发布者顺序保留单集。刷新保留进度，ETag / Last-Modified 触发的 304 不解析或更新单集。
- 元数据和进度存入独立 `podcasts.sqlite`，不改变曲库 Room schema。查询、排序、分页与写入在数据层后台执行；未变化的记录不重复写入。数据库句柄在每次操作后关闭。
- 进度由播放服务事件驱动，正常播放最多每 15 秒一次，暂停、定位、切歌和服务关闭时补记。只有实际 EOF / 自动完成事件标记自然听完，时长和 UI 插值不决定 EOF。手动标记已播不会被普通后续 tick 清除；重播或向后定位会清除。
- 进度事件队列最多 8 条。进程被直接杀死可能丢失最后一段未写入进度。取消订阅删除本地元数据和进度，但不停止正在播放或已排队的音频。
- 播客以 `podcast:` 音轨进入原有 Media3 会话，可定位且不套直播时间轴；音频直接流式读取，不创建歌曲离线磁盘缓存。

PC 当前的 RSS 功能不包含播客目录检索、付费平台提取和离线下载；Android 同样不添加这些能力。Android 的列表和导航按移动端交互组织，不复刻桌面窗口布局。订阅、收藏和收听进度保存在各端本地，本次没有新增跨端同步协议。

## 验证范围

相关回归用例位于 `:core:data` 的 `PodcastFeedParserTest`、`PodcastHttpClientTest` 和 `RadioPlaylistCodecTest`，覆盖 RSS 身份/命名空间/编码/限制、条件请求/重定向、目录筛选及多线路导入导出。保留现有电台与播放模块回归。

使用 `checkModules`、`checkLocalization`、相关模块单测、`assembleDebug` 与新 feature 的 `lintDebug` 检查。存在并行构建时可通过本地 init script 将验证输出重定向到独立目录，不改项目默认构建配置。

构建和单测不等于真机验收。公开源可用性、Android 各版本的 DNS/XML 实现、屏幕阅读、真实通知交互、USB/蓝牙输出及长时间后台播放需在连接设备后验证。

### 2026-10-25 验证结果

- `checkModules`、`checkLocalization`、Debug APK 构建通过。
- `:core:data` 640、`:core:model` 121、`:core:playback` 348 项单测通过，共 1109 项；新功能的 12 个解析/传输/导入导出用例包含在其中。
- `:feature:radio:lintDebug`、`:feature:podcasts:lintDebug` 均为零问题，复数资源使用 Android plurals。
- 在桌面 JVM 上调用编译后的正式播客网络层/解析器：BBC Global News 返回 1,140,791 字节、287 集；条件请求返回 304，无响应体。独立音频 Range 检查返回 206，读取 4096 字节后关闭，没有解码或发声。
- 在桌面 JVM 上调用编译后的正式电台目录网络层/解析器：日本 + `j-pop` 筛选返回 9 个结果。
- 取实现中的实际 SQL 在桌面 SQLite 上检查了刷新保留进度、手动已播、重播/后退、未变化写入、500 集保留和取消订阅级联删除。此项不替代 Android SQLite 集成验收。
- 没有连接 Android 设备，未进行真机交互、音频解码/输出或后台播放验收。没有发布商店版本。
