# Pass W4 — Chat (branch `pass/w4-chat`, rebased on main 1dc3667 = W4 theme + W4 home/settings merges)

Scope: `ui/screens/chat/**`, `ui/components/Markdown*`, `util/AttachmentLimits.kt`, `domain/model/StreamingState.kt`, `data/repository/ChatRepositoryImpl.kt`, `data/remote/api/ChatApiClient.kt` + `StreamingEvent.kt` (error typing only). `git diff --name-only 1dc3667..HEAD` filtered against the ownership list: nothing outside it. No `assistant/**`, `ui/theme/**`, home/settings, `scripts/`, `.github/` edits. No emulator, AVD or device. Nothing pushed.

Hard rules checked at HEAD against origin/main: `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` byte-identical (`cmp`); `FontStyle.Italic` in the ownership set: 0; `testTag|contentDescription` count 77 on origin/main, 77 at HEAD; no test removed or renamed; no androidTest added, so no judge-minimum change. Word-by-word haptics untouched.

## Commits (after the final rebase onto 1dc3667)

| Commit | Part | Gate at commit (pre-rebase count) |
|---|---|---|
| 33f1cbc | 1 — typed HTTP status end-to-end | 664 tests, 0 failures |
| ffdfb90 | 2 — `AttachmentLimits` sentence-form notices | 667 |
| df5f5ff | 4 — remaining raw shape sites onto tokens | 667 |
| 0d5929a | 3 — W4 theme token rows (header pulse, fusion breath, 8dp badges) | 673 |
| 35dd098 | audit items — `ExpressiveSnackbar`, gated `M3StreamingDots`, fusion orbit token | 673 |
| 61c7c37 | 5 — state walk round 2: `ChatErrorCopy`, errored bubble, retry label | 679 |
| (this) | docs: REPORT.md | — |

Final gate on the rebased HEAD: `:app:compileGithubDebugKotlin` rc=0, `:app:compileGithubDebugAndroidTestKotlin` rc=0, `:app:testGithubDebugUnitTest` **705 tests, 0 failures, 0 errors, 0 skipped** (the count grew with the merged home/settings tests). Each part was compiled on both targets and run through the full unit suite before its commit.

## Part 1 — typed HTTP status (33f1cbc)

Before: `ChatRepositoryImpl` wrapped `StreamingEvent.Error.message` in a plain `Exception`, so the assistant had to recognise a 429 from body text (`AssistantCopy.RATE_LIMIT_MARKERS`).

- `data/remote/api/StreamingEvent.kt:43-50`: `Error.httpStatus: Int? = null`; `:102` `fromHttpError(code, message)` sets it. Every HTTP refusal in `ChatApiClient` already goes through `fromHttpError` (OpenAI `:264`, Ollama `:370`, Copilot/Codex/Antigravity via `enqueueStreamingCall` `:588`), so all five HTTP wire formats carry it with no client change. Message text unchanged.
- `domain/model/StreamingState.kt:49-58`: `Error.httpStatus: Int?` with default `(error as? ProviderHttpException)?.httpStatus`, so the re-wraps in `SendMessageUseCase:499`, `RegenerateResponseUseCase:262` and `ChatViewModel:886` that copy only `error` keep the status without edits. `ChatViewModel.kt:891` copies it explicitly anyway.
- `StreamingState.kt:72-82`: `ProviderHttpException(httpStatus, message) : Exception` — deliberately not an `IOException`, so a refusal never reads as connectivity.
- `data/repository/ChatRepositoryImpl.kt:137-148`: builds `ProviderHttpException` when the event has a status; connectivity/timeout codes become `IOException(message)` (Part 5, see below); everything else stays `Exception(message)`.
- Tests: `ChatApiClientHttpStatusTest` (4): 401/403/429/500 × OpenAI, Ollama, Copilot, Codex, Antigravity arrive typed with `code == status.toString()` and the parsed body as message; in-stream Codex `error` event on a 200 has null status; `ConnectException` has null status and `CONNECTION_FAILED` code; recoverability unchanged. `ChatRepositoryHttpStatusTest` (2): the four statuses reach `StreamingState.Error.httpStatus` and `ProviderHttpException` through the real repository (OkHttp interceptor, mocked credentials) for OpenAI and Codex; a re-wrap keeps the status; a plain exception has none.

Negative finding: an OpenAI 200 whose body is `data: {"error":{...}}` decodes as an `OpenAiStreamChunk` with empty choices and is treated as keep-alive (`SseEventParser.kt:120-122`), so `tryParseOpenAiError` is never reached for that shape and the stream ends silently with no content and no error. Not in this pass's scope (parser is `data/remote/sse/`); listed under Needs another owner.

## Part 2 — attachment notices (ffdfb90)

