# Bridge 移动端三项修复

目标：Bridge 隐藏 PC 播放动作；文件夹按整个目录排序后分页；不展示内部文件夹引用。

现状：PC 播放回调始终非空；文件夹 UI 仅重排当前页；folder.path 被作为来源副标题。

## 范围与步骤

- [x] Android app / feature:library：可空 PC 播放回调贯穿列表和详情；Bridge 不装配该动作。
- [x] Bridge Library：新增只读文件夹分页查询，SQLite 内聚合、排序、LIMIT/OFFSET；保留不透明引用及目录范围约束。
- [x] Android core:connect：排序参数随页请求传递，切换排序取消旧请求并回第一页，父级页码恢复按查询与排序匹配。
- [x] 文件夹 UI：服务端有序结果直接展示；Bridge 来源使用可读名称，内部引用只用于导航。
- [x] 定向验证：服务端跨页排序与只读边界；客户端排序参数、重置与过期结果；编译相关模块。

协议沿用 catalog.sort，增加文件夹排序值 titleAsc/trackCountDesc/albumCountDesc/durationDesc/sizeDesc/fileModifiedDesc；现有无参数客户端按名称升序。旧 PC 文件夹接口不变。无需数据库迁移。

性能：排序、聚合留在 SQLite，客户端保留每类最多 50 项；不增加常驻缓存、后台轮询或音频热路径工作。数据库读取可取消，本次只读查询连接将临时聚合/排序设为文件存储，查询结束即释放，避免整个目录的临时结果驻留内存。

风险与回滚：Android 与 Bridge 需使用新版才能完成新增文件夹排序；按本次 diff 回退接线和新增查询即可，无用户数据变更。保留其他并行改动，不提交、不远端发布。

验证预算：单命令最多 60 秒，本轮累计最多 120 秒；到限停止自己的进程并报告。没有手机/模拟器，不能声称实机 UI、Wi-Fi 发现或出声验收。

结果：Bridge 8 项定向行为通过（首轮一项 fixture 缺必填列，修正 fixture 后仅重跑失败项）；临时存储改为文件后的 2 项目录回归通过。Android 5 项分页/排序回归零失败，feature:library 与 app Kotlin 编译通过。未打新版 Windows 便携包或 APK，现有下载产物未更新；需两端使用本次代码才能获得全目录排序。
