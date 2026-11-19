# Pass W3 — Chat interface (branch `pass/w3-chat`, rebased on main 9f95be2 = W3 theme merge)

Scope: `ui/screens/chat/**`, `ui/components/Markdown*.kt`, `ui/components/MathWebViewTouch.kt`, `ui/components/ImageViewerDialog.kt`, new `ui/components/SheetHeader.kt`, chat unit tests. Nothing under `ui/theme/**`, `assistant/**`, home, settings, `scripts/`, `.github/workflows/` was edited. `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` are byte-identical to origin/main (`git diff origin/main --stat` on those paths: empty). testTag/contentDescription count: 90 on main, 90 here; no test weakened or renamed. No androidTest added. `FontStyle.Italic` in the ownership set: 0. No emulator.

## Commits

| Commit | Part |
|---|---|
| 6a552ce | refactor(chat): shared SheetHeader in ui/components, chat sheets consume it |
| b085d2e | refactor(chat): consume W3 theme tokens — sheets on ExpressiveShapes.BottomSheet, stats pill hold on Durations.StatsPillHold |
| 8889f3d | fix(chat): state walk — fusion panel/cards and load-error screen on resolved palette slots, fusion pulse on bob+motion gate, composer demotes plus/reasoning to one neutral fill while streaming, sentence-case copy with one ellipsis, pill/card/tile/panel shape tokens |
| (this) | docs(chat): W3 REPORT.md |

Gates at 8889f3d: `:app:compileGithubDebugKotlin` rc=0, `:app:compileGithubDebugAndroidTestKotlin` rc=0, `:app:testGithubDebugUnitTest` 652 tests, 0 failures, 0 errors, 0 skipped. `ChatContrastTest` 8 (was 6 at base; +1 slot-coverage names, +1 `streamingComposerDemotesEveryControlButStop`).

## Part 1 — shared sheet header (6a552ce)

- `ui/components/SheetHeader.kt` (new; git records it as a rename of `chat/components/ChatSheetHeader.kt`): `SheetGutter = 24.dp`, `SheetHeader(title, onClose: (() -> Unit)?, modifier)`. Same geometry as the chat header and settings' private `SheetHeader` (headlineSmall `onSurface`, trailing close `IconButton` with `onSurfaceVariant` tint, `contentDescription = "Close"`, `top = 16.dp`). Two additions: `onClose` nullable for handle-only sheets, and the title gets `weight(1f, fill = false)` so a long title wraps instead of pushing the close button off a 360dp sheet.
- `RedoModelPickerSheet.kt:43-44,84,90`, `ExportBottomSheet.kt:39-40,106,112`, `FusionModelSelector.kt:39-40,92,98`: import `SheetGutter`/`SheetHeader` from `ui.components`; `ChatSheetHeader.kt` deleted.

## Part 2 — theme migration rows (b085d2e), from `docs/reports/pass-w3-theme.md`

| Row | Before | After |
|---|---|---|
| `RedoModelPickerSheet.kt:43,75` | `import settings.components.SettingsSheetShape`; `shape = SettingsSheetShape` | import removed; `:76 shape = ExpressiveShapes.BottomSheet` |
| `ExportBottomSheet.kt:39,78` | same | `:79 shape = ExpressiveShapes.BottomSheet` |
| `FusionModelSelector.kt:39,83` | same | `:84 shape = ExpressiveShapes.BottomSheet` |
| `MessageBubble.kt:627` | `delay(3_500)` | `:630 delay(MaterialChatMotion.Durations.StatsPillHold.toLong())` (+ import `:134`) |

Chat no longer imports anything from `settings/**` (grep `screens.settings` in the ownership set: 0). `ExpressiveLoadingIndicator`'s `IndicatorCycle` row belongs to home+settings and was not touched.

## Part 3 — state walk (8889f3d)

Static walk against the code paths each state takes; "verdict" is what the code guarantees, not a rendered check (see the unverified list).

