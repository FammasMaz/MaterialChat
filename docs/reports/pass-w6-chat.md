# Pass W6 — Chat (branch `pass/w6-chat`, rebased onto `3293eaa1` = W6 theme merge)

Scope: `ui/screens/chat/**`, `ui/components/Markdown*`, `ui/components/ImageViewerDialog.kt`, `data/remote/sse/**`, `data/remote/api/**`, `domain/model/StreamingState.kt`, `util/AttachmentLimits.kt`. No emulator, AVD or device. Nothing pushed.

Base was `9ef37001` (v2.41.5); after the steering message the branch was rebased onto `origin/main` at `3293eaa1` (W6 theme merged at `ad765acd`). The two commits before the rebase replayed cleanly.

Hard rules at HEAD vs `origin/main`: `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` `cmp`-identical; `FontStyle.Italic` in the ownership set: 0; `testTag`/`contentDescription` removed: 0; `scripts/`, `.github/`, `ui/theme/**` untouched; no test weakened, renamed or deleted; no androidTest added (judge minimum unchanged). Word-by-word haptics untouched.

## Commits (oldest first)

| Commit | Part | Gate at commit |
|---|---|---|
| `f00e5cd9` | 1 — `ChatCopyRulingsTest` pins no `"Retry"` literal and no ASCII `...` literal under chat ownership | compile main + androidTest rc=0; 764 tests, 0 failures |
| `cc7cd425` | 2 — `DateSeparator` hairline on `CustomShapes.Pill`; dead `spring` imports dropped | 764, 0 |
| `6e2ca830` | 3 — long-chat walk fixes (source card, quote bar, picker search field, code-block chip) | 771, 0 (gate run once for 3/4/5 before the three commits) |
| `a7392e0a` | 4 — streaming/cancel/error coherence + 4 JVM pins | 771, 0 |
| `e4089116` | 5 — theme rows: `bubbleShapeFor` exposed, `StreamingIndicator` tween documented exempt | 771, 0 |
| (this) | REPORT.md | gate rerun before commit, see final message |

## Part 1 — the "Retry" dispute (`f00e5cd9`)

The W5 home-settings report said `ChatScreen.kt:600` and `MessageBubble.kt:1720` still say "Retry"; the W5 chat report said none remain. Grep at the base: zero `"Retry"` string literals under chat ownership. The two citations were stale: `ChatScreen.kt:600` is inside the chat-content composable (local `val`s, no copy), and `MessageBubble.kt:1720` is the KDoc line `Retry button shown below errored assistant messages.` on `RetryButton`, whose label is `ChatErrorCopy.TRY_AGAIN` ("Try again"). The W5 chat report was right; no source change was needed.

Pin: `app/src/test/java/com/materialchat/ui/screens/chat/components/ChatCopyRulingsTest.kt` (3 tests). It walks the ownership list on disk (same root resolution as `AssistantKeyboardSpacingTest`), extracts every double-quoted literal on non-comment lines, and fails on `\bRetry\b` or `...` inside any literal, listing `file:line "text"`. Comment lines (`//`, `/*`, ` * `) are skipped: the first run flagged `SseEventParser.kt:107`, a `//` line quoting the `{"error":"..."}` envelope shape, which is not copy. Third test pins `ChatErrorCopy.TRY_AGAIN == "Try again"`.

## Part 2 — leftover inventory under chat ownership (`cc7cd425`)

Static grep at the base; every hit re-opened.

