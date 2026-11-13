# Assistant correction status

Updated: 2026-11-13T10:00:15Z

## Current goal

Repair the reported loss of speech input and missing neural-expressive assistant decoration. Deliver the correction through GitHub, not only local commits.

## Timeline and findings

- v2.29.18 changed recognition requests and selection. v2.29.19 added the first edge decoration. The user reports speech input now fails entirely and assistant decoration is absent.
- Source review proved the language-switch extra used a Boolean where Android requires a sensitivity string. Visibility of some installed recognizer does not validate the configured default. Earlier claims that the entire assistant-metadata/default-recognizer chain was proved were too strong.
- The original decoration used hardware-sensitive blur behind the opaque assistant card; the separate assistant composition also omitted saved pitch-black/neural preferences.

## Correction written

- Validate installed recognition-service candidates and use the explicit-component Android factory. Prefer a valid configured component, then a deterministic external service. Do not modify the user's selected recognizer.
- Bound initial startup to eight seconds, with at most one available on-device fallback before capture begins. Preserve final results after Stop and reject stale callbacks.
- Use documented Android extra types. Multilingual switching is opt-in; the installed recognizer owns silence timing.
- Propagate saved assistant appearance preferences. Draw a gradient halo and outward particles on an unclipped parent outside the opaque card. Preserve card bounds and keyboard spacing; stop decorative animation when hidden, reduced-motion or power-saving conditions apply.

## Verified locally

271 JVM tests passed, zero failures/errors/skips. Production Kotlin and Android instrumentation tests compile. These tests do not establish phone transcription quality.

## Hosted acceptance added

Android 26 and 35: real Android Intent/Bundle request types; production explicit-component binding to a deterministic test-only RecognitionService; compact/expanded production assistant card rendering with enabled/disabled screenshots; motion gates; actual system-assistant invocation and keyboard-edge geometry.

The test service returns a known transcript and is not an acoustic recognition benchmark. Screenshot tests use production card content; the separate system-invocation check is still necessary to establish the actual assistant window.

## Review and remaining work

The initial independent review identified invalid default selection and missing startup bounds. A follow-up Sol/Upstage review failed because credentials were exhausted; no successful independent approval is claimed. Direct review and local checks completed. Hosted test execution, screenshot inspection, signed release, and actual phone transcription remain pending.
