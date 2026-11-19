# Pass W3 — Assistant surface (branch `pass/w3-assistant`, rebased onto main `5d1d17a` after the W3 theme merge `9f95be2`)

Scope: `app/src/main/java/com/materialchat/assistant/**`, `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt`, `app/src/test/java/com/materialchat/assistant/ui/**`. No theme, chat, home, settings-screen, script or workflow file was edited. Nothing pushed. No emulator.

## Commits

1. `0010371` feat(assistant): rate-limit notice state, answer toolbar that drops the Open-in-app label instead of clipping at 360dp, notice text that stays whole at font scale 1.3, labelled-action copy in AssistantCopy
2. `a3792ee` refactor(assistant): route every control size in assistant/ui onto AssistantSizes; geometry unchanged
3. fix(assistant): a long partial transcript keeps its newest words (start ellipsis) instead of its first
4. (this file) docs(assistant): W3 REPORT.md

## Gates

- `:app:compileGithubDebugKotlin` rc=0 and `:app:compileGithubDebugAndroidTestKotlin` rc=0 after every part.
- `:app:testGithubDebugUnitTest`: **657 tests, 0 failures, 0 errors, 0 skipped** (`AssistantContrastTest` 11, `AssistantNoticesTest` 11, `ThemeContrastTest` 16 incl. the W3 `assistantSurfacePaletteReadsOnEveryFillInEveryTheme`, new `AssistantCopyTest` 6).
- No test, testTag, contentDescription, script or workflow file touched. No `FontStyle.Italic` under the owned paths (grep). No palette change, so the theme's read-only palette assertion is unchanged.

## What was audited (file:line at base `5d1d17a`)

- `assistant/ui/AssistantOverlay.kt`: window chain :330-420 (scrim, edge glow, particle field, backdrop blur), state lines :896-975, pill row :812-1180 (glyph, text area, `+`, keyboard, primary, close), answer surface :1190-1270, toolbar :1280-1335, attach reveal :1390-1470, chips :1475-1560, notice surface :1570-1680
- `assistant/ui/components/AssistantResponseCard.kt` (list, question bubble, answer, thinking surface, continue chip)
- `assistant/ui/AssistantCopy.kt`, `AssistantNotices.kt`, `AssistantContrast.kt` (pairs), `AssistantExpressiveStyle.kt` (motion, shapes, spacing, type)
- `assistant/ui/AssistantViewModel.kt` :400-740 (send path, every `error`/`notice` write, `handleError` :860), `AssistantUiState` :1085
- `data/repository/ChatRepositoryImpl.kt` :126-155 and `data/remote/api/ChatApiClient.kt` :1901-1925 (read-only: how an HTTP failure reaches the assistant — `Exception(event.message)` where the message is the parsed body, prefixed `HTTP <code>:` when unparsable)
- `assistant/voice/AssistantSpeechRuntime.kt`, `GeminiTtsBackend.kt`, `SherpaTtsBackend.kt`, `TextToSpeechManager.kt` (every `TtsEvent.Error` string, read-only)
- `assistant/screen/ScreenContextAccess.kt` :53-58 (notice lengths)
- `scripts/check-assistant-window.sh` (read-only: which content descriptions and tags the hosted gate reads)
- `ui/theme/Motion.kt` (token values), `docs/reports/pass-w3-theme.md` (no assistant migration rows)
- `ui/screens/settings/AssistantVoiceSettingsPanel.kt`, `AssistantScreenContextSettingsPanel.kt` (dp literals, copy)

## Part 1 — state table

Reasoned from the code at 360dp (sheet 336dp wide), font scale 0.85 / 1.0 / 1.3, 39 palette appearances (the JVM matrix). "OK" means nothing was found wrong; "fixed" names the commit.