- `util/AttachmentLimits.kt:16-23`: `limitNotice()` = "You can attach up to 4 images per message. Remove one to add another."; `skippedNotice(n)` = "Only the first 4 images were attached; n were skipped." ("1 was skipped" singular).
- `ChatScreen.kt:169,185,207`: the three composer snackbars now call the shared helper instead of duplicating the old literals (they had drifted into copies).
- `AssistantViewModelAttachmentsTest:153,173` and `AssistantAttachmentDeliveryTest:62` (androidTest) assert `AttachmentLimits.limitNotice()` / `skippedNotice(2)` by call, not by literal: they are copy pins that track the helper, so no expectation edit was needed and the assistant test passes unchanged (8/8). `AttachmentLimitsTest` (3) pins the new strings, period, sentence case, no ASCII ellipsis.

## Part 3 — theme W4 rows (0d5929a)

| File:line | Before | After |
|---|---|---|
| `ChatTopBar.kt:452` | `infiniteRepeatable(tween(1400, LinearEasing), Restart)` | `Spatial.decorativeSpin(Durations.HeaderPulse)` (same spec family, still inside the motion gate) |
| `FusionProgressView.kt:195` | `Spatial.bob(Durations.Long)` | `Spatial.bob(Durations.Pulse)` (value-equal) |
| `FusionSourcesSection.kt:65,139`, `FusionProgressView.kt:146,232`, `MarkdownText.kt:649` | `RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` |

## Part 4 — raw shape sites (df5f5ff)

`QuoteReplyPreview.kt:98`, `MessageBubble.kt:769`, `MarkdownText.kt:417` accent bars (999dp) and `StreamingIndicator.kt:446`, `BeautifulModelBadges.kt:375,673` (50%) → `CustomShapes.Pill`; `MessageInput.kt:612` press shape 12dp → `CustomShapes.ButtonPressed`; `MessageInput.kt:974` default param 16dp → `ExpressiveShapes.ContentBlock` (dead default; the caller passes `positionCorners`). Unused `RoundedCornerShape` imports removed.

## Audit items (35dd098)

- `ChatScreen.kt:403-408`: raw `Snackbar` on `CustomShapes.Snackbar` → `ExpressiveSnackbar` (shared squircle, inverse roles gated by `HomeSettingsSlot:80-81`, pill action with press haptic, 12dp inset — the inset is new for chat).
- `StreamingIndicator.kt:150-158`: `M3StreamingDots` loop behind `rememberParticleMotionGate()`, rests at 0.3f when denied (pattern of `M3ExpressivePulsingDots`). Reachability of its consumer at `:262` not re-verified.
- `FusionAnimation.kt:52-58`: raw 3000 ms linear tween → `Spatial.decorativeSpin(Durations.Indicator.MorphShape)` (3000). A `Durations.FusionOrbit` token would name the intent; see Needs another owner.

## Part 5 — state walk round 2 (61c7c37)

| State | Checked | Verdict | Change |
|---|---|---|---|
| Errored bubble, typed 401 / 403 / 429 / other 4xx / 5xx / connectivity / other | `ChatErrorCopy.kt` copy table; bubble line; snackbar line | distinct sentence-case line per class, last sentence names the action | `ChatErrorCopy.notice()`; `ChatScreen.kt:873,1001,1102` threads the notice of the current `StreamingState.Error` to the last errored assistant bubble; `MessageBubble.kt:180,475,1694-1713` `ErrorStateContent(notice)` on `palette.assistantError` (the `ChatSurfacePalette` slot that was gated but unconsumed; was `colorScheme.error` by role) with `weight(1f, fill = false)` so a two-sentence line wraps; `ChatViewModel.kt:917` snackbar via `ChatErrorCopy.snackbar()` (class line for classified failures, provider text only for unclassified ones) |
| Retry affordance | assistant notice action is "Try again" (`AssistantCopy.AFTER_ERROR`) | chat said "Retry" | `MessageBubble.kt:1726`, `ChatScreen.kt:597` → `ChatErrorCopy.TRY_AGAIN`; `ChatErrorCopyTest` pins equality with `AssistantCopy.AFTER_ERROR`, `RATE_LIMITED`, `NO_CONNECTION`, `REQUEST_FAILED` |
| Connectivity class reachable | `ChatRepositoryImpl` wrapped OkHttp `onFailure` in a plain `Exception`, so `isConnectivity` (IOException in the chain) could never match a client-reported failure | gap | `ChatRepositoryImpl.kt:140-143`: `CONNECTION_FAILED`/`TIMEOUT` codes → `IOException(message)` |
| Cancelled stream | `ChatViewModel.cancelStreaming():923-975` → `Idle`, empty placeholder deleted, partial kept with `isStreaming=false`; no errored bubble because `isErrored` requires empty content and the empty placeholder is removed | ok, unchanged | — |
| Attachments 4/4 | picker `room == 0` → `limitNotice()` and the picker is not launched (`:164-171`); camera at the limit discards the file and notices (`:202-208`); `PickMultipleVisualMedia(4)` caps the system picker; over-selection trimmed with `skippedNotice` | ok | copy only (Part 2). The plus menu stays enabled at 4/4 (notice instead of a disabled control) — left as is |
| Long code block + table, font scale 1.3 (static) | code: `13.sp/20.sp` scale with the system font scale, `horizontalScroll` + edge fade so no wrap/clip, header chip `maxLines = 1`; table: column widths measured with `rememberTextMeasurer` at the current `LocalDensity` (includes font scale; `remember` keyed on it), clamped 104–360dp, cells `softWrap` + `heightIn(min 40dp)`, viewport `min(maxWidth, tableWidth)` with `horizontalScroll`; both inside `ContentBlock` | ok on paper | — |

