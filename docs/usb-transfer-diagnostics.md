# USB 传输诊断与兼容规则

诊断属于 Android 本地输出链路，不改变 Echo Link 协议。

## 指标与边界

`core:usb-audio` 的 native writer 在现有会话锁内累计计数，不增加逐包日志、额外锁或后台诊断轮询。写入时最多每秒一次将固定长度快照复制到 Kotlin；错误、流收尾和关闭时额外采样。UI 只读取不可变缓存，不执行 ioctl 或等待 USB 会话锁。

- 排队帧数包含尚未回收完成的已提交传输，因此是主机可观察的队列，不是 DAC 内部 FIFO 水位。
- 最低值是实际写入调用所观测到的最低队列，未观测时为未知，不填成零；容量是 USB URB 队列的上限，不等于整条播放链路延迟，也不冒充另一个未实现的目标缓冲区。
- 音频/反馈 URB 完成次数、音频/反馈包错误、提交/回收失败、无效反馈值分别累计。`EAGAIN` 是正常非阻塞回收状态，不计失败。
- 最近错误保留来源和有符号内核状态/errno：`submit`、`reap`、`audio-urb`、`audio-packet`、`feedback-urb`、`feedback-packet`、`feedback-value`。原有严格输出的错误处理不由 UI 诊断替代。
- **供给间隙次数与字节是估算**：已开始播放、主机队列耗尽且后续真实音频写入超过预测队列结束时刻 1 ms 时记录。它不是 DAC 确认的欠载，不统计无法再次写入的最终中断，也不能区分解码、调度、系统挂起等所有原因。字节按估算缺失帧 × 输出帧字节计算。
- 暂停、流收尾、flush 解除供给间隙检测；flush/close 主动取消 URB 不算包错误，也不把正常取消误判成设备断开。flush 保留会话累计计数。
- 只为 native 等时传输提供这些数据。bulk 或未建立会话时显示为未提供，不能用零代表已验证正常。

USB 页、声音诊断页和复制设备反馈共用读数。关闭后保留最后一份会话快照，仅在仍连接的 VID/PID 匹配时显示，并标明已结束；不会保留会话句柄或播放器。复制内容只含设备、计数、数值错误码和已知规则，不新增歌曲路径或凭据。

## DAC 兼容规则

`UsbDeviceQuirks` 是有明确匹配条件、原因、验证边界的规则表。当前仅迁入仓库已有的 `0x0644` TEAC 厂商级处理：设置采样率后重新选择流接口。范围和行为保持原样，不能称为所有 TEAC 型号均已验证。

诊断显示命中规则 ID，复制反馈附原因和验证边界。新规则必须来自实际问题、设备证据和针对性验证，不从其他播放器的反编译代码推定设备行为。DoP 仍使用用户授权的 VID/PID 和载波上限白名单，不由兼容规则自动放开。

## 验证

```text
gradlew.bat :core:usb-audio:testDebugUnitTest :core:model:testDebugUnitTest :core:playback:testDebugUnitTest :feature:settings:testDebugUnitTest
CXX=g++ python3 core/usb-audio/src/test/cpp/run_native_regressions.py
CXX=g++ python3 core/usb-audio/src/test/cpp/run_transport_diagnostics.py
```

两组 host native 回归在 Linux/WSL 用 ASan/UBSan 运行。完整 writer 测试替换 JNI 和 ioctl，检查实际计数分支、快照不执行 USB I/O、正常取消和 EAGAIN；原 packetization 回归检查 PCM/DoP 字节保留和供给间隙生命周期。这些不等于 DAC 真机兼容、后台长时播放或延迟性能验收。
