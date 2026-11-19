# Pass W4 — Assistant surface (branch `pass/w4-assistant`, rebased onto main `a5f16d1` after the W4 theme merge)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `app/src/test/java/com/materialchat/assistant/ui/**`. No theme, chat, home, settings-screen, script or workflow file was edited. Nothing pushed. No emulator, no device.

## Commits

1. `4924cf1` feat(assistant): provider-rejected (401/403) notice state with an Open settings action, typed-status path behind a ViewModel adapter; voice settings dialogs on ExpressiveShapes.Dialog + dialogEntrance
2. `d86d5c4` fix(assistant): notice surface yields height to the pill and the answer toolbar (weighted at both hosts, message scrolls, action chip keeps 40dp) so screen-withheld + opened pill + keyboard at font scale 1.3 no longer overruns the window
3. (this file) docs(assistant): W4 REPORT.md

## Gates

- `:app:compileGithubDebugKotlin` rc=0 and `:app:compileGithubDebugAndroidTestKotlin` rc=0 after each part.
- `:app:testGithubDebugUnitTest`: **667 tests, 0 failures, 0 errors, 0 skipped** after each part (`AssistantCopyTest` 9 (+3), `AssistantNoticesTest` 11, `AssistantContrastTest` 11, `ThemeContrastTest` 18).
- No test, testTag, contentDescription, script or workflow file touched. No `FontStyle.Italic` under the owned paths. Pill geometry (76dp row, 38dp corner, `AssistantKeyboardSpacing`), keep-awake, particles, recognizer/TTS and directives untouched.

## Part 1 — provider-rejected (401/403) state

### The pin on `AssistantNoticesTest.requestFailuresNameConnectivityAndHideEverythingElse`

The test was NOT retargeted. It still asserts that `IllegalStateException("HTTP 401 {...}")` maps to `REQUEST_FAILED`, and that assertion still holds: the new state is reachable only through a typed HTTP status, never by matching "401" in a body. The test was extended (three new lines, `AssistantNoticesTest.kt` :137-142) to say so explicitly: a typed 401 selects `PROVIDER_REJECTED`, the same words with `httpStatus = null` stay generic, and an `IOException` outranks a typed 401. The W4 brief allowed re-pinning that line; it turned out not to be necessary because the typed path makes text-matching unneeded, which is the safer design (a provider echoing a status number in an unrelated error body cannot send the user to the key settings).

### Design

- `AssistantCopy.kt` :43-47 `PROVIDER_REJECTED` ("The provider rejected the request. Check the API key in settings."); :80-102 `requestFailure(error, httpStatus: Int? = null)`. Order: connectivity (any `IOException` in the chain) > typed 401/403 > typed 429 > rate-limit text markers > generic. A typed status outside those three leaves the text path in charge (a quota body on a 500 still names the limit).
- `AssistantNotices.kt` :16 new action `OpenSettings("Open settings")`, distinct from `OpenApp("Open app")` (no provider) and from `RetryRequest`; :60-61 a `PROVIDER_REJECTED` error resolves to `OpenSettings` before the retry rule, so the resting line stays "Ask anything" rather than "Try again" (re-sending cannot fix a key).
- `AssistantViewModel.kt` :696-701 the stream error passes `httpStatus = streamingErrorStatus(state)`; :919-927 the adapter `streamingErrorStatus(state: StreamingState.Error): Int?` returns `null` today. When the chat delegate's `StreamingState.Error.httpStatus` lands, the body becomes `state.httpStatus` — one line, no other change, and both paths are already tested (`AssistantCopyTest.aTypedStatusIsAuthoritativeForRejectionAndRateLimit`, `withoutATypedStatusTheTextPathNeverSelectsRejection`). `httpStatus` was not on `origin/main` at `a5f16d1` (grep over `domain/model/StreamingState.kt`). New `openSettings()` emits `AssistantEvent.OpenSettings` (:1120).
- `AssistantOverlay.kt` :178-179 / :534-535 `onOpenSettings` parameter on both overlay layers; :314 event routing; :631 notice-action dispatch; :1667 settings icon for the new action (same `assistant_notice_action` tag as every non-screen action).
- `MaterialChatVoiceInteractionSession.kt` :360 wires it; :433-445 `openSettings()` launches `MainActivity` with action `com.materialchat.OPEN_SETTINGS` through the same assistant-authorised launcher (plain-launch fallback, overlay hides on success). See "Needs another owner" for the MainActivity side.
- Tests: `AssistantCopyTest` +3 (typed path authoritative, text path never selects rejection for six real provider phrasings, rejection resolves to `OpenSettings` with no retry and is distinct from rate-limit/no-connection); `copyIsOneSystem` and `everyNewNoticeFollowsTheCopyRules` now include `RATE_LIMITED` and `PROVIDER_REJECTED`.

### Contrast (Part 4 of the brief)

