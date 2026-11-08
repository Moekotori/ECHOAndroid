# GitHub 自动更新

更新源固定为 `moekotori/echoandroid` 的最新正式 GitHub Release。应用启动后检查，自动检查间隔 12 小时（失败也限频）；设置 → 关于 → 检查更新可手动重试。没有 Release 时显示暂无更新。草稿与预发布不进入更新渠道。

用户选择下载后，在应用缓存目录流式下载并展示百分比；检查 SHA-256、文件大小、APK 包名、严格递增的 versionCode 和当前签名证书。下载完成进入系统安装器；首次需允许此应用安装未知来源应用。拒绝授权或取消安装可再次点击安装。弹窗显示当前版本、下载大小与更新日志；检查、下载、校验、安装权限和安装器失败分别提示。可取消下载并清理临时文件，或收起弹窗继续下载；收起后不会自动跳转安装器，返回关于页可继续操作。关闭检查弹窗后结果不会强行重新弹出。进程退出会取消任务，下次下载重新开始，不承诺断点续传。只保留本轮 APK/临时文件，不把安装包加载进内存。

## 发布

在 GitHub 仓库 Secrets 中配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。必须使用已分发正式版的原签名；不要新建替代签名。Debug 包与不同签名的正式包不能覆盖更新，应用会阻止安装。

手动运行 `GitHub release APK` workflow，输入 `YY.M.D`，例如 `26.9.13`；应用展示版本、APK 版本和 Release 标题均使用此格式。`revision` 为当日修订号（1–99），默认为 1，对应标签 `v26.9.13`；同日再次发布使用递增修订号，例如 2 对应 `v26.9.13-r2`，不覆盖已有 Release。内部 versionCode 为 `YYMMDD * 100 + revision`，兼容升级旧的 `YYMMDD` 编号；普通本地构建为对应版本日期的 `YYMMDD * 100`。发布前必须读取当前正式版的 `update.json`，确认新 versionCode 严格更大。更新检查按内部 versionCode 判断是否可升级。

流程验证并构建签名 APK，根据真实输出元数据生成 `update.json`，创建附带 APK 与元数据的 **草稿 Release**。检查 APK 与更新日志后发布草稿，已安装客户端即可发现。不要把 Secrets 或签名文件提交到仓库。工作流文件本身不会自动配置 Secrets 或发布第一版。

`update.json` 格式：schemaVersion=1，versionCode，versionName，apkUrl（本仓库 Release APK），size（字节），sha256（64 位十六进制）。草稿必须同时包含该文件和对应 APK 后再发布；不要单独替换其中一个。

## 更新线路

更新弹窗提供 GitHub 直连及面向中国大陆 GitHub 访问受限场景的六条 HTTPS 加速线路：`ghfast.top`、`ghproxy.net`、`gh-proxy.org`、`hk.gh-proxy.org`、`cdn.gh-proxy.org`、`edgeone.gh-proxy.org`。默认直连并记住用户选择。

版本检查与 APK 下载使用同一线路；空闲时可切换，切换后立即重新检查（不受 12 小时间隔限制）。请求过程中暂时锁定选择，检查最多等待 15 秒；下载可先取消再切换。失败可重试或切换线路，不静默切换到其他服务商。代理返回的 404 按检查失败提示，避免把代理故障显示为没有更新。

所有线路仍只接受本仓库的原始 GitHub Release APK 地址，并保留全部大小、SHA-256、包名、版本号和签名验证；不会接受镜像地址作为清单中的 apkUrl，也不使用私有服务器。`gh-proxy.org` 及其 HK / CDN / EdgeOne 子域属于同一服务商，提供多个访问入口，不代表四个独立故障域；`gh-proxy.com` 及跳转到上述子域的旧 `.com` 地址不重复列入选项。第三方加速服务的可达性随网络和运营商变化，不能保证长期可用。

### 2026-11-09 下载验证

本机禁用 HTTP 代理、保留 HTTPS 证书校验，串行请求真实 `update.json` 与同一个 `v26.10.31` APK（23,161,865 字节，约 22.1 MiB）。限时测速每次最多下载 15 秒，GitHub 直连在前后各测一次；HK 另用 30 秒上限补做完整下载。速度包含连接和首包等待，是本次快照，不是长期平均或中国大陆各运营商真机结果。

| 线路 | 平均下载速度（MiB/s） | 下载结果 |
| --- | ---: | --- |
| GitHub 直连 | 0.175–0.228 | 两次均达到 15 秒测试上限，未下完 |
| ghfast.top | 0.728 | 达到 15 秒测试上限，未下完 |
| ghproxy.net | 0.321 | 达到 15 秒测试上限，未下完 |
| gh-proxy.org | 0.913 | 达到 15 秒测试上限，未下完 |
| hk.gh-proxy.org | 0.874 | 25.269 秒完整下载，SHA-256 正确 |
| cdn.gh-proxy.org | 2.784 | 7.933 秒完整下载，SHA-256 正确 |
| edgeone.gh-proxy.org | 3.500 | 6.310 秒完整下载，SHA-256 正确 |

所有保留线路均成功解析相同版本的更新清单，且 APK 片段一致；新增的三条线路还完成了整包大小和 SHA-256 校验。实测中 `wget.la` 只有约 0.013 MiB/s，未加入；`gh.llkk.cc` 超时，`ghproxy.cc` 证书过期，`ghproxy.cn` / `ghproxy.cfd` 未返回有效清单，`gh.jiasu.in` / `github.moeyy.xyz` TLS 连接失败，也未加入。以上测试不包含应用内 UI、系统安装器或大陆手机实测。

协议参考：https://docs.github.com/en/rest/releases/releases