| State | Hierarchy | Clipping | Colour | Motion | Copy | Result |
|---|---|---|---|---|---|---|
| Cold invoke → auto-listen | glyph container + "Listening…" | — | glyph Capturing pair | stateSwitch/effect tokens | `LISTENING` | OK |
| Keyboard mode (empty / text) | field leads; keyboard toggle folds while typing | 5-control row leaves the field ≥ ~90dp at 360dp (`+` 40, send 56, close 40 only when no answer) | pill body/muted/accent | expand/shrink on stateSwitch | `PROMPT` placeholder | OK |
| Listening, short partial | transcript large/semibold | one line | pill body | in-place by `kind` | — | OK |
| Listening, very long partial | same | **was** `Ellipsis` at the end: the newest recognised words were the ones cut off | — | — | — | **fixed (c3)**: `TextOverflow.StartEllipsis` (present in ui-text 1.11.0-alpha06, API-independent), so the line always shows what was just said |
| Thinking | pill glyph is the one working indicator; quiet thinking surface | — | thinking body/muted | surfaceReveal once; polygon morph is the M3 `LoadingIndicator` (no gate needed: it is a component, not a decoration) | `THINKING`, `THINKING_TITLE/STATUS` | OK |
| Streaming short / long markdown (code + table) | answer is the only body text | `MarkdownText` on the answer fill, scrollable inside `screenHeight × 0.55` | answer body / indicator | `contentSize` token | `ANSWERING` | OK (markdown block shapes are chat-owned) |
| Speaking, paused by tap | tertiary glyph, "Speaking…"; Interrupt button tonal | — | glyph Speaking pair, interrupt pair | pop token on role change | `SPEAKING` | OK |
| No mic permission | info notice + "Type instead" | — | notice muted / outlined chip | — | `MIC_PERMISSION` | OK |
| Recognizer unavailable | error notice + "Type instead" | — | error notice pair | — | `RECOGNIZER_UNAVAILABLE` | OK |
| TTS engine missing | error notice, no action (answer on screen) | — | error notice | — | backend strings (`AssistantSpeechRuntime` :70/:81/:88): sentence case, period, no exception text | OK, left in voice/ (not display copy) |
| Model error (401/403/500) | error notice + "Try again" | — | — | — | `REQUEST_FAILED` | OK |
| Network error | error notice + "Try again" | — | — | — | `NO_CONNECTION` | OK |
| **Gemini 429 / quota** | **was** the generic "Something went wrong. Try again." — the one failure where retrying immediately is wrong | — | — | — | **fixed (c1)**: `AssistantCopy.RATE_LIMITED` ("The model is busy right now. Wait a moment and try again."), recognised from the provider body (`429`, `rate limit`, `too many requests`, `quota`, `RESOURCE_EXHAUSTED`) anywhere in the cause chain, connectivity still first; action stays "Try again" | fixed |
| Screen chip present | chip inside the opened pill | 40dp chip, 28dp thumb | chip pair | surfaceReveal | `SCREEN_CHIP` (c1, same text) | OK |
| Screen withheld notice | info notice + "Open assistant settings" | **was** `maxLines = 4`: the 131-character `ScreenContextAccess.notice` needs 5 lines at font scale 1.3 (280dp row, ~29 chars/line) and was cut mid-sentence | — | — | — | **fixed (c1)**: `maxLines = 6` (still bounds a runaway engine message); the chip label ellipsises on one line instead of wrapping under the capsule clip |
| Screen removed | chip folds, pill closes | — | — | shrink on surfaceReveal | — | OK |
| Attachments 0 / 1 / 4 | thumbnails in the opened pill | 16dp top inset keeps the 12dp remove overhang inside the capsule | attachmentRemove pair (as drawn) | — | — | OK |
| Action directive result lines | spoken/shown as the answer text | — | — | — | `DeviceActionOutcome` sentences ("Flashlight on.") | OK, owned by actions/, not display copy |
| "Open in app" / "Continue in app" | one labelled chip in the toolbar | **was** possible clip at 360dp / font 1.3 (see part 3) | chip pair | — | `OPEN_IN_APP`, `CONTINUE_IN_APP` (c1) | fixed (part 3) |
| Lock-screen invoke | keep-awake untouched | — | — | — | — | OK (not touched) |
| 360dp, font 1.3 / 0.85 | see part 3 and the two rows above | — | — | — | — | fixed where found |
| Palette × light/dark/pitch-black × 3 dynamic seeds | — | — | every pair ≥ 4.5 / 3:1 in all 39 (`AssistantContrastTest`, `ThemeContrastTest`) | — | — | OK, no palette change |

