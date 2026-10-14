# Echo Link phone library sharing v1

PC counterpart: https://github.com/moekotori/echosteam. Both applications must include this extension.

Connect the phone to the PC over LAN, then enable **Share phone library with PC** in Android Connect → PC Link. In ECHOSteam, open the window's top-right **More → Switch library** menu and select the shared phone. The main song view supports search, paging and selected-track playback; the same menu can switch back to local music or configured cloud libraries. Sharing starts disabled and is not restored across process restarts. The phone exposes only complete MediaStore / SAF local files, excluding remote sources and CUE clips. PC playback uses the original file; no import, copy, transcoding or metadata synchronization is performed. Unsupported playback formats follow the existing phone-stream player rules.

## Session registration

Android sends `POST /echo-link/v1/phone-library` with its existing PC authentication (paired Bearer, legacy v1 or explicit LAN direct headers). The JSON body is `{version: 1, sessionId, name, baseUrl, token}`. `sessionId` and `token` are separate random session capabilities, each 16–64 URL-safe characters. `baseUrl` is an HTTP origin with a numeric LAN IPv4 address and dynamic port. PC validates it against the request's TCP peer address and rejects browser Origin requests. The credentials stay in PC main-process memory.

The phone refreshes registration once per minute while enabled. PC retains at most four sessions, expires them after three minutes without refresh, rechecks original connection authorization, and removes them when Echo Link stops or the PC token rotates. `DELETE /echo-link/v1/phone-library/{sessionId}` removes only a session belonging to that authenticated connection. Phones close their server and connections immediately on explicit stop, disconnect, PC switch or a registration failure. An unreachable unregister can leave a stale device name visible until expiry; its media URLs are already revoked.

## Read-only phone endpoints

- `GET /echo-link/phone/v1/library/lyrics?id=...`: same sharing Bearer/peer restriction. Resolves one shareable local song's selected/imported, local embedded/sidecar or matched cached lyrics without online requests or source-file writes. Returns `{lyrics:null}` or public line/word timings, translation and romanization; source labels, URIs and provider metadata remain private. Limited to 4000 lines, 16000 words, 100000 characters and 512 KiB. Android parsers already apply source offsets; user adjustments are baked into outgoing timestamps with `offsetMs:0`, preventing double application on desktop. Older phones returning 404 remain readable but supply no shared lyrics. Both applications need this extension for phone lyrics display.

- `GET /echo-link/phone/v1/library/tracks?page=1&pageSize=50&q=...`: requires `Authorization: Bearer <sharing token>`. Returns `{version: 1, tracks, totalCount}`. Page size clamps to 1–100, query length to 256, page to 1–100000. PC uses 50 per page. Ordering is title (case-insensitive), then stable ID. Filtering and pagination run in Room, with a transaction for count/page consistency.
- `GET /echo-link/phone/v1/library/track?id=...`: same authentication; returns `{track}` or 404. Lookup is restricted to the same local-file scope as the paged query. PC resolves a fresh item before starting playback.
- Each track has `id`, `title`, `artist`, `album`, `durationMs`, `streamUrl`, optional `artworkUrl` and `audio`. It contains no content URI, local path or remote provider credentials.
- `GET/HEAD /echo-link/phone-stream/{token}/{base64url-track-id}` serves the original file and byte Range via the existing bounded HTTP stream server. `phone-art` serves local artwork when available. These URLs are session-only capabilities restricted to the connected PC peer. They must not be copied into logs or diagnostics.

The sharing server has four HTTP workers/connections and no full-library cache. Database and media access execute off the main/audio threads. Android keeps the explicit session alive with a connected-device foreground service and a Stop sharing notification. Its lifecycle is independent from casting to PC/DLNA/Chromecast.

PC rejects redirects, foreign stream origins/paths, more than 50 rows and JSON responses over 512 KiB. Requests time out after eight seconds, with at most four in flight; removal cancels outstanding requests. Replaced queries and renderer teardown cancel obsolete reads. Renderer IPC contains only device IDs/names and the current page's public track metadata. Playback uses the existing temporary phone-stream queue path and native player, with IDs namespaced by sharing session.

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
