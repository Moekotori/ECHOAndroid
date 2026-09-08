# 专辑在线资料

Android 专辑详情在曲目下方按需加载“关于这张专辑”，支持本地、网盘与 Echo Link 专辑入口。

- `core:model/AlbumOnlineInfo` 是独立的在线资料协议，不覆盖本地文件标签。
- `core:data/AlbumOnlineInfoRepository` 请求 MusicBrainz release 搜索与详情，取得发行日期、地区、厂牌、目录编号及发行/录音/作品关系中的制作人员。
- 使用规范化后的专辑名和完整艺人署名核对候选。同名但不同 release group 不自动选中；同组内优先曲目数、年份和正式发行状态。页面明确标注所匹配的是参考发行版本。
- 简介沿 MusicBrainz 的 Wikipedia / Wikidata 关联定位，不使用未经核实的同名百科搜索结果。Wikidata 优先当前支持的界面语言（中、英、日），再回退英文；展示原文语言、原文链接与 CC BY-SA 4.0 署名。没有关联或资料的字段不伪造。
- `app` 持有唯一 repository，通过 feature 提供的 `AlbumOnlineInfoProvider` 接线。退出详情时取消等待与 HTTP 请求；不需要 API key。

## 网络与缓存

每次 MusicBrainz 请求开始时间间隔至少 1.1 秒；所有专辑查询串行并在锁内检查缓存，防止重复并发。单次请求 25 秒超时，响应最多 4 MB，无自动重试循环。失败可手动刷新。Wikipedia 失败不丢弃已获得的 MusicBrainz 资料。

私有 cache 目录保存最多 64 份资料，缓存 key 包含标题、艺人、年份、曲目数和语言。完整资料有效 7 天，无匹配 6 小时，部分结果 15 分钟；网络失败可回退最多 30 天的已有资料，并显示提示。缓存写入失败不影响在线结果。

制作人员返回最多 160 条，手机最多展开 24 条；完整名单可通过 MusicBrainz 来源链接查看。Wikipedia 简介最多保留 4000 字符，默认折叠。

## 验证

针对性测试：`./gradlew :core:data:testDebugUnitTest --tests app.echo.android.data.AlbumOnlineInfoParserTest`。

测试覆盖同名不同艺人、联合署名、同名不同作品、同组版本选择、查询转义、录音/作品制作关系和缓存编码往返。真实网络联调不加入默认 CI。

参考：[MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API)、[MediaWiki TextExtracts](https://www.mediawiki.org/wiki/Extension:TextExtracts)。
