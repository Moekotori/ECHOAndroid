# Bridge 文件夹分页

目标：取消 Android Bridge adapter 单层最多500项的截断，完整访问 Bridge 返回的全部子目录与歌曲页；保留并行改动。仅改 Android，不改 PC/Bridge HTTP 契约、扫描、Room 或播放引擎。

现状：Bridge catalog 已提供50项页及total；Android folders adapter 固定循环1..10并合并。文件夹页面没有页状态。Bridge目录ID为root:/node:等不透明引用，不能用字符串斜杠推断上级。

范围与实施：

1. `core:connect`：adapter 每次仅请求指定页的folders/songs；新增有独立取消与generation的Bridge目录浏览owner，持有当前页和最多128级返回导航，切页替换数据，切目录/搜索/连接时隔离旧结果。旧PC fetchFolders保持原行为，新增内部page调用默认委托旧方法。
2. `core:model`：目录页码、总页数和上级ID作为UI状态，默认值保持旧PC行为。Bridge继续使用既有page/q参数，无服务端改动。
3. `feature:library`：从巨型LibraryScreen安全抽出文件夹浏览组件；复用原行、返回上级、排序与播放动作，只增加必要的上一页/下一页和页码。`app`接通切页回调。每次50个目录/50首歌，仅保留当前页，使用LazyColumn。
4. 测试：直接产品transport确认第11页以后仍可读取、根目录/歌曲total不同的页、搜索；浏览owner覆盖翻页、父页恢复、迟到响应、取消与失败重试。已有真实Bridge测试增加超过500条合成曲目并验证最后页可达。
5. 验证：定向core:connect回归，checkModules/checkLocalization及必要Android编译/增量APK；普通预算单条60秒、累计120秒，超时停止自己的验证进程并报告。无ADB设备时不声称实际手机页面验收。

风险/回滚：当前页排序保持现有UI行为；翻页后回到列表顶部，返回上级恢复父页。未引入曲库全量副本、页Map或无界缓存。失败保留重试页与已知上级。回退仅撤销本次page状态、owner、adapter与组件接线，保留此前封面/歌词与其他并行修改。

- [x] adapter与目录浏览状态：每次最多50个子目录与50首歌曲；只保留当前页，无500项截断。返回上级恢复父页，搜索重置第一页，过期响应和取消不污染新页。
- [x] UI分页和会话接线：文件夹组件独立，上一页/下一页、页码、父目录和原播放动作接通；PC旧接口保持原行为。
- [x] 定向回归：4项分页测试、3项既有Bridge连接/鉴权测试和1项旧PC目录404回归通过；真实Bridge互通另1项通过。合成SQLite含521个根目录、520个子目录与521首歌曲，第11页能读取20个子目录和21首歌曲，最后曲目page-track-0520可达。没有用全量数组绕过限制。
- [x] 模块图、checkLocalization、feature:library与app编译通过。
- [x] APK打包和签名/包内分页代码检查：首轮在单条60秒上限停止；用户批准追加90秒后，增量打包40.0秒、产物检查1.6秒通过，累计41.6秒，未重跑已通过项。包名app.echo.android，版本26.10.15 / 26101500；APK包含独立目录分页owner和Compose文件夹组件，签名有效。路径和SHA-256见`misc/echo-bridge-folder-pagination/apk-audit.json`。

第一次真实互通遇到合成曲库批量插入后的排序索引未同步，Core按契约拒绝陈旧索引。仅对自有fixture调用既有产品写入器同步索引后，失败用例通过；未修改生产Core、Bridge或用户曲库。

本轮未连接手机/模拟器做界面验收，未声称真机页面、浅深色实际渲染或Wi-Fi发现/出声验收通过。所有临时数据、日志和预算保存在PC工作区忽略的`misc/echo-bridge-folder-pagination/`。保留既有并行改动，未提交或远端发布。