The new state adds no fill/content pair: the notice is the existing error-notice container and the "Open settings" chip is the existing outlined notice-action chip (`error notice action` pair, `AssistantContrast.kt` :230, already gated in all 39 appearances by `AssistantContrastTest.noticeActionChipReadsOnBothNoticeFillsInEveryTheme` and `ThemeContrastTest`). Nothing was added to `pairs()` because nothing new is painted; adding a duplicate pair would make the notice-action count assertion (`assertEquals(2, ...)`) fail without gating anything new.

### Settings panel (from `docs/audit-cross-surface-w4.md` section 7)

`AssistantVoiceSettingsPanel.kt` :78-86 and :94-97: both `AlertDialog`s now use `shape = ExpressiveShapes.Dialog` and `Modifier.dialogEntrance()` (the loading dialog had neither). `AssistantScreenContextSettingsPanel.kt` has no dialog; nothing to do.

## Part 2 — state walk round 2 (360dp, font scale 0.85 / 1.3)

Static, reasoned from the code. Sheet width 336dp (360 − 2×12). Notice row width 280dp. Answer surface cap `screenHeight × 0.55`; with the IME up (~280dp on a 640dp screen) the sheet has ~344dp of height. bodyMedium at 1.3 ≈ 9.5dp/char → ~29 chars per notice line; at 0.85 ≈ 6.2dp/char → ~45.

| # | State | 0.85 | 1.3 | Result |
|---|---|---|---|---|
| 1 | Screen withheld + attachments open (4 thumbnails + Screen chip) + keyboard open | opened pill 76 + 12+40 (Screen chip) + 16+72 (thumbnails) + 12+40 (attach chips) = 268dp; notice 131 chars = 3 lines ≈ 60+12+10+40+24 ≈ 150dp; 268+8+150 = 426 > 344 | notice 5 lines ≈ 200dp; 476 > 344 | **was** overrun: the sheet column had no weight, so the top of the notice went off the window. **fixed (c2)**: the notice's `AnimatedVisibility` gets `weight(1f, fill = false)` at the sheet (`AssistantOverlay.kt` :766-772) so the pill (geometry contract) wins; inside the notice the message row has the weight and the text `verticalScroll`s (:1651-1680), the action chip keeps its 40dp. In unbounded hosts the weight is a no-op and the six-line cap still applies. Keyboard mode hides the `TypeInstead` chip but not `ScreenSettings`, so the chip must stay reachable: it does. |
| 2 | Listening with a 200-char partial transcript | one line, `titleLarge` semibold ≈ 10dp/char → 20 chars visible | ≈ 13dp/char → 15 chars | OK: `TextOverflow.StartEllipsis` (W3) keeps the newest words; `maxLines = 1`; the text area is the weighted middle of the row (~190dp at 360dp with glyph 40 + mic 56 + close 40 + gaps). Nothing to fix. |
| 3 | Answer with a 40-line code block | answer list `weight(1f, fill = false)` inside the 0.55-screen surface; `MarkdownText` code block is `horizontalScroll` with `softWrap = false` (`MarkdownText.kt` :956-958), so long lines never widen the surface | same | OK: the LazyColumn scrolls vertically inside the cap, the toolbar (40dp strip) and the Using-screen marker stay below it because the list has the weight and the toolbar does not. The code block's own shape and font are chat-owned. |
| 4 | Notice + thumbnails + Screen chip in the pill at once (no keyboard) | notice 3 lines (150dp) + 8 + opened pill 196dp (no attach chips) = 354dp < 600dp sheet cap | 5 lines (200dp) + 8 + 196 = 404dp | OK without the keyboard; with it, case 1. The thumbnails row `horizontalScroll`s (`MessageInput.kt` :948) so four 72dp items (294dp + joins) fit the 300dp opened region and a fifth would scroll. |
| 5 | Thinking | pill glyph + "Thinking…" one line; quiet thinking surface 2 lines | same | OK (W2/W3). |
| 6 | Speaking | "Speaking…", tertiary glyph, Interrupt | same | OK. |
| 7 | Error, generic / connectivity / **provider rejected** / rate-limited | error-notice container; one action chip ("Try again" 9 chars / "Open settings" 13 chars ≈ 95+50 = 145dp at 1.0) | "Open settings" ≈ 123+50 = 173dp < 280dp | OK: every action label fits its row at 1.3; longer ones ellipsise on one line (W3). `PROVIDER_REJECTED` is 65 chars = 3 lines at 1.3. |
| 8 | No mic / recognizer unavailable | info/error notice + "Type instead" (hidden in keyboard mode) | same | OK. |
| 9 | No network | `NO_CONNECTION` + "Try again" | same | OK; outranks any typed status (tested). |
| 10 | Answer toolbar at 360dp | labelled Open-in-app chip or compact icon by measured width (W3) | compact | OK; the new notice weight inside the answer surface (`:1285-1293`) does not touch the toolbar's measurement. |

Motion: nothing new is animated; the two weights change layout only. No new timers, no new continuous animation, particle gate untouched.

## Shapes — can `AssistantShapes.Answer/Notice/Thinking` map onto theme tokens? (from `docs/audit-cross-surface-w4.md` section 2)

