# Android 艺术家演出信息

对照 [echosteam 演出实现](https://github.com/moekotori/echosteam/tree/a8f1bd0091/src/main/library/online) 跟进。
只修改 Android；不改变 Echo Link 协议、Room schema 或 PC 配置。

## 页面与数据

- 艺术家「演唱会」页提供近期、历史切换；历史按日期倒序，最多显示 100 场。
- 演出详情和歌单独立展开。详情按来源提供入场、结束、阵容、地址、坐标、票务标记、票价、售票日期、备注和来源时间。歌单沿用 setlist.fm 和已有曲库匹配、播放接线。
- 日期和时间保留会场日历语义。已知结束时间的演出结束前仍属近期；只有开演时间时标为「已开始」，不推算演出时长。时间未知时明确显示待公布。日期型结束值保留整日精度。
- 保留 MusicBrainz、Eventernote、eplus；eplus 不提供历史查询。Eventernote 历史最多读取同一艺术家列表的四页，只跟随同源、同路径的下一页。
- 新增拨弦记、URBTIX、iCulture（分类 1、5、17）公开数据。地区源按结构化演出者精确姓名/已知别名匹配；没有阵容时才匹配有边界的标题，并排除致敬场次。不用拼音猜测身份。目前没有新增繁简转换依赖，跨字形姓名依赖已知别名。
- 合并同日期、时间、场地、城市的记录时保留可用详情和各来源时间；不同场地或场次不合并。
- 来源失败与无演出分开提示。失败刷新保留仍在回退期限内的有效缓存；空态提供用户主动打开的来源查询链接。公开快照不保证完整历史或实时余票。
- 需凭据的 Bandsintown、Ticketmaster、SeatGeek 本次未新增；Songkick 仅作外部查询入口，不伪造 API 返回。

## 公开来源与署名

| 来源 | 数据入口 | 归属与范围 |
| --- | --- | --- |
| 拨弦记 | [公开站点](https://boxianji.guitarkk.com/) / [JSON](https://boxianji.guitarkk.com/attention.json) | 每条署名拨弦记；主要是弹拨乐、古典、传统音乐演出，不代表全国商业票务覆盖。只读取事实索引，不捆绑文章、图片或第三方代码。 |
| iCulture | [音乐](https://data.gov.tw/dataset/6017)、[独立音乐](https://data.gov.tw/dataset/6006)、[演唱会](https://data.gov.tw/dataset/6013) | 每条署名 iCulture · 臺灣文化部；数据集列明政府资料开放授权条款第 1 版，保留原始详情与购票入口。 |
| URBTIX | [香港政府数据集](https://data.gov.hk/tc-data/dataset/hk-lcsd-event-urbtix-event) | 每条署名 URBTIX · 香港康文署；使用公开音乐类演出记录及单场次链接，遵守 [DATA.GOV.HK 使用条款](https://data.gov.hk/tc/terms-and-conditions)。 |

新增运行时域名：`boxianji.guitarkk.com`、`cloud.culture.tw`、
`fs-open-1304240968.cos.ap-hongkong.myqcloud.com`。固定数据集请求不含艺术家检索词、
曲库路径、标签或收听记录；正常网络连接信息和 ECHOAndroid User-Agent 会发送到主机。
现有 MusicBrainz、Eventernote、eplus 仍按原有方式使用艺术家查询。外部详情与购票链接只在用户操作后打开。

## 容量与生命周期

网络最多三路并发，单次汇总网络预算 12 秒；地区源单次读取最多 6 秒，无自动重试。
每份地区响应上限 4 MiB、最多规范化 3,000 场。五份地区磁盘快照各最多 4 MiB，
六小时有效、失败时最多回退七天；不在内存常驻整份 feed。iCulture 单分类失败不会删除其他分类的快照。
艺术家结果沿用最多 96 份、每份 512 KiB 的缓存；部分失败结果五分钟后允许重试。

阵容最多 32 人，备注最多 1,000 字符，地址和票价最多 512 字符。网络、JSON/XML 解析与磁盘缓存
均由数据层在 IO 调度器上执行。页面使用稳定事件 ID 的惰性列表，详情按需组合；近期列表仅在
页面活跃且 Activity 恢复时每分钟更新时间，切页或进入后台即停止。没有音频热路径改动。
