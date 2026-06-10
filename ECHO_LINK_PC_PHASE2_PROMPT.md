# Prompt for PC ECHO: ECHO Link Phase 2

You are working in `G:\ECHO-main`, the PC ECHO Electron/TypeScript repo. Phase 1 should expose the Android-compatible ECHO Link HTTP server documented in `G:\ECHOAndroid\ECHO_LINK_PC_PROMPT.md`. Phase 2 adds pairing/discovery, queue/handoff polish, and better diagnostics. Keep everything inside main-process service boundaries; do not put LAN server logic in the renderer.

Android Phase 2 status:
- Android remembers the last PC endpoint in DataStore.
- Android can auto reconnect once per saved endpoint when the Connect surface is opened.
- Android can paste a full `echo://pair?...` URI or enter `host:port + token`.
- Android can scan a QR code using Google Code Scanner; the QR raw value must be the same `echo://pair?...` URI.
- Android can search PC library preview with `GET /echo-link/v1/library/tracks?q=...`.
- Android treats the linked PC ECHO library as an isolated source. When the linked library option is enabled, the Library tab shows only PC ECHO songs/albums and does not merge them into the local Android Room database.
- Android defaults to reading the linked PC ECHO library after connection unless the user disables the "default linked library" switch.
- Android can forget the saved PC endpoint.
- Android sends the bearer token when loading artwork URLs on the paired PC origin, so protected artwork endpoints are supported if they are same-origin with the ECHO Link server.
- Android can request PC lyrics after starting phone playback with `GET /echo-link/v1/library/tracks/:trackId/lyrics`, falling back to `GET /echo-link/v1/lyrics/:trackId`.

Do not change the Phase 1 contract. Add the items below compatibly.

## 1. Pairing UX

Add an ECHO Link panel on PC Connect/settings surface:
- Toggle ECHO Link server on/off.
- Show LAN host, port, device name, and current pairing token.
- Button to rotate token.
- QR code, QR payload text, and copy button:

```text
echo://pair?host=<lan-ip>&port=26789&token=<token>&name=<encoded-device-name>&scheme=http
```

Rules:
- Token must be generated, not hardcoded.
- Rotating token should invalidate old phone sessions after a grace period or immediately if simpler.
- Keep the server LAN-only by default.
- URL-encode the device name and token. Keep the QR text exactly parseable by Android's `EchoPairingParser`.
- If the PC has multiple network adapters, choose the best LAN IPv4 address by default and let the user copy/switch when needed.

## 2. LAN Discovery

Expose discovery so Android can later list PCs without typing:
- Advertise `_echo-link._tcp.local` via mDNS/Bonjour if a maintained dependency already exists or can be added safely.
- TXT records should include `name`, `version=1`, and a non-secret `deviceId`.
- Do not put the bearer token in mDNS.
- Discovery is optional at runtime; if mDNS fails, manual pairing must still work.

Suggested Android-facing future discovery shape:
```json
{
  "id": "pc-device-id",
  "name": "PC ECHO",
  "host": "192.168.1.12",
  "port": 26789,
  "version": 1,
  "requiresPairing": true
}
```

## 3. Library Search And Browse

Phase 1 only requires `/library/tracks`. Phase 2 should make it production-grade:
- `GET /echo-link/v1/library/tracks?page=1&pageSize=25&q=<query>`
- Android may request up to `pageSize=500` for the first linked-library screen; clamp server-side if needed, but return `totalCount` so Android can show the real library size.
- Return stable `id`, `title`, `artist`, `album`, `albumArtist`, `artworkUrl`, `durationMs`, `sourceLabel`, `canPlayOnPhone`.
- Keep paging cheap; do not load the full library.
- Do not ask Android to write these rows into its local Room library. This is a live linked-source preview/stream path, not a sync/import path.
- `artworkUrl` must be reachable from the Android device, not `localhost` or a Windows path. Prefer an absolute URL on the same `http://<pc-lan-host>:26789` origin. If the endpoint requires auth, accept the same `Authorization: Bearer <token>` header Android uses for ECHO Link.
- Add optional endpoints if they fit existing LibraryService boundaries:
  - `GET /echo-link/v1/library/albums?page=1&pageSize=25&q=<query>`
  - `GET /echo-link/v1/library/albums/:albumId/tracks`
  - `GET /echo-link/v1/library/folders?path=<path>`

