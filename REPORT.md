# Pass W9-lite — theme + unowned copy sinks

Branch `pass/w9-theme`, base `8d270c70` (v2.41.8). Single agent, single worktree. Not pushed.

## Commits

| Commit | Part | Summary |
|---|---|---|
| `1c0186c9` | 1 | docs(theme): rule the pressable corner class once; pin card-class resting shapes |
| `241580f7` | 2/3 | refactor(theme): delete the zero-caller M3ExpressiveLoadingIndicator hero; fix stale indicator KDoc |
| `0a0d5837` | 3 | feat(copy): dash rule in CopyRules (comma in a phrase, full stop in a notice); nine dash lines rewritten; pinned at theme and home-settings |
| `06afc149` | 4 | refactor(theme): ExpressiveFastScrollBar glyph on ExpressiveSizes.IconLarge (value-identical 24dp) |
| `155dfa86` | 5a | fix(copy): no exception or SDK text in the update card, billing snackbar, TTS init/configure results, neural install failures or the auth loopback page |
| `a107163c` | 5b | test(copy): on-disk copy scanner over assistant/** and the three data sinks |
| (this commit) | 6 | docs: REPORT.md |

Before each commit: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin`, `:app:testGithubDebugUnitTest` (final run: 878 tests, 0 failures, 0 errors, 0 skipped).

## Already closed on main before this pass (verified, not redone)

The W8 convergence audit predates the W8 theme merge. These items were found already done at `8d270c70`: `CopyRules.RATE_LIMITED` / `NO_CONNECTION` (`ui/theme/CopyRules.kt`), the Expressive chip wrappers at 40dp, `CustomShapes.MessageInputContainer`, the `ExpressiveTopBarTitle` KDoc, `ContentBlockHeader`, `PersonaCard` on `ExpressiveShapes.Card` (W7).

## Part 1 — pressable corner class (`1c0186c9`)

- `ui/theme/Shapes.kt` (+19): KDoc rules the class once — buttons / FAB / icon buttons rest on `RoundedCornerShape` and press-morph (deliberate); cards, sheets, dialogs and content blocks are squircle. `CircleShape` glyph rule KDoc added here (part 3 item).
- `ui/theme/ShapeMorphing.kt` (+9): same ruling from the morph side (`expressiveControlShape` stays on `shapeMorph()`, orchestrator ruling).
- `app/src/test/.../ui/theme/ShapeClassRulingsTest.kt` (new, 155 lines): on-disk scanner listing resting-shape sites per class; fails if a card-class surface rests on a round shape.

## Part 2 — dead code (`241580f7`)

- `ui/components/ExpressiveLoadingIndicator.kt` (−226): the `M3ExpressiveLoadingIndicator` hero had zero callers across `app/src/main`, `app/src/androidTest`, `scripts/`, `.github/` (grepped before deletion). Remaining composables in the file all have callers.
- `ui/theme/Motion.kt` (22 lines): stale indicator KDoc re-located and fixed; `ui/theme/MotionTokensTest.kt` adjusted to the corrected wording only (no assertion weakened).
- `ExpressiveSizes.TileGlyph`, `ExpressiveSpacing.RowGap`: already absent at base; nothing to delete.

## Part 3 — dash rule (`0a0d5837`)

Ruling taken: no em dash and no spaced en dash joins two clauses; a one-phrase line uses a comma, a notice splits into sentences; unspaced en dash in a range is a number. Exempt by shape: the bare `\u2014` empty-value glyph and the picker `"$name \u2014 $id"` separator. Pinned in `CopyRules.kt` (EM_DASH / SPACED_EN_DASH, `passesSharedRules`), `CopyRulesTest.kt` (+15), `HomeSettingsCopyRulingsTest.kt` (+23). Nine lines rewritten: `ModelAssignmentsScreen.kt` ×2, `SettingsScreen.kt` ×1, `WorkflowExecutionScreen.kt` ×2, `ArenaModelPicker.kt` ×1, `ScreenContextAccess.kt` ×2, `ScreenContextPrompt.kt` ×1, `TaskModelAssignment.kt` ×1.

## Part 4 — icon literals (`06afc149`)

- `ui/components/ExpressiveFastScrollBar.kt`: glyph literal → `ExpressiveSizes.IconLarge` (value-identical 24dp).
- `ExpressiveLoadingIndicator.kt` ~:622: the literal was inside the hero deleted in part 2; no site left.

## Part 5a — unowned copy sinks (`155dfa86`)

Rule applied everywhere: the user-facing string is a fixed `CopyRules` / `VoiceCopy` line; the exception or SDK text goes to `Log` only.

**`data/repository/UpdateManager.kt`** (consumer: `SettingsScreen.kt:3059-3062` renders `UpdateState.Error.message` under the update card)
- `:99`, `:108`, `:180`, `:231` — `Log.e(TAG, …, e)` added at each catch.
- `:101`, `:110` — `error.message ?: "Failed to check for updates"` → `failureNotice(e, "check for updates")`.
- `:182` — `e.message ?: "Download failed"` → `failureNotice(e, "download the update")`.
- `:205` — `"APK file not found. Please download again."` → `UPDATE_FILE_MISSING` = "The downloaded update is no longer on this device. Download it again."
- `:233` — `e.message ?: "Installation failed"` → `failureNotice(e, "open the installer")`.
- `:239-247` — `failureNotice`: `IOException` anywhere in the cause chain → `CopyRules.NO_CONNECTION`; else `"${CopyRules.failureLine(action)}. ${CopyRules.TRY_AGAIN}."` (a notice, `isNotice` shape).
- `:375-384` — `private companion object { TAG = "MaterialChatUpdates"; UPDATE_FILE_MISSING }`.
- Imports `:8` `android.util.Log`, `:15` `CopyRules`, `:27` `IOException`. The internal `throw Exception("Download failed: HTTP …")` / `"Empty response body"` are left: they are caught in the same method and mapped by `failureNotice`, never shown.

**`data/monetization/MonetizationManager.kt`** (consumer: `SettingsViewModel.observeMonetizationEvents` → `SettingsEvent.ShowSnackbar(message)`, `SettingsScreen.kt:277`)
- `:61-69` — `emitFailure(line, detail)`: snackbar gets `line`, `detail` (billing `debugMessage` / `AdError.message`) goes to `Log.w(TAG)`.
- `:169` `"Could not start purchase"`, `:197` `"Could not show the ad"`, `:234` `"Could not complete the purchase"` (was `"Purchase failed"`), `:266` `"Google Play Billing is not available"` (was `"Billing unavailable"`), `:293` `"Could not load product details"`, `:322` `"Could not restore purchases"`, `:375` `"Could not acknowledge the purchase"` — all through `emitFailure`; the `x.ifBlank { … }` provider pass-through is gone.
- `:182` — `"Rewarded ad is loading. Please try again shortly."` → `"The ad is still loading. Try again in a moment."`
- `:5` import `Log`, `:45` `TAG = "MaterialChatBilling"`.

**`assistant/voice/TextToSpeechManager.kt`** (consumers: `AssistantViewModel.kt:185` only branches on the type; `AssistantVoiceSettingsViewModel.kt:107,138` likewise and show their own fixed lines; so `TtsInitResult.Error.message` is never rendered today — fixed anyway so a future consumer cannot leak it)
- `:119` — `error("Failed to initialize text-to-speech")` → `error("engine reported an initialization failure")` (internal, caught at `:124`).
- `:125` `Log.w(TAG, …, error)`; `:130-131` → `TtsInitResult.Error(VoiceCopy.SPEECH_UNAVAILABLE)`.
- `:159` `Log.w`; `:160` → `TtsInitResult.Error(VoiceCopy.VOICE_CONFIGURATION_FAILED)`.
- `:278-279` `TAG = "MaterialChatSpeech"`; `:282` KDoc on `TtsInitResult.Error`.
- `assistant/voice/VoiceCopy.kt:48-49` new `VOICE_CONFIGURATION_FAILED` = "The selected voice or language could not be applied. Check voice settings."; `:94` added to `samples()` so `AssistantCopyTest` rule-checks it.

**`assistant/voice/SherpaModelStore.kt`** (consumer: `SherpaInstallEvent.Failed.message` → `SherpaModelState.Failed` → `AssistantVoiceSettingsPanel.kt:362` and `VoiceCopy.neuralInstallFailed` in the pill notice — shown)
- `:34-39` — `SherpaInstallException(val line: String)`: non-null by type; KDoc states the line is a `VoiceCopy` sentence or a fetcher fixed line, never exception text.
- `:176` — `failure.message ?: VoiceCopy.INSTALL_FAILED` → `failure.line`.
- `OkHttpSherpaArtifactFetcher.kt:44-45` throw `SherpaInstallException` with fixed lines; unchanged.

**`data/auth/NativeAuthManager.kt`** — reach established per line:
- `:152` `throw IOException("GitHub device code expired. Please try again.")` — **left alone**: thrown out of `authenticate`; `SettingsViewModel.kt:601-607` catches every exception and shows `CopyRules.failureLine("sign in")` in `authStatus`, `apiKeyError` and the snackbar. The text is diagnostics only. The scanner's `throw`/`Exception(` exemption covers it.
- `:474` loopback `LoopbackResult.Failure(…, "State mismatch. Please retry sign-in.")` — **shown** in the browser tab via `writeLoopbackHtml` → "The sign-in response did not match this request. Return to MaterialChat and try again."
- `:501` `"Return to MaterialChat and retry sign-in."` (Ignore branch page) → "Return to MaterialChat and try again."
- `:544` `<p>Please retry sign-in.</p>` (Antigravity loopback page) → `<p>Return to MaterialChat and try again.</p>`.
- `onStatus(…)` lines (`:119,:141,:181,:185,:189,:213,:249`) reach the form's `authStatus`; none violates a rule; unchanged.

**`SettingsViewModel.kt`** sink (brief: ~`:244`, `:420-422`) — re-located to `observeMonetizationEvents` at `:418-422`. No change there: the fix is at the source (`MonetizationManager`), and the file is already under `HomeSettingsCopyRulingsTest`. The new scanner covers the two data files instead of extending `ownedFiles`.

## Part 5b — scanner (`a107163c`)

`app/src/test/java/com/materialchat/assistant/AssistantCopyRulingsTest.kt` (227 lines, 9 tests). Scans every `.kt` under `assistant/**` plus `UpdateManager.kt`, `MonetizationManager.kt`, `NativeAuthManager.kt`. Comments, raw strings and `Log.`/`Timber.` lines exempt. Rules:
- `.message ?:` / `.localizedMessage ?:` fallback outside a `throw`/`…Exception(` line;
- `${x.message}` interpolation outside a wrapping line;
- `.message.ifBlank {` / `.debugMessage.ifBlank {` (provider text with a fixed line as mere fallback);
- literal opening with a `CopyRules.BANNED_FAILURE_VERBS` verb;
- `please` in a sentence-case literal (a lower-case-opening literal is a parser token — `AssistantAction.kt:179` filler list); `SpeechRecognitionManager.kt` diagnostics are exempt only while `AssistantCopy.recognitionError(ERROR_CLIENT, text)` maps them to `RECOGNITION_FAILED`;
- `Retry` label; ASCII `...`;
- a self-check that each pre-rewrite shape (the exact v2.41.8 lines) is a hit, so pattern edits cannot silently reopen them;
- the five sink files are in the scanned set.

Verified red→green: with `app/src/main` stashed to `8d270c70` the scanner failed 4 of 9 (`banned failure verb`, `provider fallback`, `exception fallback`, `please`); restored, all 9 pass.

## Part 6 — particle API gating (verification only, no change)

`grep -rn "SDK_INT\|RequiresApi\|ChecksSdkIntAtLeast" app/src/main/java/com/materialchat/ui/theme/particles/`:
- `RuntimeParticlePainter.kt:82`, `:90` `@RequiresApi(33)` on the AGSL factory and class; `:150` `createParticlePainter` gates on `SDK_INT >= 33` with `StaticParticlePainter()` as the fallback and on a null shader.
- `CapsuleSoftEdge.kt:71-80` `resolveParticleEdgeTreatment`: `Blur` only when `sdkInt >= 31 && hardwareAccelerated && blurRadius > 0`, else `Gradient`. `NeuralParticleField.kt:273` calls it with `Build.VERSION.SDK_INT`; `:286-288` creates the `BlurEffect` layer only when `blurred`.
- `CapsuleBackdropBlur.kt:39`, `:52`, `:63` gate on `SDK_INT >= S` (31).
- `EdgeGlow.kt:148`, `:258` guard `rootWindowInsets` corner-radius reads on `SDK_INT >= 31`; the glow itself needs no RenderEffect (`:7`).
- JVM pins found: `CapsuleSoftEdgeTest.kt`, `CapsuleBackdropBlurTest.kt`. No JVM pin exists for the `createParticlePainter` 33-gate (it constructs a `RuntimeShader`, not instantiable on the JVM); left as is.

Finding: every RenderEffect / AGSL path is gated with a static fallback. No ungated path. Physics and visuals untouched.

## Left alone, and why

- `util/AttachmentImporter.kt:100` `Log.e(TAG, "Failed to import …")` — log line, exempt (brief).
- `util/AttachmentImporter.kt:101` `ImportResult.Failure("Couldn't load this image")` — opens with a banned verb and may reach the user; outside the brief's list (`util/` is not in part 5). See Needs another owner.
- `assistant/voice/SpeechRecognitionManager.kt:109,:123,:211` "… Please try again." — `ERROR_CLIENT` diagnostics; pinned by `SpeechRecognitionManagerTest`, mapped by the pill to `RECOGNITION_FAILED` (`AssistantCopyTest:278-290`). Rewriting them would require editing pinned tests (forbidden). Scanner exempts exactly this mapping.
- `assistant/voice/SherpaTtsBackend.kt:92,:134` `.message ?:` inside `SherpaSynthesisException(...)` / `throw SherpaLoadException(...)` — wrapping; the backend maps to `VoiceCopy.neuralLoadFailed/neuralSynthesisFailed` at `:112/:114`.
- `assistant/voice/OkHttpSherpaArtifactFetcher.kt:44` "Download failed (HTTP <code>). Try again later." — shown, passes every scanner rule, pinned by `SherpaModelStoreTest:79-81`; not a banned-verb opener. Carries an HTTP code; candidate for a later rewording, not changed here.
- `NativeAuthManager.kt:472` `"OAuth failed: $error"` — browser page shows the provider's OAuth `error` query value. Not exception text; left.
- `NativeAuthManager.kt:152` — see part 5a.
- `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` — byte-identical to base (`git diff 8d270c70 -- <paths>` empty).

## Needs another owner

- `util/AttachmentImporter.kt:101`: `"Couldn't load this image"` → `CopyRules.failureLine("load this image")`; confirm `ImportResult.Failure.message` reaches a snackbar/notice first.
- `OkHttpSherpaArtifactFetcher.kt:44`: drop the HTTP code from the user line if the "no API names" rule is read to cover status codes; keep the code in a `Log`. Would require updating `SherpaModelStoreTest:79-81`.

## Not verifiable without a device

- The settings update card rendering the new `UpdateState.Error` notices (`SettingsScreen.kt:3059-3062`), including the `NO_CONNECTION` branch on an airplane-mode check.
- The settings snackbar text for each `MonetizationManager` failure path (needs Play Billing / AdMob failures on a real device).
- The three rewritten loopback HTML pages in the browser during Codex / Antigravity sign-in.
- `VoiceCopy.VOICE_CONFIGURATION_FAILED` and `SPEECH_UNAVAILABLE` from a real engine init / configure failure (JVM tests cover the mapping only).
- The `SherpaInstallException.line` path end to end (real download failure in voice settings and the pill fallback notice).
- Particle gating behaviour on API 26-30 / 31-32 / 33+ devices (only the JVM `resolveParticleEdgeTreatment` and backdrop-blur pins ran).
- All part 1-4 visual claims (shapes, loading indicator, scroll-bar glyph size) — compile and JVM scanners only.