Motion: every spec in `assistant/ui` is an `AssistantMotion` alias of a theme token (checked, none hand-rolled); the exit settles on `Spatial.collapse()` (1.0/420, ~250 ms) inside `AssistantExitMotion`'s 320 ms. Nothing continuous was added; the particle field keeps its gate.

Deliberately NOT done: a 401/403 "provider rejected the request → Open app" state. I implemented it, and `AssistantNoticesTest.requestFailuresNameConnectivityAndHideEverythingElse` (W2) pins `HTTP 401 …` to the generic copy. Tests are not to be weakened or re-pinned, so the mapping was removed again before commit; only the 429 state the brief names shipped. If a later wave wants it, that test line is the one to retarget.

### Part 1 changes (file:line at HEAD)

- `AssistantCopy.kt` :41 `RATE_LIMITED`; :45-50 `OPEN_IN_APP`, `CONTINUE_IN_APP`, `TAKE_PHOTO`, `ATTACH_PHOTO`, `SCREEN_CHIP` (every labelled action now comes from the copy system); :77-96 `requestFailure` with marker detection and a cycle-safe `causes()` walk (6 deep).
- `AssistantOverlay.kt` ~:950 transcript line `TextOverflow.StartEllipsis`; labels at :1463/:1469 (attach chips), :1545 (Screen chip) read the constants.
- `AssistantResponseCard.kt` :305 continue chip label.
- `AssistantNotices.kt`: unchanged in behaviour (a rate-limit error carries `RetryRequest` exactly like any other request failure).
- New `app/src/test/.../AssistantCopyTest.kt` (6 tests): connectivity before everything, five real provider bodies map to `RATE_LIMITED`, nested causes, circular cause chains terminate, everything else is generic and never the exception text, the new notice follows the copy rules, and the resolved notice offers the retry with the "Try again" resting line.

## Part 2 — spacing audit

Every dp literal left in `assistant/ui/**` after W2 was classified. Control *sizes* (not spacing) now live in `AssistantSizes` (`AssistantExpressiveStyle.kt` :159-195): `ChipHeight` 40, `ChipIcon` 16, `Target` 48, `Control` 40, `Glyph` 32, `PrimaryButton` 56, `CompactTarget` 32, `Thumbnail` 28, `NoticeGlyph` 18, `QueryBubbleMin` 40, `TypingDot` 7. Consumers: `AssistantOverlay.kt` (glyph container, glyph slot, `+`, primary button, chip heights and icons, Screen chip thumb/remove, notice glyph, Using-screen icon), `AssistantResponseCard.kt` (bubble min width, typing dot, continue chip). **No value changed**; the hosted gate's 40dp-inside-76dp geometry is the same numbers under names.

Literals kept, with the reason:

| Site | Value | Why it stays literal |
|---|---|---|
| `AssistantOverlay.kt` :384/:411/:770/:816, `AssistantShapes.Pill` | 38dp corner, 76dp row | pill geometry contract (brief) |
| `:389` `ParticleFieldStyle(innerBand = 20.dp)`, `:412` backdrop blur radius 40dp | particle/blur physics | particle field contract |
| `:646-648` sheet offsets 0 / 120 / 400dp, `:682` `heightIn(max = 600.dp)` | entrance/exit travel, sheet cap | motion distances, not spacing |
| `:780-781`, `:1223-1224`, `:1618`, `AssistantResponseCard.kt` :174/:254 | tonal/shadow elevations | elevation is its own M3 scale |
| `:880-883` waveform bars 3/2/4/22dp, `VoiceWaveform.kt` :49-52 defaults | bar geometry | glyph drawing, sized to the 32dp slot |
| `:1099` primary corner 18 ↔ 28dp, `AssistantShapes.*` radii, `:1538` thumb corner 8dp | corner radii | shape vocabulary, not spacing |
| `:1698` `BorderStroke(1.dp)` | outline stroke | stroke width |
| `AssistantResponseCard.kt` :84 scroll buffer 8dp px | scroll slack | not laid-out spacing |
| `AssistantKeyboardSpacing.GAP_DP` | 8dp | keyboard contract |
| `AssistantVoiceSettingsPanel.kt` :92/:330/:355, `AssistantScreenContextSettingsPanel.kt` :70-71 | 12/16/280dp | Settings-screen rhythm copied from `SettingsExpressive.kt`; left so the panels keep matching Settings, which is not on `AssistantSpacing` |

