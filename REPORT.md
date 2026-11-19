# Pass W5 — Chat (branch `pass/w5-chat`, base `ec82b40` = v2.41.4)

Scope: `ui/screens/chat/**`, `ui/components/Markdown*`, `data/remote/sse/**` (part 5 only). No `assistant/**`, `ui/theme/**`, home/settings, `scripts/`, `.github/` edits. No emulator, AVD or device. Nothing pushed.

The W5 theme branch had not merged to main while this pass ran (`git log origin/main` at the time of writing stops at `ec82b40`; no `Durations.FusionOrbit`, `ExpressiveSizes`, `ContrastSlot`, shared `ErrorContent` or reshaped `ExpressiveEmptyState` exist at the base). The parts that need those tokens are listed under "Deferred to the rebase" with the exact edit each one needs so they can be applied in minutes once the orchestrator steers the rebase.

Hard rules checked at HEAD against `ec82b40`: `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` byte-identical (`cmp`); `FontStyle.Italic` in the ownership set: 0; `testTag|contentDescription` count unchanged (no edited main-source file carries one; see the gate log in the final message); no test removed or renamed (two historical test names kept, their bodies now pin the fixed behaviour); no androidTest added, so no judge-minimum change. Word-by-word haptics untouched.

## Commits

| Commit | Part | Gate at commit |
|---|---|---|
| `d91e929` | 5 — in-band `{"error":{...}}` on a 200 surfaces as `StreamingEvent.Error` | compile (main + androidTest) rc=0; 716 tests, 0 failures |
| `5f80df8` | 1 — `ChatContrast` delegates to `ThemeContrast`; `assistant/ui` import dropped | 716, 0 |
| `f7e8016` | 4 (shape decision) — source-card image clips to the card's own shape | 716, 0 |
| (this) | 7 — REPORT.md | gate rerun before commit, see final message |

## Part 5 — `data: {"error":{...}}` on a 200 (`d91e929`)

W4's negative finding reproduced from the code: `SseEventParser.parseOpenAiEvent` decoded the envelope as an `OpenAiStreamChunk` with `choices = []` (unknown keys ignored) and `parseOpenAiChunk` returned `KeepAlive` for a choiceless chunk, so `processOpenAiStream` (`ChatApiClient.kt:1262-1283`) dropped it, saw no content, emitted no `Done`, and the flow closed with neither content nor error. `tryParseOpenAiError` was only reachable from the decode-failure branch, which a well-formed envelope never takes.

- `SseEventParser.kt:66-72`: after a successful decode, if `choices` is empty, try `tryParseOpenAiError(data)` first and return its `Error`; otherwise fall through to the keep-alive path unchanged (`{}`, `{"choices":[]}`, `{"error":null}` are still `KeepAlive`).
- `SseEventParser.kt:106-110`: same shape of bug on the Ollama NDJSON path — `{"error":"..."}` decoded as a response with a null message and `done=false`. Try `tryParseOllamaError` before accepting a messageless, not-done response as a heartbeat.
- `SseEventParser.kt:213-236` `tryParseOpenAiError`: when the envelope's `code` is an HTTP status (400..599, as a JSON string or number — OpenRouter/LiteLLM relay the upstream status there), it is typed on `StreamingEvent.Error.httpStatus` with the same recoverability rule as `fromHttpError` (429 and 5xx recoverable). Any other code (`rate_limit_error`, `context_length_exceeded`) stays an untyped in-stream error. `message` and `code` text unchanged, so W4's `ChatErrorCopy` classification and the assistant's typed-status notices (`f27f85e`) both fire for a relayed 429 without reading the body.
- `ChatApiClient` needed no change: `processOpenAiStream` already forwards an `Error` and returns (`:1269-1273`), and `ChatRepositoryImpl` already builds `ProviderHttpException` from `httpStatus` (W4 part 1).

