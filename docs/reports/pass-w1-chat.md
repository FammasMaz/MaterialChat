# Pass W1 — Chat interface (branch `pass/w1-chat`, rebased on main 979189b / df77ce6)

Scope: `ui/screens/chat/**`, `ui/components/MarkdownText.kt`, chat unit tests. Nothing under `ui/theme/**`, `assistant/**`, home, settings, `scripts/`, `.github/workflows/` was edited. `SmoothStreamingText.kt`, `BubbleTapRouter.kt`, `TapThroughScroll.kt`, `MathWebViewTouch.kt` are byte-identical to main.

## Commits

| Commit | Part |
|---|---|
| b9d1e3f | Chat surface palette resolver + JVM contrast test |
| 8afa67f | Motion tokens for every spring, no italics, `LoadingIndicator` for loading states |
| 4c0557e | One container shape language: math blocks, sources panel |
| 4d17e79 | Audit tokens (`readableOn` / `readableMutedOn`, `Press.*`, `fast()`/`slow()`), gated infinite indicators |

## What was audited

Every file in `ui/screens/chat/components/` (MessageBubble, MessageActions, MessageInput, ComposerSendButton, ChatTopBar, ChatWorkingIndicator, DateSeparator, ScrollToBottomFab, ModelPickerDropdown, BeautifulModelBadges, RedoModelPickerSheet, ExportBottomSheet, FusionSourcesSection, FusionProgressView, FusionModelSelector, FusionAnimation, WebSearchSourcesCarousel, QuoteReplyPreview, BookmarkBurstAnimation, StreamingIndicator, TokenCounter, ChatEmptyState), `ChatScreen.kt`, and `MarkdownText.kt` (headings, lists, blockquote, HR, inline code, code block, table, math block). Grepped for: `FontStyle.Italic`, `on*.copy(alpha =` on text, raw `spring(` / `tween(`, `CircularProgressIndicator`, `RoundedCornerShape(` radii, `.dp` spacing.

## Part 1 — colour and contrast (b9d1e3f)

- `components/ChatContrast.kt` (new): `ChatSurfacePalette.resolve(scheme, bubbleFamily)` resolves every chat text/icon colour against the fill it actually sits on (bubble lerps, translucent thinking wash, 0.86-alpha memory panel, 0.72-alpha model pill, 0.55-alpha image card, 0.15-alpha inline code) using the WCAG helper from `assistant/ui/AssistantContrast.kt`. Keeps the preferred Material role when it reads, steps to the next role otherwise. ~95 pairs per appearance.
- `test/.../ChatContrastTest.kt` (new, 5 tests): 39 appearances (10 static palettes x light/dark/pitch-black + 3 dynamic-shaped seeds x 3) x 4 bubble families = 156 combinations; asserts every pair >= 4.5:1 body / 3:1 icon. Also records the v2.41.0 baseline: 592 failing pairs, all alpha-faded labels — model label and sibling label `onSurfaceVariant@0.5` (2.46:1 on every light palette), elapsed label `@0.7` (3.86), thinking preview `onSurface@0.6` (3.98–4.39), user quote `onPrimaryContainer@0.65` (3.40), memory panel title `@0.72` (3.61). No solid role pair failed.
- Wired: `MessageBubble.kt:603` (model label), `:775` (user quote), `:1111/1121/1134/1154` (thinking chevron/title/preview/body), `:1208` (sibling label), `:1412/1425` (memory panel titles), `:1652` (image-loading model line); `MessageActions.kt:302` (elapsed label). `rememberChatSurfacePalette()` is `remember(scheme, family)`.

## Part 2 — motion, italics, loading (8afa67f)

- Italics removed (banned): `MarkdownText.kt` blockquote (`:422`→ upright, accent bar carries the quote), inline-math placeholder measure (`:593`), native math block (`:932`), `*emphasis*` → `FontWeight.Medium` (`:2309`), inline-math fallback span (`:2395`), code comments (`:2830`); `MessageBubble.kt` user quote; `FusionSourcesSection.kt:83`; `FusionProgressView.kt:275/296`.
- Hand-rolled springs → `ExpressiveMotion` tokens: `ChatTopBar.kt` title reveal (`Spatial.slow()`), `ChatWorkingIndicator.kt` entrance (`Spatial.container()`), `ScrollToBottomFab.kt` exit (`Spatial.collapse()`), `QuoteReplyPreview.kt` shrink (`Spatial.collapse()`), `FusionSourcesSection.kt` + `WebSearchSourcesCarousel.kt` disclosures (`Disclosure.*`), `FusionModelSelector.kt` content size (`Spatial.contentSize()`), `MessageBubble.kt:402` streaming resize (`Effects.slow()`, still critically damped).
- `M3ExpressiveCircularProgress` → `LoadingIndicator`: `ExportBottomSheet.kt:144`, `BeautifulModelBadges.kt:468/738`.
- Blockquote accent bar: 1.5dp clip → pill (`MarkdownText.kt:414`), matching the user-quote bar in the bubble.

## Part 3 — shape coherency (4c0557e)

