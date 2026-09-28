# Theme-aware glass refinement

Source visual truth: https://www.bilibili.com/video/BV1eJGJ6LEeP/ (home around 00:12; layered settings around 01:28). This is a material and motion reference, not a layout or artwork clone. ECHO's existing light/dark themes, typography, navigation, and artwork remain the implementation target.

## Evidence

Screenshots are stored under `/Users/moekotori/.codex/visualizations/2026/09/13/01a09a34-b2e4-7173-980c-368f715b6286/glass-review/`:

- `before.png`: existing dark home.
- `dark.png`: first implementation, dark home.
- `light.png`: revised material, light home.
- `dark-final.png`: revised material, dark library during a page transition.

Android API 36 ARM64 emulator, native 1080 × 2400 captures. No browser CSS scaling applies. The source was viewed in a 1280 × 720 browser video frame with a portrait recording inside it; no pixel-for-pixel fidelity claim is made. The home captures also differ in displayed layout scale, so typography/spacing differences are not attributed to this change. The final dark capture is a different route and is used only to inspect the persistent controls.

## Comparison and corrections

The reference uses soft colored backgrounds, translucent rounded controls, and layered page presentation. ECHO now derives ambient gradients from the active Material palette, uses a shared tinted substrate and directional rim for panels, navigation and the mini-player, and adds a small background recession as the player opens.

First pass: [P2] scrolling text remained too visible behind the mini-player. Increased the substrate opacity from 0.86/0.88 to 0.94 and strengthened the bottom scrim. Revised light capture shows a substantially fainter background behind the title/buttons; revised dark controls retain clear labels and highlights. This is simulated glass using compositing, not real-time backdrop blur or refraction.

Focused control review: title, artist, play/queue icons, navigation labels and selected state remain visible in both themes; rounded borders remain within the control bounds. No added layout overflow was identified in the captured states.

## Fidelity surfaces

- Typography: existing type choices retained; no font or text size edits. Visible control labels are legible.
- Spacing/layout: existing margins, touch sizes, list structure and item hierarchy retained. No portrait wallpaper or different app layout was copied.
- Colors/tokens: dark tinted glass and light milky glass follow the existing theme; ambient colors follow the active Material palette. No new theme preference or hardcoded global purple palette.
- Images: existing artwork/fallbacks and user wallpaper loading retained; no new bitmap or copied artwork.
- Copy: unchanged. The emulator's existing unavailable-audio message is present in the baseline and is outside this visual change.

## Verification and boundaries

- `:core:design:testDebugUnitTest` passed, including existing light/dark palette checks.
- `:app:assembleDebug --no-configuration-cache --no-parallel` passed after the visual correction; installed on the emulator.
- `git diff --check` passed; no AndroidRuntime errors were returned in the sampled startup log.
- A temporary test theme field was restored after the light capture. Other settings were preserved.
- The build includes concurrent workspace work; it is not an isolated release artifact.
- Native emulator window control was unavailable through the active computer-use surface. Complete expand/collapse, cancellation and predictive-back gesture validation remains open. Other emulator activity was observed, so further interaction was stopped.
- Lightweight mode omits added shadows, ambient washes and player recession. Static gradients add no periodic timer or animation. Device performance, custom-wallpaper extremes and older Android versions were not measured.

Final result: blocked

Blocker: full motion/gesture acceptance is not completed. Captured light/dark material states and build checks passed; no claim of complete device or release acceptance.

---

# Record sleeve player — visual QA

Date: 2026-09-27. Scope: the approved light song-detail mock, with icon-only bottom utilities, implemented in the existing Android player module.

## Evidence

- Selected reference: `C:/Users/Moe/.codex/generated_images/01a0e18e-65c5-7311-81ee-a70ad6700189/exec-e535f23c-7b87-47b3-a61a-f8cd454fe52e.png` (839 × 1874).
- Final Android screenshot: `C:/Users/Moe/.codex/visualizations/2026/09/27/01a0e18e-65c5-7311-81ee-a70ad6700189/sleeve-final.png` (1080 × 2400).
- Full comparison: `C:/Users/Moe/.codex/visualizations/2026/09/27/01a0e18e-65c5-7311-81ee-a70ad6700189/comparison.png`.
- Typography/seek comparison: `C:/Users/Moe/.codex/visualizations/2026/09/27/01a0e18e-65c5-7311-81ee-a70ad6700189/type-comparison.png`.
- Android 16 emulator, 420 dpi / 2.625 density; physical viewport approximately 411 × 914 dp. Native status/navigation bars remain OS-owned. The comparison crops them to 1080 × 2244 and scales each image to equal width while preserving aspect ratio. The mock's content-only viewport is taller; it is not stretched to disguise the difference.
- Both final images show the King Crimson cover page with pause controls. Live progress differs by a few seconds. The app uses real embedded artwork and full metadata, including album subtitle and the song's edition suffix; the generated reference simplified these.

## Comparison and fixes

1. Initial capture (`sleeve-first.png`): excessive title contrast, thin body font, and bottom utilities outside the first viewport. Replaced the initial font choice with fixed upstream font instances and reserved space for the controls in a bounded layout.
2. Intermediate capture (`sleeve-static-fonts.png`): cover became too small with the full long title. Kept the edition suffix in a separate small text line, corrected small-text line heights, and reduced excess spacing. The title remains complete, and the cover can use the remaining height.
3. Final comparisons: Cormorant Garamond Medium for display text, Outfit Regular for metadata; both fonts are bundled with OFL licenses. Refined title size/weight against the reference. The native thin scrubber uses a single drawing origin so the dot is centered on its line.

