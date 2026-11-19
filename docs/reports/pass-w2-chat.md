# Pass W2 — Chat interface (branch `pass/w2-chat`, rebased on main 621c05c = W2 theme merge)

Scope: `ui/screens/chat/**`, `ui/components/MarkdownText.kt`, `ui/components/ImageViewerDialog.kt`, chat unit tests. Nothing under `ui/theme/**`, `assistant/**`, home, settings, `scripts/`, `.github/workflows/` was edited. `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` are byte-identical to main (verified with `git diff origin/main --stat` on those paths: empty). No testTag, contentDescription or test was weakened or renamed. No androidTest added. No italics anywhere (`grep FontStyle.Italic` on the ownership set: 0).

## Commits

| Commit | Part |
|---|---|
| 69b5721 | refactor(chat): chat bottom sheets share the settings sheet family |
| a97a7b5 | fix(chat): image viewer on theme scrim/inverse roles, pair gated by ChatContrastTest |
| a7f5a44 | refactor(chat): one list treatment for memory/sources, one table tier set, fusion rows on shape tokens |
| 0045161 | refactor(chat): consume W2 theme tokens — ContentBlock for markdown/fusion blocks, Spatial.bob for the FAB arrow |
| e71944b | test(chat): gate the composer's disabled send state as distinct from backdrop and Ready on every palette |
| (this) | docs(chat): W2 REPORT.md |

## Component inventory (chat screen)

Shape / text / icon / padding / press for every surface, as shipped after this pass. "drift" marks what this pass changed; everything else was audited and left as is.

