# 音乐关系浏览

本地曲库页的「音乐关系」按作曲家、作品、乐章、指挥、演奏者、录音版本、参与者、厂牌和署名角色浏览。选择一个条目后可播放关联曲目，或切换分类继续缩小范围；上方路径可返回上一级或全部音乐。查询、分组和筛选在数据库中完成，列表分页加载。

页面显示当前关系范围内的曲目数、含关系标签的曲目数和各类关系条目数；仅有普通艺术家署名的曲目不会计入“含关系标签”。这些统计不随文本搜索改变。曲目菜单支持下一首和加入队列；“播放已载入曲目”只播放当前已加载窗口中的前 40 首，不代表整个筛选结果，也不套用其他首选版本。

新增扫描会保存关系标签。已有曲库首次进入时只建立已保存元数据的关系索引；点右上角「读取关系标签」才读取本地媒体文件。读取串行进行，可以停止，离开页面取消；已完成结果保留，失败文件保留原记录。不联网、不修改媒体文件，也不改变歌曲 ID、歌单或现有队列。

支持常见 Vorbis/FLAC/Opus 和 APE 文本标签，ID3 的 TCOM、TPE3、MVNM 及 TXXX 自定义文本，以及 MP4 的作曲者、作品、乐章和 iTunes 自定义文本：

| 关系 | 标签 |
| --- | --- |
| 作曲家 | COMPOSER / TCOM / ©wrt |
| 作品 | WORK、WORKTITLE / ©wrk |
| 乐章 | MOVEMENT、MOVEMENTNAME / MVNM / ©mvn |
| 指挥 | CONDUCTOR / TPE3 |
| 演奏者 | PERFORMER、SOLOIST、SOLOISTS |
| 参与者与角色 | ARTIST、COMPOSER、CONDUCTOR、PERFORMER、PRODUCER、ENGINEER、ARRANGER、LYRICIST、REMIXER；Vorbis 的 PERFORMER:乐器；ID3 的 TIPL/IPLS/TMCL 角色与人名配对 |
| 厂牌 | LABEL、PUBLISHER、ORGANIZATION / TPUB |
| 录音版本 | RECORDING、RECORDINGTITLE、VERSION；或显式 MusicBrainz recording ID |
| 显式身份 | MUSICBRAINZ_WORKID、MUSICBRAINZ_TRACKID、MUSICBRAINZ_RECORDINGID |

人物可读取 MUSICBRAINZ_ARTISTID、MUSICBRAINZ_COMPOSERID、MUSICBRAINZ_CONDUCTORID、MUSICBRAINZ_PERFORMERID。只有姓名与 ID 数量严格相等时才按标签顺序关联；不把一个 ID 分配给多个名字。人物身份只接受 UUID 或官方 MusicBrainz 人物地址，不联网验证。同一明确 ID 可连接不同语言姓名；同名不同 ID 分开保留。已有库需再次读取文件标签，才能获取新增的角色和人物 ID。

录音版本对齐 PC 的演绎分组：明确作品按专辑身份、作品身份、指挥和演奏者组织，版本标签参与区分；不要求先有 VERSION 标签。同名专辑的不同 albumKey 不会合并，同一版本的不同乐章可归入同组。无专辑身份时保守按曲目区分，缺作品时仍可使用明确的版本标签或录音 ID。

只有明确的多值分隔符（分号、全角分号、空字符）拆分人名；不拆 AC/DC 等名称。没有身份 ID 的同名作品按作曲者范围区分，作曲者缺失时按专辑范围区分；这只是本地标签关系，不是联网确认的人物身份。同名乐章按作品范围区分；录音版本带发行上下文。缺标签不从标题、文件名或专辑猜测，也不把普通艺术家自动当作演奏者。CUE 子曲保留作品和人员关系，不继承整轨文件的乐章及录音身份。

Room 25 → 26 增加可空关系元数据列、带外键的关系索引及查询索引，保留旧曲库。本轮新增字段沿用 JSON 列和已有关系表，不增加 schema 版本。每首最多 64 项文件角色署名，姓名 256 字、角色 80 字；旧 JSON 保持可读。隐藏歌曲不参与关系结果，移除曲目时关系记录级联删除。PC、Echo Link 协议、远程来源和迁移包格式不变；恢复后可重新读取本地文件建立关系索引。

当前仍不包含 PC 的图形关系画布、人工身份绑定和手工补充关系；不按同名推断人物已经得到身份确认。
