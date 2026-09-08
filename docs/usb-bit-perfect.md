# USB 严格整数直通

设置中的「USB bit-perfect 模式」默认关闭，开启时同时开启 USB 独占输出。
数字音量固定为 100%，开启前先调低 DAC 的硬件音量。关闭 USB 独占会同时关闭严格模式。
EQ、耳机校正、ReplayGain、睡眠渐弱、变速和跳过静音不参与严格输出；原有偏好保留，退出后恢复普通路径。

## 支持范围

- WAV 的 16/24 位整数 PCM；FLAC、ALAC 的 16/24 位无损音频；单声道或双声道。
- FLAC/ALAC 强制经 FFmpeg 输出左对齐 s32，读取解码器实际报告的有效位深。WAV 原整数样本直接复制。
- USB Type I 整数 PCM、UAC1/UAC2、等时 OUT 端点。设备须明确报告通道、有效位深和容器大小。
- 只允许原采样率和不损失有效位的格式；例如 16 位送入 24 位容器属于无损扩位，界面会显示实际位深。
- 设置 DAC 时钟后必须读回同一采样率；仅返回 SET 成功不能通过严格检查。

暂不覆盖浮点 WAV、32 位有效精度、DSD/DoP、APE、多声道、UAC3、Bulk 输出，以及需要额外 encoder delay/padding 裁剪的流。
未知格式、未知精度、无法读回时钟时会拒绝播放，兼容模式仍可正常尝试这些设备。

## 实现边界

`EchoBitPerfectAudioRenderer` / `EchoBitPerfectAudioSink` 位于 `core:playback`，拥有严格解码、缓冲和时钟。
`core:usb-audio` 拥有描述符判断、设备时钟、整数打包和 USB 传输。
共享状态在 `core:model`，开关由 `core:data` 持久化，`app` 接线到 `feature:settings`。
没有增加模块或跨 feature 依赖。

严格 sink 的数据不经过 DefaultAudioSink 的处理链或 AudioTrack。软件音量变化（包括音频焦点要求的衰减）会阻止继续发送，而不是忽略衰减继续以满幅播放。
不支持或传输出错时停止当前曲目，不自动转码、跳曲或回落到系统输出。关闭模式后可恢复普通播放。
切换设备格式前先排空已提交数据，时钟使用 USB 已完成的音频帧数，不使用 UI 计时。
数据缓冲固定 32 KiB；设置/状态使用现有生命周期，没有新增 UI 轮询。

「整数直通已生效」表示软件路径已满足精度、格式和时钟条件，且 USB 正在接收整数样本。
USB 独占标志、系统 preferred mixer 属性和 offload 标志都不会单独触发这个状态。
这个状态不等同于已经用物理设备回读证明 DAC 全链路 bit-perfect。

## 验证

- JVM：16 位全部取值的大小端转换和扩位、24 位边界值、拒绝未声明精度、拒绝未知/降位深/非 PCM USB 格式。
- Android：FLAC/ALAC × 16/24 位的已知样本，比较 s32 解码值和 USB 打包字节，并在 flush 后再次比较。
- Android：没有 DAC 时明确失败、不创建 AudioTrack；关闭严格模式后恢复普通输出。
- 原有 s16/float FFmpeg 冒烟测试继续运行，仪器测试不加入默认 CI。

测试素材位于 `core/playback/src/androidTest/assets`，全部由整数样本合成，无第三方音乐内容。
`bitperfect-codecs.json` 保存 FFmpeg 编码的一帧、初始化数据和原始整数值，不依赖在线下载。

真机验收尚需：至少一台 UAC1/UAC2 DAC，确认 44.1/48/96 kHz 时钟读回、暂停恢复、同/不同采样率切歌、拔插和错误停止。
要对外宣称端到端 bit-perfect，还需要 DAC 测试功能或数字回采比对；仅显示采样率正确不够。
