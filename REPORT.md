# Pass W8 — Chat (branch `pass/w8-chat`, rebased onto `2ae059d5` = W8 theme merge)

Scope: `ui/screens/chat/**`, `ui/components/Markdown*`, `ui/components/MathWebViewTouch.kt`, `data/remote/**`, `domain/model/StreamingState.kt`, `util/AttachmentLimits.kt`. No emulator, AVD or device. Nothing pushed.

Base was `6ade255c` (v2.41.7). On the steering message the branch was rebased onto `origin/main` at `2ae059d5` (W8 theme merged at `3b79c36c`); no local commit existed yet, so the rebase was a fast-forward. Every file:line below was taken from grep on this branch after the edits.

Gates before every commit: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin`, `:app:testGithubDebugUnitTest` (0 failures, 0 errors, 0 skipped).

## Hard-rule checks at HEAD vs `2ae059d5`

- `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt`: `git diff --quiet` identical, all four.
- `FontStyle.Italic` in the ownership set: 0.
- `testTag` / `contentDescription` lines removed in the diff: 0.
- `scripts/`, `.github/`, `ui/theme/**`: untouched (0 lines in the diff).
- `@Test` lines removed: 0. No test renamed. No androidTest added (judge minimum unchanged).
- Word-by-word haptics untouched (the streaming text file is byte-identical).

## Commits (oldest first)

| Commit | Part | Unit tests at commit |
|---|---|---|
| `d4a49923` | 5 — W8 theme rows C1–C5 | 850, 0 failures |
| `35a97f1f` | 1, 2 — pins (the code was already in the base) | 850, 0 |
| `64cadcdb` | 3 — edit row never wraps; part-4 bubble wiring | 850, 0 |
| `ea4ef907` | 4 — thinking-only Stop is cancelled, not failed; attach pills refuse at 4/4 | 850, 0 |
| (this) | REPORT.md | gate rerun, see final message |

The four code commits were gated by one green run of the full gate over the combined working tree (compile both targets, then the suite at 850 / 0), then split by part with `git add` of the files each part owns; each commit's content compiles on its own except that `64cadcdb` already wires `messageItem.isErrored` into the action-row condition that `ea4ef907` makes meaningful (the field existed before, so it compiles either way).

## Part 1 — `ChatScreen.kt:283` share failure (`35a97f1f`, `d4a49923`)

Already done in the base: the W7 chat merge (`b74622f0`, before `6ade255c`) had replaced `"Failed to share: ${e.message}"` with `ChatErrorCopy.COULD_NOT_SHARE` (`ChatScreen.kt:283`, `catch (_: Exception)`). The home-settings W7 report that listed it was written against the pre-merge tree. What this pass adds:

- `ChatErrorCopy.kt:57` `const val COULD_NOT_SHARE = CopyRules.COULD_NOT_SHARE` ("Could not share", `== CopyRules.failureLine("share")`; theme row C5). The W7 string was "Could not share the export"; the theme's constant is the shorter line, so the snackbar text changes by three words.
- `ChatCopyRulingsTest.kt:143` `the chat screen is scanned and no Failed-to literal survives under chat ownership`: asserts `ChatScreen.kt` is in the scanned UI set, re-runs the two exception-text regexes on it alone (0 hits), scans every string literal in the chat-owned UI files for `CopyRules.BANNED_FAILURE_VERBS` (0 hits), and pins `COULD_NOT_SHARE == CopyRules.COULD_NOT_SHARE == failureLine("share")`. The banned-verb scan is scoped to `ui/` on purpose: `data/remote/**` carries twelve "Failed to parse / fetch …" exception messages (`SseEventParser.kt:78, 116`, `ChatApiClient.kt:817, 904, 1163, 1497`, `GitHubReleaseApiClient.kt:52`, `ModelListApiClient.kt:128, 176, 205, 265, 309`) that the view model classifies before display; the first run of the scan over the whole ownership set listed exactly those, and the existing scanner KDoc (`:104-110`) already rules them inputs, not copy.
- Confirmed by the scanner: `ui/screens/chat/**` has zero `${e.message}` templates and zero `.message ?: "` fallbacks.

## Part 2 — `bubbleShapeFor` on the theme tokens (`35a97f1f`)

Already done in the base (`MessageBubble.kt:946-947, 967-971`): `COMPACT`/`GEOMETRIC` system → `ExpressiveShapes.Bubble.Compact.System` / `Geometric.System`; `ROUNDED` → `Bubble.Rounded`; `COMPACT` and `GEOMETRIC` user/assistant → the `.User` / `.Assistant` tokens. The home-settings report's claim was, again, pre-merge.

- `BubbleShapeFamilyTest.kt` (new, 2 tests): `rounded compact and geometric read the theme bubble tokens for every role and position` runs `bubbleShapeFor(style, isUser, isAssistant, groupPosition)` for `ROUNDED` × `COMPACT` × `GEOMETRIC` × {user, assistant, system} × every `MessageGroupPosition` and `assertSame`s the token (`ROUNDED` system is `MessageBubbleShapes.SystemBubble`, the documented exception); `expressive reads the grouped message bubble shapes by position` pins the `EXPRESSIVE` branch to `MessageBubbleShapes.Grouped.*` / `UserBubble` / `AssistantBubble`. `MessageBubble.kt` loads on the JVM (its only top-level initialiser is the lenient `Json` at `:140`), so the function is called directly.

## Part 3 — edit row width (`64cadcdb`)

Orchestrator ruling applied: at font scale 1.3 / 360dp the submit label may shorten to "Save"; the full label stays wherever it fits.

- `EditRowLayout.kt` (new): `EditRowCopy.CANCEL / SAVE_FULL ("Save & Submit") / SAVE_SHORT ("Save")`, `EditRowGap = 8.dp`, `editRowButtonWidth(labelWidth, inset) = label + 2 × inset`, and the decision `editRowSaveLabel(rowWidth, cancelLabelWidth, fullSaveLabelWidth, size = ExpressiveButtonSize.Default, gap)`: the full label stays iff `(cancel + 2 × textInset) + gap + (save + 2 × inset) <= rowWidth`. Insets are read from the theme's `ExpressiveButtonSize.Default` (56 / 28 / 24 / 20, W8 theme part 2), so a theme change moves the decision with it.
- `MessageBubble.kt:1785-1810` `EditingContent`: `rememberTextMeasurer` measures both labels in `labelLarge` Bold (the style `ButtonContent` draws, at the current font scale) inside a `BoxWithConstraints`, like the assistant toolbar measures its chip label; the `Row` keeps `Arrangement.End`, the two `ExpressiveButton`s and the 8dp gap, and now takes `saveLabel`. Imports `:39`, `:111`.
- `EditRowLayoutTest.kt` (new, 5 tests): at 360dp the row is 295 − 32 − 4 = 259dp (82% bubble, 16dp content padding, 2dp air); with labels measured as 14sp × 0.6 per character (bold label average) the full label fits at scale 1.0 (≈ 50 + 48 + 8 + 109 + 56 = 271 … see the test's numbers) and shortens at 1.3; the boundary is inclusive; the short row fits wherever the full one does; both labels are `CopyRules.isPhrase` and the short one is a prefix of the full one. The per-character estimate is only the test's stand-in for the measurer; the composable uses the real measurement.

Visible change: at scale 1.3 on 360dp (and any narrower combination) the tonal button reads "Save" instead of "Save & Submit". Nothing changes where the full label fits.

## Part 4 — streaming / long-chat walk round 4 (`64cadcdb`, `ea4ef907`)

Walked at the view-model seam: thinking block + memory chips + table + code block in one assistant bubble; cancelled stream; errored bubble + Try again; attachments 4/4.

| Scenario | Finding | Fix / verdict |
|---|---|---|
| Stop after the first thinking token, before any content | `cancelStreaming` (`ChatViewModel.kt:935-1000`) keeps the placeholder because `shouldRemoveCancelledPlaceholder` (`:998`) sees non-blank thinking, and sets `isStreaming = false`; the Room collector then derived `isErrored = ASSISTANT && content.isEmpty() && !hasAttachments && !isStreaming` (old `:291`), which is true for that reply. The bubble showed the thinking block **and** "Request failed" + `Try again` under it, with no action row (`MessageBubble.kt:688` gates the retry on `isErrored`), for a reply the user had just stopped. | **fixed**: `ChatUiState.kt:161` `isErroredReply(message, isLastAssistantMessage, streamingState)`: no content/attachments/streaming as before; no thinking → errored (nothing arrived); thinking only → errored only when it is the last assistant reply and `streamingState` is an `Error` for that message id (or one with no id). `ChatViewModel.kt:291` reads it on every Room emission with `streamingStateAtCollect`; `MessageBubble.kt:593` shows the action row for a non-errored reply that holds content, attachments **or** thinking, and withholds it for an errored one. Pinned. |
| Reply fails after only thinking (503) | The use case writes `isStreaming = false` to Room and emits `Error`; the order is not fixed. With the new rule, if the Room emission arrived before the `Error` the item would have been derived against `Idle`/`Streaming` and stayed non-errored. | **fixed**: `ChatViewModel.kt:911-921` `updateStreamingState` re-derives `isErrored` for every item when an `Error` lands, so whichever lands last marks the reply. Pinned in both orders. |
| Older thinking-only reply, later send fails with no message id | Could have turned red under a messageId-less `Error`. | guarded: the rule requires `isLastAssistantMessage`. Pinned. |
| Attach photo / Take photo at 4/4 | `onAttachImage` (`ChatScreen.kt:456`) launched the picker and `onTakePhoto` (`:461`) opened the camera; the limit notice came only from the result handlers (`:160-166`, `:198-204`) after the round trip through the other app. | **fixed**: both pills read the pending count and raise `AttachmentLimits.limitNotice()` at once when `!AttachmentLimits.hasRoom(existing)`; the camera file is not prepared in that case. `AttachmentLimits.kt:12, 19` `room(existing)` / `hasRoom(existing)`; the two result handlers read `room` / `hasRoom` instead of restating the arithmetic. Pinned. |
| Thinking + memory chips + table + code block in one bubble | Column order `ThinkingSection` → attachments → content → `MemoryDisclosure` → sources carousel (`MessageBubble.kt:445-560`) unchanged; the table header strip now draws `ExpressiveShapes.ContentBlockHeader` (value-identical). | no contradiction found; nothing changed in those modifier chains |
| Stop → regenerate → Stop; failed regenerate; failed edit-and-resend; failed branch | The W6/W7 pins still hold (`ChatViewModelStreamingCoherenceTest`, first six tests unchanged). | ok |

Tests: `ErroredReplyTest.kt` (new, 5): empty stopped reply errored; stopped thinking-only reply not errored under `Idle` / `Cancelled` / `Streaming`; errored under `Error(messageId = it)` and `Error(messageId = null)`, not under an `Error` for another id; an older thinking-only reply never errored; content, attachments, streaming and non-assistant roles never errored. `ChatViewModelStreamingCoherenceTest` (+2, now 8): `Stop on a reply that holds only thinking leaves a cancelled reply, not a failed one` (open stream, Room shows the thinking-only reply, Stop, Room write lands after → `isErrored == false`, `Idle`, no snackbar); `a reply that failed after only thinking is errored whichever of the Room write and the error lands last` (order A: error then write; order B: a gated flow lets the write land before the error; both end errored, the older reply never turns errored, exactly one `SERVER_ERROR` snackbar per failure). `AttachmentLimitsTest` (+2): `room` counts down and never goes negative; `hasRoom` is false at 4 and above.

## Part 5 — W8 theme rows (`d4a49923`)

| Row | Change |
|---|---|
| C1 | `ChatErrorCopy.kt:32, 41` `RATE_LIMITED = CopyRules.RATE_LIMITED`, `NO_CONNECTION = CopyRules.NO_CONNECTION` (still `const val`, same strings). |
| C2 | `ChatErrorCopyTest.kt:113-121`: the two `AssistantCopy.*` cross-owner pins deleted (`CopyRulesTest` holds chat == assistant for all four lines now); replaced by chat == `CopyRules` for `RATE_LIMITED`, `NO_CONNECTION`, `COULD_NOT_SHARE`. The `AssistantCopy` import is gone from the chat test. |
| C3 | `FusionModelSelector.kt:246-254` `ExpressiveButton(…, style = Filled, colors = ExpressiveButtonColors.tertiary())`; the W7 comment saying the tertiary fill is not a parameter is replaced. **Visible change**: the Start fusion button is tertiary / onTertiary again (W7 chat part 2 had lost it). Import `:38`. |
| C4 | `MarkdownText.kt:1726` `RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)` → `ExpressiveShapes.ContentBlockHeader` (value-identical); the `RoundedCornerShape` import (`:22`) was its only consumer in the file and is gone. Chat now holds no static raw `RoundedCornerShape(` (the remaining ones are press-animated or per-position). |
| C5 | `ChatErrorCopy.COULD_NOT_SHARE` aliases `CopyRules.COULD_NOT_SHARE` (Part 1 above). |
| C6 | `ChatErrorCopy.kt:108` in-stream provider error text pass-through: left as the documented unclassified-snackbar diagnostic (`snackbar()` KDoc); same decision as W7. |

## Deliberately left alone

- `ChatErrorCopy.kt:108` (`snackbar` pass-through of an in-stream provider error body) and `SseEventParser.kt:230` (provider's in-stream error body): the only diagnostic the user gets for a 200-with-error.
- The twelve `"Failed to …"` exception messages in `data/remote/**`: never shown unmapped by chat (`ChatErrorCopy.action` / `notice` classify them); not copy.
- `MessageBubble.kt` animated `RoundedCornerShape(animatedDp)` morphs (`:1093`, `:1384`), `ExpressivePositionShapes`, `MessageInput.kt:566-585` per-position corners: no static token by design (W8 theme audit).
- `ComposerSendButton` raw `Button` (Morph track), composer 28dp, memory chip as a row: W7 rulings, unchanged.
- `SmoothStreamingText`, tap router chain, haptic timing, keyboard spacing.

## Needs another owner

- **theme**: nothing required. Optional: `CopyRules` could carry the other seven `Could not …` action lines (`COULD_NOT_LOAD_MODELS` … `COULD_NOT_RUN_FUSION`) the way it now carries `COULD_NOT_SHARE`, if the orchestrator wants every failure line in one place; they are chat-only today.
- **home-settings**: the two W7 "needs chat" rows (`ChatScreen.kt:283`, `bubbleShapeFor`) were already done by the W7 chat merge; the W7 home-settings report should be read as pre-merge. No action.
- **assistant**: nothing. The attach limit's `room` / `hasRoom` helpers are on the shared `AttachmentLimits`; `AssistantViewModel.kt:1113, 1138` still compute `MAX_PER_MESSAGE - size` inline and may read `room()` (value-identical, optional).
- **orchestrator**: whether the cancelled thinking-only reply should also show the `Regenerate` action in its row (it does now, since the action row's `showRegenerate` is `isLastAssistantMessage && !image && onRegenerate != null`); it is the obvious next step after a Stop and matches the "cancelled, not failed" reading, but it is a visible addition the brief did not name.

## Not verifiable without a device

- The edit row at font scale 1.3 / 360dp: the decision uses the real `TextMeasurer` width of the two labels, but whether the measured `labelLarge` Bold widths cross the 259dp boundary exactly where the type-scale arithmetic says (and so whether "Save" appears at 1.3 and not at 1.15) was not rendered. The JVM test uses a per-character estimate as a stand-in for the measurer.
- The cancelled thinking-only bubble: thinking header + preview line, then the action row with Copy hidden (no content), Branch / Redo / Regenerate / bookmark and no stats pill — reasoned from the composition at `MessageBubble.kt:593-686`, not seen.
- The errored thinking-only bubble after the fix: thinking block, then the class-line notice and `Try again` — the re-derivation on `Error` is pinned at the view model; the rendered swap from action row to retry button was not watched.
- Attach / Take photo at 4/4: the snackbar fires from the pill's click without the picker; the picker's own `PickMultipleVisualMedia(4)` cap on a device that honours it was not exercised.
- Start fusion in tertiary / onTertiary inside the sheet: the pair is in the theme's contrast gate across palettes; the rendered sheet was not seen.
- Table header strip on `ContentBlockHeader`: value-identical (0/0/8/8), pinned by the theme, not rendered.
- No screenshot, emulator or hosted run was made.
