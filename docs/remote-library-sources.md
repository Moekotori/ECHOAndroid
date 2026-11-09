# 远程曲库来源

「互联 → 曲库来源」提供 Subsonic / Navidrome、WebDAV、SMB、Jellyfin / Emby、OneDrive（Beta）及 SSHFS 挂载目录。来源清单对应 [ECHOSteam 的正式构建](https://github.com/moekotori/echosteam/blob/5865174cd66397b3331f59d0884d32f87d304bd7/src/renderer/components/settings/RemoteSourcesPanel.tsx)。

## OneDrive（Beta）

在系统浏览器登录 Microsoft 账号，返回 ECHO 选择音乐文件夹并同步。曲目同步后可在「曲库 → 云端」浏览和串流。同步通过 Microsoft Graph 读取目录和音频属性，播放时打开临时下载地址。每次同步以选中的文件夹为索引范围。切换账号前，先移除原有配置。移除配置会删除对应的曲库索引。

构建时通过 Gradle 属性、环境变量或 `local.properties` 提供公开的 `ECHO_ONEDRIVE_CLIENT_ID`。GitHub Release 构建读取同名仓库变量。Microsoft Entra 注册可沿用 [ECHOSteam 的移动和桌面应用注册设置](https://github.com/moekotori/echosteam/blob/5865174cd66397b3331f59d0884d32f87d304bd7/docs/onedrive-integration.md)，使用 `http://localhost` 回调及 `Files.Read`、`offline_access` 权限。授权范围为账号可访问的 OneDrive 文件，ECHO 索引用户选中的目录。应用使用 Android Keystore 保护账号令牌。

## SSHFS 挂载目录

先使用能向 Android 系统文件选择器提供目录的挂载工具，再从 SSHFS 入口授权音乐文件夹。导入后可从本机曲库的文件夹视图播放。ECHO 保存目录的读取授权，由挂载工具负责 SSH 登录与文件访问。重新选择同一个目录可刷新曲库。挂载工具需支持 Android 目录树授权及音频文件读取。 [Android 系统目录授权](https://developer.android.com/training/data-storage/shared/documents-files#grant-access-directory)
