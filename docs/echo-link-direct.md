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