- Math blocks (KaTeX and native) use `MarkdownBlockOuterShape` 16dp (`MarkdownText.kt:883/940`), same as code block (`:744`) and table (`:1710`). Previously 8dp, the only 8dp outer container in a bubble.
- Web-search sources panel uses `ExpressiveShapes.DisclosurePanel` (`WebSearchSourcesCarousel.kt:88`), same token as the memory panel beside it.
- Checked and left alone (already coherent): bubble 28/24dp family vs action-group 20dp outer / 6dp inner vs memory chip 24→18→14 morph vs thinking header 16→10 morph vs code/table 16dp outer + 8dp inner vs composer 28→20 squircle; 4dp grid between bubble, action row (4dp), memory chips (8dp), date pills (12dp vertical); 20dp action-row icons, 22dp composer glyphs, 18dp chip/thinking glyphs, 14dp header-chip glyphs.

## Part 4 — audit tokens (4d17e79, after rebase on the theme pass)

- Every `on*.copy(alpha)` on text in my files replaced: `readableMutedOn(fill)` where the fill is a container (`ChatTopBar.kt:191` ephemeral header muted, `ModelPickerDropdown.kt:155` disabled pill, `BeautifulModelBadges.kt:354/652` disabled badges, `FusionSourcesSection.kt:153`), `readableOn(fill)` for body (`FusionSourcesSection.kt:163`), plain `onSurfaceVariant` / `outline` for secondary/tertiary labels (TokenCounter, WebSearchSourcesCarousel domain, FusionProgressView, BeautifulModelBadges hints, MessageBubble image card via the palette). Drag handles use `outlineVariant` (Redo/Export/FusionModelSelector sheets). Disabled rows in FusionModelSelector use `outline`.
- Press physics on one token: `Press.Shape` / `Press.Scale` in MessageActions (`:364/369/374`), ComposerSendButton (`:138`), MessageInput (`:451/607/613/741`), ScrollToBottomFab (`:145`), ModelPickerDropdown (`:91`), BeautifulModelBadges (`:336/633`), MessageBubble memory chip (`:1345/1350`), ChatTopBar status chip (`:535`). Private `M3Springs` object in BeautifulModelBadges deleted.
- `ChatScreen.kt:825` auto-scroll → `Effects.fast()`; `:1117–1130` `animateItem` → `ListItem.FadeIn` / `ListItem.Placement`. `FusionAnimation.kt:66` and dots in `StreamingIndicator.kt:163` → `Spatial.slow()`. `BookmarkBurstAnimation` → `Spatial.playful()`.
- Remaining `tween(` are the four infinite indicators the audit allows (`ChatTopBar.kt:456` pulse, `StreamingIndicator.kt:474` shared `gatedInfiniteFloat`, `FusionAnimation.kt:56`, `ScrollToBottomFab.kt:124`); each is now behind `rememberParticleMotionGate()` and holds a static pose when motion is denied.

## Verified

- `:app:compileGithubDebugKotlin` after each part.
- `:app:testGithubDebugUnitTest`: 602 tests, 0 failures, 0 errors (full module).
- `:app:compileGithubDebugAndroidTestKotlin`: green; no androidTest edited.
- Greps on the final tree: 0 `FontStyle.Italic`, 0 `on*.copy(alpha` on text, 0 raw `spring(` in chat/markdown, `M3Springs` gone, `SmoothStreamingText.kt` / `BubbleTapRouter.kt` / `TapThroughScroll.kt` unchanged vs main.

## Deliberately left alone

- `BubbleTapRouter` / `bubbleTapRouting` / `tapThroughScroll` chain and its modifier order; swipe-to-quote and double-tap bookmark gestures; haptics timing; `shouldMathWebViewClaimHorizontalDrag`.
- `MessageBubble` streaming `animateContentSize` stays critically damped (retargets every frame).
- Headings/list indents in `MarkdownText`: already one scale (headings via typography tiers, 2-space bullets, 12dp table cell padding, 8dp HR padding). No change needed.
- `ChatEmptyState`, `DateSeparator`, `ChatWorkingIndicator` copy and tone.

## Needs another owner

- `ui/theme`: `ExpressiveShapes.DisclosurePanel` (20dp squircle) vs `MarkdownBlockOuterShape` (16dp round) — a shared `CustomShapes.CodeBlock`-style token for in-bubble content blocks would let MarkdownText drop its private constant. Not added (theme owner).
- `ui/components/ExpressiveLoadingIndicator.kt` `M3ExpressiveCircularProgress`: no chat caller remains; other screens still use it.
- `ui/theme/Motion.kt`: `MaterialChatMotion.Durations` is still used for the FAB bob period; a named `Durations.Bob` token would be cleaner.

## Not verifiable without a device

- Visual result of every resolved colour (ratios are proven on the JVM; rendering, tonal elevation overlays from `Surface(tonalElevation)` and dynamic wallpaper schemes are not).
- Press-morph feel on `Press.Shape` (0.6/800) vs the previous 0.6/500 on the action row, send button, chips, FAB.
- Static pose of the four gated indicators under power saver / animator scale 0.
- Math-block 16dp corners against KaTeX WebView content; long-chat table horizontal scroll (logic untouched, but not exercised).
- `LoadingIndicator` sizing at 16dp in the Beautiful badges dropdown rows.
