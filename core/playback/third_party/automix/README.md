# AutoMix source integration

Source: `automix (1).zip`, supplied by the ECHOAndroid user, 2026-10-25.
Original package: `dev.t1m3.qplayer`, Apache-2.0 (see LICENSE.md).
Only Java audio algorithms and their logger are included. The CLI, prebuilt JARs,
scripts, Git metadata and JCodec decoder are not included. Android supplies decoding.
The Apache license is also packaged in the playback module's license assets.

Android changes to the supplied sources:

- Decoder.withOpener scopes decoder creation to one preparation worker.
- URI envelope scans reuse a four-entry cache only within that preparation;
  provider content is re-read on the next preparation.
- AiMix reads its two analysis windows sequentially on that worker, instead of
  using an unbounded cached thread pool; its window limit respects the host PCM budget.
- Harmonic and onset analysis use one worker; analysis frame and cue-candidate
  loops check the scoped cancellation/deadline budget. Harmonic plans use an
  eight-entry LRU cache instead of an unbounded map.

ECHO uses AiMix + AutoCueTransition behind the existing smart-transition option.
Preparation is interruptible and time limited. Full outgoing scanning is streaming;
analysis decoding is restricted to local mono/stereo sources at up to 48 kHz and
tracks up to 15 minutes. Unsupported codecs or plans outside the configured budget
retain ordinary playback. Model weights are absent in the supplied package; no
model downloads or network requests are added.
