# Theme audit: colour, contrast, type, shape, motion (pass W1, v2.41.0 base 574b5c3)

Scope: `app/src/main/java/com/materialchat/ui/theme/**` (edited) plus a read-only grep of `ui/` and `assistant/` (listed at the end for the consumer agents; nothing outside the theme package was edited).

Method: WCAG 2.x relative luminance and contrast ratio (`ThemeContrast.kt`), computed for every `ColorScheme` the app can build: 10 static palettes x light / dark / pitch-black, plus three Material-You-shaped dynamic ramps (blue, green, amber seeds) x the same three modes, 39 appearances. The JVM gate is `app/src/test/java/com/materialchat/ui/theme/ThemeContrastTest.kt`; it asserts every pair in `ColorScheme.standardContrastPairs()` (body text >= 4.5:1, icons / outline >= 3:1) and the pitch-black tier steps (>= 1.1:1 luminance per tier).

## 1. Findings and fixes (static palettes)

| # | Finding | Before | After | Change |
|---|---------|--------|-------|--------|
| 1 | `cosmicLight.secondary` literal `Color(0xAA2F96)` has no alpha byte: alpha = 0x00, the colour is fully transparent. `onSecondary` (white) on it measured 1.00:1 (white on white). Secondary buttons / chips / nav indicator under COSMIC light drew nothing. | 1.00 | 5.69 | `ThemePalettes.kt:282` -> `Color(0xFFAA2F96)` |
| 2 | `OutlineDark` `#9D91AA` on `SurfaceVariantDark` `#51465E` below the 3:1 icon / border threshold. Shared by every dark palette (all ten use the same neutral ramp) and pitch-black. | 2.96 | 3.15 | `Color.kt:100` -> `#A296AF` (same hue, +2.5 L*) |
| 3 | Pitch-black flattened `surfaceContainerLowest`, `Low` and `surfaceContainer` to `#000000`: list cards, bubbles and the input container on those tiers collapsed into the background. | tier steps 1.00 / 1.00 / 1.00 / 1.47 / 1.17 | 1.00 / 1.12 / 1.13 / 1.16 / 1.17 | new `PitchBlack.kt`; `Theme.kt` now calls the public `withPitchBlackSurfaces()` |
| 4 | `headlineSmall` declared `SemiBold` on `MaterialChatExpressiveTitleFontFamily`, which only ships a Bold instance; it rendered Bold anyway. | - | - | `Typography.kt:223` declares `Bold` (what is drawn) |

No other on-colour / container pair failed in any static palette. Every accent (`primary`, `secondary`, `tertiary`, `error`) also reads at >= 4.5:1 as body text on `surface` / `background` in every palette, and at >= 3:1 on every raised tier.

### Worst pair per appearance, after fixes (from the gate's printed summary)

Tier steps are `Lowest/surface`, `Low/Lowest`, `container/Low`, `High/container`, `Highest/High`.

| Appearance | Worst pair | Ratio (min) | Tier steps |
|---|---|---|---|
| VIOLET light | primary/background | 4.70 (4.5) | 1.05 1.11 1.06 1.07 1.07 |
| VIOLET dark | outline/surfaceVariant | 3.15 (3.0) | 1.05 1.12 1.07 1.13 1.17 |
| VIOLET pitch-black | outline/surfaceVariant | 3.15 (3.0) | 1.00 1.12 1.13 1.16 1.17 |
| OCEAN light | outline/surfaceDim | 3.22 (3.0) | 1.05 1.11 1.06 1.07 1.07 |
| OCEAN dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| JADE light | outline/surfaceDim | 3.22 (3.0) | as above |
| JADE dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| SUNSET light | outline/surfaceDim | 3.22 (3.0) | as above |
| SUNSET dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| ROSE light | outline/surfaceDim | 3.22 (3.0) | as above |
| ROSE dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| AMBER light | outline/surfaceDim | 3.22 (3.0) | as above |
| AMBER dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| GRAPHITE light | outline/surfaceDim | 3.22 (3.0) | as above |
| GRAPHITE dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| COSMIC light | outline/surfaceDim | 3.22 (3.0) | as above (secondary was 1.00 before fix 1) |
| COSMIC dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| FLAMINGO light | outline/surfaceDim | 3.22 (3.0) | as above |
| FLAMINGO dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| CITRUS light | primary/background | 4.77 (4.5) | as above |
| CITRUS dark / pitch-black | outline/surfaceVariant | 3.15 (3.0) | as VIOLET |
| dynamic blue light | primary/surface | 6.02 (4.5) | 1.06 1.11 1.05 1.06 1.07 |
| dynamic blue dark | inversePrimary/inverseSurface | 4.92 (4.5) | 1.01 1.02 1.02 1.05 1.08 |
| dynamic blue pitch-black | inversePrimary/inverseSurface | 4.92 (4.5) | 1.00 1.10 1.10 1.11 1.11 |
| dynamic green / amber | same shape as blue | >= 4.90 | pitch-black >= 1.10 each step |

