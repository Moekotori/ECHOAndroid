# Android 应用图标

当前选定图标为 2026-11-08 用户提供的薄荷绿底、猫耳看板娘版本。源图为 [`scripts/assets/echo-app-icon.png`](../../scripts/assets/echo-app-icon.png)，保留用户提供文件的原始内容。

Windows 重新生成启动图标：

```powershell
powershell -ExecutionPolicy Bypass -File app/artwork/generate-launcher-icons.ps1
```

macOS 可运行历史脚本 `swift scripts/sync-desktop-icon.swift`；默认同样读取 Android 选定源图。

五个 density 的普通图标使用完整方形原图。自适应图标将原图置于 108dp 图层中央的 72dp 区域，仅向外延展背景边缘供系统动效及圆形、圆角裁切使用，不预先裁圆角。

## 启动画面

系统启动屏和应用内动效共用 `echo_desktop_launch_mark.webp`。素材来自 [ECHOSteam 应用图标](https://github.com/moekotori/echosteam/blob/5865174cd66397b3331f59d0884d32f87d304bd7/build-resources/icons/echo-app-icon.png)，以浅色圆角底板托住原图，按 512 × 512 无损 WebP 打包。桌面启动器继续使用上面的 Android 图标。