| Component | Shape token | Text styles | Icon | Padding | Press motion | Status |
|---|---|---|---|---|---|---|
| Masthead (`ChatTopBar.kt`) | `CustomShapes.TopAppBar` 24dp bottom | titleMedium title, labelSmall status | 24dp nav, 14dp chip glyphs | 56dp min row, 16dp gutter | status chip `Press.Shape` (`:535`) | ok |
| Pulse strip (`ChatTopBar.kt:250`) | pill | — | — | 8dp end | gated infinite (`:456`) | ok |
| Date pill (`DateSeparator.kt:56`) | pill 999dp | labelSmall | — | 12dp vertical | none | ok |
| User bubble (`MessageBubble.kt:946`) | `MessageBubbleShapes` 28/8 family | bodyLarge × chat scale | — | 14/10dp | swipe-to-quote only | ok |
| Assistant bubble (`:952`) | 28/8 family, 24dp grouped | bodyLarge × chat scale | — | 14/10dp | double-tap bookmark | ok |
| Errored bubble | same, `errorContainer` lerp 0.3 | bodyMedium error line | — | same | — | ok |
| Streaming bubble | same, `animateContentSize` `Effects.slow()` | `SmoothStreamingText` (untouched) | — | same | — | ok |
| Working indicator (`ChatWorkingIndicator.kt`) | `ContainedLoadingIndicator` 48dp | titleSmall Bold + bodyMedium | — | 12dp gap | `Spatial.container()` entrance | ok |
| Thinking block (`MessageBubble.kt:1094`) | 16→10dp header morph | labelLarge title, bodySmall preview/body | 18dp chevron | 10/8dp | `Disclosure.*` | ok — wash 0.07/0.04 gated (`ChatContrast` thinkingHeaderFills) |
| Memory chips (`:1345`) | 24→18→14 morph | labelLarge | 18dp | 14dp horizontal, 48dp tall | `Press.Scale` (`:1351`) | ok |
| Memory panel list (`:1404–1440`) | `DisclosurePanel` | labelMedium title, bodySmall rows | — | 6dp rows | — | **drift fixed**: "• label" → indexed `primaryContainer` badge + label, same row as the sources card index |
| Action row (`MessageActions.kt`) | 20dp outer / 6dp inner segments | labelSmall stats | 20dp | 4dp above bubble | `Press.Shape`/`Press.Scale` (`:364–374`) | ok |
| Code block (`MarkdownText.kt:750`) | `ExpressiveShapes.ContentBlock` (16dp squircle) outer, 8dp inner | `CodeTypography`, labelSmall chip | 18dp | 12dp | — | **migrated** |
| Table native (`:1716`) | `ContentBlock` outer, 8dp header bottom | bodyMedium, Bold header | — | 12/8dp cells | — | **migrated**; tiers unified (below) |
| Table WebView (`:463–468`) | CSS, same tiers | 700 header | — | 8/12px | — | **drift fixed**: alternate row `surfaceContainerLow` → `surfaceContainer` (matches native) |
| Math block KaTeX/native (`:889/:946`) | `ContentBlock` | — | — | 12dp | — | **migrated** |
| Inline code (`:2399`) | `CustomShapes.InlineCode` 6dp | Monospace 14sp | — | — | — | ok |
| Sources carousel header (`WebSearchSourcesCarousel.kt:95–165`) | `DisclosurePanel` | labelLarge SemiBold, labelSmall muted | 18dp in 999dp tile, 24dp chevron | 14/8dp, 48dp min | `Spatial.default()` chevron | **drift fixed**: `.alpha(0.68/0.72)` muted labels → `palette.sourcesHeaderMuted*` |
| Source card (`:260–335`) | 20dp squircle, pressed 14 | labelSmall index/domain, bodySmall title | — | 12/10dp | `expressivePressScale` | ok |
| Fusion sources (`FusionSourcesSection.kt:126`) | `ContentBlock` | labelSmall badge, bodySmall body | 16dp chevron | 12dp | `Disclosure.*` | **migrated** (was `RoundedCornerShape(16.dp)`) |
| Attachments row (`MessageInput.kt:884`) | pill thumbs, `errorContainer` remove | — | 16dp | 8dp | — | ok |
| Quote-reply preview (`QuoteReplyPreview.kt:83/96`) | 20dp + pill bar | bodySmall | 18dp | 10dp | `Spatial.collapse()` | ok |
| Composer field (`MessageInput.kt:322–337`) | `ComposerFieldShape` squircle 28→20 focus morph | bodyLarge | 22dp glyphs | 48dp min | `Spatial.shapeMorph()` | ok |
| Plus-menu (`:245–270`, `:773`) | `expressiveControlShape` tokens | labelMedium | 22dp | 8dp gap | `Spatial.shapeMorph()` (theme owner kept it) | ok |
| Send button (`ComposerSendButton.kt`) | Cookie4→Cookie9→Square track | — | 22dp | 48dp | `Spatial.shapeMorph()` + `Press.Scale` | ok — disabled state now JVM-gated |
| Reasoning selector (`MessageInput.kt:746`) | `expressiveControlShape` | labelSmall | 20dp | 48dp min | shapeMorph | ok |
| Model picker pill (`ModelPickerDropdown.kt:91/171`) | corner morph | labelLarge | 18dp | 10/6dp | `Press.Shape` | ok |
| Redo sheet (`RedoModelPickerSheet.kt:75–88`) | `SettingsSheetShape`, `surfaceContainerLow` | headlineSmall title + close, bodyMedium | 20dp check | 24dp gutter | `Press.Scale` (`:145`) | **drift fixed** (was `CustomShapes.BottomSheet`, `surfaceContainerHigh`, custom handle, titleLarge, 16dp gutter) |
| Export sheet (`ExportBottomSheet.kt:78–110`) | same | same | 24dp in `ExpressiveShapes.Tile` | 24dp gutter | `Press.Scale` (`:176`) | **drift fixed**; option card `ExpressiveShapes.Card` on `surfaceContainerHigh`, no tonalElevation (`:191`) |
| Fusion sheet (`FusionModelSelector.kt:83–96`) | same | same | 20dp | 24dp gutter | — | **drift fixed**; rows `ExpressiveShapes.MenuSelection` (`:131/:197`), button `CustomShapes.Button` (`:244`) |
| Scroll FAB (`ScrollToBottomFab.kt`) | corner morph + `Press.Shape` (`:143`) | labelSmall badge | 24dp | 16dp | `Spatial.bob()` (`:120`, gated) | **migrated** |
| Empty state (`ChatEmptyState.kt`) | `ExpressiveShapes.Card` / `Tile` | titleMedium, bodyMedium | 28dp | 20dp | — | ok |
| Image viewer (`ImageViewerDialog.kt:57–130`) | full-screen `Dialog` | — | 24dp | 8dp + status bar | — | **drift fixed**: `Color.Black@0.96` → `scheme.scrim@0.96`, `Color.White` → resolved `inverseOnSurface`/`onSurface`, chip fill content@0.14 over scrim |
| Dialogs in chat | none owned (chat uses sheets; `AlertDialog`s live in home/settings) | — | — | — | — | n/a |

