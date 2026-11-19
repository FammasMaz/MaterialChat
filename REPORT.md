# W4 theme pass report (branch `pass/w4-theme`, base `bb91963` v2.41.3)

Ownership in this pass: `app/src/main/java/com/materialchat/ui/theme/**` and `app/src/test/java/com/materialchat/ui/theme/**` only. No file outside that set was edited. No emulator, AVD or device was used. Nothing was pushed.

## Commits

| Hash | Part | Summary |
|---|---|---|
| `5c0adfb` | 1 | `Durations.HeaderPulse = 1_400` and `Durations.Pulse = 500` motion tokens with test pins |
| `327c4a9` | 4 | `ThemeContrastTest` asserts every `ChatSurfacePalette` and `HomeSettingsPalette` pair across all 39 appearances |
| `cd6ad26` | 2 | `dataReveal()` (0.8 / 50) ruled the one insights data-reveal spec; derivation from the M3E spatial tokens pinned |
| `7ae75f6` | 3 | `ExpressiveShapes.Badge` (8dp, equal to `extraSmall`) for the nine raw `RoundedCornerShape(8.dp)` sites found by the re-inventory |
| (this commit) | 3, 5, report | Shape drift table per owner, part 5 documentation, this file |

## What changed (file:line)

### Part 1 — pulse durations
- `ui/theme/Motion.kt:593-606`: `MaterialChatMotion.Durations.HeaderPulse = 1_400` (period of the `ChatTopBar` pulse strip, drawn with `Spatial.decorativeSpin`, gated like every ornament loop) and `Durations.Pulse = 500` (half period of a breathing pulse, drawn with `Spatial.bob`; value-equal to `Durations.Long` but a token of its own so a breath is named as a breath).
- `ui/theme/MotionTokensTest.kt:49-64`: pins `HeaderPulse == 1400`, `decorativeSpin(HeaderPulse)` resolves, `Pulse == 500`, `Pulse == Long`, `bob(Pulse)` resolves, full breath cycle `2 * Pulse == 1000`.

### Part 2 — insights data-reveal decision
- Decision: **`ExpressiveMotion.Spatial.dataReveal()` = spring(0.8, 50)** is the one insights reveal spec. Justification (`Motion.kt:230-243` doc): damping is the M3E default spatial damping (`ExpressiveMotionTokens.defaultSpatialSpec`, 0.8, same as `default()`) so a count-up never overshoots its final value by a visible digit; stiffness is a quarter of the M3E slow spatial token (`slow()` 200 -> 50) so a figure is seen arriving rather than snapping. The per-chart variants (heatmap 0.9/40, comparison and response-time charts 0.7/60) are retired.
- `MotionTokensTest.kt:86-108`: pins 0.8/50, `dampingRatio == default().dampingRatio`, `stiffness == slow().stiffness / 4`, and rejects 0.9/0.7 and 40/60 so the retired values cannot creep back into the token.
- Consumer state at `bb91963` (grep, all five already on the token, no consumer change needed): `insights/components/StatCard.kt:43`, `ModelUsageChart.kt:72`, `ActivityHeatmap.kt:56`, `ModelComparisonCard.kt:53`, `ResponseTimeChart.kt:53` all call `ExpressiveMotion.Spatial.dataReveal()`.

### Part 3 — shape drift re-inventory and the `Badge` token
- `ui/theme/Shapes.kt:132-140`: `ExpressiveShapes.Badge = RoundedCornerShape(8.dp)`. Plain circular corner (a badge is too small for G2 smoothing to read), identical to `MaterialChatShapes.extraSmall` (`Shapes.kt:199`) so the two never diverge.
- `ui/theme/ShapeTokensTest.kt:73-79`: pins `Badge == RoundedCornerShape(8.dp)`, `Badge == MaterialChatShapes.extraSmall`, `Badge != ContentBlock`.
- Inventory method: `grep -rn "RoundedCornerShape(" app/src/main/java/com/materialchat --include=*.kt | grep -v ui/theme/` at `bb91963`, then filtered for 16-32dp and for 8dp.

### Part 4 — contrast assertions
- `ui/theme/ThemeContrastTest.kt:305-335` `chatSurfacePaletteReadsOnEveryFillInEveryThemeAndBubbleFamily`: resolves `ChatSurfacePalette.resolve(scheme, family)` for every appearance in the 39-entry matrix and every bubble family, asserts body pairs >= 4.5:1 and large/icon pairs >= 3:1.
- `ThemeContrastTest.kt:337-368` `homeSettingsPaletteReadsOnEveryFillInEveryTheme`: same for `HomeSettingsPalette.resolve` / `.drawn`.
- `chat/components/ChatContrast.kt` and `ui/components/HomeSettingsContrast.kt` were read only; not edited.

### Part 5 — `ExpressiveControlShapes` spring (documented only, no code change)
- `ui/theme/ExpressiveControlShapes.kt:61,73,83` keep `ExpressiveMotion.Spatial.shapeMorph()` for the CLASSIC / BALANCED corner radius and the EXPRESSIVE morph progress. Device check of `shapeMorph()` vs `Press.Shape` (`pressMorph()`) is still pending; switching blind would change the feel of every pressable control on every surface.
- Spring values at `bb91963` (`Motion.kt`, audit finding 3 asked for these):