## Required fidelity surfaces

- Typography: serif display title, normal sans-serif artist/metadata, tracked uppercase album and brand. Title scales within 28–46 sp for long names. The comparison includes a focused typography crop. CJK/system fallback and very large accessibility text have not been visually sampled.
- Layout: square artwork, fine divider, title and favorite on the same row, one quality line, thin scrubber, five transport controls, then two icon-only utilities. All controls are visible at the verified viewport. Short windows and enlarged text can scroll. Native insets and extra real metadata account for the modest cover-size difference from the mock.
- Colors: paper `#FAF7F2`, ink `#242830`, wine `#78364B`; dark system icons on the paper screen, restored when leaving it.
- Artwork: real album image retained with square crop and no rounding. The source mock contains an AI-redrawn rendition; replacing real artwork with that rendition would be incorrect.
- Copy: real title/artist/album/format/position; no visible queue or cast captions. Accessibility descriptions remain present. Playback settings moved to the top ellipsis; artwork tap and horizontal paging still reach lyrics.

## Validation

- `:feature:player:testDebugUnitTest`: 36 tests, zero failures/errors, including edition suffix preservation.
- `:app:assembleDebug`: passed after final UI/font edits; APK installed on the emulator.
- Runtime: playback session reached `PLAYING`; pause, previous-track action, seek position update, queue opening, artwork-to-lyrics navigation, and return swipe observed. Playback paused after verification.
- No recent AndroidRuntime error observed during the smoke check.
- No PC/protocol/audio-engine edits. No instrumented or lengthy performance tests. No claim of physical-device audio quality, measured performance improvement, cast-device connectivity, or tablet visual acceptance.

## Remaining polish

- P3: the existing Android shuffle/repeat/queue vectors have slightly heavier strokes than the generated mock. Their actions and selected states remain functional.
- Expected adaptation: real album subtitle and edition qualifier add text absent from the mock; native system insets reduce available content height.

No remaining actionable P0/P1/P2 finding in the verified portrait flow.

final result: passed


---

# Pixel handheld and type poster — 2026-09-28

- Selected pixel reference: `C:/Users/Moe/.codex/generated_images/01a0e18e-65c5-7311-81ee-a70ad6700189/exec-f9c93e98-0ee3-4827-8b53-e1db5326c3f6.png`.
- Selected poster reference: `C:/Users/Moe/.codex/generated_images/01a0e18e-65c5-7311-81ee-a70ad6700189/exec-48397b4d-51e5-4270-bbaf-8e83d88294cb.png`.
- Native captures: `build/player-styles/pixel-final.png`, `build/player-styles/poster-final.png`; picker: `build/player-styles/four-styles.png`.
- Combined visual comparisons: `build/player-styles/pixel-comparison.png`, `build/player-styles/poster-comparison.png`. Both reference and implementation were inspected together. Captures are 1080 x 2400 on Android 16 / 420 dpi. OS bars were cropped to 1080 x 2244; each content image was scaled to the same 400 px width, without stretching its aspect ratio. Native available content height is shorter than the generated mock.

## Findings and corrections

- Initial P1: poster autosizing clipped the visible letter shapes vertically. Replaced it with actual glyph-bound sizing and Android end ellipsis. Final capture shows complete letters; the long third line ends with an ellipsis, as explicitly requested by the user. Accessibility retains the full line text.
- Initial P2: the pixel controls used ordinary icons and flat square surfaces. Added upstream Pixelarticons resources, stepped outlines, offset shadows, a larger primary glyph and an outlined segmented timeline.
- Initial P2: ordered dithering produced conspicuous checkerboard blocks. The cover now uses bounded, cancellable error diffusion to a two-color palette, cached by Coil. Actual embedded album art is retained rather than replacing it with the image generator's altered artwork.

## Fidelity surfaces

- Fonts: bundled Pixelify Sans Bold and VT323 for the handheld; Anton and Outfit for the poster. Font/icon licenses are included. Poster glyph height and end ellipsis were reviewed in a full-resolution native capture.
- Layout: handheld LCD grouping and asymmetric square transport, and poster three-line title / large clock / black transport bar are distinct. Bottom utilities remain icon-only. Native insets, full album metadata and the edition suffix account for spacing differences from the reference.
- Colors: handheld forest/pistachio two-tone treatment; poster acid yellow/black. Real cover artwork is processed once per cache miss, not on playback ticks.
- Artwork: actual device album data, bounded decode and transformed bitmap size, crisp nearest-neighbor display. The generated reference altered the album face; pixel-for-pixel likeness of that artwork is not claimed.
- Copy: real title/artist/album/audio format/time. Long title and album content ellipsize; full title data remains intact.

## Verification

- Four styles visible in the existing settings drawer; switched between both new styles and verified the selection survives reinstall/relaunch.
- Existing progress/transport callbacks retained; on-device playback updates observed. Final captures leave playback paused.
- Player logic tests (39), preference tests, module graph and localization checks passed before visual refinement. Final native APK build passed after refinement.
- Concurrent builds caused shared output/cache failures. Final validation used isolated module build directories and a private project cache; source files from parallel work were preserved. Final artifact: `build/player-styles/clean-modules/app/outputs/apk/debug/ECHOAndroid-26.9.28-debug.apk`.
- No physical-device performance claim. CJK fallback and accessibility font extremes have not been visually exhaustively sampled.

Remaining P3: minor icon-shape and real-metadata spacing differences from the generated artwork. No remaining clipping or inaccessible primary control in the verified portrait state.

final result: passed
