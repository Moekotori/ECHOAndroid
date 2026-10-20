# Echo Link phone library sharing v1

PC counterpart: https://github.com/moekotori/echosteam. Both applications must include this extension.

Connect the phone to the PC over LAN. Android now defaults **Share phone library with PC** to enabled and starts sharing automatically after a successful connection while the app is visible. The switch and notification Stop sharing action save the user's choice; disabling it remains effective after reconnection or app restart. A restored connection in the background defers service startup until the app becomes visible, while an existing share continues in the background. Each new sharing session uses fresh credentials. In ECHOSteam, open the window's top-right **More → Switch library** menu and select the shared phone. The main song view supports search, paging and selected-track playback; the same menu can switch back to local music or configured cloud libraries. The phone exposes only complete MediaStore / SAF local files, excluding remote sources and CUE clips. PC playback uses the original file; no import, copy, transcoding or metadata synchronization is performed. Unsupported playback formats follow the existing phone-stream player rules.

## Session registration

Android sends `POST /echo-link/v1/phone-library` with its existing PC authentication (paired Bearer, legacy v1 or explicit LAN direct headers). The JSON body is `{version: 1, sessionId, name, baseUrl, token}`. `sessionId` and `token` are separate random session capabilities, each 16–64 URL-safe characters. `baseUrl` is an HTTP origin with a numeric LAN IPv4 address and dynamic port. PC validates it against the request's TCP peer address and rejects browser Origin requests. The credentials stay in PC main-process memory.

The phone refreshes registration once per minute while enabled. PC retains at most four sessions, expires them after three minutes without refresh, rechecks original connection authorization, and removes them when Echo Link stops or the PC token rotates. `DELETE /echo-link/v1/phone-library/{sessionId}` removes only a session belonging to that authenticated connection. Phones close their server and connections on explicit stop, disconnect, PC switch or a terminal registration failure. Temporary status reconnects to the same authenticated PC retain the sharing session. Registration retries network failures and temporary HTTP responses at 5/10/20-second intervals, with at most four attempts and an 80-second deadline; authorization and unsupported responses fail immediately. An unreachable unregister can leave a stale device name visible until expiry; its media URLs are already revoked.

While sharing is enabled, the connection owner retains background status checks every 30 seconds (failure backoff up to 60 seconds), without background SSE or library loading. Once registration has succeeded, exhausting one transient renewal round retains the same sharing session and retries after 30 seconds instead of turning sharing off; initial registration failure still ends startup. A real network outage can interrupt audio or expire the PC lease, but restoration re-registers the existing phone session automatically. All jobs remain cancellable and stop on user disconnect/stop, identity change, revoked authorization or service destruction. Sharing stays enabled during an outage and can be stopped from its notification.

## Read-only phone endpoints

- `GET /echo-link/phone/v1/library/lyrics?id=...`: same sharing Bearer/peer restriction. Resolves one shareable local song's selected/imported, local embedded/sidecar or matched cached lyrics without online requests or source-file writes. Returns `{lyrics:null}` or public line/word timings, translation and romanization; source labels, URIs and provider metadata remain private. Limited to 4000 lines, 16000 words, 100000 characters and 512 KiB. Android parsers already apply source offsets; user adjustments are baked into outgoing timestamps with `offsetMs:0`, preventing double application on desktop. Older phones returning 404 remain readable but supply no shared lyrics. Both applications need this extension for phone lyrics display.

- `GET /echo-link/phone/v1/library/catalog?kind=...&page=...&q=...&parentKind=...&parentId=...`: same authenticated session and Origin restriction; returns `{catalog:{entries,total,page,pageSize:50}}`. Categories are songs, albums, artists, folders, playlists, liked and genres. Parent containers open their phone-local tracks. Queries read existing Room tables in bounded pages; smart playlists reuse the phone's rule predicate. Folder IDs use a session-owned table capped at 512 entries, without directory paths in responses. Non-shareable local clips may be shown with `canPlay:false`; original audio export still excludes CUE clips and remote files.
- Updated desktop builds keep the phone as the source for songs, albums, artists, genres, playlists and liked tracks, reusing desktop layouts and the normal local playback queue. The desktop Folder navigation always opens that computer's local directories, independently of phone selection. Older phones remain compatible with the original song endpoints; other categories require updating Android, and never fall back to the computer's records.

- `GET /echo-link/phone/v1/library/tracks?page=1&pageSize=50&q=...`: requires `Authorization: Bearer <sharing token>`. Returns `{version: 1, tracks, totalCount}`. Page size clamps to 1–100, query length to 256, page to 1–100000. PC uses 50 per page. Ordering is title (case-insensitive), then stable ID. Filtering and pagination run in Room, with a transaction for count/page consistency.
- `GET /echo-link/phone/v1/library/track?id=...`: same authentication; returns `{track}` or 404. Lookup is restricted to the same local-file scope as the paged query. PC resolves a fresh item before starting playback.
- Each track has `id`, `title`, `artist`, `album`, `durationMs`, `streamUrl`, optional `artworkUrl` and `audio`. It contains no content URI, local path or remote provider credentials.
- `GET/HEAD /echo-link/phone-stream/{token}/{base64url-track-id}` serves the original file and byte Range via the existing bounded HTTP stream server. `phone-art` serves local artwork when available. These URLs are session-only capabilities restricted to the connected PC peer. They must not be copied into logs or diagnostics.