If album/folder endpoints are too expensive right now, keep them out and document the omission.

## 4. Artwork And Lyrics

Artwork:
- Return `artworkUrl` on both `/status` track objects and `/library/tracks` rows when cover art exists.
- Serve artwork via a bounded token or same bearer auth. Do not expose raw local paths.
- Support common image content types such as `image/jpeg`, `image/png`, and `image/webp`.
- Keep artwork endpoints cheap: thumbnail or cached resized cover is fine; avoid blocking the server on heavy extraction for every list row.

Lyrics:
- Add `GET /echo-link/v1/library/tracks/:trackId/lyrics`.
- Optional legacy-compatible fallback: `GET /echo-link/v1/lyrics/:trackId`.
- Return either plain LRC/text or JSON:

```json
{
  "lyrics": "[00:01.00]line one\n[00:03.00]line two",
  "sourceLabel": "PC ECHO"
}
```

Accepted JSON text fields on Android: `lyrics`, `lyric`, `rawText`, `text`, `syncedLyrics`, `plainLyrics`, or nested `lrc.lyric` / `yrc.lyric` / `tlyric.lyric`.

Rules:
- Use the same bearer auth as the rest of ECHO Link.
- Return `404` or an empty body when no lyrics are available; Android will keep showing missing lyrics.
- Keep lyrics lookup off the renderer and inside existing library/metadata service boundaries.
- Do not write PC lyrics into Android local library; Android caches them in memory for the current linked playback session.

## 5. Queue And Handoff

Add compatible command bodies under existing `POST /echo-link/v1/playback/command`:

```json
{ "command": "playTrack", "trackId": "track-id", "output": "pc" }
{ "command": "handoff", "trackId": "track-id", "positionMs": 42000, "target": "pc" }
{ "command": "queueReplace", "trackIds": ["a", "b"], "startTrackId": "b", "output": "pc" }
```

Status response should include optional queue fields:
```json
{
  "playback": {
    "state": "playing",
    "positionMs": 42000,
    "durationMs": 240000,
    "volume": 0.72,
    "outputMode": "WASAPI Shared",
    "track": { "id": "track-id", "title": "Song", "artist": "Artist" },
    "queue": {
      "currentTrackId": "track-id",
      "items": [
        { "id": "track-id", "title": "Song", "artist": "Artist", "durationMs": 240000 }
      ]
    }
  }
}
```

Android currently ignores unknown fields, so this is safe to add now.

## 6. Phone Playback Stream Hardening

For `POST /echo-link/v1/library/tracks/:trackId/stream`:
- Return short-lived stream tokens.
- Support `Range`, `HEAD`, stable content length when known, and correct `Content-Type`.
- Add `expiresAtEpochMs`.
- Never expose Windows paths.
- If the source is remote/streaming and requires headers/cookies, proxy through PC and do not leak credentials to Android.
- If the source format is not Android-friendly, return a clear error for now; do not silently transcode unless a tested transcode path exists.

Suggested error body:
```json
{
  "code": "unsupported_format",
  "message": "This DSD source cannot be streamed to Android yet."
}
```

## 7. Diagnostics

Add diagnostics to PC UI and optionally status:
- Server running state.
- Bound addresses and selected LAN address.
- Last phone connection time.
- Last auth failure time/count.
- Last media token served and byte range summary.
- Last artwork/lyrics request result.
- Recent HTTP errors.

Keep diagnostics bounded; no unbounded logs in memory.

## 8. Tests

Add focused tests only:
- Pairing token generation and rotation.
- Auth reject/accept.
- `/status` shape.
- `/library/tracks` paging and query pass-through.
- Artwork URL is phone-reachable and accepts bearer auth.
- Lyrics endpoint returns LRC/text for a known library track and 404/empty for a track without lyrics.
- `playTrack`, `handoff`, and `queueReplace` command dispatch.
- Stream token expiry and Range response.
- mDNS failure does not prevent manual server operation.

Use the repo's existing focused Vitest patterns. If native ABI noise appears on Windows, use the known `ECHO_SKIP_NATIVE_ABI=1` targeted path rather than running broad low-value loops.