Tests (11 new/changed assertions, +5 test methods):
- `SseEventParserTest`: the historical `well-formed error response ...` test (name kept) now asserts `Error` with message, `code == "429"`, `httpStatus == 429`, recoverable; non-numeric code → no status, not recoverable; numeric JSON `503` → typed, recoverable; `"401"` → typed, not recoverable; choiceless chunk / `{}` / `{"error":null}` → still `KeepAlive`. Ollama: `{"error":"model not found"}` → `Error`, null status; messageless non-error → `KeepAlive`.
- `ChatApiClientHttpStatusTest.a 200 whose SSE body is an OpenAI error envelope surfaces a typed error, not a silent end`: the exact body `data: {"error":{"message":"Rate limit exceeded","type":"rate_limit_error","code":"429"}}` through `ChatApiClient.streamChat` on an `OPENAI_COMPATIBLE` provider with a 200 `text/event-stream` response: exactly one `Error`, `httpStatus == 429`, message is the envelope text, no `Content`, no `Done`.

## Part 1 — `ChatContrast` on `ThemeContrast` (`5f80df8`)

- `ChatContrast.kt:9-12`: imports `com.materialchat.ui.theme.ThemeContrast` / `ThemeContrastPair`; the two `com.materialchat.assistant.ui` imports are gone. Chat no longer depends on the assistant package for contrast.
- `ChatContrast.kt:33,36,38,41,43-47,73`: `BODY_TEXT`, `ICON`, `ratio`, `over`, both `pick` overloads and `relativeLuminance` delegate to `ThemeContrast`. The values are identical (`ThemeContrast` is the theme's copy of the same arithmetic and the theme test pins the two agree), so no resolved colour changes.
- `ChatContrast.kt:212-214`: `ChatSurfacePalette.pairs()` returns `List<ThemeContrastPair>` (the type `ThemeContrastTest.chatSurfacePaletteReadsOnEveryFillInEveryThemeAndBubbleFamily` already consumed structurally).
- `ChatContrastTest.kt:8,163-178`: the legacy-baseline helper builds `ThemeContrastPair`s. Assertions unchanged; still 39 appearances × 4 bubble families, all pairs ≥ 4.5:1 / 3:1.
- `ContrastSlot` (theme F5) and a chat snackbar slot: not at the base; deferred (below). The chat snackbar already renders through `ExpressiveSnackbar` (W4), whose inverse roles are gated by `HomeSettingsSlot:80-81`, so no chat-side pair is missing today.

## Part 4 — shape and duration decisions

| Site | W4 table said | Decision | Change |
|---|---|---|---|
| `WebSearchSourcesCarousel.kt:295` top-only `RoundedCornerShape(20.dp)` on the preview image | keep, or theme adds `TopSquircleShape(20)` | **Remove the inner clip.** The card already clips to `rememberPositionMorphShape(outer 24, inner 12, pressed 14)` (`:261-267`, `.clip(cardShape)` at `:275`), so a fixed 20dp top clip on the 66dp image was wrong on every corner: a visible wedge of card fill at the 24dp outer corners, an image overshoot past the 12dp inner corners between neighbours, and a static corner while the card morphed to 14dp on press. The card's own shape now clips the image; the `RoundedCornerShape` import is gone (`f7e8016`). No one-sided token is needed. |
| `MessageInput.kt:611` press shape | `CustomShapes.ButtonPressed` | already on it since W4 (`df5f5ff`) | none |
| `MessageInput.kt:973` default param | `ExpressiveShapes.ContentBlock` | already on it since W4 | none |
| Composer container `CustomShapes.MessageInputContainer` (round 28dp) | document | **Stays round 28dp**, per the orchestrator ruling: the bubble family it sits under is round (`MessageBubbleShapes.*`, user-selectable, deliberate per W3), and a squircle composer under round bubbles would be the only squircle in that vertical stack. Recorded here; not changed. | none |
| `FusionAnimation.kt:52-58` orbit on `Durations.Indicator.MorphShape` | `Durations.FusionOrbit` | token absent at base | deferred (below) |

## Part 2 — chip sizes

- Memory-reference chip (`MessageBubble.kt:1340-1363`, `.height(48.dp)` at `:1364`): **documented as a row, not a chip.** It is a full-width disclosure row that opens the memory panel under the bubble and carries the message-action group's 20/16/14dp corner language (`:1344-1346`), not a filter/assist chip; 48dp is the M3 list-row/touch-target height. Not changed.
- Model-badge compact chip (`BeautifulModelBadges.kt:374,672`, `defaultMinSize(40 × 28)`, 18dp icon, `labelMedium`): target `ExpressiveSizes.Compact`; token absent at base. Deferred (below).

## Part 3 — empty state and load-error screen

`ChatEmptyState.kt:94-129` already matches the W4 proposed target (`ExpressiveShapes.Card`, `Tile` glyph, `headlineSmall` Bold + `bodyMedium`, 24dp inset); the theme was to move `ExpressiveEmptyState` toward it, not the reverse. `ChatScreen.kt:583-600` load-error screen still draws its own column on `palette.screenError/screenErrorMuted` with Go back + Try again (`ExpressiveButtonStyle.Text`). A shared `ErrorContent(title, body, onRetry?, onBack?)` does not exist at the base (`LoadErrorContent` exists but has no back action and renders through the home empty-state cluster). Deferred (below).

## Part 6 — third state walk (static, with measurements)

Reference width 360dp; font scale 1.3 scales `sp` only (`dp` sizes are fixed). Assistant bubble max width = screen width (`MessageBubble.kt:851`); horizontal bubble inset 4dp (`:553,:607`) plus list gutter, so ~340dp of usable content width.

| State | Path | Verdict |
|---|---|---|
| Streaming cancelled | `ChatViewModel.cancelStreaming():927-987`: jobs cancelled, use-cases cancelled, `streamingState = Idle`, `isFusionRunning = false`; each streaming message either deleted when `shouldRemoveCancelledPlaceholder` (`:990-995`: assistant, blank content, blank thinking, no attachments) or kept with `isStreaming = false`. `isErrored` (`ChatViewModel.kt:287`) requires an assistant message with empty content, and the empty placeholder is removed, so a cancelled partial reply shows as a plain finished bubble: no error line (`MessageBubble.kt:474`), no Try-again button (`:680`, also gated by `onRetry` which `ChatScreen.kt:1099` only passes when `isErrored`). | ok, no change |
| 4/4 attachments | `ChatScreen.kt:161` `PickMultipleVisualMedia(4)`; `:166-169` room 0 → `limitNotice()` snackbar, picker not launched; `:173,:185` over-selection trimmed with `skippedNotice(n)`; `:207` camera at limit discards and notices. Preview row `MessageInput.kt:949-953`: `horizontalScroll`, `spacedBy(2.dp)`, item 72dp box with 64dp image and a 24dp remove disc (16dp glyph, `contentDescription = "Remove attachment"`). Four items = 4 × 72 + 3 × 2 = **294dp**, inside the ~328dp composer content width, so no scroll is needed at 4/4 and the fourth remove button is fully visible. | ok, no change |
| Long code block, font scale 1.3 | `MarkdownText.kt:805-816`: `bodyMedium` Monospace at 13sp/20sp → **16.9sp / 26sp** at 1.3; `.horizontalScroll` + `horizontalEdgeFade` (`:813-814`), 12dp side padding, so a 120-column line (~120 × 0.6 × 16.9sp ≈ 1 217dp) scrolls instead of wrapping or clipping. Header chip `labelSmall` `maxLines = 1` (`:785`), copy/open buttons fixed-size. Nothing in the header can wrap. | ok on paper |
| Table in the same bubble, 1.3 | `MarkdownText.kt:1729-1752`: columns measured with `rememberTextMeasurer` at the current density (font scale included; `remember` keyed on it), each clamped **104–360dp** after 2 × cell padding; `tableWidth` = sum; viewport `min(maxWidth, tableWidth)` with `horizontalScroll` (`:1779`); cells `softWrap` with `heightIn(min 40dp)` (`:1873`). Example: 3 columns whose longest cell is 14 chars of `bodySmall` 12sp → 15.6sp at 1.3 ≈ 14 × 0.55 × 15.6 ≈ 120dp + padding → ~136dp per column, 408dp table on a 340dp viewport → scrolls by 68dp, rows stay one line. A 361dp+ cell hits the 360dp clamp and wraps inside the cell, growing the row, never the table. Code block and table are independent `ContentBlock`s in one column, so neither constrains the other. | ok on paper |
| Errored bubble wrap, 1.3 | `MessageBubble.kt:1694-1716` `ErrorStateContent`: `Row` of an 18dp icon, 8dp gap, `Text` `bodyMedium` with `weight(1f, fill = false)`. 14sp → **18.2sp** at 1.3, ~9dp per character average. Longest notice `ChatErrorCopy.FORBIDDEN` (81 chars, `ChatErrorCopy.kt:21`) ≈ 730dp of text on ~314dp (340 − 18 − 8) → **3 lines**, wrapping at word boundaries; `fill = false` keeps the bubble no wider than the text needs. The Try-again `FilledTonal` button (`:1723-1729`) sits on its own row below (`:680-683`), so it never competes for width with the notice. | ok on paper |

## Deferred to the rebase (needs the W5 theme merge)

Each entry is the exact edit; none was applied because the token is absent at `ec82b40` and the theme owner adds tokens.

1. `FusionAnimation.kt:55-57`: `decorativeSpin(MaterialChatMotion.Durations.Indicator.MorphShape)` → `decorativeSpin(MaterialChatMotion.Durations.FusionOrbit)`; drop the "no dedicated token yet" comment at `:52-54`.
2. `BeautifulModelBadges.kt:374,672`: `defaultMinSize(minWidth = 40.dp, minHeight = 28.dp)` → `ExpressiveSizes.Compact.*`; icon `size(18.dp)` at `:411,:709` → the Compact icon token if one ships.
3. `ChatScreen.kt:560-600` `ErrorContent` → the shared `ui/components/ErrorContent(title = "Something went wrong", body = message, onRetry, onBack)` once it exists, keeping `ChatErrorCopy.TRY_AGAIN` as the primary action text and primary-first order. `ChatEmptyState`: switch to the shared `ExpressiveEmptyState` only if the theme's version lands on `Card` + `headlineSmall` Bold + `bodyMedium` + 24dp (otherwise chat keeps its own, which is already the target).
4. `ChatSurfacePalette` implements `ContrastSlot` (and adds a snackbar pair if the shared slot does not carry one) so `ThemeContrastTest` can enumerate it through the interface.

## Deliberately left alone

- Composer shape (round 28dp) — orchestrator ruling, see part 4.
- Memory-reference chip at 48dp — a row, see part 2.
- `MessageBubble.kt:925-968` bubble-family radii (user-selectable).
- Animated `RoundedCornerShape(animatedDp)` morphs (`MessageInput.kt:565-585`, `MessageActions.kt`, `ChatTopBar.kt`, `ModelPickerDropdown.kt`, `ScrollToBottomFab.kt`): the corner is the animated value.
- `MarkdownText.kt:1720` table-header bottom-only 8dp: still no one-sided token and the header is a sub-shape of its `ContentBlock`, so unlike the carousel image there is no outer clip to inherit.
- The plus menu staying enabled at 4/4 (notice instead of a disabled control).
- `SmoothStreamingText`, tap router chain, haptic timing, keyboard spacing.

## Needs another owner

- **theme**: `Durations.FusionOrbit = 3000`, `ExpressiveSizes.Compact`, `ContrastSlot`, shared `ErrorContent(title, body, onRetry?, onBack?)` — the four deferred items above consume them.
- **assistant**: nothing new. `AssistantCopy.requestFailure` already reads `httpStatus` (`f27f85e`); with part 5 a relayed in-stream 429 now carries it too, so the rate-limit notice fires for that shape without any assistant change.
- **home/settings**: nothing.

## Not verifiable without a device

- Source-card image after the inner clip removal: that the card's `.clip(cardShape)` at `WebSearchSourcesCarousel.kt:275` clips the `AsyncImage` identically on API 26 (clip is applied on the parent node, which is the documented API 26-safe direction) and that the press morph to 14dp now carries the image corners with it.
- Part 5 against live provider streams: the test bodies are the OpenAI envelope shape and OpenRouter/LiteLLM numeric-code variants synthesised through an OkHttp interceptor; a real gateway's 200-with-error envelope was not sampled, and gateways that send the envelope under a different key are still a silent end.
- The errored-bubble 3-line wrap, table 68dp overscroll and 294dp attachment row are arithmetic from the modifier chains and type scale, not rendered measurements.
- `ThemeContrast` delegation: numerically identical by construction and by the theme's own pin test; no colour was observed on a device.
