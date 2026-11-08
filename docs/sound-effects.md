# PC 同款音效

入口：**信号 → DSP → 音效**。打开开关或选择一种音效即可启用，再次关闭可对比原音。
个人 EQ、MSEB、耳机校正及其他 DSP 设置独立保留；音效不会把它们改成某个 EQ 预设。

完整对应 ECHOSteam 音效选择器的 22 项，参考提交为
[`a8f1bd0091db9fd103144909ff36257d6de54b2f`](https://github.com/moekotori/echosteam/tree/a8f1bd0091db9fd103144909ff36257d6de54b2f)。

| 音效 | 原生处理 |
| --- | --- |
| 清晰人声 | PC 的两段人声 EQ、轻压缩及输出增益 |
| 深沉低音 | PC 的低架/低中频 EQ、轻压缩及输出增益 |
| 温暖柔和 | Signalsmith Crunch 饱和 |
| 宽阔声场 | Signalsmith Chorus 微量合唱/失谐 |
| LiveHouse 现场 | Signalsmith 小空间混响 |
| 音乐厅 | Signalsmith 大空间混响 |
| 磁带质感 | Airwindows Tape |
| 夜间舒适 | Signalsmith 动态压缩与补偿增益 |
| 耳机自然声场 | Bauer/BS2B 交叉馈送 |
| 短回声 | 120 ms 有界反馈延迟 |
| 电子管质感 | Airwindows Tube |
| 黑胶质感 | Airwindows Vinyl |
| 左右流动 | Airwindows AutoPan |
| 齿音柔化 | PC DeEsser 高频动态衰减 |
| 颤音律动 | Airwindows Tremolo |
| 相位扫频 | 六级全通相位与低速调制 |
| 鼓点塑形 | Airwindows Point 瞬态处理 |
| 镶边扫频 | 调制短延迟与反馈 |
| 电话音色 | 300–3400 Hz 电话带宽滤波 |
| 复古数字 | Airwindows DeRez3 |
| 自动哇音 | 输入包络控制带通滤波 |
| 金属调制 | Airwindows RingModulator |

## 实际处理和状态

支持 8–192 kHz、1–8 声道 PCM；耳机自然声场和左右流动需要至少两个声道。
不支持的格式保持原音并显示原因。USB 位完美由独立直通链路旁路音效，界面显示旁路；
不会把保存的开关当作实际处理结果。

音效位于现有 PCM EQ/声道处理之后、HRTF/等响及最终输出增益/峰值保护之前。
启用后会改变数字信号；峰值保护继续生效。输出状态由原生处理节点实际运行后发布，
主界面按状态事件更新，无新增进度轮询。

开关、选择、设备音效配置和迁移备份均保存音效。默认关闭，DSP 复位时关闭；关闭后
保留上次选择，便于重新试听。未知的新版本音效 ID 会关闭处理，不会偷偷换成另一种声音。

## 生命周期与资源

算法、系数、延迟及混响缓冲在单一后台准备线程中建立。快速选择合并为最新请求；
过期图在后台释放。音频线程独占当前图，只处理预分配的最多 1024 帧/块。
切换在旧音效淡出到原音后采用新图，再平滑淡入；旧图在音频读者离开后后台释放。
seek/flush 清空历史，播放器 reset 释放当前和待采用图。

队列最后一首结束时补齐有界尾音，最长 10 秒上限；实际混响、延迟按各自的衰减长度
结束。无缝换歌、循环及曲目中途的处理器重配置不额外追加尾音，不改变队列顺序。

## 来源与短验证

`core/playback/third_party/sound-effects` 只放实际编译使用的可移植 DSP 和依赖。
PC 的 Signalsmith/Airwindows 适配、EQ 和 DeEsser 算法保留；Android 适配层负责
PCM 交错转换、后台准备、切换、JNI 和生命周期。

Signalsmith DSP/Basics、Airwindows 为 MIT，STFX 为 BSL-1.0，ECHO 适配为 AGPL-3.0-only。
来源提交、原始及本地 SHA-256、ARM32/AArch64 浮点寄存器适配补丁均记录在 `sources.json`
及 `patches/`。完整许可在设置的开源协议页可读。

可手动运行 `core/playback/scripts/sync-pc-sound-effects.py` 更新固定版本；运行和构建时
不会联网下载算法。原生短测脚本 `test-sound-effects-native.py <临时输出目录>` 同时核对
PC/Android 的 22 个 ID，并覆盖原生响应、四种声道布局、关闭/复位、尾音上界和分块连续性。
JVM 定向用例验证后台准备/回收、切换、尾音、旁路与保存。以上检查不代替真机听感、耗电
或特定 DAC 的兼容性验收。