The light palettes share one neutral ramp (`Color.kt`), so their surface pairs are identical; only the accent pairs differ per palette, and the tightest of those are VIOLET `primary/background` 4.70 and CITRUS `primary/background` 4.77 (both over 4.5, left alone).

Note on the dynamic dark ramps: a Material You dark ramp spaces its tiers by only 1.01..1.08 (that is Google's own spacing and is not something the app controls); pitch-black re-derives `Low` and `container` from black toward `High` and lifts each tier just enough for a 1.1:1 step, so under pitch black the dynamic ramp is actually better separated than Google's default.

## 2. Dynamic colour: what cannot be guaranteed, and the runtime guard

Material You builds every role from HCT tone, so these pairs are guaranteed by construction (tone 100 on 40, 10 on 90, 90 on 30, ...): `onPrimary/primary`, `onPrimaryContainer/primaryContainer` (same for secondary / tertiary / error), `onSurface/surface*`, `onSurfaceVariant/surfaceVariant`, `inverseOnSurface/inverseSurface`. The gate asserts those on the three dynamic-shaped ramps as well and they pass.

Not guaranteed under dynamic colour (the generator does not target them):

- `outline` on `surfaceVariant` and on the raised tiers (tone 60 on tone 30 / 90 is ~3:1 only at the limit; some wallpapers land under).
- `primary` / `secondary` / `tertiary` as *body text* on `surface` (tone 40 on 98/99 is usually ~5:1 but a high-chroma yellow/lime seed can dip under 4.5).
- Any blended fill: `lerp(surfaceContainerHigh, primaryContainer, t)`, `primaryContainer.copy(alpha = 0.5f)` over a surface, `secondaryContainer.copy(alpha = 0.72f)`, the heatmap `primaryContainer.copy(alpha = intensity)`. The theme has no role for "on a mix".
- Any `on*` role with alpha applied (`onSurfaceVariant.copy(alpha = 0.6f)`): every light palette already fails 3:1 at 0.6 on `surfaceContainerHigh` (the assistant test recorded 2.86..2.92).

Runtime guard (additive, `ThemeContrast.kt`):

```kotlin
MaterialTheme.colorScheme.readableOn(fill)                       // body text, tries onSurface -> inverseOnSurface -> white/black
MaterialTheme.colorScheme.readableOn(fill, ThemeContrast.ICON, listOf(scheme.primary))
MaterialTheme.colorScheme.readableMutedOn(fill)                  // onSurfaceVariant first
MaterialTheme.colorScheme.readableAccentOn(fill)                 // primary first, 3:1
```

Rule for consumers: when the fill IS a role's container, use that role's `on*` colour and nothing else. Reach for `readableOn` only when the fill is a blend, a translucent container, or anything not a plain role. Never apply alpha to an `on*` text colour; use `onSurfaceVariant` for secondary text, `outline` for tertiary / timestamps, and `readableMutedOn(fill)` when the fill is not a plain surface tier.

## 3. Typography

- One type scale, `buildTypography()` in `Typography.kt`. Display and headline tiers use `MaterialChatExpressiveTitleFontFamily` (gflex variable, Bold instance, width 92, ROND 100, XTRA 520, YOPQ 90, YTLC 505). `displayLarge..headlineMedium` were already `Bold`; `headlineSmall` is now declared `Bold` to match the only instance the family ships. No `ExtraBold` / `Black` in the scale (the `createFontFamily` loader still fetches those weights for user-chosen Google Fonts so a user font can be requested at them, but nothing in the scale asks for them; left as is).
- Title tier: `titleLarge` Bold 22/28, `titleMedium` SemiBold 16/24, `titleSmall` SemiBold 14/20 on the rounded body family. Labels stay Medium so headline weight reads as emphasis.
- No `FontStyle.Italic` anywhere in `ui/theme`. Eleven italic uses remain in consumer files (listed in section 6; chat agent / components owner).
- Line heights: body 16/24, 14/20, 12/16 (1.33..1.5 ratio), labels 14/20, 12/16, 11/16, titles 22/28, 16/24, 14/20. All at or above the 1.3 ratio CJK needs; `includeFontPadding = false` is only set on the display / headline tiers, where it prevents the expressive face from clipping its ascenders.
- `TimestampTypography` 11/14 is the tightest (1.27); it is Latin-only numerals and left alone.

## 4. Shape vocabulary (what each surface class should use)

| Surface class | Token | Value |
|---|---|---|
| Dialog | `ExpressiveShapes.Dialog` | 28dp squircle |
| Modal / bottom sheet | `ExpressiveShapes.Sheet` | 32dp squircle |
| Card, grouped list container | `ExpressiveShapes.Card` | 28dp squircle |
| Disclosure panel inside a bubble (sources, memory refs, thinking) | `ExpressiveShapes.DisclosurePanel` | 20dp squircle |
| Leading tile on a list row (48dp) | `ExpressiveShapes.Tile` / `RowTile.Resting` -> `Pressed` | 18dp -> 24dp squircle |
| Selected row in a menu / sheet | `ExpressiveShapes.MenuSelection` | 16dp squircle |
| Screen content surface under the app bar | `ExpressiveShapes.ScreenContainer` | 28dp top corners |
| Chip / badge / tag | `CustomShapes.Pill` | 50% |
| Standard button | `CustomShapes.Button` -> `ButtonPressed` (or `ShapeMorphing.buttonShape`) | 24dp -> 12dp |
| Expressive control (send, mic, FAB-like) | `expressiveControlShape(token, pressed)` | per `ControlShapeStyle` |
| Assistant pill | fixed geometry | 76dp tall, 38dp radius (not a token; must not change) |
| Message bubble | `MessageBubbleShapes.UserBubble` / `AssistantBubble` / `Grouped.*` | 28 / 8dp tail, 24 / 8dp grouped |
| Input container | `CustomShapes.MessageInputContainer` | 28dp |
| Code block / inline code | `CustomShapes.CodeBlock` / `InlineCode` | 20dp / 6dp |
| Search bar | `CustomShapes.SearchBar` (focus morph `ShapeMorphing.searchBarShape`) | 32dp -> 24dp |
| Swipe action pills | `ExpressiveShapes.SwipeActionPosition.Outer / Inner / Armed` | 24 / 10 / 28dp |
| Material defaults (`MaterialTheme.shapes`) | `MaterialChatShapes` | 8 / 12 / 16 / 20 / 32dp |

Duplicates that exist today and should converge next wave (not renamed here, other files reference them): `CustomShapes.Dialog` (28dp round) vs `ExpressiveShapes.Dialog` (28dp squircle): new code should use the squircle. `CustomShapes.BottomSheet` (32dp top-only) is the right shape for a bottom sheet; `ExpressiveShapes.Sheet` is for a modal card-sheet.

## 5. Motion tokens (Motion.kt), verified against material3 1.5.0-alpha15

`ExpressiveMotionTokens` in the AAR (read from the class initialiser): default spatial 0.8 / 380, default effects 1.0 / 1600, fast spatial 0.6 / 800, fast effects 1.0 / 3800, slow spatial 0.8 / 200, slow effects 1.0 / 800. `MaterialChatTheme` passes `MotionScheme.expressive()` to `MaterialExpressiveTheme`, so Material's own components use these; the app's tokens mirror them:

| Token | Spec | Matches |
|---|---|---|
| `Spatial.default()` | 0.8 / 380 | defaultSpatial |
| `Spatial.fast()` (new) | 0.6 / 800 | fastSpatial |
| `Spatial.slow()` (new) | 0.8 / 200 | slowSpatial |
| `Spatial.pressMorph()` (new) | 0.6 / 800 | fastSpatial; the shared press-morph spec the brief asked for |
| `Effects.color()` / `alpha()` / `elevation()` / `blur()` | 1.0 / 1600 | defaultEffects |
| `Effects.fast()` (new) | 1.0 / 3800 | fastEffects |
| `Effects.slow()` (new) | 1.0 / 800 | slowEffects |
| `Effects.pressTint()` (new) | 1.0 / 1600 | defaultEffects |
| `Press.Shape / Progress / Scale / Tint / Alpha` (new) | typed specs for the press state | - |
| `Disclosure.*`, `ListItem.*` | unchanged | - |

Existing names (`shapeMorph` 0.6/500, `scale` 0.6/600, `container` 0.6/380, `playful` 0.5/400, `screenSlide` 0.75/200, ...) are left as they are: consumer files reference them, and they are deliberate tunings rather than errors. Next wave: `ShapeMorphing.*` and `expressiveControlShape` should move from `Spatial.shapeMorph()` to `Press.Shape` / `Press.Progress` so every pressable settles alike (both are in `ui/theme`; not switched this pass to keep the consumer agents' baseline stable while they work).

## 6. Cross-surface inventory (read-only; consumers own these)

Generated by grep over `app/src/main/java/com/materialchat/{ui,assistant}` minus `ui/theme`. Paths are relative to `app/src/main/java/com/materialchat/`. Replacement rule per list is given once at the top of each.

### 6a. FontStyle.Italic (banned; replace with FontWeight.Medium or onSurfaceVariant colour, no slant)

- `ui/screens/chat/components/FusionSourcesSection.kt:83:                fontStyle = FontStyle.Italic`
- `ui/screens/chat/components/FusionProgressView.kt:275:                            fontStyle = FontStyle.Italic`
- `ui/screens/chat/components/FusionProgressView.kt:296:                        fontStyle = FontStyle.Italic`
- `ui/screens/chat/components/MessageBubble.kt:772:                    fontStyle = FontStyle.Italic,`
- `ui/screens/conversations/ConversationsScreen.kt:598:                    fontStyle = FontStyle.Italic,`
- `ui/components/MarkdownText.kt:422:                                style = style.copy(fontStyle = FontStyle.Italic),`
- `ui/components/MarkdownText.kt:593:        style = style.copy(fontStyle = FontStyle.Italic, color = Color.Unspecified)`
- `ui/components/MarkdownText.kt:932:        fontStyle = FontStyle.Italic,`
- `ui/components/MarkdownText.kt:2308:                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {`
- `ui/components/MarkdownText.kt:2395:        fontStyle = FontStyle.Italic,`
- `ui/components/MarkdownText.kt:2830:        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {`

### 6b. Color.White / Color.Black outside the theme (replace: scrims -> `MaterialTheme.colorScheme.scrim.copy(alpha)`, content on a scrim -> `readableOn(fill)`, pure white fills -> `surfaceContainerLowest`)

- `ui/components/ImageViewerDialog.kt:68:                .background(Color.Black.copy(alpha = 0.96f))`
- `ui/components/ImageViewerDialog.kt:116:                    containerColor = Color.White.copy(alpha = 0.14f),`
- `ui/components/ImageViewerDialog.kt:117:                    contentColor = Color.White`
- `assistant/ui/AssistantOverlay.kt:350:                .background(Color.Black.copy(alpha = 0.42f))`

### 6c. Hard-coded Color(0x...) literals outside the theme (replace with a colorScheme role, or add a named token here)


### 6d. alpha applied to an on* text role (replace: secondary text -> `onSurfaceVariant`, tertiary -> `outline`, on a blended fill -> `readableMutedOn(fill)`; decorative dots -> `readableAccentOn`)

- `ui/navigation/MaterialChatNavBar.kt:233:        targetValue = if (selected) selectedColors.content else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),`
- `ui/screens/settings/ModelAssignmentsScreen.kt:232:                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)`
- `ui/screens/settings/ModelAssignmentsScreen.kt:484:                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)`
- `ui/screens/settings/ModelAssignmentsScreen.kt:576:                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)`
- `ui/screens/settings/SettingsScreen.kt:1435:                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)`
- `ui/screens/settings/SettingsScreen.kt:1490:                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)`
- `ui/screens/settings/SettingsScreen.kt:1548:                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)`
- `ui/screens/settings/components/SystemPromptField.kt:197:                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)`
- `ui/screens/settings/OnDeviceModelsScreen.kt:280:                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)`
- `ui/screens/settings/OnDeviceModelsScreen.kt:440:                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)`
- `ui/screens/chat/components/RedoModelPickerSheet.kt:82:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),`
- `ui/screens/chat/components/MessageActions.kt:299:                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),`
- `ui/screens/chat/components/FusionSourcesSection.kt:82:                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),`
- `ui/screens/chat/components/FusionSourcesSection.kt:169:                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)`
- `ui/screens/chat/components/FusionSourcesSection.kt:179:                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),`
- `ui/screens/chat/components/FusionProgressView.kt:268:                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)`
- `ui/screens/chat/components/FusionProgressView.kt:287:                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:333:                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),`
- `ui/screens/chat/components/ExportBottomSheet.kt:82:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),`
- `ui/screens/chat/components/ModelPickerDropdown.kt:153:            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)`
- `ui/screens/chat/components/TokenCounter.kt:84:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:257:                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:372:            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:532:                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:674:            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:807:                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:824:                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)`
- `ui/screens/chat/components/BeautifulModelBadges.kt:869:                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),`
- `ui/screens/chat/components/FusionModelSelector.kt:88:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),`
- `ui/screens/chat/components/FusionModelSelector.kt:173:                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)`
- `ui/screens/chat/components/MessageBubble.kt:602:                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),`
- `ui/screens/chat/components/MessageBubble.kt:1202:            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)`
- `ui/screens/chat/components/MessageBubble.kt:1402:            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)`
- `ui/screens/chat/components/MessageBubble.kt:1415:                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)`
- `ui/screens/chat/components/MessageBubble.kt:1640:                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),`
- `ui/screens/workflows/components/StepEditor.kt:134:                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),`
- `ui/screens/canvas/components/CanvasRefinementInput.kt:77:            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)`
- `ui/screens/explore/ExploreScreen.kt:334:                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),`
- `ui/screens/bookmarks/components/BookmarkCard.kt:137:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),`
- `ui/screens/bookmarks/components/BookmarkCard.kt:199:                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)`
- `ui/screens/bookmarks/components/BookmarkSearchBar.kt:48:                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),`
- `ui/screens/bookmarks/components/BookmarkSearchBar.kt:58:                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),`
- `ui/screens/conversations/components/ConversationItem.kt:113:    val pressedOverlay = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)`
- `ui/screens/conversations/components/BranchConversationItem.kt:98:            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)`
- `ui/screens/arena/components/ArenaModelPicker.kt:91:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),`
- `ui/screens/arena/components/ArenaModelPicker.kt:116:                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),`
- `ui/screens/arena/components/ArenaBattleView.kt:116:            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),`
- `ui/screens/arena/components/ArenaLeaderboard.kt:100:                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),`
- `ui/screens/arena/components/ArenaLeaderboard.kt:115:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)`
- `ui/components/UpdateBanner.kt:158:                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)`
- `ui/components/UpdateBanner.kt:240:                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)`
- `ui/components/UpdateBanner.kt:261:            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),`
- `ui/components/UpdateBanner.kt:306:                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),`
- `assistant/ui/components/AssistantInputBar.kt:209:                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)`
- `assistant/ui/components/VoiceWaveform.kt:129:    barColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),`

### 6e. Hard-coded spring( / tween( outside the theme (replace: press -> `ExpressiveMotion.Press.*`, disclosure -> `Disclosure.*`, list -> `ListItem.*`, fades -> `Effects.alpha()`/`fast()`/`slow()`, position/size -> `Spatial.default()`/`fast()`/`slow()`; infinite tweens for streaming indicators are the only tween that stays, gated by ParticleMotionGate)

- `ui/screens/insights/components/ModelComparisonCard.kt:53:            animationSpec = spring(`
- `ui/screens/insights/components/ActivityHeatmap.kt:55:            animationSpec = spring(`
- `ui/screens/insights/components/ModelUsageChart.kt:72:            animationSpec = spring(`
- `ui/screens/insights/components/StatCard.kt:42:        animationSpec = spring(`
- `ui/screens/insights/components/ResponseTimeChart.kt:53:            animationSpec = spring(`
- `ui/screens/settings/components/SystemPromptField.kt:97:                animationSpec = spring(`
- `ui/screens/settings/components/SystemPromptField.kt:155:                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/settings/components/SystemPromptField.kt:157:                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),`
- `ui/screens/settings/components/SystemPromptField.kt:161:                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/settings/components/SystemPromptField.kt:163:                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),`
- `ui/screens/settings/components/SystemPromptField.kt:182:                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/settings/components/SystemPromptField.kt:184:                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)`
- `ui/screens/settings/components/SystemPromptField.kt:187:                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/settings/components/SystemPromptField.kt:189:                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/settings/components/SystemPromptField.kt:209:                    animationSpec = spring(`
- `ui/screens/settings/components/SystemPromptField.kt:311:        animationSpec = spring(`
- `ui/screens/settings/OnDeviceModelsScreen.kt:148:                        animationSpec = spring(`
- `ui/screens/chat/components/FusionAnimation.kt:53:                        animation = tween(durationMillis = 3000, easing = LinearEasing),`
- `ui/screens/chat/components/FusionAnimation.kt:62:                    animationSpec = spring(`
- `ui/screens/chat/components/ChatTopBar.kt:153:                animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)`
- `ui/screens/chat/components/ChatTopBar.kt:449:            animation = tween(durationMillis = 1400, easing = LinearEasing),`
- `ui/screens/chat/components/FusionSourcesSection.kt:91:                animationSpec = spring(`
- `ui/screens/chat/components/FusionSourcesSection.kt:96:                animationSpec = spring(`
- `ui/screens/chat/components/FusionSourcesSection.kt:102:                animationSpec = spring(`
- `ui/screens/chat/components/FusionSourcesSection.kt:107:                animationSpec = spring(`
- `ui/screens/chat/components/BookmarkBurstAnimation.kt:64:                    animationSpec = spring(`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:84:        animationSpec = spring(`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:96:                animationSpec = spring(`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:185:                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:187:                animationSpec = spring(`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:193:                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)`
- `ui/screens/chat/components/WebSearchSourcesCarousel.kt:195:                animationSpec = spring(`
- `ui/screens/chat/components/QuoteReplyPreview.kt:74:            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/chat/components/StreamingIndicator.kt:88:            animation = tween(durationMillis = 3000, easing = LinearEasing),`
- `ui/screens/chat/components/StreamingIndicator.kt:99:            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),`
- `ui/screens/chat/components/StreamingIndicator.kt:176:                        animationSpec = spring(`
- `ui/screens/chat/components/StreamingIndicator.kt:183:                        animationSpec = spring(`
- `ui/screens/chat/components/StreamingIndicator.kt:332:            animation = tween(durationMillis = 3000, easing = LinearEasing),`
- `ui/screens/chat/components/StreamingIndicator.kt:343:            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),`
- `ui/screens/chat/components/StreamingIndicator.kt:353:            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),`
- `ui/screens/chat/components/StreamingIndicator.kt:363:            animation = tween(durationMillis = 2400, easing = EaseInOutCubic),`
- `ui/screens/chat/components/StreamingIndicator.kt:482:            animation = tween(durationMillis = 1400, easing = EaseInOutCubic),`
- `ui/screens/chat/components/ChatWorkingIndicator.kt:56:        entrance.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f))`
- `ui/screens/chat/components/ScrollToBottomFab.kt:103:            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),`
- `ui/screens/chat/components/ScrollToBottomFab.kt:106:            animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f),`
- `ui/screens/chat/components/ScrollToBottomFab.kt:119:                animation = tween(800),`
- `ui/screens/chat/components/BeautifulModelBadges.kt:405:                    animationSpec = spring(`
- `ui/screens/chat/components/BeautifulModelBadges.kt:707:                    animationSpec = spring(`
- `ui/screens/chat/components/FusionModelSelector.kt:105:                        animationSpec = spring(`
- `ui/screens/chat/components/MessageBubble.kt:400:                            animationSpec = spring(`
- `ui/screens/chat/ChatScreen.kt:823:                        animationSpec = spring(`
- `ui/screens/chat/ChatScreen.kt:1117:                        fadeInSpec = spring(`
- `ui/screens/chat/ChatScreen.kt:1126:                            spring(`
- `ui/screens/workflows/WorkflowsScreen.kt:244:                    fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowsScreen.kt:248:                    fadeOutSpec = spring(`
- `ui/screens/workflows/components/StepProgressIndicator.kt:71:            animation = tween(800),`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:348:                    fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:352:                    fadeOutSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:365:                        fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:369:                        fadeOutSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:385:                        fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:389:                        fadeOutSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:405:                        fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:409:                        fadeOutSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:433:                        fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:437:                        fadeOutSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:453:                    fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:457:                    fadeOutSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:476:                    fadeInSpec = spring(`
- `ui/screens/workflows/WorkflowExecutionScreen.kt:480:                    fadeOutSpec = spring(`
- `ui/screens/explore/ExploreScreen.kt:232:            animation = tween(durationMillis = 48_000, easing = LinearEasing),`
- `ui/screens/explore/ExploreScreen.kt:369:        offset.animateTo(0f, animationSpec = spring(stiffness = 420f, dampingRatio = 0.8f))`
- `ui/screens/search/components/SearchResultItem.kt:75:        animationSpec = spring(`
- `ui/screens/search/components/SearchResultItem.kt:88:        animationSpec = spring(),`
- `ui/screens/search/components/SearchResultItem.kt:194:                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)`
- `ui/screens/search/components/SearchResultItem.kt:196:                animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/search/components/SearchResultItem.kt:199:                animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/search/components/SearchResultItem.kt:201:                animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/search/components/SearchBar.kt:96:                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)`
- `ui/screens/search/components/SearchBar.kt:99:                    animationSpec = spring(stiffness = Spring.StiffnessMedium)`
- `ui/screens/personas/PersonaStudioScreen.kt:175:                                fadeInSpec = spring(`
- `ui/screens/personas/PersonaStudioScreen.kt:179:                                fadeOutSpec = spring(`
- `ui/screens/bookmarks/BookmarksScreen.kt:192:                animationSpec = spring(`
- `ui/screens/bookmarks/BookmarksScreen.kt:246:                            fadeInSpec = spring(`
- `ui/screens/bookmarks/BookmarksScreen.kt:250:                            fadeOutSpec = spring(`
- `ui/screens/bookmarks/components/BookmarkFilterBar.kt:77:                    animationSpec = spring(`
- `ui/screens/bookmarks/components/BookmarkFilterBar.kt:134:                        animationSpec = spring(`
- `ui/screens/bookmarks/components/AddBookmarkSheet.kt:115:                        animationSpec = spring(`
- `ui/screens/conversations/ConversationsScreen.kt:515:                        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),`
- `ui/screens/conversations/ConversationsScreen.kt:529:                        anim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = stiffness))`
- `ui/screens/conversations/HomeEmptyState.kt:168:            animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing)),`
- `ui/screens/arena/ArenaScreen.kt:232:                animationSpec = spring(`
- `ui/screens/arena/ArenaScreen.kt:267:                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)`
- `ui/screens/arena/ArenaScreen.kt:269:                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),`
- `ui/screens/arena/ArenaScreen.kt:286:                animationSpec = spring(dampingRatio = 1.0f, stiffness = 300f)`
- `ui/screens/arena/ArenaScreen.kt:288:                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),`
- `ui/screens/arena/ArenaScreen.kt:304:                        animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)`
- `ui/screens/arena/ArenaScreen.kt:306:                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),`
- `ui/screens/arena/components/ArenaBattleView.kt:161:                        (fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 3 })`
- `ui/screens/arena/components/ArenaBattleView.kt:162:                            .togetherWith(fadeOut(tween(200)))`
- `ui/screens/arena/components/ArenaLeaderboard.kt:135:                                fadeInSpec = spring(`
- `ui/screens/arena/components/ArenaLeaderboard.kt:139:                                fadeOutSpec = spring(`
- `ui/screens/arena/components/ArenaLeaderboard.kt:146:                                animationSpec = spring(`
- `ui/screens/mindmap/components/MindMapCanvas.kt:130:            animation = tween(durationMillis = 1200, easing = LinearEasing),`
- `ui/screens/memories/MemoriesScreen.kt:177:                animationSpec = spring(`
- `ui/screens/memories/MemoriesScreen.kt:207:                            fadeInSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f),`
- `ui/screens/memories/MemoriesScreen.kt:208:                            fadeOutSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f),`
- `ui/screens/memories/MemoriesScreen.kt:209:                            placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f)`
- `ui/components/UpdateBanner.kt:67:            animationSpec = spring(`
- `ui/components/UpdateBanner.kt:74:            animationSpec = spring(`
- `ui/components/ExpressiveLoadingIndicator.kt:102:            animation = tween(durationMillis = 4000, easing = LinearEasing),`
- `ui/components/ExpressiveLoadingIndicator.kt:117:            animation = tween(durationMillis = 3000, easing = EaseInOutCubic),`
- `ui/components/ExpressiveLoadingIndicator.kt:289:            animation = tween(durationMillis = 1332, easing = LinearEasing),`
- `ui/components/ExpressiveLoadingIndicator.kt:299:            animation = tween(durationMillis = 666, easing = EaseInOutCubic),`
- `ui/components/ExpressiveLoadingIndicator.kt:349:                        animationSpec = spring(`
- `ui/components/ExpressiveLoadingIndicator.kt:356:                        animationSpec = spring(`
- `ui/components/ExpressiveLoadingIndicator.kt:399:            animation = tween(durationMillis = 1500, easing = LinearEasing),`
- `ui/components/ExpressiveLoadingIndicator.kt:409:            animation = tween(durationMillis = 750, easing = EaseInOutCubic),`
- `ui/components/ExpressiveLoadingIndicator.kt:455:            animationSpec = spring(`
- `ui/components/ExpressiveLoadingIndicator.kt:625:        animationSpec = spring(`
- `ui/components/ExpressiveLoadingIndicator.kt:634:        animationSpec = spring(`
- `ui/components/ExpressiveFastScrollBar.kt:113:        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),`
- `ui/components/ExpressiveFastScrollBar.kt:118:        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),`
- `ui/components/ExpressiveFastScrollBar.kt:125:        animationSpec = spring(`
- `ui/components/ExpressiveFastScrollBar.kt:249:                                animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing)`
- `ui/components/ExpressiveFastScrollBar.kt:282:            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),`
- `ui/components/ExpressiveFastScrollBar.kt:287:            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),`
- `ui/components/ExpressiveFastScrollBar.kt:292:            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),`
- `assistant/ui/AssistantExpressiveStyle.kt:32:    fun <T> stateSwitch(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 520f)`
- `assistant/ui/AssistantExpressiveStyle.kt:35:    fun <T> surfaceReveal(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = 300f)`
- `assistant/ui/AssistantExpressiveStyle.kt:38:    fun <T> container(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 380f)`
- `assistant/ui/AssistantExpressiveStyle.kt:41:    fun <T> playful(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 400f)`
- `assistant/ui/AssistantExpressiveStyle.kt:48:    fun <T> exit(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)`
- `assistant/ui/AssistantExpressiveStyle.kt:51:    fun <T> morph(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 1400f)`
- `assistant/ui/AssistantExpressiveStyle.kt:54:    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.45f, stiffness = 650f)`
- `assistant/ui/AssistantExpressiveStyle.kt:57:    fun <T> contentSize(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 340f)`
- `assistant/ui/AssistantExpressiveStyle.kt:60:    fun <T> effect(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f)`
- `assistant/ui/components/VoiceWaveform.kt:142:            animation = tween(durationMillis = 2000, easing = LinearEasing),`
- `assistant/ui/components/VoiceWaveform.kt:203:                        animationSpec = spring(`
- `assistant/ui/components/VoiceWaveform.kt:210:                        animationSpec = spring(`
