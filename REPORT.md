# Pass W7 — Chat (branch `pass/w7-chat`, rebased onto `213efa87` = W7 theme merge)

Scope: `ui/screens/chat/**`, `ui/components/Markdown*`, `ui/components/MathWebViewTouch.kt`, `data/remote/sse/**`, `domain/model/StreamingState.kt`, `util/AttachmentLimits.kt`. No emulator, AVD or device. Nothing pushed.

Base was `7e6f6624` (v2.41.6). After the steering message the branch was rebased onto `origin/main` at `213efa87` (W7 theme merged at `4bf268d3`); the two commits before the rebase replayed cleanly. `origin/main` has since moved on to the W7 assistant merge (`1f3a90e0`); the branch was not rebased a second time (no overlap: the assistant diff touches no chat-owned file).

Hard rules at HEAD vs the merge-base: `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` `cmp`-identical; `FontStyle.Italic` in the ownership set: 0; `testTag` removed: 0; `contentDescription` removed: 0 descriptive (the two `-contentDescription = null` lines in the diff are the Send glyph inside the deleted `FusionBadge` and the Send glyph that became `ExpressiveButton(leadingIcon = …)`, both `null` before); `scripts/`, `.github/`, `ui/theme/**` untouched; no test weakened, renamed or deleted; no androidTest added (judge minimum unchanged). Word-by-word haptics untouched. Deleted theme symbols (`CustomShapes.Fab/BottomSheet/CodeBlock/ProviderCard/Dialog/MessageActionButton/SendButton`, `M3ExpressiveCircularProgress/LinearProgress`, `Indicator.Arc*/Linear*`): grep of the ownership set after the rebase, 0 hits.