Not this wave, and here is why:

- `ExpressiveShapes` offers `SquircleShape(radius)` (uniform radius, `Shapes.kt` :30) and `TopSquircleShape` (top-only, :64). Every `AssistantShapes` surface except the pill and the chips is per-corner asymmetric: `Answer` 36/36/28/28, `Notice` 24/24/18/18, `Thinking` 20/20/8/20 (`AssistantExpressiveStyle.kt` :74-86). There is no per-corner squircle in the theme, and the theme agent owns `ui/theme/**`; the assistant must not add one.
- Mapping `Notice` onto `ExpressiveShapes.DisclosurePanel` (20dp uniform) or a future `ExpressiveShapes.Notice = SquircleShape(20.dp)` is possible once the theme adds the token; the assistant notice would then lose its 24/18 asymmetry, which is acceptable (the audit proposes exactly that). `Answer` could take `ExpressiveShapes.Sheet` (32dp uniform) at the cost of the 36/28 taper against the pill. `Thinking`'s 8dp bottom-start corner is the one intentional asymmetric hero shape (it points at the pill); it needs a per-corner squircle or stays `RoundedCornerShape`.
- Decision: left as `RoundedCornerShape` with the values unchanged, so the theme can add `SquircleShape(topStart, topEnd, bottomStart, bottomEnd)` and the assistant can switch the three sites in one commit without re-deciding radii. Recorded under "Needs another owner".

## Copy

"Try again" stays the assistant's error verb (`AssistantCopy.AFTER_ERROR`, `AssistantNoticeAction.RetryRequest/RetryListening`). The chat and home owners use "Retry" (`ChatScreen.kt:600`, `ConversationsScreen.kt:1317`, `BookmarksScreen.kt:170`, `SettingsScreen.kt:2860`, `MessageBubble.kt:1720` per the audit); whichever way that is decided once, the assistant's is the sentence-case verb form the audit's proposed `Copy` object names.

## Deliberately left alone

- `requestFailuresNameConnectivityAndHideEverythingElse`'s original four assertions (extended, not changed).
- Text-based 401/403 detection: not added, by design (see Part 1).
- Pill geometry, tags, content descriptions, keyboard spacing, keep-awake, particles, recognizer/TTS, directives, `SmoothStreamingText`.
- `AssistantShapes` values (see Shapes).
- `ScreenContextAccess.notice` wording (131 chars); the layout now holds it under every host.
- `AssistantScreenContextSettingsPanel.kt`: no dialog, no dp drift found.

## Needs another owner

- **chat** (`domain/model/StreamingState.kt`, `data/repository/ChatRepositoryImpl.kt`): add `httpStatus: Int?` to `StreamingState.Error` and populate it from `StreamingEvent.Error.code` when that is an HTTP status. Then in `AssistantViewModel.streamingErrorStatus` (:919-927) return `state.httpStatus`. Until then the 401/403 state is unreachable at runtime (the adapter returns null) and 401/403 render as the generic notice, exactly as before.
- **home-settings** (`MainActivity.kt` :201-212): handle `intent.action == "com.materialchat.OPEN_SETTINGS"` the way `OPEN_CHAT` is handled and navigate to `Screen.Settings` (or straight to the provider list). The session already sends it (`MaterialChatVoiceInteractionSession.kt` :433-445); today the app opens on its start destination, where Settings is one tab away. The constant should move to `MainActivity.companion` next to `ACTION_OPEN_CHAT` when that owner picks it up.
- **theme**: a per-corner `SquircleShape` (or `ExpressiveShapes.Notice`) so `AssistantShapes.Answer/Notice/Thinking` can leave `RoundedCornerShape` (see Shapes).
- (Carried) `util/AttachmentLimits.limitNotice()` copy, chat agent; `ThemeContrastTest` delegate-of-itself assertions, theme agent.

## Not verifiable without a device

- The 401/403 notice end to end: no stream can carry a typed status until the chat change lands, so the state has only been exercised through `AssistantCopy`/`AssistantNotices` JVM tests, never on a live provider rejection.
- `com.materialchat.OPEN_SETTINGS` reaching `MainActivity` through `startAssistantActivity` and the plain-launch fallback from the session (same path as `OPEN_CHAT`, which is verified; this action is not).
- The two new weights: that the sheet column really gives the notice the remainder above the opened pill with the IME up (depends on the host's measured height under `AssistantKeyboardSpacing`), that the notice text scrolls rather than clips at the squeeze point, and that the action chip stays inside the clip at font scale 1.3 on a 640dp screen.
- `verticalScroll` inside a `weight(1f)` text in a `Row` whose parent column is unbounded: reasoned as a no-op, not rendered.
- `ExpressiveShapes.Dialog` + `dialogEntrance()` on the voice-settings loading dialog: appearance not rendered.
- Everything carried from the W2/W3 lists (toolbar measured-width switch, `StartEllipsis` at API 26, six-line notice against the answer cap, rate-limit bodies beyond the sampled five, exit fade with `lastNotice`).