## Part 1 — sheets match settings (69b5721)

Reference: `settings/components/AddProviderSheet.kt:118–122` (`SettingsSheetShape`, `surfaceContainerLow`, default `ModalBottomSheet` drag handle, `SheetHeader` headlineSmall + close `IconButton`, 24dp gutter). All three chat sheets diverged on every axis.

- `components/ChatSheetHeader.kt` (new): `ChatSheetGutter = 24.dp`; `ChatSheetHeader(title, onClose)` = headlineSmall + trailing close, same geometry as settings' private `SheetHeader`.
- `RedoModelPickerSheet.kt:75–88`, `ExportBottomSheet.kt:78–110`, `FusionModelSelector.kt:83–96`: `shape = SettingsSheetShape`, `containerColor = surfaceContainerLow`, custom `outline` drag handle removed (default M3 handle), titles sentence-case via `ChatSheetHeader`, gutter 24dp, description `top = 4, bottom = 16`.
- `ExportBottomSheet.kt:176/191/201`: option press on `Press.Scale`; card `ExpressiveShapes.Card` on `surfaceContainerHigh` with no `tonalElevation` (the fill is now the role its text is resolved on); tile `ExpressiveShapes.Tile`.
- `RedoModelPickerSheet.kt:145`: row press on `Press.Scale`.
- `FusionModelSelector.kt:131/197`: rows `ExpressiveShapes.MenuSelection` (were raw 16dp); `:244` start button `CustomShapes.Button` (was raw 24dp).
- Note: the export sheet now closes via the header X as well as the handle; `onClose` is guarded by `!isExporting` like `onDismissRequest` (`ExportBottomSheet.kt:87`).

## Part 2 — image viewer on theme roles (a97a7b5)

- `ImageViewerDialog.kt:73`: backdrop `scheme.scrim.copy(alpha = 0.96f)`; `:118–122`: control colours from `ChatSurfacePalette.imageViewerChip`/`imageViewerScrim` (content resolved against the scrim: `inverseOnSurface` where it reads at 3:1, else `onSurface`, else white/black).
- `ChatContrast.kt:189–190, 272–273, 322–330, 421–422`: `imageViewerScrim`, `imageViewerChip` pairs. The scrim is composited over `scheme.surface` as the worst case (the dialog sits over whatever screen is underneath; at 0.96 the underlay contributes <4%).
- `ChatContrastTest.kt:91`: both names required in `resolvedPairsCoverEverySurfaceTheChatDraws`.

## Part 3 — bubble internals and markdown (a7f5a44)

