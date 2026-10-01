# Echo Link 配对与地址连接

PC 对应仓库为 https://github.com/moekotori/echosteam。两端均更新到支持局域网直连的版本后，在 PC 开启 Echo Link，Android 即可选择附近电脑或输入 IP 连接。未更新的 PC 仍需要配对凭据。

- 首次连接无需复制 token。PC 在 mDNS TXT 中新增 `direct=1`，Android 仅在明确收到此能力时对附近设备启用无凭据一键连接；手动输入 IP 可直接尝试连接。
- 旧 PC 或需要配对时，在 PC 生成配对链接，并在两分钟内将完整链接粘贴到 Android。支持原始 `echo://pair?...` 和包含 `#pair=` 的网页遥控链接。
- Android 沿现有 `/echo-link/v2/pair` 交换 access token，再访问 v1 companion API；配对成功后沿现有流程保存凭据，后续无需重复粘贴。
- 选择附近 PC 时，优先复用已保存凭据；没有凭据且未广播直连能力则展开配对输入。已成功保存的直连 PC 可直接重连。连接失败也会展开输入。

## 局域网直连契约

- 复用 `/echo-link/v1` 状态、曲库、控制与流地址接口，JSON 形状不变。
- 无 token 的 Android 请求发送 `X-ECHO-Link-Direct: 1` 和 `X-ECHO-Link-Version: 1`，不发送 `Authorization`。
- PC 仅在 Echo Link（v1 或 Basic v2）开启、socket 来源通过局域网检查、上述请求头均匹配时接受直连。携带 Origin、Cookie、Sec-Fetch-* 或 Authorization 的请求不走免 token 分支；v2 配对和事件接口仍保留原鉴权。mDNS 的 `auth` 字段保持兼容，另加 `direct=1`。
- 旧 token 和完整配对链接继续按原路径鉴权；错误或过期凭据不会自动降级为直连。
- 直连保存地址与名称，token 为空；重启后沿现有重连流程连接。直连复用现有 v1 状态轮询，不增加新的定时器。
- PC 拒绝直连时说明开启 Echo Link、使用支持直连的 PC 版本或完整配对链接，不将 401/403 一律归因为 PC 版本过旧。

Android 本地 HTTP 回归覆盖无凭据访问被拒绝、v2 链接换取 token、v1 状态读取及凭据重连。既有直连请求头与旧 token 测试保留。这些测试不替代真实 PC 与手机联调。

## 固定音量反馈

PC 开启固定音量时，v1 状态和 v2 状态／事件快照在 playback 中返回可选字段 `volumeControlEnabled: false` 与 `volumeLockedReason: "fixed_volume"`。Android 禁用音量滑块并显示锁定原因；播放、暂停和跳转仍可用。关闭固定音量后，字段恢复为 `true` 与 `null`，沿现有状态更新恢复音量控制，不新增轮询。

PC 对受限的音量指令返回 HTTP 409 `fixed_volume`，不再返回成功；Android 不将此错误当作鉴权失败或断开配对。旧 PC 缺少这些可选字段时保持原有音量控制行为。要启用锁定提示需更新 ECHOSteam 与 Android 两端。

## Android 会话与响应处理

- 配对、曲库、队列、流地址与歌词的完整响应解析在计算调度器执行，网络仍使用可取消的异步请求。
- v2 事件流单独使用 45 秒读取超时，容纳 PC 的 15 秒心跳；普通控制请求保留原超时，两者复用连接池与线程池。
- 完整事件快照以单个最新待处理项交给会话协程，避免后台网络回调直接修改会话状态；更旧的 HTTP 状态不会覆盖刚收到的事件快照。
- 临时事件失败按 1、2、4 秒最多重试三次；永久失败或预算耗尽时使用现有轮询。收到首个快照后才降到低频心跳轮询，稳定 30 秒后可重置下一轮失败预算，避免短连接反复重试。
- 退到后台、断开连接与凭据失效时取消订阅；回到前台立即刷新一次状态。切换 PC 或更换同一 PC 的凭据时，旧事件与请求结果不得恢复旧状态。
## 收藏与普通歌单同步（元数据协议 v1）

此扩展由 Android 主动发起，需要已配对的 Bearer token；直连只浏览曲库，不能使用同步写入。两端实现仍以 `https://github.com/moekotori/echosteam` 的 ECHOSteam 服务端为准。旧 PC 返回 404/501 时，Android 显示更新提示，其余联动继续使用原协议。

- `GET /echo-link/v1/library/sync/collections`：返回 `version: 1` 和最多 501 个收藏/普通歌单摘要。
- `GET /echo-link/v1/library/sync/tracks?key=…&offset=…`：每批最多 200 条，offset 为 200 的倍数。
- `POST /echo-link/v1/library/sync/merge`：请求包含 `version: 1`、`preview`、`collection` 和 `tracks`；`preview: true` 仅检查匹配，不修改曲库。返回 `matched`、`skipped`。

collection 包含 key、name、trackCount、favorites。收藏 key 为 `favorites`，普通歌单 key 使用 `pc:` 或 `android:local:` 前缀。接收端保存原 key，往返同步和重试复用同一份歌单。

歌曲引用只有 title、artist、album、durationMs，不包含 URI、本机路径、凭据、歌词、封面或音频。依歌名、艺术家、可用的专辑与时长（容差 2 秒）匹配已有本地歌曲；多个匹配、未找到或无法确定唯一性时跳过，不从 PC 下载音频。每次预览最多 10,000 条引用，按批次取消。

合并只追加目标设备缺少的歌曲，保留目标已有名称和顺序，不传播删除。智能歌单规则不跨设备同步。取消或断线保留已完成的批次；再次预览后重试不会重复添加歌曲。此版本不在后台自动同步，也不包含删除冲突裁决。
## 完整同步扩展（状态协议 v2）

此扩展保留 v1 的追加合并接口。`GET /echo-link/v1/library/sync2/state?key=…` 返回 key、name、exists、tracks 和 revision；`POST /echo-link/v1/library/sync2/replace` 接收 version=2、expectedRevision、desired。所有调用沿用已配对 Bearer token。

手机保存两台设备各自上次完成同步的状态，比较新增、移除、名称和顺序变更。单侧修改应用到对方；双方均修改时，用户必须选择手机版本、电脑版本或合并曲目。合并模式保留各自名称及现有顺序；删除会明确显示在预览中，且仅删除歌单和收藏引用，不删除音频。

应用时再次检查 revision；内容在预览后变化会返回 409，要求重新预览。歌曲无法唯一匹配时保留目标已有曲目，不执行相关移除和重排，返回 missing / keptExisting。两台设备之间不可能建立一个数据库事务；中断可能保留已完成的步骤，下一次需重新预览。

每次预览最多包含两端合计 10,000 条引用，状态基线有数量与字节上限，取消时终止未发出的后续操作。旧 PC 未提供状态接口时，可切回兼容的追加合并。
