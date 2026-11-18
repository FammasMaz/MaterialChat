# Pass W1 — Theme, colour system, contrast guarantees

Branch `pass/w1-theme`, based on v2.41.0 (574b5c3). Not pushed.

Commits:
- 779ffca fix(theme): WCAG contrast gate for every palette, opaque cosmic secondary, outlineDark 3:1, pitch-black tier ramp
- 082c212 feat(theme): shared press-morph motion tokens and M3E fast/slow spatial+effects specs (additive)
- cdb72bd docs(theme): colour/contrast audit, shape vocabulary table, cross-surface token inventory
- (this file) docs(theme): REPORT.md

Tests: `:app:testGithubDebugUnitTest` 597 tests, 0 failures (10 new in `ThemeContrastTest`, 8 existing `AssistantContrastTest` untouched and green). `:app:compileGithubDebugKotlin` and `:app:compileGithubDebugAndroidTestKotlin` green. No androidTests added, no workflow or script touched.

## What was audited

- `ui/theme/ThemePalettes.kt` (10 static palettes x light/dark), `ui/theme/Color.kt` (shared surface/outline/error/inverse ramp), `ui/theme/Theme.kt` (scheme selection, pitch-black, `MaterialExpressiveTheme` + `MotionScheme.expressive()`), `ui/theme/Typography.kt`, `ui/theme/Shapes.kt`, `ui/theme/ShapeMorphing.kt`, `ui/theme/ExpressiveControlShapes.kt`, `ui/theme/Motion.kt`. `particles/**` read only; behaviour unchanged.
- Material3 1.5.0-alpha15 AAR (`ExpressiveMotionTokens` static initialiser via javap) to confirm the M3E spring constants: default spatial 0.8/380, default effects 1.0/1600, fast spatial 0.6/800, fast effects 1.0/3800, slow spatial 0.8/200, slow effects 1.0/800. The existing `Spatial.default()` and `Effects.color()` already matched; the new `fast()`/`slow()` tokens use the AAR values verbatim.
- Cross-surface grep of `ui/**` and `assistant/**` (read-only): inventory with file:line and replacement token in `docs/audit-color-contrast.md`.

## Contrast gate (ThemeContrastTest)

`app/src/test/java/com/materialchat/ui/theme/ThemeContrastTest.kt` enumerates 39 appearances: 10 static palettes x {light, dark, pitch-black} + 3 dynamic-like seeds x the same three (same seed construction as `AssistantContrastTest`). For each it asserts every pair from `ColorScheme.standardContrastPairs()` (`ui/theme/ThemeContrast.kt:146`): on-role/role for primary, secondary, tertiary, error and their containers (4.5:1); inverse surface pairs (4.5:1); `onSurface`, `onSurfaceVariant` (4.5:1) and `outline` (3:1) on every surface fill (`surface`, `background`, `surfaceDim`, `surfaceBright`, `surfaceVariant`, five container tiers); accent roles as body text on `surface`/`background` (4.5:1) and as icons on every container tier (3:1). Pitch-black additionally requires each container tier >= 1.1:1 luminance above the tier below while `surface`/`background`/`surfaceDim`/`surfaceContainerLowest` are #000000. Dynamic-like schemes only assert the surface/outline pairs and the pitch-black ramp (accent pairs under real wallpaper colour cannot be guaranteed; see below).

### Before / after (worst pair per appearance; full per-pair numbers are printed by `printsPerThemeSummary`)

| Appearance | Before (worst, status) | After (worst) |
|---|---|---|
| VIOLET light | primary/background 4.70 pass | 4.70 |
| VIOLET dark | outline/surfaceVariant 2.96 FAIL | outline/surfaceVariant 3.15 |
| OCEAN / JADE / SUNSET / ROSE / AMBER / GRAPHITE / FLAMINGO light | outline/surfaceDim 3.22 pass | 3.22 |
| CITRUS light | primary/background 4.77 pass | 4.77 |
| COSMIC light | secondary = `Color(0xAA2F96)` alpha 0x00 — fully transparent, onSecondary/secondary and secondary/surface undefined (FAIL at runtime) | secondary/surface 5.9, onSecondary/secondary 6.0 |
| every dark palette (10) | outline/surfaceVariant 2.96 FAIL (shared `OutlineDark`) | 3.15 |
| every pitch-black palette (10) | tier steps Low/Container/High over black 1.00 / 1.00 / 1.00 — no separation (FAIL) | 1.12 / 1.13 / 1.16 / 1.17 |
| dynamic blue/green/amber light | primary/surface 6.0 pass | 6.0 |
| dynamic blue/green/amber dark | inversePrimary/inverseSurface 4.9 pass | 4.9 |
| dynamic blue/green/amber pitch-black | tier steps 1.00 / 1.00 / 1.00 (FAIL) | 1.10 / 1.10 / 1.11 / 1.11 |

All other on-role pairs in every static palette already passed (lowest: onTertiaryContainer/tertiaryContainer >= 10:1, accent icons on `surfaceContainerHighest` >= 3.4:1) and are unchanged.

## What changed (file:line)