| Spec | damping | stiffness | Line |
|---|---|---|---|
| `Spatial.default()` | 0.8 | 380 | `Motion.kt:45` |
| `Spatial.shapeMorph()` | 0.6 | 500 | `:54` |
| `Spatial.expand()` | 0.75 | 420 | `:142` |
| `Spatial.collapse()` | 1.0 | 420 | `:151` |
| `Spatial.pressMorph()` (`Press.Shape`) | 0.6 | 800 | `:206` |
| `Spatial.fast()` | 0.6 | 800 | `:215` (value-identical to `pressMorph`) |
| `Spatial.slow()` | 0.8 | 200 | `:226` |
| `Spatial.dataReveal()` | 0.8 | 50 | `:240` |

`AssistantMotion.morph()` -> `Spatial.fast()` and `ExpressiveMotion.Press.Shape` -> `pressMorph()` are therefore already value-identical; only `expressiveControlShape` (`shapeMorph`, 0.6/500) differs, and that is the part 5 hold.

## Shape drift table (re-inventory at `bb91963`)

W3 counted 43 raw `RoundedCornerShape(16-32dp)` lines outside `ui/theme`. At `bb91963` the same grep returns **17** lines. Of these, 10 are bubble-style enum tables that are deliberate and stay (`SettingsScreen.kt:1971-1984` preview shapes, `MessageBubble.kt:925-926`), and `MessageInput.kt:973` is a default parameter. Seven real sites remain. Separately, nine raw `RoundedCornerShape(8.dp)` sites justify the new `Badge` token.

### chat

| Site | Today | Token | Note |
|---|---|---|---|
| `chat/components/FusionSourcesSection.kt:65` | `.clip(RoundedCornerShape(8.dp))` favicon tile | `ExpressiveShapes.Badge` | |
| `chat/components/FusionSourcesSection.kt:139` | `shape = RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` | |
| `chat/components/FusionProgressView.kt:146` | `shape = RoundedCornerShape(8.dp)` model name chip | `ExpressiveShapes.Badge` | |
| `chat/components/FusionProgressView.kt:232` | `shape = RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` | |
| `chat/components/WebSearchSourcesCarousel.kt:295` | `RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)` | none yet | top-only 20dp; the only top-only token is `TopSquircleShape(32)` (`BottomSheet`). Keep, or theme adds `TopSquircleShape(20.dp)` in W5 if a second consumer appears |
| `chat/components/MessageInput.kt:973` | default param `RoundedCornerShape(16.dp)` | `ExpressiveShapes.ContentBlock` (16dp squircle) | chat decides; pressed state at `:611` is a raw 12dp, candidate `CustomShapes.ButtonPressed` |
| `chat/components/MessageBubble.kt:925-926, 946-968` | bubble style enum (COMPACT 20 / GEOMETRIC 16 / ROUNDED 34, per-corner) | keep | user-selectable bubble geometry, deliberate per W3 |
| `chat/components/ChatTopBar.kt:456` | `tween(1400, LinearEasing)` | `Spatial.decorativeSpin(Durations.HeaderPulse)` | motion, part 1 |
| `chat/components/FusionProgressView.kt:196` | `Spatial.bob(Durations.Long)` | `Spatial.bob(Durations.Pulse)` | motion, part 1; same value, named token |

### home-settings

| Site | Today | Token | Note |
|---|---|---|---|
| `settings/SettingsScreen.kt:1686` | `RoundedCornerShape(if (selected) 28.dp else 20.dp)` | `ExpressiveShapes.Card` (selected) / `ExpressiveShapes.DisclosurePanel` (resting) | selection cards; both tokens are squircles, so the swap also moves them off circular corners. If the radius is animated between the two states the consumer needs `animateDpAsState` + `SquircleShape(radius)` instead of two fixed tokens |
| `settings/SettingsScreen.kt:1787` | same | same | |
| `settings/SettingsScreen.kt:1892` | same | same | |
| `settings/SettingsScreen.kt:1971-1984` | bubble style preview enum | keep | mirrors the chat enum |
| `workflows/components/StepResultCard.kt:78` | `RoundedCornerShape(8.dp)` step badge | `ExpressiveShapes.Badge` | |
| `workflows/WorkflowExecutionScreen.kt:492` | `RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` | |
| `workflows/WorkflowExecutionScreen.kt:544` | `RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` | |
| `personas/components/PersonaCard.kt:196` | `RoundedCornerShape(8.dp)` tag | `ExpressiveShapes.Badge` | |
| `insights/components/{StatCard:43, ModelUsageChart:72, ActivityHeatmap:56, ModelComparisonCard:53, ResponseTimeChart:53}` | `Spatial.dataReveal()` | already on token | part 2; no change |

### assistant