| State | Checked | Verdict | Fix (file:line) |
|---|---|---|---|
| Empty conversation | `ChatEmptyState` card/tile tokens, copy, entrance on `Spatial.container()` gated | ok (W1/W2) | — |
| Model loading | `ModelPickerDropdown` loading row, `RedoModelPickerSheet`/`FusionModelSelector`/`BeautifulModelBadges` loading rows | copy drift: ASCII `...` | `ModelPickerDropdown.kt:265`, `RedoModelPickerSheet.kt:111`, `FusionModelSelector.kt:116`, `BeautifulModelBadges.kt:474,744` → `\u2026` |
| Model fetch error | `ChatUiState.Success` with empty `availableModels` → "No models available" row; conversation-load `Error` → `ErrorContent` | `ErrorContent` drew `error`/`onSurfaceVariant` by role name on the scaffold's `surfaceContainer`, unresolved; "Go Back" title-case | `ChatScreen.kt:569-570,583,592` on `palette.screenError`/`screenErrorMuted`; `:599` "Go back". Slots `ChatContrast.kt:205-207,301-302,471-472` |
| First streaming token | `ChatWorkingIndicator` → bubble; `CHAT_WORKING_TITLE/STATUS` match `AssistantCopy.THINKING_TITLE/STATUS` exactly | ok | — |
| Long streaming (thinking + answer) | `ThinkingSection` header text, wash fills gated, `Disclosure.*` motion | header "Thinking..." ASCII | `MessageBubble.kt:1059,1061` → "Thinking…" (same form as `AssistantCopy.THINKING`). "Thought for 3s"/"Thought" pinned by androidTests, unchanged |
| Cancelled | `cancelStreaming` → `Idle`, every `isStreaming` false in one update (W2 audit) | ok | — |
| Errored send (images restored) | `ChatViewModel` restores `pendingAttachments` on failure; errored bubble `errorContainer` lerp + `assistantError` slot gated | ok; provider fallback name "Unknown Provider" title-case | `ChatViewModel.kt:330,350` → "Unknown provider" |
| Redo | action row primary segment, `RedoModelPickerSheet` on shared header + `BottomSheet` | ok after Parts 1-2 | — |
| Branch / sibling switch | `Spatial.screenSlide()` on `slideOffset`, sibling counter on `metaLabel` slot | ok | — |
| Quote reply | `QuoteReplyPreview` raw `RoundedCornerShape(20.dp)` = `DisclosurePanel` radius with round corners; `Spatial.collapse()` motion | shape drift | `QuoteReplyPreview.kt:84` → `ExpressiveShapes.DisclosurePanel` |
| Attachments 0/1/4, remove | `positionCorners` grouped thumbs, `attachmentRemove` slot, 48dp remove target; limit notice via `AttachmentLimits` | ok; copy proposal in Part 4 | — |
| Bookmark double-tap | `BubbleTapRouter` chain untouched; burst animation | ok (untouchable) | — |
| Search-sources carousel | header tile raw `RoundedCornerShape(999.dp)` | shape drift | `WebSearchSourcesCarousel.kt:129` → `CustomShapes.Pill` |
| Fusion progress | panel raw 24dp on `surfaceContainerHigh`; cards raw 16dp with translucent fills (`tertiaryContainer@0.25`, `surfaceContainerHighest@0.5`) whose text was `onSurfaceVariant`/`tertiary` by role name; status dot pulse was a hand-rolled `while(true) delay(500)` loop ignoring the motion gate; "streaming..."/"Generating..."/"Synthesizing..." ASCII and lower-case | all four fixed | `FusionProgressView.kt:80-81` `ExpressiveShapes.Card` + `palette.fusionPanel`; `:104-120,163` accent/muted/body slots; `:184-199` pulse on `rememberInfiniteTransition` + `Spatial.bob(Durations.Long)` inside `rememberParticleMotionGate().motionAllowed`, solid otherwise; `:206-207` `ExpressiveShapes.ContentBlock` + `card.fill`; `:218-219,257-283` card content/accent slots, "Streaming…"/"Generating…". Slots `ChatContrast.kt:191-203,293-299,359-363,457-469` |
| Fusion badge / selector | `FusionBadge` raw 16dp pill; "Judge Model", "Start Fusion" title-case | drift | `FusionBadge.kt:33` → `CustomShapes.Pill`; `FusionModelSelector.kt:176,258` sentence case |
| Export sheet | shared header, `BottomSheet`, "Exporting..." | ASCII ellipsis; chooser title "Export Conversation" | `ExportBottomSheet.kt:135`; `ChatScreen.kt:282` "Export conversation" |
| Redo-model sheet | as above | ok after Parts 1-2 | — |
| Very long markdown (tables + math + code) | `ContentBlock` outer on all four block kinds (W2), table tiers gated, `horizontalEdgeFade` inside clip | ok | — |
| Font scale 1.3 / 0.85 | sheet header title now weighted so it wraps; `SheetHeader` row `CenterVertically`; composer `defaultMinSize(48.dp)` not fixed height; action row labels `labelSmall` in fixed 20dp segments | sheet title fixed; action-row stats pill at 1.3 is a device question | `SheetHeader.kt:49` |
| 360dp width | sheets 24dp gutter leaves 312dp; export option cards full-width; fusion selector rows `MenuSelection` | ok on paper | — |
| RTL locale | grep for `Icons.Default.Send/ArrowBack/ArrowForward/Reply` non-mirrored: none; all directional glyphs are `AutoMirrored`; `horizontalArrangement` only, no absolute `Alignment.*Start/End` offsets except the attachment remove badge (`offset(x = 12.dp)` on a `TopEnd`-aligned box, which mirrors with the alignment) | ok on paper | — |
| Masthead menu | "Mind Map" | title-case | `ChatTopBar.kt:303` "Mind map" |
| Other shape rows from the theme inventory | `MessageBubble.kt:1502` 28dp clip, `:1630` 28dp, `:1644` 24dp clip; `BeautifulModelBadges.kt:267` 24dp; `DateSeparator.kt:56` 999dp; `MessageInput.kt:599,884` 999dp | drift | `MessageBubble.kt:1503` `ExpressiveShapes.Card`, `:1631` `Card`, `:1645` `Tile`; `BeautifulModelBadges.kt:267`, `DateSeparator.kt:57`, `MessageInput.kt:600,893` `CustomShapes.Pill`. `MessageBubble.kt:924-925,945` are the bubble-style enum's own radii (kept, per the theme inventory) |