| Class | Count | Migrated | Deliberate leftovers (reason) |
|---|---|---|---|
| raw `spring(` / `tween(` | 1 call (`StreamingIndicator.kt:481`) + 2 dead imports | dead `spring` import in `QuoteReplyPreview.kt:4` and dead `Spring`/`spring` imports in `WebSearchSourcesCarousel.kt:7,9` removed (zero references; the W4 swap left them) | `StreamingIndicator.kt:481` `tween(durationMillis, easing)` inside `gatedInfiniteFloat`: the one continuous linear-time indicator tween, pass-through of the caller's period. Documented as exempt in Part 5. |
| `.copy(alpha =` | 54 | none | None tints text or an icon except `MessageBubble.kt:716` `primary.copy(alpha = swipeProgress)`, a progress-driven reveal that reaches 1.0 (W4 audit exempt, confirmed by the W6 theme inventory row). The other 53 are fills, washes, tracks, hairlines, gradient stops, CSS colour strings for the math WebView, and `ChatContrast` composite-fill arithmetic (`over(...)`) that exists precisely so the content colours drawn on them are gated. |
| raw `RoundedCornerShape(` | 20 | `DateSeparator.kt:81` `RoundedCornerShape(1.dp)` on a 28×1dp hairline → `CustomShapes.Pill` (a 1dp-tall box with 50% corners is the same pixel result); the `RoundedCornerShape` import is gone | `MessageBubble.kt:927-928, 948-972` bubble-family sampler (user-selectable, no token; see Part 5); animated morphs where the corner *is* the animated value: `MessageBubble.kt:1098` (thinking header), `:1375` (memory row), `MessageActions.kt:428`, `ChatTopBar.kt:532`, `ModelPickerDropdown.kt:171`, `MessageInput.kt:565,576,584`, `ScrollToBottomFab.kt:181`; `ExpressivePositionShapes.kt:33,64` (the per-position shape builder itself); `MarkdownText.kt:1720` table-header bottom-only 8dp (still no one-sided token; sub-shape of a `ContentBlock`, W5 reasoning stands). |
| ASCII `...` | 2 literals in the first grep, 0 after re-check | none | Both are regex/comment text in `MarkdownText.kt` (`$$...$$` pattern comments, `\[...\]`) and the `SseEventParser` comment. No copy carries `...`; the Part 1 pin keeps it that way. |

## Part 3 — long-chat walk, round 3 (`6e2ca830`)

Method: 360dp reference, font scale 1.3 (`sp` scales, `dp` does not; `bodyMedium` 14/20sp → 18.2/26sp, `bodySmall` 12/16 → 15.6/20.8, `labelSmall` 11/16 → 14.3/20.8). Grep over the ownership set for fixed `height(`/`width(`/`size(` on text containers, `maxLines = 1` without `overflow`, unweighted rows with a trailing control; each hit re-opened and measured from the modifier chain. The list itself is a `LazyColumn` keyed on `message.id` with `contentType = role` (`ChatScreen.kt:993-994`), so 200 items of mixed families recycle by role.

Fixed (provably wrong from the arithmetic):

