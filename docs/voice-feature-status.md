# Voice assistant feature verification

Branch: `feature/voice-assistant` (pushed, unmerged, unreleased).
Verified source HEAD: `a89c632`.

## Implemented

- On-device Android recognition capability selection with installed-service fallback.
- Turn-owned recognition, generation, speech queue and interruption.
- Sentence speech before paragraph completion, cumulative snapshot deduplication and final flush.
- Independent persisted provider/model/reasoning settings.
- Queued Android TTS with voice configuration.
- Optional encrypted-key Gemini TTS backend and cancellable AudioTrack PCM playback.
- Compact overlay and expandable voice UI.
- Speech deadlines reported as speech errors instead of cancelling generation.

## Reproducible local checks

From this worktree with JDK 17 and SDK configured in local.properties:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testGithubDebugUnitTest :app:assembleGithubDebug --no-daemon --max-workers=2
```

Latest results: 193 JVM tests, zero failures/errors/skips; GitHub debug APK assembled.

APK: `app/build/outputs/apk/github/debug/app-github-debug.apk`
SHA-256: `f379e8d91c2c01f866068f03a4ed64b220a508166dd0e1b47c306b946ea407c5`
Package: `com.materialchat`; version name `2.29.10`; version code `158`. This is a debug build, not a feature release. Its debug signing key may prevent installation over the release APK; do not uninstall a user's app/data to bypass that without backup and explicit approval.

## Outstanding acceptance gates

No Android device is connected. Native recognition, audible playback, audio routing/focus, permission lifecycle, and rendered overlay/keyboard behavior have not been accepted on-device. Unit tests and assembly are not end-to-end acceptance.

No live Gemini API call has been made and no user credential has been requested or stored by this session. Google model compatibility/free-tier claims in `voice-gemini-tts.md` still require independent primary-source verification before presenting them as confirmed. Current backend supports explicitly listed raw-PCM generateContent identifiers, not the newer claimed 3.8 schema.

Settings currently accept a typed Android voice identifier rather than a browsable installed-voice picker/preview. Recognition language wiring and full hands-free continuation also need product acceptance; current interaction is turn-based and does not claim echo-safe full duplex.

Next work: production-path ViewModel lifecycle regression coverage; independent Google schema verification; installed-voice picker/preview; hosted or physical-device audio/UI acceptance. Never merge or release this feature without the user's instruction.