`ChatErrorCopyTest` (6): each typed status maps to its class line and never to the provider body; the seven lines are distinct; every line starts upper, ends with a period, at most two sentences, no Title Case inside a sentence; connectivity recognised from an `IOException` anywhere in a 6-deep cause chain; unclassified failure shows the generic bubble line and keeps its text in the snackbar; shared lines equal the assistant's.

Limitation by design: only the latest `StreamingState.Error` is carried in UI state, so an older errored bubble that is no longer the last assistant message shows the generic line.

## Deliberately left alone

- `MessageBubble.kt:925-968` bubble-family radii (user-selectable geometry, per W3/W4 theme reports).
- `MessageInput.kt:565-585` menu pill per-position corners, `MessageActions.kt:428`, `ChatTopBar.kt:535`, `ModelPickerDropdown.kt:171`, `ScrollToBottomFab.kt:181`: `RoundedCornerShape(animatedDp)` press/position morphs; the corner is the animated value, not a constant to tokenise. `ExpressivePositionShapes.kt` is the generator.
- `MarkdownText.kt:1720` table-header bottom-only 8dp and `WebSearchSourcesCarousel.kt:295` top-only 20dp: no one-sided tokens exist.
- `ChatContrast.kt:9-10` import of `AssistantContrast` (audit chat item 2) and the memory chip height (item 5): not in the brief.
- `AssistantCopy.requestFailure` text matching: assistant-owned; see below.
- `SmoothStreamingText`, tap router chain, haptic timing, keyboard spacing.

## Needs another owner

- **assistant (W4 assistant agent)**: `AssistantCopy.requestFailure(error)` can now read `(error as? ProviderHttpException)?.httpStatus` (or `StreamingState.Error.httpStatus` at `AssistantViewModel.kt:696-700`) instead of `RATE_LIMIT_MARKERS`; `ChatErrorCopy` already pins its shared lines to `AssistantCopy`, so the assistant may reuse `ChatErrorCopy.notice(httpStatus, error)` or copy the 401/403/5xx lines.
- **data/remote/sse owner**: `SseEventParser.parseOpenAiEvent` treats `{"error":{...}}` as an empty-choices keep-alive (`:120-122`); try `tryParseOpenAiError` before accepting a chunk with no choices.
- **theme**: `Durations.FusionOrbit = 3000` (audit F10) so `FusionAnimation.kt:55` stops reusing `Indicator.MorphShape`; `TopSquircleShape(20.dp)` if a second top-only consumer appears (`WebSearchSourcesCarousel.kt:295`).
- **home/settings**: nothing new.

## Not verifiable without a device

- Everything rendered: `ExpressiveShapes.Badge` corners on the fusion chips and markdown inner shapes (value-identical to the old 8dp, not seen); header pulse on `decorativeSpin(HeaderPulse)` (spec-equal to the old tween); fusion breath on `bob(Pulse)` (value-equal).
- Chat snackbar after the `ExpressiveSnackbar` swap: squircle 20dp instead of round 24dp, new 12dp inset, pill action with press morph — not observed over the composer/IME.
- `M3StreamingDots` resting dim under a denied motion gate, and whether its consumer at `StreamingIndicator.kt:262` is reachable from a live screen.
- Errored bubble: two-sentence notices wrapping at font scale 1.3 on 360dp (`weight(1f, fill = false)` proven by reading the modifier chain); `palette.assistantError` on real wallpaper-dynamic schemes (JVM-gated on 39 generated appearances); "Try again" tonal button width next to the bubble.
- The status mapping against live provider bodies: tests use interceptor-synthesised 401/403/429/500 responses and one Codex in-stream error shape; real OpenRouter/Gemini/Copilot error envelopes were not sampled.
- Code block + table at font scale 1.3 on a real font (text measurer vs drawn width).
- Cancelled-state and 4/4 attachment flows: audited from the view-model and launcher code paths only.
