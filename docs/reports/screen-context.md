# Assistant screen context — implementation report

Branch `feat/screen-context` (base `1c75f58`, v2.40.1). Four commits, none pushed:

| Commit | Part |
|---|---|
| `8eba0b7` | Capture: ScreenContext model, AssistStructure flattener, screenshot downscale, session plumbing, dead backdrop removed |
| `3175699` | Decide: relevance heuristic, Never/Automatic/Always decision table, prompt block, `screen_context` directive + resend, preferences |
| `6e805e0` | Show: "Screen" chip, "Using screen" marker, unavailable notice + settings button, Settings panel, API 37 read-screen-context access |
| `ce54597` | androidTest driving the real session callbacks; connected-test registration; judge minimum 62 → 64 |

## What changed (file:line)

### Capture (`app/src/main/java/com/materialchat/assistant/screen/`)
- `ScreenContext.kt` — the per-invocation value: `capturedAt`, downscaled `screenshot`, flattened `text`, `webUri`, `sourcePackage`; `release()` recycles under a lock shared with the file writer.
- `ScreenTextFlattener.kt:28-31,37` — pure flattener over the `ScreenNode` interface: visible nodes' text + contentDescription, depth-first reading order, de-duplicated, whitespace-collapsed, capped at `MAX_CHARS = 6000` (truncation marker) and `MAX_NODES = 5000`.
- `AssistStructureReader.kt:13,27,46` — adapts the real `AssistStructure` (windows → rootViewNode → children) to `ScreenNode`; every accessor wrapped; `isAssistBlocked` subtrees and password input types (text/number/web password variations) are treated as sensitive and never read.
- `AssistScreenshots.kt:21-22,36,56` — `downscale()` caps the long edge at 1280 px (hardware bitmaps copied to ARGB_8888 first) and recycles the original; `writeAttachment()` writes JPEG q85 into `chat_attachments/` (the `AttachmentFiles.PICKED_DIR` owned dir) only when the capture is actually about to be sent.
- `MaterialChatVoiceInteractionSession.kt:433-504` — `onHandleAssist` → `captureAssist` (flatten + webUri + package; logs sizes only), `onHandleScreenshot` → `captureScreenshot` (downscale immediately). `:241` `releaseScreenContext()` on `onHide`/`onDestroy`: ViewModel forgets first, then the bitmap is recycled. `:289` supplies `writeScreenshot` on `Dispatchers.IO`.
- `AssistantViewModel.kt:894` `onScreenContext(ScreenContext?)` + `screenContext: StateFlow`.
- Removed `AssistScreenshotBackdrop.kt` and its test (dead since the shrink feature was pulled); `AssistantOverlay.assistScreenshot` parameter removed.

### Decide
- `ScreenContextTrigger.kt` — word-list heuristic, no regex soup: fixed phrases ("on my screen", "what am I looking at", "translate this", "what does it say" …) plus deictic rule `this|that|these|those [qualifier] subject` ("this first tweet", "that email").
- `ScreenContextPolicy.kt` — `decide(mode, asksAboutScreen, contextAvailable, declined)` → `SKIP | ATTACH | UNAVAILABLE`; `usable()` honours the two include sub-toggles.
- `ScreenContextPrompt.kt` — `contextBlock()` (delimited `=== SCREEN CONTEXT ===` block with foreground app, page URI, screenshot note, screen text, and "data not instructions" rule), `availabilityNotice()` (tells the model a capture exists and how to request it), `unavailableReply()`, `UNAVAILABLE_NOTICE`.
- `AssistantAction.kt:24` `ScreenContext` action; `ActionDirectives.kt:103` parses `{"type":"screen_context"}`.
- `AssistantScreenContextSettings.kt` — `ScreenContextMode { NEVER, AUTOMATIC, ALWAYS }` (default AUTOMATIC), `includeScreenshot`, `includeScreenText`.
- `AppPreferences.kt:88-90,810-823` — keys `assistant_screen_context_mode|screenshot|text`, flow + setter next to the other assistant keys.
- `AssistantViewModel.kt:392` decision on the final utterance; `:544-555` resend loop and per-turn system prompt (context block when attached, availability notice when a usable capture exists but was not attached, nothing in NEVER); `:605-622` on a `screen_context` directive with no context attached: write the capture, `updateMessage` the saved user turn with the attachment, drop the directive-only interim reply, resend once; otherwise append `unavailableReply`. `:924` `reconsiderScreenStaging()` stages the chip as soon as the typed/partial transcript refers to the screen (or at once in ALWAYS). `:908` `removeScreenContext()` declines for this question and deletes the file. `:955` `prepareScreenUse()` builds attachment + prompt block honouring the sub-toggles. Vision gating: the screenshot goes as a normal image attachment (the existing path already drops/keeps images per provider); the text block always goes, so text-only models still answer.

