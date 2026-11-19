# Pass W2 — Assistant surface (branch `pass/w2-assistant`, rebased onto main `621c05c`)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `ui/screens/settings/AssistantScreenContextSettingsPanel.kt`, `app/src/test/java/com/materialchat/assistant/ui/**`. No theme, chat, home, settings-screen, script or workflow file was edited. Nothing pushed.

## Commits (in order)

1. `bef48dd` refactor(assistant): delegate AssistantContrast's WCAG arithmetic to ThemeContrast
2. `e1c1871` feat(assistant): one copy system and one notice model across every state
3. `e4b19c6` refactor(assistant): settings panels on the shared Settings row/button pattern, sentence case, one helper style
4. `d7cf7e0` refactor(assistant): one spacing scale (AssistantSpacing) across pill, answer, thinking, notice and chips; status lines share the notice style
5. (this file) docs(assistant): W2 REPORT.md

## What was audited (file:line at base `621c05c`)

- `assistant/ui/AssistantOverlay.kt` :548-561 (notice precedence), :700-735 (answer/notice surfaces), :896-960 (state lines), :1155-1270 (answer surface, Using-screen marker, settings chip), :1553-1590 (notice surface)
- `assistant/ui/components/AssistantResponseCard.kt` :220-300 (thinking surface with its own `LoadingIndicator`)
- `assistant/ui/AssistantViewModel.kt` :319, :356, :500, :690, :712 (every string that reached the pill), :470-481 / :777-782 (TTS notice/error paths)
- `assistant/ui/AssistantContrast.kt` :24-98 (private WCAG copy)
- `assistant/ui/AssistantExpressiveStyle.kt` (type and shape tokens; no spacing tokens existed)
- `assistant/voice/VoiceState.kt` `SpeechRecognitionErrors.getErrorMessage` ("Please", "Client error occurred.", engine wording)
- `ui/screens/settings/AssistantVoiceSettingsPanel.kt` (raw `OutlinedButton` dropdown rows, `Button`/`TextButton`, periods on helper text, mixed helper styles), `AssistantScreenContextSettingsPanel.kt`
- `ui/screens/settings/components/SettingsExpressive.kt` and `SettingsScreen.kt` (the row/helper/dropdown pattern to copy)

## State table (what the user sees, before -> after)

| State | Before | After |
|---|---|---|
| Idle | sparkle glyph, "Ask anything" | unchanged (copy now from `AssistantCopy.PROMPT`) |
| Listening (no words yet) | waveform, "Listening…" | unchanged |
| Listening (partial) | large semibold transcript | unchanged |
| Thinking (request sent, nothing streamed) | pill: `LoadingIndicator` in secondary square + "Thinking…"; answer surface: a SECOND `LoadingIndicator` in a badge + "Thinking" / "Working on your answer" | pill is the sole owner of the working indicator; the thinking surface is a quiet placeholder (bold "Thinking" title, status "Working on your answer" in the shared secondary style), no second polygon morph |
| Streaming | pill still said "Thinking…" while text was already arriving | pill says "Answering…" (`AssistantCopy.ANSWERING`, own `kind`, so the switch animates once) |
| Speaking | sound glyph, "Speaking…" | unchanged |
| Error (request failed) | raw `Throwable.message` or "Assistant request failed. Check provider and voice settings." as a bare coloured line on the answer fill; pill "Try again" with nothing to tap | error-container notice surface with the copy-system phrase ("No connection. Check your network and try again." for any `IOException` in the cause chain, else "Something went wrong. Try again.") and ONE action chip "Try again" that re-sends the same question (`retryLastQuery`, question bubble reused, photos/screen not re-sent); pill state line "Try again" |
| Error (recognizer) | engine string verbatim ("Didn't catch that. Please try again.", "Client error occurred.", "Speech recognizer is busy.") | mapped by error code in `AssistantCopy.recognitionError`; action "Try again" reopens the mic, or "Type instead" for permission / no recognizer |
| Mic permission denied | "Enable microphone permission in system settings to speak." (info line) | "Microphone access is off. Allow it in system settings, or type your question." with "Type instead" chip (hidden in keyboard mode) |
| Voice input off | "Voice input is disabled in settings." | "Voice input is off in settings. Type your question instead." + "Type instead" |
| No provider | "No active AI provider. Add one in Settings." | "No AI provider is set up. Add one in the app." + "Open app" chip |
| Screen unavailable | notice text + separate tonal settings chip, both outside the notice | one notice surface carrying the message and "Open assistant settings" as its action (tag `assistant_screen_settings` kept) |
| TTS failed / fallback notice | bare line | same notice surface (error vs info container), no action (the answer is already on screen) |
| Attach limit | bare line | info notice, no action |
| Attachments open / Screen chip | unchanged geometry | spacing routed through `AssistantSpacing` (same dp values) |
| Keyboard mode | "Ask anything" placeholder | unchanged; "Type instead" chip hidden |

