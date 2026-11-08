# 专辑在线资料

Android 专辑详情在曲目下方按需加载“关于这张专辑”，支持本地、网盘与 Echo Link 专辑入口。

- `core:model/AlbumOnlineInfo` 是独立的在线资料协议，不覆盖本地文件标签。
- `core:data/AlbumOnlineInfoRepository` 请求 MusicBrainz release 搜索与详情，取得发行日期、地区、厂牌、目录编号及发行/录音/作品关系中的制作人员。
- 使用规范化后的专辑名和完整艺人署名核对候选，兼容联合署名中的连接词，以及明确的豪华版、重制版等版本后缀；不删除 Live、Remix 等作品标识。每次搜索最多返回 100 个版本，完整联合署名、清理版本后缀及单个合作者检索总共最多 4 次；搜索可以放宽，但结果仍核对完整署名。未知专辑艺人优先回退到已有的曲目艺人字段。同名但不同 release group 不自动选中；同组内优先曲目数、年份和正式发行状态。页面明确标注所匹配的是参考发行版本。
- 百科与 MusicBrainz 从一开始并行检索，不等待 MusicBrainz 匹配。含假名的元数据优先日文，其余优先界面语言，再回退英、日、中文。每种语言先查“专辑名 + 艺人名”，再查专辑名；每轮最多 8 次标题检索、每次最多 5 个候选。百科检索可去掉演出者/活动等括注（包括嵌套括号），Live、Remix、原声等作品标识保留；此规则不放宽 MusicBrainz 的发行版本匹配。
- 页面标题可直接匹配专辑名或括号消歧；译名页面要求导语明确提及原专辑名和发行作品身份。艺人可由导语直接核对；只有译名时，必须沿条目的 Wikidata 表演者 P175 核对所有署名艺人的中、英、日名称/别名，不能只凭相似译名猜测。每轮最多补核对 2 个候选，每个最多 4 位艺人。导语缺少年份时可用专辑、EP、单曲等作品身份核对；明确出现相冲突的年份、已知厂牌不符、Wikidata 的发行年或已知 P436 冲突，仍拒绝。MusicBrainz 尚未选中作品时，百科有自己的 P436 并不构成冲突；选中后会再次核对身份，人物、团体等条目不用。
- 独立百科检索无结果且 MusicBrainz 有匹配时，再使用其 Wikipedia / Wikidata 关联或 P436 反查补充；百科匹配优先首次发行年。中文简介按界面转换简体或繁体，保留原文语言、来源链接与 CC BY-SA 4.0 署名。没有把握的字段不伪造。
- `app` 持有唯一 repository，通过 feature 提供的 `AlbumOnlineInfoProvider` 接线。退出详情时取消等待与 HTTP 请求；不需要 API key。
- MusicBrainz 无匹配或不可用时，仍可独立按本地专辑名、完整艺人署名和年份查询百科；没有足够证据仍不使用结果，也不补造厂牌或制作人员。百科匹配使用作品首次发行年，参考再版的日期仍按实际版本显示。中日文艺人名可与助词直接相连，拉丁艺人名保留词边界核对。精确的 P436 关联不再额外要求本地年份或厂牌。

## 网络与缓存

每次 MusicBrainz 请求开始时间间隔至少 1.1 秒；专辑之间串行并在锁内检查缓存，同一专辑最多并发两路来源请求。MusicBrainz 搜索及详情预算 12 秒，独立百科检索预算 10 秒，整个来源查询与关联补充共享 18 秒总预算（不包含等待前一张专辑、磁盘缓存和结果序列化）。辅助分组/关联定位最多 3 秒，单次百科检索最多 4 秒；超时取消 HTTP 请求。每个响应最多 4 MB，无自动重试循环。失败可手动刷新；任一来源失败不丢弃另一来源已取得的资料。

私有 cache 目录保存最多 64 份资料，缓存 key 包含查询规则版本（当前 v4，旧无匹配缓存不会复用）、标题、艺人、年份、曲目数和语言。完整资料有效 7 天，无匹配 6 小时，部分结果 15 分钟；网络失败可回退最多 30 天的已有资料，并显示提示。缓存写入失败不影响在线结果。来源失败且无可用资料时抛出异常，不缓存成无匹配。

制作人员返回最多 160 条，手机最多展开 24 条；完整名单可通过 MusicBrainz 来源链接查看。Wikipedia 简介按 TextExtracts 支持范围请求最多 1200 字符，默认折叠。

## 验证

针对性测试：`./gradlew :core:data:testDebugUnitTest --tests app.echo.android.data.AlbumOnlineInfoParserTest --tests app.echo.android.data.AlbumWikipediaLookupTest --tests app.echo.android.data.AlbumOnlineLookupTest --tests app.echo.android.data.AlbumOnlineQueryPolicyTest`。

测试覆盖同名不同艺人、联合署名、版本后缀、同名不同作品、同组版本选择、查询转义、录音/作品制作关系、缓存编码往返、中日文艺人匹配、首次发行年、MusicBrainz 失败/无匹配后的独立百科查询、来源并行、译名、日文语言优先、跨语言艺人身份、无年份导语与页面取消。真实网络联调不加入默认 CI。

参考：[MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API)、[MediaWiki TextExtracts](https://www.mediawiki.org/wiki/Extension:TextExtracts)。

查询策略对照：[ECHOSteam AlbumOnlineInfoService](https://github.com/moekotori/echosteam/blob/main/src/main/library/online/AlbumOnlineInfoService.ts)。Android 保持自身 Kotlin 实现；本次同步查询和核对策略，不引入 PC 的 IPC、手动版本识别、曲目映射或完整版本列表接口。