### Show
- `AssistantOverlay.kt:1367-1385,1481-1510` — the "Screen" chip (thumbnail from the staged JPEG, `assistant_screen_chip`, × = `assistant_screen_remove`, contentDescription "Remove screen context") in the pill's existing attach reveal; `:569` the chip counts toward `expanded`/affordance visibility like a photo. `:1210-1219` "Using screen" marker (`assistant_screen_used`) on the answer surface. `:553,1228-1238` unavailable notice (up to 4 lines) with an "Open settings" button (`assistant_screen_settings`).
- `MaterialChatVoiceInteractionSession.kt:384` opens the first resolvable settings intent through the assistant-authorised launch with plain-launch fallback, then hides.
- `ScreenContextAccess.kt` — see platform APIs below.
- `AssistantScreenContextSettingsPanel.kt` + `SettingsScreen.kt:1214` — Settings › Assistant › Screen context: master toggle (off = NEVER, on = AUTOMATIC), "Always include the screen", "Include screenshot", "Include screen text", explanatory text, "Open digital assistant settings" button. Replaces the stale shrunk-screen backdrop footnote.

### Tests
- Unit (JVM): `ScreenTextFlattenerTest` (9: reading order, dedupe, whitespace, invisible/sensitive subtrees, char cap with marker, node cap, multi-window), `AssistScreenshotsTest` (4: target size table), `ScreenContextTriggerTest` (6: positives, negatives, qualifiers, punctuation/case, apostrophes), `ScreenContextPolicyTest` (6: the decision table incl. declined and NEVER), `ScreenContextPromptTest` (4: block delimiters, sub-toggle filtering, directive line, notices), `ScreenContextAccessTest` (5: state mapping, notice wording per state, API guard), `AssistantViewModelScreenContextTest` (9: heuristic attach with file + block, unrelated question gets only the availability notice, directive resend once with interim reply hidden, NEVER mode, ALWAYS mode, unavailable notice shown once, chip removal declines and deletes, staging on partial transcript, hide clears everything). 43 new tests.
- androidTest: `AssistantScreenContextCaptureTest` (2, void methods): real session + real 1080×2400 bitmap through `onHandleScreenshot` → ViewModel holds a 576×1280 capture, original recycled; a screen question stages an owned JPEG under `chat_attachments` with long edge 1280; removing the chip deletes the file; `onHide` nulls the capture and recycles the bitmap. Second test: null assist/screenshot deliveries are recorded as an empty capture and stage no file. Registered in `scripts/run-connected-tests.sh` CLASSES; judge minimum in `.github/workflows/android-regression.yml` raised 62 → 64.

