# Prompt for PC ECHO: receive a phone cast stream

You are working in https://github.com/moekotori/echosteam (ECHOSteam). Do not use any other PC repo path.

Android can already remote-control PC library playback and pull a PC track onto the phone. This prompt adds the reverse: Android casts the song playing on the phone to ECHOSteam over LAN.

Keep Phase 1 / Phase 2 Echo Link contracts unchanged. Add compatible command bodies under the existing `POST /echo-link/v1/playback/command`. If the command is unknown, return HTTP 400/404/501 with a short JSON/text error. Do not treat it as `playTrack`.

## Commands

```json
{
  "command": "playRemoteStream",
  "target": "pc",
  "quality": "original",
  "positionMs": 42000,
  "streamUrl": "http://<phone-lan-ip>:<port>/echo-link/cast/<token>",
  "track": {
    "id": "phone-track-id",
    "title": "Song",
    "artist": "Artist",
    "album": "Album",
    "durationMs": 240000
  },
  "audio": {
    "codec": "flac",
    "mimeType": "audio/flac",
    "sampleRateHz": 96000,
    "bitDepth": 24,
    "channelCount": 2,
    "lossless": true
  }
}
```

```json
{
  "command": "queueReplaceRemote",
  "target": "pc",
  "startTrackId": "phone-track-id",
  "items": [
    {
      "id": "phone-track-id",
      "streamUrl": "http://<phone-lan-ip>:<port>/echo-link/cast/<token>",
      "title": "Song",
      "artist": "Artist",
      "album": "Album",
      "durationMs": 240000
    }
  ]
}
```

Rules:

- Play on the PC output. Do not import the phone file into the PC library.
- `streamUrl` is a short-lived HTTP URL on the phone. Use GET/HEAD with `Range`. Do not log the token.
- `quality` is `original`: play the bytes as-is. Do not transcode, resample, or downsample.
- `audio` is optional. When present, use it to pick WASAPI exclusive / bit-perfect output when the device can match `sampleRateHz` / `bitDepth` / `channelCount`.
- HTTP responses may also send `X-ECHO-Link-Codec`, `X-ECHO-Link-Sample-Rate`, `X-ECHO-Link-Bit-Depth`, `X-ECHO-Link-Channels`, `X-ECHO-Link-Lossless`. Prefer JSON `audio` if both exist.
- Keep `Content-Type` stable (`audio/flac`, `audio/wav`, `audio/x-dsf`, …). Do not wrap the body in another container.
- The phone stays on the same LAN and keeps serving bytes while PC is playing those items.
- Report progress through existing `/status` so Android can keep acting as the remote.
- If Android sends `queueReplaceRemote` then `playRemoteStream`, replace the queue first, then start the start track at `positionMs`.
- Reject non-LAN URLs. Do not follow redirects off-LAN.
- DSD (`dsf`/`dff`) and other unsupported-on-PC formats should fail with a clear error, not silent transcode.

## Validation

Add focused tests for unknown-command errors, queue replace + play dispatch, and Range fetches against a local fixture URL. Do not copy Android code into this repo.