## Part 3 — notice / toolbar at 360dp

Measured widths (360dp screen, sheet 336dp = 360 − 2×12):

- Answer toolbar strip: 336 − 2×20 (surface inset) − 8 − 4 (strip padding) = **284dp**.
- Connected icon group: 3 × 48dp targets + 2 × 2dp joins = **148dp** (2 icons: 98dp).
- "Open in app" chip: 14 + 16 + 6 + label + 14. `labelLarge` bold 14sp ≈ 7.3dp/char → label ≈ 88dp at 1.0 (**138dp** chip), ≈ 115dp at 1.3 (**165dp** chip).
- 1.0: 138 + 8 + 148 = 294 > 284 → the chip was already **clipped by the capsule** with Copy shown; at 1.3 by 29dp more. Without Copy (98dp group) it fit.

Fix (`AssistantOverlay.kt` :1299-1365, commit 1): the strip is a `BoxWithConstraints`; the label width is measured with `rememberTextMeasurer` from the same `AssistantTypography.action` style the chip draws, and when `chip + Between + group > maxWidth` the Open-in-app action joins the connected group as its leading icon (`contentDescription = "Open in app"`, same handler) instead of being clipped. Nothing is reserved, no ellipsis; at widths ≥ 294dp the labelled chip is unchanged. Chip labels are additionally `maxLines = 1` + `Ellipsis` so a label can never wrap under a 40dp capsule.

Notice surface: row width 336 − 2×14 − 18 − 10 = **280dp**. `bodyMedium` medium 14sp → at 1.3 = 18.2sp ≈ 9.5dp/char → ~29 chars/line; the longest notice (131 chars) needs 5 lines → `maxLines = 6`. The action chip ("Open assistant settings", 23 chars) is ≈ 168dp at 1.0 and ≈ 218dp at 1.3 plus 50dp insets = 268dp < 280dp, so it fits on its own row at every scale; it ellipsises rather than wraps beyond that.

## Part 4 — AttachmentLimits.limitNotice()

Not in my ownership. `AssistantViewModelAttachmentsTest` :153/:173 compares `uiState.notice` to `AttachmentLimits.limitNotice()` by reference, not by literal, so any new copy the chat agent proposes stays compatible with that test as long as the function keeps its name. The assistant shows it as a plain info notice (no action), sentence form or not.

## Deliberately left alone

- Pill geometry, `AssistantKeyboardSpacing`, keep-awake, particle field, recognizer selection, TTS backends and their strings, action directives, auto-listen semantics, every testTag and contentDescription, `SmoothStreamingText`.
- `AssistantContrast` / palette: no change (theme W3 now asserts its outputs read-only).
- The 401/403 state (see Part 1).
- `ScreenContextAccess.notice` wording (131 chars): long but accurate; the layout now holds it.
- Settings panels: already on the Settings pattern since W2; nothing found.

## Needs another owner

- None new. The theme W3 migration table has no assistant rows; `ThemeContrastTest`'s assistant assertion passes unchanged.
- (Carried from W2) `util/AttachmentLimits.limitNotice()` copy, chat agent.

## Not verifiable without a device

- The toolbar's measured-width switch: that `rememberTextMeasurer` width matches the drawn `FilledTonalButton` label width within the 8dp slack on a real font, and the compact Open-in-app icon's connected shape at the group's start.
- `TextOverflow.StartEllipsis` rendering on the transcript at API 26 (the attribute is Compose-side, but the platform `Layout` ellipsize path is what draws it).
- The six-line notice at font scale 1.3 against the 0.55-screen answer cap on a short (640dp) screen: the sheet scrolls, the notice itself does not.
- Rate-limit detection against live provider bodies other than the five sampled in `AssistantCopyTest` (OpenAI, Gemini, OpenRouter shapes).
- Everything carried from W2's list (exit fade with `lastNotice`, "Answering…" timing, dropdown anchoring, API 26 rendering).