The sharing server has four HTTP workers/connections and no full-library cache. Database and media access execute off the main/audio threads. Android keeps the explicit session alive with a connected-device foreground service and a Stop sharing notification. The service holds a partial CPU wake lock only during sharing, plus a legacy high-performance Wi-Fi lock on Android versions before 14; newer Android restricts that Wi-Fi lock to foreground/screen-on use. Service destruction, stop and terminal renewal failure release the locks. Explicit sharing therefore consumes additional battery without keeping the screen on. Its lifecycle is independent from casting to PC/DLNA/Chromecast.

PC rejects redirects, foreign stream origins/paths, more than 50 rows and JSON responses over 512 KiB. Requests time out after eight seconds, with at most four in flight; removal cancels outstanding requests. Replaced queries and renderer teardown cancel obsolete reads. Renderer IPC contains only device IDs/names and the current page's public track metadata. Playback uses the existing temporary phone-stream queue path and native player, with IDs namespaced by sharing session.

Shared artwork is read on demand through the existing bounded local/embedded artwork decoder and exported as JPEG with longest edge at most 512 pixels, without modifying source files or retaining an extra bitmap cache. The response uses the encoded body's MIME type and supports Range. Desktop artwork handling admits two jobs at a time and queues at most 64 waiting references with a 30-second overall deadline; each network read retains its eight-second and 2 MiB limits. This prevents large originals or a page of parallel image requests from being rejected as missing covers.

## Compatibility and acceptance

This is an additive optional extension. Existing PC library browsing, pairing, direct connections and phone casting retain their endpoints. A PC returning 404/405/501 on registration produces an Android update hint and sharing is stopped.

Focused checks cover auth/peer gates, bounded paging, response size, credential-free browse DTOs, selected-track playback relay, Range bytes, shutdown and the page's play/paging interactions. Real phone background behavior, firewall reachability, native output/seek and device-to-device continuity still require a real LAN acceptance run. Compilation and local HTTP tests do not establish those conditions.

## Local implementation checks (2026-10-04)

- Android: `checkModules`, `checkLocalization`, nine selected connect HTTP tests, and `:app:compileDebugKotlin` passed. No APK/device acceptance was performed.
- ECHOSteam: seven selected tests passed, including real local HTTP registration → browse → playback relay; quick main/preload/renderer build and Steam distribution bundle check passed.
- Browser component preview: light at 1000 px and dark at 380 px, selected-track play and paging passed with no horizontal overflow. This used fixture data, not a native PC or phone connection.
- Full PC typecheck was stopped at its 30-second limit, so remains unverified. Global theme-colour check reports existing untouched stylesheets; the new phone stylesheet has no reported findings. Checks ran with local Node 24; the project's declared Node 22 runtime and native playback remain outside this acceptance.

## Windows repair checks (2026-10-04)

- Android sharing now selects its bind address using a bounded TCP connection to the selected PC instead of the first enumerated LAN/VPN interface. Original-file and artwork Range responses clamp their end to the actual file length, including HEAD responses; invalid/out-of-bounds requests return 416.
- Android: 12 selected connect tests and `:app:compileDebugKotlin` passed. No APK or real-phone acceptance was performed.
- ECHOSteam: added the missing phone-library registration and session manager and typed IPC/preload extension in the Steam repository. The initial standalone Connect panel was replaced by the top-right library switch and the main song-list view. Local HTTP registration → browse → selected-track playback relay, paired compatibility, cancellation/expiry/limits and component interactions passed (8 selected tests), alongside 11 existing Connect IPC tests. The new source selector/main-list behavior and existing titlebar tests passed (19 tests). Focused manager/source-selection typechecks, quick main/preload/renderer build and Steam distribution check passed using Node 22.
- The top-right source menu and main phone song list were inspected in light/dark, with cloud-to-phone switching, paging and selected-row playback relay using synthetic data. Full PC typecheck was stopped; the global theme-colour check still reports unrelated existing stylesheets. Real LAN/firewall, screen-off continuity and native audio/seek remain unverified.

## Current PC integration (2026-10-13)

The current desktop checkout implements the same phone-library protocol and exposes it through More → Switch library. Android Connect → PC Link already connects its explicit sharing switch to the process-owned session, foreground service, authenticated registration, Room-backed pages and original-file Range streams. Sharing now shows the matching PC menu instructions in English, simplified Chinese and traditional Chinese. No Android protocol, media writes, Room migration or playback engine changes are required. Real LAN/native output/background acceptance still requires both devices.

## Artist album detail scope (2026-10-14)

Shared catalog now supports `kind=albums&parentKind=artist&parentId=...`. Room filters album keys through the selected artist's shared local track credits before counting and paging; complete album metadata/counts remain intact. The response confirms the actual scope with `catalog.parent={kind:"artist",id:...}`. Updated desktop clients reject an older unscoped response instead of showing the whole library under an artist. Both sides must load these updates for this tab; existing category and track queries remain compatible. This adds no database migration, background scan, source-media write, or playback-engine change.

## CUE 分轨与协议补齐

共享手机 CUE 时只读打开原文件（去除本地 URI fragment），`sourceRange` 独立携带物理起止秒数；电脑选曲请求必须带 `sourceRange=1`，旧接收端明确返回 415。手机接收电脑 CUE 时将同一范围用于现有 Media3 clipping，缓存能力地址时同时保留范围；进度和 seek 仍相对单首曲目。仅 EchoLink 点对点传输允许分轨投送，DLNA/Chromecast 保持原有边界。

`queueReplace` 可携带 `startIndex` 区分同一歌曲的重复 occurrence。新版来源用 `supportsScopedSearch` 声明容器内搜索能力。共享歌词使用 4000 行、每行 1000 词、总计 16000 词/100000 字符和 512 KiB 响应上限。
