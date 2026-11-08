# ECHO 插件

## 插件歌词源

教程页可安装 LRCLIB 示例。安装后分别授权「提供歌词候选」与「网络访问」并启用，
到播放器的歌词管理中主动搜索。插件结果带来源名称，可以先预览，再选用保存；
撤销授权、停用或删除插件后不再调用。LRCLIB 示例只在搜索时向 `lrclib.net`
发送歌名、歌手、专辑和时长，不发送曲库 ID、路径或配对凭据。

作者在 manifest 的 `permissions` 声明 `lyrics.provider`，实现以下同步 hook：

```javascript
function onLyricsSearch(track) {
  // track 只有 title、artist、album、durationMs。
  // 网络仍通过 echo.net.fetch，需要另外授权 network。
  return [{ text: "[00:00.00]第一句\n[00:05.00]第二句" }];
}
```

返回最多 8 条字符串或 `{text}` 对象；每条最多 65536 字符，合计最多 131072 字符。
宿主解析 LRC / TTML 等已有支持的歌词格式。每次查询最多调用前 4 个已启用且授权的
插件；单来源最多 8 秒，全部插件合计最多 12 秒，网络读取遵守同一个期限。
取消或换歌会取消等待并停止后续来源。常规插件 hook 保持原有 2 秒执行期限。
候选内容不进入日志，未选用的内容只随本次搜索保留。

插件是导入到 ECHO 里的 zip，不是另一个安装包。脚本在应用内的沙箱里运行，不能加载 DEX，也碰不到文件路径、凭证、音频缓冲和均衡器。

在设置首页的「插件」进入插件页，从那里导入、授权、打开插件自己的页面。权限默认全部关闭。

## 包里有什么

zip 根目录：

```text
echo-plugin.json
main.js
```

解压后的体积不超过 1 MB，最多 64 个文件，单个文件不超过 256 KB。路径里不能出现 `..`、绝对路径或盘符。`id` 只能是小写字母、数字、`.`、`_`、`-`，并且要和目录名一致。

`format` 和 `api` 都必须是 `1`。不认识的字段会忽略。不认识的权限名称也会忽略，旧版本 ECHO 因此不会被新字段弄坏。

```json
{
  "format": 1,
  "id": "echo.sample.hello",
  "name": "Sample",
  "version": "1",
  "entry": "main.js",
  "api": 1,
  "summary": "Shows the current track on its own page.",
  "permissions": ["playback.read", "ui.page"]
}
```

## 脚本

用到哪个再写哪个：

| 函数 | 什么时候调用 |
| --- | --- |
| `onLoad()` | 脚本加载后 |
| `onEnable()` | 用户启用后 |
| `onDisable()` | 用户关闭时 |
| `onPageOpen()` | 用户打开插件页面时 |
| `onPlayback(now)` | 切歌、播放或暂停时；进度最快 2 秒一次 |
| `onAction(id)` | 用户点了页面上的按钮 |

`onPlayback` 收到的对象只有 `title`、`artist`、`album`、`playing`、`positionMs`、`durationMs`。

每次主动调用都返回 `{ ok: true, ... }` 或 `{ ok: false, error: "..." }`。还没授权时 `error` 是 `not_granted`。

```javascript
function onPageOpen() {
  show("Sample plugin loaded. Allow Read playback, then tap the button.");
}

function onAction(id) {
  if (id !== "refresh") return;
  var now = echo.playback.now();
  if (!now.ok) {
    show("Playback can't be read yet: " + now.error);
    return;
  }
  var state = now.playing ? "Playing" : "Paused";
  var title = now.title ? now.title : "Nothing is playing";
  show(state + "\n" + title + " — " + now.artist);
}

function show(text) {
  echo.ui.setPage({
    title: "Sample",
    items: [
      { type: "text", text: text },
      { type: "button", id: "refresh", label: "Read current track" }
    ]
  });
}
```

页面描述只有三种条目：`text`、`button`、`list`。`list` 的每一行有 `title` 和可选的 `subtitle`。按钮的 `id` 会原样传给 `onAction`。

## echo 能做什么

| 调用 | 权限 | 说明 |
| --- | --- | --- |
| `echo.playback.now()` | `playback.read` | 当前曲子的标题、艺人、专辑、是否在播、进度 |
| `echo.playback.play()` / `pause()` / `next()` / `previous()` | `playback.control` | 播放控制 |
| `echo.playback.seek(ms)` | `playback.control` | 跳到指定毫秒，最大 24 小时 |
| `echo.library.search(query)` | `library.search` | 最多 20 条。`id` 如果像路径或 URI 会被拿掉 |
| `echo.storage.get(key)` / `set(key, value)` | `storage` | 字符串。最多 64 个键，单个值最多 4096 字 |
| `echo.net.fetch(url)` | `network` | 只允许 http/https，不接受账号密码，响应最多 256 KB |
| `echo.ui.setPage(page)` | `ui.page` | 替换插件页面 |
| `echo.log(message)` | 无 | 显示在插件详情里，最多 40 行 |
| `echo.after(ms, fn)` / `echo.cancel(id)` | 无 | 不能快于 1 秒，每个插件最多 8 个 |

没有已启用并且拿到 `playback.read` 的插件时，ECHO 不会为插件订阅播放进度。脚本不在主线程上跑，也不在音频回调里跑。单次调用超过大约 2 秒会停用这个插件，播放继续。关掉或删除插件时，它的定时器会清掉。

脚本里不能使用 Java 或 Android API。`java.lang.Runtime` 这类访问会被拒绝。

应用里的教程页可以一键安装上面的示例插件。把同一份 `echo-plugin.json` 和 `main.js` 打成 zip，也能从插件页导入。
