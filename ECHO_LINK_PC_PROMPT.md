# Prompt for PC ECHO: ECHO Link Phase 1 Server

You are working in `G:\ECHO-main`, the PC ECHO Electron/TypeScript repo. Implement the PC side for Android ECHO Link Phase 1. The Android client is already implemented in `G:\ECHOAndroid` and expects the protocol below.

Goal:
- Android can connect to PC ECHO over LAN.
- Android can read a small preview of the PC library.
- Android can ask PC ECHO to play a PC-library track on the PC.
- Android can request a temporary HTTP stream URL and play the PC-library track on the phone with Media3.

Do this inside the existing PC architecture. Do not put server logic in the renderer. Prefer main-process service boundaries under the existing connect/library/playback surfaces. Reuse the existing library service/store and playback command path. Reuse or mirror existing HTTP media-server patterns such as Connect/HQPlayer media serving where appropriate.

Android contract:

Base URL:
`http://<pc-host>:26789/echo-link/v1`

Every request sends:
- `Authorization: Bearer <pairing-token>`
- `X-ECHO-Link-Version: 1`

Pairing URI Android can parse:
`echo://pair?host=<lan-ip>&port=26789&token=<token>&name=PC%20ECHO&scheme=http`

Required endpoints:

1. `GET /echo-link/v1/status`

Response:
```json
{
  "device": { "id": "pc-device-id", "name": "PC ECHO" },
  "playback": {
    "state": "playing",
    "track": {
      "id": "track-id",
      "title": "Song",
      "artist": "Artist",
      "album": "Album",
      "artworkUrl": "http://host:26789/echo-link/v1/artwork/token",
      "durationMs": 240000,
      "sourceLabel": "Local Library",
      "canPlayOnPhone": true
    },
    "positionMs": 42000,
    "durationMs": 240000,
    "volume": 0.72,
    "outputMode": "WASAPI Shared",
    "updatedAtEpochMs": 1780000000000
  }
}
```

States Android understands: `idle`, `loading`, `playing`, `paused`, `stopped`, `error`.

2. `POST /echo-link/v1/playback/command`

Bodies Android sends:
```json
{ "command": "playPause" }
{ "command": "next" }
{ "command": "previous" }
{ "command": "stop" }
{ "command": "seekTo", "positionMs": 42000 }
{ "command": "setVolume", "volume": 0.72 }
{ "command": "playTrack", "trackId": "track-id", "output": "pc" }
```

Return either the same shape as `/status`, or at least `{ "playback": { ... } }`. If no playback object is returned Android will refresh `/status`.

3. `GET /echo-link/v1/library/tracks?page=1&pageSize=12&q=<query>`

Response:
```json
{
  "tracks": [
    {
      "id": "track-id",
      "title": "Song",
      "artist": "Artist",
      "album": "Album",
      "artworkUrl": "http://host:26789/echo-link/v1/artwork/token",
      "durationMs": 240000,
      "sourceLabel": "Local Library",
      "canPlayOnPhone": true
    }
  ],
  "totalCount": 1234
}
```

Keep this endpoint paged and cheap. Do not load the whole library into memory for one phone preview.

4. `POST /echo-link/v1/library/tracks/:trackId/stream`

Body:
```json
{ "target": "phone" }
```

Response:
```json
{
  "streamUrl": "http://<pc-host>:26789/echo-link/media/<temporary-token>",
  "expiresAtEpochMs": 1780000300000,
  "track": {
    "id": "track-id",
    "title": "Song",
    "artist": "Artist",
    "album": "Album",
    "artworkUrl": "http://host:26789/echo-link/v1/artwork/token",
    "durationMs": 240000,
    "sourceLabel": "Local Library",
    "canPlayOnPhone": true
  }
}
```

The `streamUrl` must be playable by Android Media3. It must support HTTP Range requests, stable `Content-Type`, and seek. Do not expose raw Windows file paths to Android. Use temporary media tokens with expiry.

Security boundaries:
- Default to LAN only.
- Generate a pairing token on the PC side; do not hardcode a token.
- Reject missing or wrong bearer tokens.
- Do not expose library write APIs in Phase 1.
- Do not allow arbitrary path reads through the media endpoint.
- Clear errors should be returned as HTTP status + short JSON/text detail.

Suggested PC implementation shape:
- Add an `EchoLinkService` in main-process code, near existing connect services.
- It owns the HTTP server lifecycle, pairing token, endpoint status, and media-token map.
- It calls existing library APIs for paged track reads and track lookup.
- It calls existing playback command APIs for PC playback control.
- It uses an existing or shared media-serving helper for local/remote track streaming, with Range support.
- Add a small renderer/Connect-page surface only for enabling ECHO Link, showing host/port/token/QR text, and diagnostics.

Validation:
- Add focused tests for auth rejection, `/status`, command dispatch, library paging shape, stream token expiry, and Range responses.
- On Windows, prefer the existing narrow test strategy. If native ABI noise appears, use the repo's known `ECHO_SKIP_NATIVE_ABI=1` path for focused Vitest where appropriate.
- Do not run huge low-value test loops; run the smallest tests that prove the service contract and then a normal build/smoke check.
