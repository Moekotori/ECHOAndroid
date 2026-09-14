# Echo Link 地址直连

Android 可点选局域网设备或输入地址（默认端口 26789），无需填写 token。
PC 对应仓库为 https://github.com/moekotori/echosteam，需同时更新其直连鉴权支持。

- 复用 `/echo-link/v1` 状态、曲库、控制与流地址接口，JSON 形状不变。
- 无 token 的 Android 请求发送 `X-ECHO-Link-Direct: 1` 和 `X-ECHO-Link-Version: 1`，不发送 `Authorization`。
- PC 只在 Echo Link 已开启且请求通过现有局域网来源检查后接受直连；携带浏览器 Origin 或 Authorization 的请求不走免 token 分支。
- 开启 Echo Link 后，同一局域网的原生客户端可读取曲库、控制播放；关闭 Echo Link 会关闭相应访问。
- 旧 token 和完整配对链接继续按原路径鉴权；错误或过期凭据不会自动降级为直连。
- 直连保存地址与名称，token 为空；重启后沿现有重连流程连接。直连复用现有 v1 状态轮询，不增加新的定时器。
- 老版 PC 拒绝直连时提示更新 ECHOSteam，也可粘贴其完整配对链接。

验证覆盖 Android 地址解析与真实 HTTP 请求头、PC HTTP 状态和曲库直连、浏览器来源拒绝及旧鉴权回归。真实设备播放仍需两端更新后验收。