Copy rule applied (from `AssistantCopy`): phase lines are a present participle with one `\u2026`; labels and menu items sentence case; composer placeholder "Ask anything" = `AssistantCopy.PROMPT` (was "Ask anything..."; `MessageInput.kt:381`). Grep `\.\.\."` on chat strings after the pass: 0.

## Part 4 — `AttachmentLimits.limitNotice()` (not changed; outside ownership, pinned by `AssistantViewModelAttachmentsTest`)

Current: `"Maximum 4 images allowed"`. Proposed sentence form, matching the assistant notice rules (one or two sentences, sentence case, ends with a period, last sentence names what the user can do):

`"Up to $MAX_PER_MESSAGE images per message. Remove one to add another."`

and for `skippedNotice(dropped)`: `"Only the first $MAX_PER_MESSAGE images were added; $dropped skipped."` Owner: util + assistant (update the test's expected string in the same commit).

## Part 5 — composer control group

States and what each control paints (`MessageInput.kt`, `ComposerSendButton.kt`):

| State | Plus | Field | Reasoning | Send/stop |
|---|---|---|---|---|
| Idle, empty | `tertiaryContainer` CookieSoft | `surfaceContainerHigh` | per-effort accent | idle `surfaceContainerHigh` (disabled) |
| Typing | same | `surfaceContainerHighest` focus morph | same | Ready `primary` |
| Streaming | neutral `surfaceContainerHigh` (`:423-432`) | same | **now** neutral `surfaceContainerHigh` (`:745-780`; was still the effort accent while disabled) | Stop `errorContainer` |
| Attachments open | plus rotated 45° on `Spatial.shapeMorph()`, Flower token; menu pills `CustomShapes.Pill` | — | — | — |
| Keyboard open | `imePadding` from `WindowInsets.ime - navigationBars`; row `Alignment.Bottom` | — | — | — |

Before: while streaming the plus button went neutral but the reasoning selector kept its `primaryContainer`/`secondaryContainer`/`tertiaryContainer` accent, so the row read as two accented controls (reasoning + stop) with the plus demoted — three tiers. Now the plus and reasoning share one neutral fill and the stop button is the only accent. JVM gate `ChatContrastTest.streamingComposerDemotesEveryControlButStop` (`:137-157`): on all 39 appearances × 4 families, `reasoningDisabled.fill == composerAttachStreaming.fill == sendIdle.fill` and `sendStreaming.fill != that fill`; the glyph on the disabled fill is gated at 3:1 with every other pair (`reasoning disabled`, `ChatContrast.kt:303`). Idle vs Ready distinction stays gated by `composerSendStatesStayDistinctOnEveryPalette` (W2).

## Deliberately left alone

- `BubbleTapRouter` / `tapThroughScroll` / `bubbleTapTarget` chain, swipe-to-quote, double-tap bookmark, `shouldMathWebViewClaimHorizontalDrag`, `SmoothStreamingText.kt`, haptic timing.
- `expressiveControlShape` default spring (theme owner's call per W2/W3 theme reports).
- `ChatTopBar.kt:456` header pulse `tween(1400, Linear)` inside `infiniteRepeatable`: gated already; no `Durations` token for it exists and chat cannot add one (listed below).
- `MessageBubble.kt:924-925,945` bubble-family radii: the enum's own geometry, not drift.
- `MarkdownText.kt:648` `MarkdownBlockInnerShape` 8dp and `FusionSourcesSection.kt:65,139`, `FusionProgressView.kt:146,242` 8dp badge/row shapes: inner-header radius of the 28/16/8 ladder; no token at 8dp.
- `AttachmentPreviewItem` default `RoundedCornerShape(16.dp)` (`MessageInput.kt:964`): the real caller always passes `positionCorners`; the default is dead.
- Thinking wash alphas, image viewer scrim alpha, `DateSeparator` hairline, blockquote bars: fills and rules, not text.

## Needs another owner

- **home+settings**: `settings/components/AddProviderSheet.kt:~401-427` private `SheetHeader` → delete it; add `import com.materialchat.ui.components.SheetHeader` and `import com.materialchat.ui.components.SheetGutter`; call sites unchanged (`SheetHeader(title = ..., onClose = ...)`); replace the sheet's 24dp horizontal padding literal with `SheetGutter`. Then `SettingsSheetShape` (`SettingsExpressive.kt:58`) has one consumer and can be deleted or aliased to `ExpressiveShapes.BottomSheet`.
- **util + assistant**: `AttachmentLimits.limitNotice()` / `skippedNotice()` sentence-form copy (Part 4) and the matching expectation in `AssistantViewModelAttachmentsTest`.
- **theme**: a `Durations.HeaderPulse` (1 400 ms) token for `ChatTopBar.kt:456` if every infinite tween is to sit on a token; chat would then switch to `Spatial.decorativeSpin(Durations.HeaderPulse)` (it is a linear restart loop, the same spec family).
- **theme**: `ExpressiveMotion.Spatial.bob()` is typed `InfiniteRepeatableSpec<Float>` and takes a half-period; the fusion card uses it with `Durations.Long` (500 ms) to keep the previous 1 s breath. A dedicated `Durations.Pulse` would make that intent a token instead of a reuse.

## Not verifiable without a device

- `ExpressiveShapes.BottomSheet` top squircle on the three chat sheets: the shape is the theme's `TopSquircleShape`; the sheet's drag handle alignment inside the smoothed corner is a render question.
- `SheetHeader` title wrapping at font scale 1.3 on 360dp (weight + `fill = false`): layout proven only by reading the modifier chain.
- Fusion card pulse on `Spatial.bob(Durations.Long)` via `rememberInfiniteTransition`: 1f → 0.4f → 1f over 1 s is spec-equivalent to the old delay loop, but the old loop snapped through `animateFloatAsState`'s spring while the new one is a reversing tween; the feel differs and was not observed. Motion-gate hold (solid dot) not observed.
- Reasoning selector dropping to neutral while streaming, and the `Effects.color()`/`ColorTransition` spring on that drop: colour values proven, transition not observed.
- Load-error screen `screenError` slot: on the default palettes `error` already reads on `surfaceContainer` so nothing changes; on any palette where the resolver steps to `onSurface` the title loses its red — a deliberate trade, not seen.
- `CustomShapes.Pill` (`RoundedCornerShape(50)`) vs the previous `RoundedCornerShape(999.dp)` on the date pill, sources tile, reasoning option pills, image action pills and `FusionBadge`: identical for any element taller than wide-or-equal; for a very wide, short pill `50%` and `999dp` both saturate at half the height, so no visible change is expected, but it was not rendered.
- `ExpressiveShapes.Card`/`Tile` G2 corners on the generated-image container, image-generation loading card and its inner tile (radius unchanged at 28/24→18dp for the tile: **the tile radius changed from 24dp to the token's 18dp**; a 72dp tile at 18dp reads tighter than before, not seen).
- RTL: audited by grep for non-mirrored directional icons and absolute alignments only.
- Everything in the state table marked "ok on paper".