Gates: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin`, `:app:testGithubDebugUnitTest` before every commit.

## Commits (oldest first)

| Commit | Part | Gate at commit |
|---|---|---|
| `6b34086b` | 1 — classified copy for every failed chat action; `ChatCopyRulingsTest` fails on exception text | 799 tests, 0 failures |
| `ac2ad2a6` | 2, 5 — fusion Start button on `ExpressiveButton`; send button documented as the exempt raw `Button`; `FusionBadge` deleted | 799, 0 |
| `8c4c2c12` | 4 + theme rows C1–C10 — `bubbleShapeFor` on `ExpressiveShapes.Bubble.*`, `StreamingIndicator` on `Durations.Indicator.Streaming*` + `Tweens.*`, `ChatErrorCopy` aliases `CopyRules`, send glyphs on `ExpressiveSizes.IconLarge` | 806, 0 |
| `5e529ab8` | 3, 6, memory-chip and composer rulings, walk round 4 fixes + pins | 808, 0 |
| (this) | REPORT.md | gate rerun before commit, see final message |

## Part 1 — exception text (`6b34086b`, audit #2)

Every `e.message ?: …` / `"…${e.message}"` under chat UI ownership, re-opened and replaced. The brief listed eight sites at the base; the grep found fifteen (`ChatViewModel.kt:212, 392, 1087, 1098, 1131, 1240, 1346, 1413, 1425, 1634, 1714, 1790, 2025`, `ChatScreen.kt:283`), plus two parse-error lines in `SseEventParser.kt:77, 115`.

- `ChatErrorCopy.action(error, fallback)` (`ChatErrorCopy.kt:67-84`): a `ProviderHttpException` anywhere in the cause chain (depth 6) classifies exactly like a failed reply (`notice(status, error)`), an `IOException` in the chain is `NO_CONNECTION`, everything else is the action's own line. The exception's text is never used. `isConnectivity` and the new `httpStatusOf` share one `causeChain` sequence.
- Nine one-phrase lines in the theme's `Could not …` family (`CopyRules.FAILURE_VERB`), no trailing period: `COULD_NOT_LOAD_MODELS` (+ `couldNotLoadModels(providerName)` naming the provider when known), `COULD_NOT_CHANGE_MODEL`, `COULD_NOT_BRANCH`, `COULD_NOT_RESEND_EDIT`, `COULD_NOT_EXPORT`, `COULD_NOT_SHARE`, `COULD_NOT_REDO`, `COULD_NOT_UPDATE_BOOKMARK`, `COULD_NOT_RUN_FUSION`. One notice for the full-screen error card (`ErrorContent` on `ChatScreen.kt:411`): `COULD_NOT_LOAD_CONVERSATION` = "Could not load this conversation. Try again to reload it." (full stops, the last sentence names the action).
- The two `ChatUiState.Error(...)` sites (`:212` flow `catch`, `:392` load catch-all) take the notice; the Room / flow failure text had no user meaning.
- Fusion failure (`:2025`) now goes through `action` too (the W6 report had left it for the orchestrator; the W7 brief's rule covers every chat site).
- Each of the `viewModelScope.launch { try … catch (e: Exception) }` action paths gets `catch (e: CancellationException) { throw e }` first (branch, edit-resend, redo, change model, load models, toggle/add bookmark), the shape `regenerateResponse` already had: a cancelled scope must not raise a `Could not …` snackbar.
- `SseEventParser.kt:77, 115`: the `PARSE_ERROR` event's message loses the serialization exception text (`"Failed to parse SSE event"` / `"… NDJSON event"`). That message is the unclassified-snackbar diagnostic (`ChatErrorCopy.snackbar`), so `kotlinx.serialization`'s text reached the user. The three `SseEventParserTest` assertions pin `contains("Failed to parse")` and still hold. Left: `SseEventParser.kt:230` `error.message ?: "Unknown OpenAI error"` — that is the provider's in-stream error body, the documented diagnostic, not an exception's text.

Pins: `ChatCopyRulingsTest` (+2 tests, `:101-160`): `no exception text reaches a user-visible string under chat ownership` scans every non-comment line of the chat-owned files under `ui/` for `${…\.(localized)?message…}` and `.message ?: "` and lists `file:line code`. Scope and shape were narrowed after the first run flagged `ChatViewModel.kt:1265` (`?.message ?: return` on a domain `Message`, so the fallback must be a string literal) and `data/remote/**` lines that build the event/exception messages the view model classifies (inputs, not copy); the KDoc says why. `failed-action snackbar lines are one phrase in the Could not family` pins every line against `startsWith("Could not ")`, `CopyRules.isPhrase`, no period, no ASCII ellipsis, and the card notice against full stops + "Try again". `ChatErrorCopyTest` (+1): typed 429 wrapped in a `RuntimeException` → `RATE_LIMITED`; 401 → `UNAUTHORIZED`; 502 → `SERVER_ERROR`; `ConnectException` in the chain → `NO_CONNECTION`; an `IllegalStateException("SQLiteConstraintException…")` → the fallback, text absent; `null` → fallback.

## Part 2 — raw M3 buttons (`ac2ad2a6`, `8c4c2c12`; audit #5 / F9 / F11)

