# Extended audio format fixtures

The `tone.*` files are synthesized 997 Hz tones. Each is
0.25 seconds at 48 kHz, stereo. Generated using local FFmpeg 9.0.2; the app keeps
its pinned FFmpeg 6.1.6. `expected.json` records reference decoded s16le byte counts
and SHA-256 hashes. The test compares hashes only for integer AIFF and WavPack;
lossy decoder rounding is allowed to vary across FFmpeg versions/architectures.

Regenerate each `tone.*` with:

```sh
ffmpeg -f lavfi -i 'sine=frequency=997:sample_rate=48000:duration=0.25' -ac 2 CODEC OUTPUT
```

| Output | CODEC |
| --- | --- |
| tone.aiff | `-c:a pcm_s16be` |
| tone.aifc | `-c:a pcm_f32be` |
| tone.dts | `-c:a dca -strict -2 -b:a 768k -f dts` |
| tone.wv | `-c:a wavpack` |
| tone.wma | `-c:a wmav2 -b:a 128k -f asf` |
| tone.tta | `-c:a tta` (prepend an empty ID3v2.4 header to cover tagged files) |
| tone.w64 | `-c:a pcm_s24le` |

Reference PCM: `ffmpeg -i tone.EXT -f s16le -c:a pcm_s16le -`.

`sample.tak` is FFmpeg's 9.5-second FATE regression sample from
https://fate-suite.ffmpeg.org/lossless-audio/luckynight-partial.tak.
FFmpeg has no TAK encoder, so this is a separate reference fixture rather than a
synthesized tone. The three new lossless fixtures compare the entire decoded PCM
against host FFmpeg and then exercise a 100 ms seek. Data-module `.header`
fixtures retain only the first 160 bytes for bounded metadata parsing tests.
