# Extended audio format fixtures

These are synthesized 997 Hz tones, not recorded/copyrighted music. Each is
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

Reference PCM: `ffmpeg -i tone.EXT -f s16le -c:a pcm_s16le -`.
