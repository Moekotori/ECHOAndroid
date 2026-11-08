# Mobile ECHO Link manual IP connection

## Scope and current evidence

The Android client already accepts a bare IP, host:port, HTTP(S) URL and pairing links, but the manual entry is collapsed for saved PCs and has no separate port control. LAN direct access is an existing v1 client capability. The PC deliberately requires pairing when external access is enabled; this change preserves that contract.

Implementation stays in `:core:connect` and `:feature:connect`. No PC server changes, new wire protocol, public `:core:model` changes, Gradle dependencies, playback/cast/stream changes or permission bypass.

## Steps

- [x] Inspect both worktrees, current pairing/authorization behavior and existing parallel phone-sharing diffs.
- [x] Add a focused manual-address resolver in `:core:connect`: optional explicit port, existing parser semantics, pairing-link preservation and exact-origin matching for saved credentials. Never reuse a token for another host, protocol or port.
- [x] Update the existing manual form in `:feature:connect`: visible by default while disconnected, optional numeric port, and targeted validation. Keep the current connect callback/session owner.
- [x] Update existing manual-connection and direct-access error resources in all nine supported locales; add only the port label/error required by the requested control.
- [x] Verify focused connect tests, feature Kotlin compilation and localization. Attempt scoped lint if time permits. Each verification command is capped at 60 seconds, cumulative budget 120 seconds; no full APK, full suite or instrumentation test by default.

## Performance, risks and rollback

Address parsing and comparison are small, synchronous operations. Only the current form state and existing bounded saved-PC list are used. No network activity on input, new polling, timers, caches, or background jobs. Existing connection cancellation and authorization-failure handling remain authoritative.

Saved credentials are reused only for the exact scheme/host/port. A newly entered cross-network address still requires existing pairing authorization; entering an IP does not grant access. Pasted v1/v2 pairing capabilities retain their own target port and are never redirected by the manual port field.

Rollback consists of the new resolver/tests, the existing form/component integration and localized resources. Preserve the unrelated pre-existing parser, app session, cast and phone-sharing changes.

## Validation outcome

- `:core:connect:testDebugUnitTest` restricted to `EchoManualConnectionInputTest`, `EchoPairingParserTest`, `EchoLinkExternalAddressTest` and `EchoLinkDirectConnectionTest`: 22 tests passed, no failures/errors. The five new cases protect IP/custom-port defaults, strict credential origin binding, current credential precedence, pairing-capability destination preservation and invalid input rejection.
- `:feature:connect:compileDebugKotlin` and `checkLocalization`: passed in the same bounded invocation, 40.682 seconds. The compiler reported an existing unrelated redundant conversion in `RemoteSourcesPanel.kt`; it was not changed.
- `:feature:connect:lintDebug`: stopped at the explicit 45-second limit (45.098 seconds), during dependency lint analysis. No completed lint result is claimed, and no further verification was run after timeout.
- Total verification time approximately 85.8 seconds. JDK 21; offline, two Gradle workers, single-use daemon and in-process Kotlin compilation. Only this task's validation process tree was terminated.

No APK build/install, emulator/phone UI inspection, real LAN/Tailscale pairing or release was performed. First-time remote connections still require the PC's existing pairing authorization; the feature does not provide unauthenticated external access.
