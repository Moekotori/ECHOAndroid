# GitHub 自动更新

更新源固定为 `moekotori/echoandroid` 的最新正式 GitHub Release。应用启动后检查，自动检查间隔 12 小时（失败也限频）；设置 → 关于 → 检查更新可手动重试。没有 Release 时显示暂无更新。草稿与预发布不进入更新渠道。

用户选择下载后，在应用缓存目录流式下载并展示百分比；检查 SHA-256、文件大小、APK 包名、严格递增的 versionCode 和当前签名证书。下载完成进入系统安装器；首次需允许此应用安装未知来源应用。拒绝授权或取消安装可再次点击安装。弹窗显示当前版本、下载大小与更新日志；检查、下载、校验、安装权限和安装器失败分别提示。可取消下载并清理临时文件，或收起弹窗继续下载；收起后不会自动跳转安装器，返回关于页可继续操作。关闭检查弹窗后结果不会强行重新弹出。进程退出会取消任务，下次下载重新开始，不承诺断点续传。只保留本轮 APK/临时文件，不把安装包加载进内存。

## 发布

在 GitHub 仓库 Secrets 中配置 `ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。必须使用已分发正式版的原签名；不要新建替代签名。Debug 包与不同签名的正式包不能覆盖更新，应用会阻止安装。

手动运行 `GitHub release APK` workflow，输入 `YY.M.D`，例如 `26.9.13`；应用展示版本、APK 版本和 Release 标题均使用此格式，标签为 `v26.9.13`。每个日期发布一个版本，后续版本使用更大的日期，不添加第四段。内部 versionCode 为 `YYMMDD * 100 + 1`，兼容升级旧的 `YYMMDD` 编号；普通本地构建为对应版本日期的 `YYMMDD * 100`。更新检查读取 GitHub 最新正式 Release 的 `update.json`，按内部 versionCode 判断是否可升级。

流程验证并构建签名 APK，根据真实输出元数据生成 `update.json`，创建附带 APK 与元数据的 **草稿 Release**。检查 APK 与更新日志后发布草稿，已安装客户端即可发现。不要把 Secrets 或签名文件提交到仓库。工作流文件本身不会自动配置 Secrets 或发布第一版。

`update.json` 格式：schemaVersion=1，versionCode，versionName，apkUrl（本仓库 Release APK），size（字节），sha256（64 位十六进制）。草稿必须同时包含该文件和对应 APK 后再发布；不要单独替换其中一个。GitHub 不可达时更新失败并允许重试，不使用镜像或私有服务器。

协议参考：https://docs.github.com/en/rest/releases/releases