| Site | Today | Token | Note |
|---|---|---|---|
| `assistant/ui/AssistantExpressiveStyle.kt:74` | `Answer = RoundedCornerShape(36/36/28/28)` | none yet | per-corner squircle token does not exist (audit F2); see Needs another owner |
| `assistant/ui/AssistantExpressiveStyle.kt:77` | `Notice = RoundedCornerShape(24/24/18/18)` | none yet | same |
| `assistant/ui/AssistantExpressiveStyle.kt:86` | `Thinking = RoundedCornerShape(20/20/8/20)` | none yet | same |
| `assistant/ui/AssistantExpressiveStyle.kt:71` | `Pill = RoundedCornerShape(38.dp)` | keep | fixed geometry contract |

### ui/components (not owned in this pass)

| Site | Today | Token |
|---|---|---|
| `ui/components/MarkdownText.kt:648` | `private val MarkdownBlockInnerShape = RoundedCornerShape(8.dp)` | `ExpressiveShapes.Badge` |
| `ui/components/MarkdownText.kt:1719` | `RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)` | keep (bottom-only) |

### Candidate 12dp Chip token
The brief allowed a 12dp Chip/Badge token if used >= 3 times. Raw 12dp outside the theme: `MessageInput.kt:611` only (one site); `AssistantShapes.ConnectedPressed` already wraps its own 12dp. Below the threshold, so no 12dp token was added. The 8dp class (nine sites) got `Badge` instead.

## Verified gates (at `7ae75f6`, nothing in code changed since)
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 -q :app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` -> exit 0.
- `./gradlew --max-workers=2 -q :app:testGithubDebugUnitTest` -> exit 0; `app/build/test-results/testGithubDebugUnitTest/*.xml`: **664 tests, 0 failures, 0 errors, 0 skipped**.
- No test, testTag, contentDescription, script or workflow was weakened, deleted or renamed. No `FontStyle.Italic` added.

## Deliberately left alone
- `ExpressiveControlShapes.kt:61,73,83` spring (part 5 hold).
- `CustomShapes.Snackbar` (`Shapes.kt:387`), `CustomShapes.Fab/ExtendedFab/FabPressed` (`:296-307`): audit F1/F14 proposals, not in this brief.
- `ExpressiveShapes.Sheet` (32dp all-corner squircle): still no consumer, kept as the floating-sheet token.
- The audit's theme per-owner list (`docs/audit-cross-surface-w4.md`, 10 items) was used as input only; none of it was implemented beyond what the brief asked.

## Needs another owner
- **chat**: `ChatTopBar.kt:456` -> `decorativeSpin(Durations.HeaderPulse)`; `FusionProgressView.kt:196` -> `bob(Durations.Pulse)`; `FusionSourcesSection.kt:65,139` and `FusionProgressView.kt:146,232` -> `ExpressiveShapes.Badge`; decide `MessageInput.kt:973/611`; decide `WebSearchSourcesCarousel.kt:295`.
- **home-settings**: `SettingsScreen.kt:1686,1787,1892` -> `Card`/`DisclosurePanel`; `StepResultCard.kt:78`, `WorkflowExecutionScreen.kt:492,544`, `PersonaCard.kt:196` -> `ExpressiveShapes.Badge`.
- **assistant**: `AssistantShapes.Answer/Notice/Thinking` stay on `RoundedCornerShape` until theme ships a per-corner `SquircleShape` (next theme pass, audit F2).
- **ui/components (theme, next pass)**: `MarkdownText.kt:648` -> `ExpressiveShapes.Badge`; per-corner `SquircleShape`; `ExpressiveShapes.Notice` + `ExpressiveSnackbar` (F1); `ExpressiveSizes`/`ExpressiveSpacing` (F4/F13); `ContrastSlot` interface (F5); `ErrorContent` (F7); `ExpressiveEmptyState` shape (F11); FAB tokens (F14); disabled-content alpha (F15); `Durations.FusionOrbit = 3000` and `MainActivity.kt:320-356` / `ui/navigation/**` springs (F10).

## Not verifiable without a device
- That the 8dp `Badge` corner reads correctly at badge size on real pixel densities (test proves equality with `extraSmall`, not appearance).
- That `decorativeSpin(HeaderPulse)` at 1400 ms matches the previous `ChatTopBar` tween visually once chat migrates (same duration and easing, but the consumer change is not in this branch).
- That the 1 s fusion-card breath (`bob(Pulse)`) reads as a breath rather than a flicker; value is unchanged from `Long`, so no behavioural change is expected.
- That `dataReveal()` 0.8/50 feels right on `ActivityHeatmap`, `ModelComparisonCard` and `ResponseTimeChart`, which previously had their own specs; the consumers were already on the token at `bb91963`, so this pass only pins what they draw.
- Part 5: whether `shapeMorph()` 0.6/500 or `pressMorph()` 0.6/800 is the better press feel for the expressive control shapes.
- The contrast assertions are numeric (WCAG ratio on the resolved `ColorScheme`); dynamic colour schemes are approximated by `ThemeContrastTest.dynamicLike` (`:407`), not a real Monet extraction, and rendering (elevation tint, alpha compositing, blur) is not covered.
- No screenshot, emulator or hosted device run was made for any of the above.