Notice lingering: the notice surface above the pill keeps the last notice while it fades out (`lastNotice`) so the exit does not snap to an empty 0dp box.

## What changed and why (file:line at HEAD)

### Contrast delegation (`AssistantContrast.kt`)
`:26` `BODY_TEXT`, `:29` `ICON`, `:35` `relativeLuminance`, `:38-39` `compositeOver`, `:45-46` `contrastRatio(fg,bg)`, `:49-50` `contrastRatio(fg, fills)`, `:57-58` `pick(fills, …)`, `:60-61` `pick(fill, …)` all delegate to `ThemeContrast`; the private luminance/composite/ratio/pick bodies and `opaque`/`linear` are deleted. `SCRIM_ALPHA`, `AssistantContrastPair`, `assistantFills`, the palette types and every call site keep their names. `readableOn/readableMutedOn/readableAccentOn` are NOT used by the palette: its candidate lists differ per surface (e.g. `error` tries `error, onErrorContainer, …`; `indicator` tries a translucent muted first) and switching would change resolved colours the matrix pins.

New pair: `noticeAction` (`AssistantContrast.kt` ~:180, :218, :300): the notice's action chip is outlined in the notice's own content colour, so the pair is the notice/error-notice content on each fill at `ICON`; `AssistantContrastTest.noticeActionChipReadsOnBothNoticeFills` (+1 test, 11 total).