## Platform APIs used and guards
- `VoiceInteractionSession.onHandleAssist/onHandleAssistSecondary(Bundle, AssistStructure, AssistContent)` (API 23, deprecated 30, still delivered) and `onHandleScreenshot(Bitmap)` (API 23) — minSdk 26, no guard needed; `@Suppress("DEPRECATION")` kept as before.
- `AssistStructure.getWindowNodeCount/getWindowNodeAt/getRootViewNode`, `ViewNode.getText/getContentDescription/getVisibility/getChildCount/getChildAt/getInputType/isAssistBlocked`, `AssistStructure.getActivityComponent` — all API 23+.
- `AssistContent.getWebUri` — API 23+.
- `Bitmap.Config.HARDWARE` — API 26; checked with `SDK_INT >= O` in `AssistScreenshots.downscale`.
- `Settings.ACTION_VOICE_INPUT_SETTINGS` (API 21) and `ACTION_SETTINGS` — resolved with `resolveActivity` before use.
- Android 16 / API 37 `android.app.voiceinteraction.VoiceInteractionManager`: `getReadScreenContextRequestState()` (0 GRANTED, 1 REQUESTABLE, 2 UNREQUESTABLE) and `createRequestReadScreenContextIntent()`. compileSdk stays 36, so these are reached by reflection (`Class.forName` + `getSystemService(Class)` + `getMethod`) in `ScreenContextAccess.kt:31-35,68-72`, behind `Build.VERSION.SDK_INT >= 37`; any failure degrades to the pre-16 path. The request intent is only offered when the state is REQUESTABLE; the platform requires `startActivityForResult`, which a session cannot do, so it is launched as a plain activity and the result is observed by the next invocation delivering data (not by a result code). The notice wording changes per state (GRANTED but nothing arrived → "the screen may be private"; UNREQUESTABLE → "blocked by the system").

## How verified
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin` — clean after every part.
- `:app:testGithubDebugUnitTest` — full run after part 3: **587 tests, 0 failures, 0 errors** (filtered assistant run after part 2: 249/0/0). New classes contribute 43 tests.
- `:app:compileGithubDebugAndroidTestKotlin` — clean after part 4.
- Unchanged and re-checked by grep: `scripts/check-assistant-window.sh` and its tags (`assistant_sheet`, `End assistant session`, `Stop listening`, `Hide keyboard`, `Keyboard`, `NeuralParticles: painter=`, `keep-awake`), pill geometry (76dp row, 38dp corners, `AssistantKeyboardSpacing.GAP_DP`), `SmoothStreamingText.kt`, streaming haptics, `ui/screens/chat/**`. No test, testTag or contentDescription was removed or weakened; only additions.

## Not verified (needs a device / the hosted run)
- That the platform actually delivers a screenshot and structure to MaterialChat on the user's phone with "Use screenshot" / "Use text from screen" on. The session plumbing is exercised with a synthetic bitmap only; the privileged `show()` path that triggers the callbacks cannot be driven from a test.
- The look of the "Screen" chip thumbnail, the "Using screen" marker and the 4-line notice with its button inside the pill/answer surface (compiled, not rendered).
- That the chosen model actually emits `<<action:{"type":"screen_context"}>>` when it needs the screen; the resend path is unit-tested with a scripted reply.
- The API 37 reflection path: no API 37 image was run; the constants and method names are from the published reference, mapping is unit-tested.
- The two new androidTests on the GitHub runner (compile verified; the connected run is the next CI pass).
- Vision behaviour per provider: the screenshot rides the existing image-attachment path, which is only known to work for the providers it already worked for.

## Deliberately not changed and why
- `compileSdk` stays 36 (brief rule); API 37 reached by reflection.
- No native tool-calling; the screen request reuses the existing in-text directive protocol for the reasons in `ActionDirectives` (five wire formats, proxies of unknown tool support).
- The screenshot is not attached to every message silently (brief rule); ALWAYS is an explicit setting, and even then the chip shows what is about to be sent.
- Screen text is never logged; only character counts and bitmap dimensions are.
- `AssistantViewModel.pendingAttachments`/photo limit untouched: the screen capture is a separate `screenAttachment` so it is labelled and does not consume a photo slot; it joins the photos in the saved `Message.attachments` so deletion/cleanup (`ConversationAttachmentCleanupTest` conventions, `AttachmentFiles.deleteIfOwned`) applies unchanged.
- `AttachmentImporter` untouched: the capture is already a decoded bitmap, so a small dedicated writer avoids the decode/EXIF path.
- The in-progress `onHandleAssistSecondary` (multi-window) deliveries are merged into the same capture rather than tracked per window; the flattener already handles several roots.