- `ui/theme/ThemePalettes.kt:282` — `cosmicLight.secondary` `0xAA2F96` -> `0xFFAA2F96`. The literal had no alpha byte so the colour was transparent; Compose draws it as nothing. Hue/value unchanged.
- `ui/theme/Color.kt:99-100` — `OutlineDark` `0xFF9D91AA` -> `0xFFA296AF` (2.96 -> 3.15:1 on `SurfaceVariantDark`; 6.69:1 on `SurfaceDark`). Smallest lift that clears 3:1; hue preserved. Shared by all 10 dark palettes.
- `ui/theme/PitchBlack.kt` (new) — `ColorScheme.withPitchBlackSurfaces()` moved out of `Theme.kt` (was private there, `Theme.kt:212-226` removed) and made public so the test exercises the production path. Screen-filling surfaces still go #000000. `surfaceContainerLow`/`surfaceContainer` are re-derived from the scheme's own `surfaceContainerHigh` (channel-wise sRGB, not Compose `lerp` which is Oklab) and every tier is lifted toward white only as far as needed to sit 1.1:1 above the tier below. On all 10 static palettes `High`/`Highest` are untouched (asserted); on a dynamic ramp whose `High` is too dark to fit two steps they are lifted, never darkened or re-hued (asserted).
- `ui/theme/ThemeContrast.kt` (new) — `ThemeContrast` (WCAG arithmetic, agrees with the assistant's private copy to 1e-9 on pinned samples, asserted), `ThemeContrastPair`, `ColorScheme.standardContrastPairs()`, `surfaceTiers()`, `surfaceFills()`, `surfaceTierSteps()`, and the runtime guards `ColorScheme.readableOn(fill, minimum, preferred: List<Color>)`, `readableMutedOn(fill)`, `readableAccentOn(fill)`.
- `ui/theme/Typography.kt:221-224` — `headlineSmall` weight `SemiBold` -> `Bold`. `MaterialChatExpressiveTitleFontFamily` only ships a Bold instance, so SemiBold already rendered Bold; the declaration now matches what is drawn. No size/lineHeight change. Display/headline are Bold (not ExtraBold); no `FontStyle.Italic` in theme sources (none existed).
- `ui/theme/Motion.kt:195-222, 285-322` — additive: `Spatial.pressMorph()`, `Spatial.fast()`, `Spatial.slow()`, `Effects.fast()`, `Effects.slow()`, `Effects.pressTint()`, and `ExpressiveMotion.Press { Shape, Progress, Scale, Tint, Alpha }`. Nothing renamed or removed.
- `docs/audit-color-contrast.md` (new) — palette table, dynamic-colour caveats, shape vocabulary table per surface class, and the grep inventories (299 `Color(0x` lines all inside `ui/theme`; 7 `Color.White/Black`; 55 `on*.copy(alpha=)` text uses; 170 raw `spring(`/`tween(`; 11 `FontStyle.Italic`) with the token each should use.

## Deliberately left alone

- `OutlineLight` (3.22:1 on `surfaceDim`, passes).
- Static palette accent/container pairs: all pass; retuning them would change the app's look for no WCAG gain.
- `MaterialChatMotion`, `IntSprings`, `SpringSpecs` legacy objects: still referenced by consumers; kept verbatim.
- `ShapeMorphing.chipShape` uses `Spatial.scale()`, `fabShape` uses `Spatial.playful()`: consumers rely on them; the new `Press` tokens are the migration target, documented for the next wave.
- `particles/**`, `ParticleMotionGate`: untouched.
- Assistant contrast test and `AssistantContrast.kt`: untouched; still green with the new `OutlineDark` and pitch-black ramp.

## Dynamic colour: what cannot be guaranteed

Under wallpaper colour the system supplies every role; the app cannot retune them. Material's tonal palette guarantees on-role vs role (tone 100/40, 10/90) and `onSurface`/`surface`; it does not guarantee `primary` as body text on `surface` (tone 40 vs 98/99 is ~4.5 at the edge, and some hues fall under), accent icons on `surfaceContainerHighest`, `outline` on `surfaceVariant`, or any blended/tinted fill (`lerp(..., primaryContainer, x)`, `.copy(alpha=)` containers). For those, consumers should call `MaterialTheme.colorScheme.readableOn(fill)` / `readableMutedOn` / `readableAccentOn` (additive API, `ui/theme/ThemeContrast.kt:103-124`). The three dynamic-like seeds in the test only cover the surface/outline pairs and the pitch-black ramp for that reason.

## Needs another owner

All in `docs/audit-color-contrast.md` with file:line; headline items:
- 11 `FontStyle.Italic` uses in `ui/screens/chat/components/{FusionSourcesSection,FusionProgressView,MessageBubble}.kt`, `ui/screens/conversations/ConversationsScreen.kt:598`, `ui/components/MarkdownText.kt` (5) — chat/home owners; remove or replace with weight/colour emphasis.
- `ui/components/ImageViewerDialog.kt:68,116-117` hard-coded `Color.Black/White` — acceptable for a photo viewer but should be `scrim`/`inverseOnSurface`.
- 55 text colours with `.copy(alpha = 0.4..0.88)` on `onSurface*`/`on*Container` — several at 0.4-0.6 cannot reach 4.5:1 on their fill (e.g. `RedoModelPickerSheet.kt:82`, `ExportBottomSheet.kt:82`, `TokenCounter.kt:84`); replace with `onSurfaceVariant` or `readableMutedOn(fill)`.
- 170 raw `spring(`/`tween(` call sites outside `ui/theme` — map to `ExpressiveMotion.*` tokens per the table; `Press` tokens for press morphs.

## Not verifiable without a device

- Visual separation of the pitch-black tiers on an actual OLED panel at 1.1:1 (numeric gate only; panel gamma/black-crush may differ).
- That `headlineSmall` renders identically after the weight declaration change (expected identical; font instance unchanged).
- `OutlineDark` perceptual change (+5 units lightness) across the dark palettes.
- Dynamic-colour schemes: the test uses synthetic seeds, not the system's `dynamicDarkColorScheme(context)`; real wallpaper output can only be checked on API 31+ hardware.
- Motion feel of the new `Press` tokens (no consumer uses them yet).