- Memory panel list (`MessageBubble.kt:1404–1440`): the "• label" rows now use the same row treatment as a source card's index line — a `primaryContainer` 8dp index badge (labelSmall Bold, `onPrimaryContainer`) followed by the label in bodySmall, 6dp vertical rhythm. One list language for memories and sources inside a bubble.
- Sources header (`WebSearchSourcesCarousel.kt:95–165`): the two `.alpha(0.68/0.72)` muted labels (duration, hint line) were the last alpha-faded text in the chat screen. Replaced with `palette.sourcesHeaderMutedCollapsed/Expanded`, resolved against the two header fills (`surfaceContainerHigh`; `secondaryContainer@0.72` over the assistant bubble). Title/content colours also come from the palette (`:114–116`). `ChatContrast.kt:128–133, 223–226, 313–316`.
- Tables: both markdown paths now paint the same three tiers — container `surfaceContainerLow`, header `surfaceContainerHigh`, alternate row `surfaceContainer` (`MarkdownText.kt:467–468` WebView, `:1713–1715` native). The WebView path alternated with `surfaceContainerLow`, i.e. invisibly. Zebra kept (both paths had it); the code block container stays on the same `codeBlockFill` tiers as before.
- `*emphasis*` (`MarkdownText.kt:2319`): `FontWeight.Medium`. Checked against the shipped families: `MaterialChatRoundedFontFamily` ships a Medium instance and `createFontFamily` loads Medium for every Google font, so Medium renders as its own weight, visibly a step above Normal body. No colour emphasis needed. (Theme's new `TypographyWeightsTest` asserts the family instances.)
- Headings/list indents/blockquote/links/inline code: already one scale (W1); re-checked, no change.

## Theme migration (0045161) — rows from `docs/reports/pass-w2-theme.md`

| Row | Before | After |
|---|---|---|
| `ExpressiveShapes.ContentBlock` | `MarkdownText.kt:639` private `RoundedCornerShape(16.dp)`, used at `:744/:883/:940/:1710` | `MarkdownText.kt:647` `private val MarkdownBlockOuterShape: Shape get() = ExpressiveShapes.ContentBlock`; consumers unchanged at `:750` (code), `:889` (KaTeX math), `:946` (native math), `:1716` (table). The private name is kept as a delegating alias so the four sites read the same; the constant is gone. |
| `ContentBlock` for fusion source cards | `FusionSourcesSection.kt:125` `RoundedCornerShape(16.dp)` | `:126` `ExpressiveShapes.ContentBlock` (inside the bubble body → ContentBlock, not DisclosurePanel) |
| `Spatial.bob(Durations.Bob)` | `ScrollToBottomFab.kt:124` `infiniteRepeatable(tween(ExtraLong + Quick), Reverse)` | `:120` `animationSpec = ExpressiveMotion.Spatial.bob()`; `rememberParticleMotionGate()` gate kept; dead imports (`RepeatMode`, `infiniteRepeatable`, `tween`, `MaterialChatMotion`) removed |
| `expressiveControlShape` | `Spatial.shapeMorph()` | untouched, as instructed |

KaTeX WebView / table clipping with the squircle: the WebView is clipped by `Modifier.clip(MarkdownBlockOuterShape)` on its parent Box (`:889`), so the G2 corner is applied by Compose's clip, not by the WebView; the table's `horizontalEdgeFade` (`:1769`) draws inside the same clip. Radius is unchanged (16dp), only the corner curve changed. Cannot verify rendering without a device — see below.

## Part 4 — streaming lifecycle and composer disabled state (e71944b)

Lifecycle audit (code only): the stop button (`ComposerSendButton` via `state.isStreaming`), masthead pulse (`ChatTopBar.kt:250`), scroll FAB (`ChatScreen.kt:941–942`) and arrival bar (`:752`) all read `ChatUiState.Success.isStreaming` = `streamingState is Starting || Streaming` (`ChatUiState.kt:84`). `Completed → Idle`, `Error`, `Cancelled` are all non-streaming (`ChatViewModel.kt:866–902`); `cancelStreaming` (`:923–957`) sets `Idle` and flips every message's `isStreaming` false in the same update. The working indicator, action row and stats pill follow the Room `message.isStreaming` flag (`MessageBubble.kt:484, 584, 619–625`); `generationStats`/`isErrored` in `ChatViewModel.kt:276–287` gate on the same flag. Two sources of truth, but one is the viewmodel's and the other is the persisted message's, and both go false on the completion path before the UI state is emitted. No contradictory state found; no change made.

Composer disabled state: idle fill `surfaceContainerHigh` sits on the content surface `surfaceContainerLow` (`ChatScreen.kt:838`). New `ChatContrastTest.composerSendStatesStayDistinctOnEveryPalette` (`:109–125`): idle vs Ready fill ≥ 3:1 on all 39 appearances × 4 families; idle fill a visible tier (`ThemeContrast.TIER_STEP` 1.1) above the backdrop on the 30 shipped palette appearances. The 9 synthetic dynamic-like schemes are excluded from the tier check only: their hand-built container ramp (`tone(neutral, -0.74 / -0.8)`) sits at 1.07–1.10, flatter than Material You's real ramp, and the theme's own `ThemeContrastTest` tier-step assertion is likewise on shipped palettes. The glyph on the idle fill was already gated (`send idle`, 3:1).

## Verified

- `:app:compileGithubDebugKotlin` after each part and after the rebase.
- `:app:compileGithubDebugAndroidTestKotlin`: green.
- `:app:testGithubDebugUnitTest`: see final message for the count (0 failures). `ChatContrastTest` 6 (was 5).
- `git diff origin/main -- SmoothStreamingText.kt BubbleTapRouter.kt TapThroughScroll.kt MathWebViewTouch.kt`: empty.
- Greps on the ownership set: 0 `FontStyle.Italic`, 0 `.alpha(` on text, 0 `Color.Black`/`Color.White`, 0 `CustomShapes.BottomSheet`, 0 private 16dp block constants.

## Deliberately left alone

- `BubbleTapRouter` / `bubbleTapRouting` / `tapThroughScroll` / `bubbleTapTarget` chain and modifier order; swipe-to-quote; double-tap bookmark; `shouldMathWebViewClaimHorizontalDrag`; `SmoothStreamingText.kt`; haptics timing.
- `expressiveControlShape` on `shapeMorph()` (theme owner's call).
- Thinking wash alphas 0.04/0.07: they are fills, the text on them is resolved and gated; alphas unchanged.
- `DateSeparator.kt:81`, `FusionProgressView.kt:140`, `MarkdownText.kt:394/415`: `outlineVariant`/`primary` at alpha are hairlines and the blockquote bar, not text; left.
- `MessageBubble.kt:712` swipe icon tint at `swipeProgress`: a gesture reveal, not a resting state.
- `FusionProgressView`, `FusionAnimation`, `StreamingIndicator`, `TokenCounter`, `BeautifulModelBadges`: W1 already on tokens; re-audited, no drift.

## Needs another owner

- `settings/components/SettingsExpressive.kt:58` `SettingsSheetShape` is now consumed from chat (`RedoModelPickerSheet`, `ExportBottomSheet`, `FusionModelSelector`). Theme owner: promote it to `ExpressiveShapes.BottomSheet` (top-only 32dp squircle) and let `CustomShapes.BottomSheet` (`RoundedCornerShape` 32dp, now zero chat consumers) delegate to it, so chat does not import from settings.
- `settings/components/AddProviderSheet.kt:401` private `SheetHeader` and `chat/components/ChatSheetHeader.kt` are the same composable; a shared `ui/components/SheetHeader.kt` would remove the copy (settings owner + chat).
- `ui/theme`: a `Durations.StatsPillHold` (3 500 ms, `MessageBubble.kt:627`) token if the audit wants every delay on a token.

## Not verifiable without a device

- `ExpressiveShapes.ContentBlock` G2 corners on the KaTeX WebView (`MarkdownText.kt:889`) and on the table's horizontal-scroll edge fade (`:1769`): the clip is Compose-side and the radius is unchanged, but whether the WebView's own paint shows any seam at the smoothed corner is a render question.
- The three sheets on `surfaceContainerLow` with the default M3 drag handle: tier contrast of the export option card (`surfaceContainerHigh` on `Low`) and the Redo selected row (`secondaryContainer`) is proven on the JVM; the rendered look, sheet entrance and the headline title's optical weight next to the close icon are not.
- Image viewer: `scrim@0.96` over a light screen (a dark grey rather than black); control chip readability over a bright photograph behind the chip — the gate composites over the scrim only.
- Memory panel index badges and the sources header's resolved muted colours: ratios proven, rendering not.
- `Spatial.bob()` on the FAB arrow: spec-identical to the previous tween; motion not observed.
- Composer idle vs Ready distinction under a real dynamic (wallpaper) scheme: the tier check runs on shipped palettes; the 3:1 idle-vs-Ready check runs on the synthetic dynamic seeds too, but a real Material You ramp was not available on the JVM.
- Streaming lifecycle: audited statically; the stop→settle→action-row sequence was not exercised.
