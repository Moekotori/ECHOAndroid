# Echo Link 配对与地址连接

PC 对应仓库为 https://github.com/moekotori/echosteam。当前 PC 鉴权要求有效的 bearer token，不能把“发现设备”视为“已配对”，也不能承诺只输入地址即可连接。

- 首次连接在 PC 开启 Echo Link，生成配对链接，并在两分钟内将完整链接粘贴到 Android。支持原始 `echo://pair?...` 和包含 `#pair=` 的网页遥控链接。
- Android 沿现有 `/echo-link/v2/pair` 交换 access token，再访问 v1 companion API；配对成功后沿现有流程保存凭据，后续无需重复粘贴。
- 选择附近或已保存的 PC 时，有保存凭据则连接；没有凭据则展开配对输入，不立即发送无凭据请求。连接失败也会展开输入，方便重新配对。
- 手动地址直连的传输兼容能力仍保留，但仅适用于明确支持以下扩展的 PC。它不是当前 ECHOSteam 的默认配对方式。

## 可选直连扩展

- 复用 `/echo-link/v1` 状态、曲库、控制与流地址接口，JSON 形状不变。
- 无 token 的 Android 请求发送 `X-ECHO-Link-Direct: 1` 和 `X-ECHO-Link-Version: 1`，不发送 `Authorization`。
- 支持该扩展的 PC 应只在 Echo Link 已开启且请求通过局域网来源检查后接受直连；携带浏览器 Origin 或 Authorization 的请求不走免 token 分支。这是扩展要求，不代表当前 PC 已实现。
- 旧 token 和完整配对链接继续按原路径鉴权；错误或过期凭据不会自动降级为直连。
- 直连保存地址与名称，token 为空；重启后沿现有重连流程连接。直连复用现有 v1 状态轮询，不增加新的定时器。
- PC 拒绝未配对访问时引导使用新生成的完整配对链接，不将 401/403 一律归因为 PC 版本过旧。

Android 本地 HTTP 回归覆盖无凭据访问被拒绝、v2 链接换取 token、v1 状态读取及凭据重连。既有直连请求头与旧 token 测试保留。这些测试不替代真实 PC 与手机联调。
