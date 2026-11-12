# Assistant correction status

Updated: 2026-11-12

## Current changes

- Recognition prefers Android's configured service rather than forcing on-device recognition. This does not establish Gemini/Gboard accuracy parity.
- Finish-speaking preserves final results; cancellation rejects stale callbacks. Stop exceptions and an eight-second final-result deadline terminate cleanly.
- Automatic listening is consumed once per visible invocation, with permission and preference checks repeated across suspended loading. Hide invalidates pending startup.
- Keyboard spacing reserves only obstruction remaining inside the measured host. The hosted check measures the sheet edge in density-adjusted units and requires three stable valid samples.

## Verified locally

243 JVM tests passed with zero failures, errors, or skips. Android instrumentation sources compile. Recognition tests cover stop completion, stop exceptions, missing final callbacks, repeated end-of-speech, replacement callbacks, delayed stop/cancel, and cancelled-collector cleanup. ViewModel tests cover once-only auto-listening, hide/loading races, permission denial and disabled voice input.

## Independent review

Sol/Upstage reviewers identified lifecycle, timeout and geometry-verification defects. Lifecycle ownership and recognition termination were revised with deterministic regression tests. Final rendered-window acceptance remains required.

## Outstanding

Hosted API 26/35 attached assistant-window checks have not yet verified these changes. Actual recognition accuracy and microphone startup using the phone's installed recognition service remain unverified. No corrective release has been published for these changes. Do not equate JVM tests or callback-only instrumentation with visible system-assistant acceptance.