| File:line | Was | Measurement | Now |
|---|---|---|---|
| `WebSearchSourcesCarousel.kt:272`, `:302` | card `height(132.dp)` inside a `height(140.dp)` carousel; image `height(66.dp)` | content at scale **1.0**: 66 image + 10 top pad + 20 badge row (`labelSmall` 16sp line + 2×2dp) + 6 spacer + 32 title (2 × `bodySmall` 16sp) + 10 bottom pad = **144dp** > 132 → the second title line clipped at every font scale; at 1.3 the title alone is 41.6dp | card `fillMaxHeight()` (the carousel owns the 140dp), image `weight(1f)`: the text column measures first at the current scale and the image takes the remainder (62dp at 1.0, ~52dp at 1.3). Title still `maxLines = 2` + ellipsis. `fillMaxHeight` import added |
| `QuoteReplyPreview.kt:90-101` | accent bar `height(48.dp)` beside a text column of `labelSmall` + 2 × `bodySmall` + 16dp padding | column = 16 + 16 + 32 = **64dp** at 1.0, 78dp at 1.3 → the bar stopped 16–30dp short of the text it accents | `Row.height(IntrinsicSize.Min)` + bar `fillMaxHeight()` (same idiom as the bubble's quote block, `MessageBubble.kt:766-772`); `IntrinsicSize`/`fillMaxHeight` imports added |
| `BeautifulModelBadges.kt:233` | picker `SearchField` `OutlinedTextField` `.height(48.dp)` "to prevent expansion" | `OutlinedTextField` pads 16dp above and below the text; 48 − 32 = **16dp** for a 20sp `bodyMedium` line (26sp at 1.3) → descenders clipped, worse at 1.3 | `heightIn(min = 48.dp)`; the field settles at the M3 56dp minimum and the dropdown already scrolls inside `heightIn(max = 350/400.dp)` (`:424`, `:723`) |
| `MarkdownText.kt:776-795` | code-block header: language chip `Box` with no weight, `Spacer(weight(1f))`, then copy/open buttons | an unweighted chip is measured against the full row width, so a long info string (`typescript title=App.tsx`) pushed the two 40dp buttons past the block edge | chip wrapped in a `weight(1f)` box (`CenterStart`), `Text` gets `overflow = TextOverflow.Ellipsis`; the spacer is gone; `TextOverflow` import added |

Measured and left (nothing wrong):

- Attachment grid `MessageBubble.kt:1458-1476`: thumbnails `size(120.dp)` in a `FlowRow`-style wrap; 2 per row at ~340dp usable width, 4 attachments = 2 rows. Generated images `aspectRatio(1f)` full width. Fixed by design, no text inside.
- Thinking header `MessageBubble.kt:1096-1143`: `heightIn(min = 40.dp)`, title `labelLarge` unbounded, preview `bodySmall` `maxLines = 1` + ellipsis inside a `weight(1f)` column; 18dp chevron fixed. Grows with scale.
- Memory row `MessageBubble.kt:1365-1396`: `height(48.dp)` with `labelLarge` 14/20sp (26sp at 1.3) + 2×0 vertical padding → 26 < 48, fits; label `maxLines = 1` + ellipsis + weightless but between two 18dp icons in a `spacedBy(8)` row, so it takes the remainder. A row, not a chip (W5 Part 2 ruling).
- Image-generation card `MessageBubble.kt:1640-1685`: column, every text unbounded or ellipsised; 72dp tile is a glyph box.
- Errored bubble `ErrorStateContent` (`:1694-1716`): unchanged from the W5 measurement (3-line wrap at 1.3, `fill = false`).
- Masthead `ChatTopBar.kt:332-392`: title `headlineSmall` `maxLines = 2` + ellipsis in `weight(1f)`; badge row is `horizontalScroll` (`:359`) so model badge + context chip + branched chip never squeeze; streaming label `labelLarge` `maxLines = 1` without overflow — the text is the fixed `CHAT_WORKING_STATUS` constant next to a 3-bar pulse, cannot exceed the row. `HeaderStatusChip` `labelMedium` `maxLines = 1` without overflow: labels are `Context NN%` / `Context full` / `Branched` (≤ 12 chars) inside a scrolling row.
- Action row `MessageActions.kt:264,303`: stats line has ellipsis; elapsed label `labelMedium` `maxLines = 1` is `formatDuration` output (≤ 8 chars) in an `AnimatedVisibility` at the row end.
- Carousel header `WebSearchSourcesCarousel.kt:156`: `· 1.2s` duration `labelSmall` `maxLines = 1` without overflow sits after an ellipsised `Sources (N)` in a `weight(1f)` column; ≤ 8 chars.
- Code block `MarkdownText.kt:805-816` and table `:1729-1873`: unchanged from the W5 measurement (horizontal scroll, 104–360dp column clamp).
- Picker dropdowns `BeautifulModelBadges.kt:424-425, 723-724`: `width(240/280.dp)` + `heightIn(max)`; every row text `maxLines = 1` + ellipsis in `weight(1f)`. The count row `height(28.dp)` (`:804`) holds one `labelSmall` 16sp line (20.8sp at 1.3) → fits.
- `MessageInput.kt:985-1018` attachment preview (72dp box, 64dp image, 24dp remove disc): icons only; the 294dp row at 4/4 from W5 still holds.
- `FusionProgressView.kt:85` `heightIn(max = 320.dp)` + `verticalScroll`: a cap, not a fixed height.

No gesture-chain edit: `BubbleTapRouter`, `TapThroughScroll`, `MathWebViewTouch`, the `tapThroughScroll`/`bubbleTapTarget` modifier sites and `SmoothStreamingText` are untouched (cmp above).

## Part 4 — streaming / cancel / error coherence (`a7392e0a`)

State sources (static): the bubble reads `message.isStreaming` + `messageItem.isErrored` (`ChatViewModel.kt:287`: assistant, empty content, no attachments, not streaming) + `errorNotice` from `state.streamingState as? Error` (`ChatScreen.kt:828`); the action row, Try-again button and edit affordance read `isLastAssistantMessage`/`isErrored`/`!isStreaming` (`ChatScreen.kt:1012-1066`); the composer's send/stop button and field enablement read `state.isStreaming` (`ChatUiState.kt:84-86` = `Starting || Streaming`; `MessageInput.kt:247-411`); the masthead pulse reads `isStreaming` (`ChatTopBar.kt:195,399`); the scroll FAB reads `state.isStreaming && autoScrollEnabled` (`ChatScreen.kt:897-900`). All five derive from `ChatUiState.Success.streamingState` plus the Room-backed `isStreaming` flag, which `updateStreamingState` (`:864-924`) and `cancelStreaming` (`:927-987`) write together, so they cannot disagree *unless* a path writes one and not the other. Walk:

| Scenario | Path | Verdict |
|---|---|---|
| Stop mid-send | `cancelStreaming()`: jobs cancelled, use case cancelled, `streamingState = Idle`, placeholder removed or `isStreaming = false`; `SendMessageUseCase` `onCompletion` (`:471-481`) clears the DB flag. Bubble: finished (no error line, `isErrored` false because the placeholder is gone). Composer: send. Pulse: off. FAB: normal. | ok, unchanged |
| **Stop mid-regenerate** | `regenerateResponse` (`:1154-1210`) caught **every** `Exception`, including the `CancellationException` thrown by Stop, and then called `updateStreamingState(Error(...))` **after** `cancelStreaming()` had set `Idle` → the UI re-armed to `Error`: errored bubble line + Try-again on the last assistant message, a snackbar with the coroutine's cancellation text ("StandaloneCoroutine was cancelled"), composer back to send, pulse off. Bubble and composer disagreed. | **fixed**: `catch (e: CancellationException) { throw e }` first (the same shape `sendMessage` already had at `:821`), and the job's conversation id captured at launch and passed as `expectedConversationId` so a late error cannot land on a sibling conversation the user navigated to meanwhile |
| Regenerate fails (HTTP/connectivity) | `updateStreamingState(Error)` already emits one `ShowSnackbar(ChatErrorCopy.snackbar(state))`; the `catch` then emitted a **second** `ShowSnackbar(e.message ?: "Failed to regenerate response")` → two snackbars, the second with raw provider text (W4 ruling: no exception text in snackbars). | **fixed**: second emit removed; one classified line |
| Redo with model | `redoWithModel` (`:1588`) guards `isStreaming`, branches, navigates with `autoSend`; the new screen's send path owns the state. | ok |
| **Edit-and-resend under a stream** | `startEditingMessage` (`:1247`) had no `isStreaming` guard: the screen hides the pencil while the *edited message* streams (`ChatScreen.kt:1057` checks `message.isStreaming` of the user message, which is never true) — so during a regenerate the edit affordance was live, and `submitEditedMessage` (`:1278`) would branch + `NavigateToBranch(autoSend = true)` under a reply in flight, leaving the source conversation's `Starting/Streaming` state orphaned. | **fixed**: `startEditingMessage` returns when `isStreaming` (same guard as regenerate/branch/redo); `submitEditedMessage` keeps the draft and raises the one-phrase snackbar `EDIT_WHILE_STREAMING_NOTICE` = "Wait for the reply to finish before editing" (no period, per ruling) when the editor was opened *before* the stream began |
| Network loss mid-stream | `ChatApiClient` `onFailure(IOException)` → `StreamingEvent.Error`; `SendMessageUseCase` `:320-333` keeps partial content, clears the DB flag, emits `Error(error = IOException…)`; `ChatErrorCopy.isConnectivity` → `NO_CONNECTION` in bubble and snackbar. If partial content was saved, `isErrored` is false (content non-empty) → finished bubble with content, snackbar "No connection…", composer send, pulse off. Coherent: the partial reply is real content. | ok, unchanged |
| In-band error on a 200 (W5) | `SseEventParser` → `Error(httpStatus)` → same path as above with the class line. | ok, unchanged |
| Fusion failure | `:1998-2010` writes `Error` + `isFusionRunning = false` and one snackbar with `e.message ?: "Fusion failed"` — raw exception text, not through `ChatErrorCopy`. | left: fusion owns its own panel copy and the W4 ruling listed the chat send path; recorded under "deliberately left" for the orchestrator |

Tests: `app/src/test/java/com/materialchat/ui/screens/chat/ChatViewModelStreamingCoherenceTest.kt` (4): Stop during regenerate → `Idle`, no `ShowSnackbar`; failed regenerate with `ProviderHttpException(429)` → `streamingState.httpStatus == 429`, exactly one snackbar = `ChatErrorCopy.RATE_LIMITED`; `startEditingMessage` under an open stream leaves `editingMessageId` null; `submitEditedMessage` with an editor opened before the stream keeps id + text, emits exactly `[EDIT_WHILE_STREAMING_NOTICE]`, no `NavigateToBranch`. Harness mirrors `ChatViewModelDraftStateTest` (real `ChatViewModel`, `StandardTestDispatcher`, `backgroundScope` as `applicationScope`).

## Part 5 — W6 theme rows (`e4089116`)

`docs/reports/pass-w6-theme.md`: "Nothing to consume from parts 1–4" for chat; Part 5 inventory rows:

| Row | Change |
|---|---|
| `MessageBubble.kt:927, 928, 948` fixed radii (`COMPACT 20`, `GEOMETRIC 16`, `ROUNDED 34`) — "theme adds `ExpressiveShapes.Bubble.*` next wave if wanted; chat consumes" | No `ExpressiveShapes.Bubble.*` exists on `origin/main` after the merge (`grep -n "Bubble" ui/theme/Shapes.kt`: only `MessageBubbleShapes`, which `EXPRESSIVE` already reads). The radii stay; `bubbleShapeFor` (`MessageBubble.kt:919-931`) is now `internal` with a KDoc naming it the family's record, so the settings preview (`SettingsScreen.kt:1974-1990`, home-settings owner, which duplicates the radii and already drifts: `ROUNDED` 28 there vs 34 here, `COMPACT` 24/6 vs 18/6) can call it instead of its own copy. |
| `StreamingIndicator.kt:481` parameterised `tween` — "chat to confirm callers" | Confirmed and documented in the `gatedInfiniteFloat` KDoc (`:462-468`): callers pass literals 3000 (rotation), 2400 (morph), 1400 (shimmer) at `:83,85,311-314,425`; `MaterialChatMotion.Durations.Indicator` has no matching entries (`MorphRotation` 4000 and `MorphShape` 3000 belong to the M3 `LoadingIndicator` wrapper), so the row is exempt and tokenising the three periods is a theme addition. |
| `MessageBubble.kt:716` swipe-reveal alpha, `DateSeparator.kt:81` hairline | exempt per the inventory; the hairline moved to `CustomShapes.Pill` in Part 2 anyway. |

## Deliberately left alone

- Composer shape (round 28dp) and the bubble family on circular corners — W4/W5 rulings, re-stated in the merged `Shapes.kt` `MessageBubbleShapes` KDoc.
- Memory-reference row at 48dp (a row, not a chip; W5).
- All animated `RoundedCornerShape(animatedDp)` morphs and `ExpressivePositionShapes` (the corner is the animated/positional value).
- `MarkdownText.kt:1720` table-header bottom-only radius (no one-sided token).
- `ChatContrast.kt` `.copy(alpha =` composite fills (they *are* the contrast arithmetic).
- Fusion failure snackbar with raw `e.message` (`ChatViewModel.kt:2008`) and the branch/redo/edit-submit catch-all snackbars (`:1231, :1327, :1615`) that still show `e.message ?: "Failed to …"`: these are not streaming-state failures and the W4 copy ruling targeted the send path; listed for the orchestrator below.
- The plus menu staying enabled at 4/4 attachments (notice instead of a disabled control).
- `SmoothStreamingText`, tap router chain, haptic timing, keyboard spacing.

## Needs another owner

- **home-settings**: `SettingsScreen.kt:1974-1990` `bubbleShapeFor`-style preview should call `com.materialchat.ui.screens.chat.components.bubbleShapeFor(style, isUser, isAssistant = !isUser, groupPosition = MessageGroupPosition.Single)` (now `internal`, same module) and delete its radii; it currently draws `ROUNDED` at 28 and `COMPACT` at 24/6 while the real bubbles are 34 and 18/6.
- **theme**: if the orchestrator wants the `ROUNDED`/`COMPACT`/`GEOMETRIC` radii and the three indicator periods (3000/2400/1400) tokenised, add `ExpressiveShapes.Bubble.*` and `Durations.Indicator.{Spin,Morph,Shimmer}`; chat consumes in one edit each (`MessageBubble.kt:931-972`, `StreamingIndicator.kt:83-425`).
- **orchestrator**: rule whether the non-streaming catch-all snackbars (`branchFromMessage`, `submitEditedMessage`, `redoWithModel`, fusion) should drop `e.message` for fixed copy like the send path did in W4.
- **assistant**: nothing.

## Not verifiable without a device

- Source card after `fillMaxHeight` + weighted image: that `HorizontalMultiBrowseCarousel` passes a bounded 140dp height to each item (it sets the item height from its own modifier; if it measured items with an unbounded max, `fillMaxHeight` would collapse to content and the image to 0dp — the carousel's `modifier.height(140.dp)` is applied to the item container in material3 1.5.0-alpha15 per the AAR layout, but no frame was rendered).
- Quote-preview `IntrinsicSize.Min` row inside `AnimatedVisibility(expandVertically)`: intrinsic measurement during the expand animation was not observed.
- Picker search field at the M3 56dp minimum inside the 240/280dp dropdown; the 8dp growth vs the old 48dp was not seen.
- Code-block header: that a weighted chip does not shrink a *short* language label (the inner `Box` wraps content inside the weighted slot, so on paper it does not).
- All Part 3 arithmetic is from modifier chains and the type scale; nothing was rasterised at 360dp / 1.3.
- Part 4: the four transitions are JVM pins on the view-model; the rendered agreement of bubble, action row, composer, pulse and FAB after Stop-mid-regenerate was reasoned from the shared `streamingState` source, not watched.
