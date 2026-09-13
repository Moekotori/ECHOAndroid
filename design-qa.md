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