### Copy system (`AssistantCopy.kt`, new)
One object for every phrase: phase lines (`PROMPT`, `LISTENING`, `THINKING`, `ANSWERING`, `SPEAKING`, `AFTER_ERROR`), `THINKING_TITLE`/`THINKING_STATUS`, `USING_SCREEN`, the six standing notices, `recognitionError(code, engineMessage)` (per-code mapping; the engine's "please"/engine-name wording never reaches the pill for a known code), `requestFailure(Throwable?)` (connectivity vs generic). Rules documented at the top of the file: present participle + ellipsis for phases, sentence-case notices with a period and the user's next step last, verb labels without a period.

### Notice model (`AssistantNotices.kt`, new; `AssistantNoticesTest.kt`, new, 11 tests)
`AssistantNotice(message, isError, action)` with `restingLine`; `AssistantNotices.resolve(...)` encodes the precedence (request/TTS error > recognizer error > info notice > screen unavailable > mic permission > voice off) and picks the single action. Tests enumerate every path, including that `TypeInstead` disappears in keyboard mode and that a TTS error after an answer carries no retry.

### ViewModel (`AssistantViewModel.kt`)
- `:317-322` missing recognizer -> `AssistantCopy.RECOGNIZER_UNAVAILABLE` with `ERROR_CLIENT`
- `:356-364` recognizer error -> `AssistantCopy.recognitionError(code, message)`
- `:508` -> `AssistantCopy.NO_PROVIDER`
- `:695-700` `StreamingState.Error` -> `AssistantCopy.requestFailure(state.error)` (exception text never shown)
- `:722` generic catch -> `requestFailure(e)`
- `:843-856` new `retryLastQuery()`

### Overlay (`AssistantOverlay.kt`)
- `:559` the notice is resolved by `AssistantNotices.resolve`; `:615-623` one `onNoticeAction` dispatch (retry request / reopen mic / enter keyboard / open app / screen settings)
- `:757` notice surface above the pill (keeps `lastNotice` through the exit)
- `:920`, `:947-960` state lines from `AssistantCopy`, `ANSWERING` distinguished from `THINKING`, resting line from the notice
- `:1255` Using-screen marker text from `AssistantCopy`
- `:1264` the answer surface shows the SAME `AssistantNoticeSurface` instead of a bare `Text` + separate chip
- `:1572-1640` `AssistantNoticeSurface(notice, onAction)`: glyph + message + one outlined action chip (`AssistantNoticeActionChip`, 40dp, 16dp icon, bold label, press morph on `ExpressiveMotion.Press.Shape`, no new fill); `assistant_screen_settings` tag preserved on the screen-settings action, `assistant_notice_action` on the others
- Removed: the separate `AssistantActionChip("Open assistant settings")` block and the `isError`/`notice: String?` parameters of `AssistantResponseSurface`

### Thinking owner (`AssistantResponseCard.kt` :220-265)
The thinking surface no longer draws a `LoadingIndicator`/badge; the pill's glyph is the one working indicator. Header/status from `AssistantCopy`. The `thinkingBadge` palette entry is kept (still a valid pair, still tested) so the matrix does not shrink.

### Settings panels
- `AssistantVoiceSettingsPanel.kt` (rewritten, 203 lines): `VoiceChoice` is now the Settings dropdown row (label over value, trailing arrow, `surfaceContainerHigh` tile, `DropdownMenu`), every action is `ExpressiveButton` (Filled for preview/download, Text for retry/refresh/stop/cancel/delete/remove-key), helper text is one `bodySmall`/`onSurfaceVariant` style via a local `Helper`, section titles `titleSmall`, sentence case, no trailing periods, Save/Close unchanged. Strings not pinned by any test (grep of `app/src/test`, `androidTest`, `scripts/`).
- `AssistantScreenContextSettingsPanel.kt` :118-122 the long platform note uses the same helper style and loses its trailing period; rows and the `assistant_screen_context_open_settings` button unchanged.

### Spacing scale (`AssistantExpressiveStyle.kt` :120-158)
`AssistantSpacing` (Hairline 2, Tight 4, Gap 6, Between 8, Row 10, Inset 12, ChipInset 14, Block 16, PillInset 18, SurfaceInset 20). Every padding/gap in `AssistantOverlay.kt` and `AssistantResponseCard.kt` is one of these (33 sites); values unchanged except the answer surface's top inset 22 -> 20 (`SurfaceInset`) and the opened pill's action/Screen-chip top inset 14 -> 12 (`Inset`). Left literal: the 76dp row, `AssistantKeyboardSpacing.GAP_DP`, elevations, waveform bar geometry, button corner radii. `AssistantTypography.thinkingStatus` now aliases `notice` (:195) so the window has two body styles: `answer` and `notice`.

## Deliberately left alone
- Pill geometry 76/38dp, every testTag/contentDescription, `AssistantKeyboardSpacing`, keep-awake, particle field, recognizer selection, TTS backends, action directives, auto-listen semantics, `SmoothStreamingText`.
- `VoiceWaveform.kt` and `AssistantAttachReveal.kt`: already on shared tokens; nothing to iron.
- `SpeechRecognitionErrors.getErrorMessage` wording: the overlay no longer shows it, and `SpeechRecognitionManagerTest` pins three of its strings.
- Markdown in answers: `AssistantBubble` is a bare `Column` with `MarkdownText` (no bubble since `d707616`); not re-enabled.
- No new timers in the composition; `lastNotice` is plain state.

## Needs another owner
- `ThemeContrastTest.themeAndAssistantContrastAgree` / `pickOnSeveralFillsRequiresTheCandidateToReadOnEveryOne` (`ThemeContrastTest.kt:215-237`) now compare `ThemeContrast` with a pure delegate of itself; theme owner may want to retarget them at `AssistantSurfacePalette` outputs. Not weakened here.
- `util/AttachmentLimits.limitNotice()` ("Maximum 4 images allowed") is not in the copy system's sentence form; shared with the chat composer and pinned by `AssistantViewModelAttachmentsTest`.
- `AttachmentPreviewRow` remove badge: unchanged, still checked as drawn.

## Verified
- `:app:compileGithubDebugKotlin` rc=0 after every part; `:app:compileGithubDebugAndroidTestKotlin` rc=0.
- `:app:testGithubDebugUnitTest`: 638 tests, 0 failures, 0 errors, 0 skipped (`AssistantContrastTest` 11/11, `AssistantNoticesTest` 11/11, `ThemeContrastTest` 14/14).
- No test, testTag, contentDescription, script or workflow file touched; no `FontStyle.Italic` under the owned paths.

## Not verifiable without a device
- That the notice surface's action chip and the answer toolbar fit side by side with the Using-screen marker on a 360dp screen without wrapping.
- The `lastNotice` exit: that the surface fades with its last content rather than collapsing first.
- The "Answering…" line arriving exactly when the first token lands (depends on `isStreaming` timing on the real stream).
- The settings dropdown rows' touch feel and `DropdownMenu` anchoring inside the `AlertDialog`.
- Rendering of the two spacing changes (22 -> 20, 14 -> 12) against the hosted gate's geometry (the sheet bottom edge and the 76dp row are untouched).
- API 26 rendering of any of the above (no API-gated effect was added).