- `FusionModelSelector.kt:241` raw `Button` on `CustomShapes.Button` with `tertiary`/`onTertiary` colours → `ExpressiveButton(style = Filled, leadingIcon = Send, text = "Start fusion")`. `Button`, `ButtonDefaults`, `CustomShapes` imports gone. The tertiary tint is lost: `ExpressiveButton` has no container-colour parameter (theme-owned) and the orchestrator's rule prefers the shared button over a bespoke copy; the sheet's checkmarks and judge star stay tertiary, so the sheet keeps its accent. Recorded in the source comment at the call site.
- `ComposerSendButton.kt:169` stays a raw `Button`: its contour is a `Morph` track over `MaterialShapes` (cookie → scalloped cookie → square) that also encodes the Idle/Ready/Streaming state; `ExpressiveButton` animates one `RoundedCornerShape` radius and cannot carry it. Recorded in the KDoc (`ComposerSendButton.kt:110-119`).
- `ComposerSendButton.kt:216, 222` 22dp glyphs → `ExpressiveSizes.IconLarge` (24dp, the M3 glyph inside a 48dp filled control, theme's W7 icon ladder). **Visible change**: +2dp on the send and stop glyphs. Not rendered.

## Part 3 — alpha fills (`5e529ab8`, audit #8)

Both sites already had a `ChatSurfacePalette` slot that composites the same alpha over the assistant bubble fill and gates the content colour on the result; the composables were drawing the alpha themselves beside it.

| File:line | Was | Now |
|---|---|---|
| `MessageBubble.kt:1289` memory disclosure panel | `surfaceContainerHighest.copy(alpha = 0.86f)` + `onSurfaceVariant` | `palette.memoryPanel.container` / `.content` (resolver `ChatContrast.kt:356, 421`: `over(surfaceContainerHighest@0.86, assistantFill)`, content picked for body text) |
| `MessageBubble.kt:1622` image-generation loading card | `primaryContainer.copy(alpha = 0.55f)` + `onPrimaryContainer` (three sites: surface, icon tint, title) | `palette.imageLoadingCard.container` / `.content` (resolver `:365, 465`) |

Same painted colour on an opaque assistant bubble (the composite is what the alpha produced); the content colour can now differ from `onSurfaceVariant` / `onPrimaryContainer` only where the palette's contrast pick says those fail on the composite. `ChatContrastTest.kt:188, 196` already pins the panel pair; no new test.

## Part 4 — bubble radii on theme tokens (`8c4c2c12`, theme rows C1–C5)

`bubbleShapeFor` (`MessageBubble.kt:919-966`): `COMPACT`/`GEOMETRIC` system bubbles → `ExpressiveShapes.Bubble.Compact.System` (20) / `Geometric.System` (16); `ROUNDED` → `Bubble.Rounded` (34); `COMPACT` user/assistant → `Bubble.Compact.User` (18/6/18/18) / `.Assistant` (6/18/18/18); `GEOMETRIC` → `Bubble.Geometric.User` (18/4/18/10) / `.Assistant` (4/18/10/18). Value-identical to the literals (theme `ShapeTokensTest.bubbleFamilyTokensCarryTheChatRadiiExactly` pins every value). KDoc rewritten: the function is the family, the radii are the theme's, the settings preview reads the same tokens. The `RoundedCornerShape` import stays (animated morphs at `:1109`, `:1386`).

## Part 5 — `FusionBadge` (`ac2ad2a6`, audit #14)

`grep -rn "FusionBadge" app/src`: one hit, its own definition (`FusionBadge.kt:27`). Deleted. The remaining references are in `docs/` reports (history).

## Part 6 — cross-owner pins (`5e529ab8`, theme row C11)

`CopyRulesTest.kt:22-30` on main pins `ChatErrorCopy.TRY_AGAIN == CopyRules.TRY_AGAIN == AssistantCopy.AFTER_ERROR` and the two `REQUEST_FAILED`s. `ChatErrorCopyTest:96-100` dropped exactly those two assistant-equality lines (`REQUEST_FAILED`, `AFTER_ERROR == TRY_AGAIN`); `RATE_LIMITED` and `NO_CONNECTION` have no `CopyRules` constant, so chat still pins those two against `AssistantCopy`. The commit message says so.

## Theme rows C6–C10 (`8c4c2c12`)

| Row | Change |
|---|---|
| C6–C8 | `StreamingIndicator.kt:83, 311` `3000` → `Durations.Indicator.StreamingSpin`; `:85, 312-314` `2400` → `StreamingMorph`; `:425` `1400` → `StreamingShimmer`. |
| C9 | `gatedInfiniteFloat` takes a `TweenSpec<Float>`; callers pass `Tweens.linear(StreamingSpin)` / `Tweens.easeInOut(StreamingMorph | StreamingShimmer)`. The raw `tween(` at `:486`, the `Easing`/`LinearEasing`/`EaseInOutCubic`/`tween` imports and the "no tokens exist" KDoc are gone. Chat now holds zero raw `tween(` / `spring(`. |
| C10 | `ChatErrorCopy.REQUEST_FAILED = CopyRules.REQUEST_FAILED`, `TRY_AGAIN = CopyRules.TRY_AGAIN` (still `const val`, same strings). |

## Rulings recorded (`5e529ab8`)

- **Composer stays 28dp** (audit #6): `ComposerSendButton.kt:49-64` `ComposerFieldRadius = 28.dp` carries the ruling in its KDoc (same value as `ExpressiveShapes.Card` and the former `CustomShapes.MessageInputContainer`; drawn as a `SquircleShape`, tightening to `ComposerFieldFocusedRadius = 20.dp` on focus; not migrated to a chip capsule or to the bubble family). `MessageInput.kt:333-336` reads the two constants instead of restating `20.dp`/`28.dp`; `ComposerFieldShape` is built from the resting constant.
- **Memory chip is a row** (audit #4, brief: decide one): kept at 48dp, now `ExpressiveSizes.Target`, with a KDoc on `MemoryIndicatorChip` (`MessageBubble.kt:1319-1328`) saying why it is not `Chip.Height` 40 + `ExpressiveShapes.Chip`: it shares the action group's animated 20/16/14dp corner and opens a panel in place like the thinking header; only its glyph takes `Chip.Icon`. The composable keeps its name (`MessageListMemoryChipTest` and the tap-routing tests reference the chip by tag/behaviour, not by this private name; renaming is cosmetic and was not done).
- `ChatViewModel.kt:1317` "Cannot edit the first message" → "The first message cannot be edited": `CopyRules.BANNED_FAILURE_VERBS` includes "Cannot" as an opening verb. Grep of chat UI literals for the banned families: that was the only one.

## Part 8 — coherence walk round 4 (`6b34086b`, `5e529ab8`)

Sequence: streaming → Stop → regenerate → edit → branch → bookmark, 200-message chat, font scale 1.3, 360dp. The W6 Part 3/4 measurements still hold (nothing in those modifier chains changed this pass except the two palette fills and the 24dp send glyph, which sits in a fixed 48dp control). New findings at the view-model seam:

| Scenario | Finding | Fix / verdict |
|---|---|---|
| Edit → Save & Submit fails (branch use case throws) | `submitEditedMessage` cleared `editingMessageId`/`editingText` optimistically before launching; on failure it raised the snackbar and the edited text was gone — the bubble showed the original message, the composer was empty, and the snackbar carried `IllegalStateException`'s text. | **fixed** (`ChatViewModel.kt:1330-1368`): the source conversation id is captured at launch; on failure the editor reopens on the same message with the same text (only if the user has not opened another editor meanwhile and is still on that conversation), one `COULD_NOT_RESEND_EDIT` line. Pinned. |
| Branch fails with a typed provider status | raw `e.message` snackbar | **fixed**: `SERVER_ERROR`/class line via `action`; no `NavigateToBranch`. Pinned. |
| Stop → regenerate → Stop | unchanged from W6 (CancellationException rethrown, Idle, no snackbar) | ok, still pinned |
| Bookmark toggle fails (Room) | raw `SQLite…` text in the snackbar | **fixed**: `COULD_NOT_UPDATE_BOOKMARK`; the optimistic `bookmarkedMessageIds` update only runs after the use case returns, so the icon never flips on a failure. |
| Export → share chooser throws (`ChatScreen.kt:283`) | raw `e.message` | **fixed**: `COULD_NOT_SHARE` |
| Action launched then scope cancelled (navigate away during branch/redo) | `catch (e: Exception)` swallowed `CancellationException` and emitted a `Could not …` snackbar into a dead collector | **fixed**: rethrown first on every action path |
| Model load fails after a conversation switch | the snackbar named the provider plus the fetch text | `couldNotLoadModels(provider.name)` / `COULD_NOT_LOAD_MODELS`; the `isLoadingModels` reset was already conversation-guarded |

Tests: `ChatViewModelStreamingCoherenceTest` (+2, now 6): `a failed edit-and-resend hands the edited text back and raises one classified line` (editor reopens on `u2` with the edited text, no `NavigateToBranch`, exactly `[COULD_NOT_RESEND_EDIT]`, no "Source conversation" text); `a failed branch with a typed status shows the class line and never the exception text` (503 → `[SERVER_ERROR]`). Harness unchanged except `branchConversationUseCase` is now a field mock.

Measured and left: everything in the W6 Part 3 table (no modifier chain in those rows changed); the editing row `Cancel` / `Save & Submit` as two `ExpressiveButton`s (`defaultMinSize(56)`, 28dp horizontal padding) inside the 82%-width user bubble at 360dp: 295dp bubble − 32dp padding = 263dp for ~104 + 8 + ~176dp = 288dp at scale 1.3 (`labelLarge` 14sp Bold → 18.2sp, "Save & Submit" ≈ 120dp text + 56 padding) — **tight; the row may wrap or clip `Cancel` at 1.3 on 360dp**. Not fixed: changing the edit row's layout (wrap, or Text-style cancel at 24dp padding) is a visible change the brief did not ask for and I could not render; listed under unverified for a device check.

## Deliberately left alone

- `ArenaPromptInput.kt:61` (home-settings' file; needs-another-owner below).
- `MarkdownText.kt:1720` table-header bottom-only 8dp radius: still no one-sided token.
- `SseEventParser.kt:230` provider in-stream error body as the unclassified snackbar diagnostic (documented in `ChatErrorCopy.snackbar`).
- All animated `RoundedCornerShape(animatedDp)` morphs and `ExpressivePositionShapes`.
- `ChatContrast.kt` `.copy(alpha =` composite fills (they are the contrast arithmetic the two Part 3 slots read).
- `MessageBubble.kt:716` swipe-reveal alpha (progress-driven, exempt).
- `CustomShapes.Pill` on the date separator and sources tile (`ExpressiveShapes.Chip` aliases it; the W5 row said same shape, optional rename).
- The `MemoryIndicatorChip` name.
- `SmoothStreamingText`, tap router chain, haptic timing, keyboard spacing.

## Needs another owner

- **home-settings**: `ArenaPromptInput.kt:61` is the last consumer of `CustomShapes.MessageInputContainer` (28dp round). Chat's composer ruling is now `ComposerFieldRadius` (`ComposerSendButton.kt:58`, `internal`, same module); arena can read it (or `ExpressiveShapes.Card`'s 28dp) and the theme can delete the token. `SettingsScreen.kt` bubble preview: theme row H1 (consume `ExpressiveShapes.Bubble.*` or chat's `bubbleShapeFor`).
- **theme**: `CopyRules` has no constants for the `RATE_LIMITED` / `NO_CONNECTION` class lines that chat and the assistant share; `ChatErrorCopyTest:114-115` still pins them cross-owner. Add them to `CopyRules` and the last two chat↔assistant pins can move. `ExpressiveButton` has no container-colour parameter: the fusion Start button lost its tertiary tint (Part 2); if a tertiary filled variant is wanted, it is a theme addition. `MarkdownText.kt` is in `ui/components` (theme list in the W6 theme report) but `Markdown*` is chat-owned per this brief: `:1720` needs a one-sided token if the orchestrator wants it tokenised.
- **orchestrator**: the edit-row width at font scale 1.3 / 360dp (Part 8) — rule whether `Save & Submit` may shorten to `Save` or the row may wrap.
- **assistant**: nothing.

## Not verifiable without a device

- Send/stop glyph at 24dp instead of 22dp inside the 48dp morphing contour: optical balance against the cookie/square shapes was reasoned, not seen.
- The memory panel and image-loading card after moving to the palette composites: on an opaque assistant bubble the composite equals what the alpha painted; on the `GEOMETRIC` family (assistant fill is a lerp) the palette composites over that lerp too, but the rendered colour was not compared frame-to-frame.
- The fusion Start button as `ExpressiveButton` (primary, 56dp min height, 28dp padding) inside the sheet at 360dp.
- The edit row at scale 1.3 / 360dp (above): the ~25dp overflow is arithmetic from the type scale and button padding; whether Compose wraps, clips or shrinks `Cancel` was not observed.
- Part 8 transitions are JVM pins on the view-model; the rendered agreement of bubble, composer and snackbar after a failed edit-and-resend was reasoned from the shared state, not watched.
- `ShapeTokensTest` proves the bubble tokens equal the old literals; that the settings preview now draws the same shape is home-settings' H1, not rendered here.
