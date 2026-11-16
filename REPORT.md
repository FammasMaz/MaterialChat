# Pass 1 (assistant): pill text contrast — REPORT

Branch `feat/p1-assistant`, based on main `476349a`. Scope: `app/src/main/java/com/materialchat/assistant/**` and its JVM tests only. No geometry, particle field, keyboard spacing, keep-awake, testTag or contentDescription changed.

## Commits

| Commit | Content |
|---|---|
| `ddf6341` | fix(assistant): resolve every text/icon colour against the fill it is drawn on |
| `21a314c` | test(assistant): WCAG AA contrast for every assistant fill across light/dark/pitch-black/dynamic/Sea Glass |
| (this) | docs: REPORT.md |

## What was actually broken (confirmed by the test's legacy baseline, 97 failing pairs at main 476349a)

The brief assumed a near-black pill under a light `onSurface`. That is not what HEAD draws: the pill's feather fill is `surfaceContainerHigh` of the *same* scheme the content roles come from (`AssistantOverlay.kt:374` / `:719`), so under opaque surfaces every light/dark/pitch-black/dynamic combination of the pill text was already >= 4.5:1. The real breakage is where a fill decouples from the role it is named after:

1. **Sea Glass palette + light theme (dark-on-dark).** `com.materialchat.ui.components.seaglass.Surface` (imported by both `AssistantOverlay.kt:50` and `AssistantResponseCard.kt:26`) sets the container to `Color.Transparent` and draws tinted glass at `transparency = 0.72` over the window backdrop. The assistant's window puts a `Color.Black@0.42` scrim under everything (`AssistantOverlay.kt:343`), so a light-theme pill/answer/thinking/notice surface becomes a mid-dark glass (luminance ~0.35) while the text stays the light theme's dark `onSurfaceVariant`/`primary`/`error`. Measured at HEAD, light theme, Sea Glass, scrimmed backdrop: pill prompt `onSurfaceVariant` 3.77:1, idle sparkle `primary` 2.09–2.75:1, answer notice 3.77:1, answer error 2.62:1, thinking status 3.71:1, notice line 3.77:1, typing dots 2.11:1. All eleven static palettes and all three dynamic-like seeds fail here (8 pairs each for statics, 3 for dynamics).
2. **Light theme + opaque surfaces, typing-indicator dots.** `onSurfaceVariant.copy(alpha = 0.6f)` composited on the tinted answer surface is 2.86–2.92:1 (< 3:1 for a graphical object) on every light palette (`AssistantResponseCard.kt:216` at HEAD).
3. **Negative finding:** no dark or pitch-black combination fails at HEAD under either surface mode; pitch black only blackens `surface`/`background`/`surfaceContainer{,Low,Lowest}` and the assistant draws on `surfaceContainerHigh`/`Highest`, which keep their tonal step. The "white on white" the user saw cannot be reproduced from the role tables; the likeliest device-side cause is (a) a mid-change theme flash (the session collects preferences with `initialValue` light defaults before the real values arrive) or (b) the glass case above seen on a bright wallpaper where the scrim is thin. The resolver now covers both because the content colour is computed from the fill the composition actually hands to the Surface, in the same recomposition.

## What changed

### `app/src/main/java/com/materialchat/assistant/ui/AssistantContrast.kt` (new, pure Kotlin)
- `AssistantContrast`: WCAG relative luminance, source-over compositing, contrast ratio (single fill and worst-of-many), `pick(fills, candidates, minimum)` — keeps the preferred Material role when it reaches the ratio on every fill, else walks the candidate list, with pure white/black appended so a readable colour always exists.
- `AssistantSurfaceMode { Opaque, SeaGlass }` and `assistantFills()`: the colours a container can present. Opaque = the container itself; Sea Glass = tint blended 72% toward the backdrop, modelled against both the plain ambient background and the 42%-scrimmed one.
- `AssistantContentColors.on(container, scheme, mode)`: `body` (onSurface → inverseOnSurface), `muted` (onSurfaceVariant → onSurface → inverseOnSurface), `accent` (primary → onPrimaryContainer → onSurface…, 3:1), `error`, `indicator` (muted@0.6 → muted → body, 3:1).
- `AssistantSurfacePalette.resolve(scheme, mode)`: every fill the window paints (pill, answer, thinking, toolbar, notice, error notice, query bubble, chips, continue chip composited on the answer, primary/stop/interrupt buttons, thinking badge, the four glyph phases — the idle glyph sits on the pill's fills since its container is transparent) with its resolved content, and `pairs()` listing the same values for the test so UI and test cannot drift.

### `AssistantExpressiveStyle.kt`
- `AssistantColors.glyphContent` removed (role-only); `rememberAssistantSurfacePalette()` added (`:171`): reads `MaterialTheme.colorScheme` and `LocalSeaGlassEnabled`, remembers `AssistantSurfacePalette.resolve(scheme, mode)`.

### `AssistantOverlay.kt` (line numbers after the change)
- `:375` feather colour via `AssistantColors.pillFill(scheme)` (same value as before: `surfaceContainerHigh`; now one definition shared with the resolver).
- `:722`/`:726` pill Surface colour/contentColor → `palette.pill.fill` / `palette.pill.body`.
- `:789` glyph colour → `palette.glyph(phase).content`.
- `:861` keyboard placeholder → `palette.pill.muted`; `:871` input text → `palette.pill.body`; `:874` cursor → `palette.pill.accent`.
- `:917-918` state line → `palette.pill.body` / `palette.pill.muted`.
- `:1013-1014`, `:1024-1025`, `:1034-1037`, `:1051-1052` Send/Stop/Interrupt/Mic button colours → resolved tonal pairs (the interrupt button previously used `filledTonalIconButtonColors()` defaults).
- `:1106-1107` answer surface → `palette.answer.fill/body`; `:1133-1134` notice/error text → `palette.answer.error/muted`.
- `:1167-1168` toolbar → `palette.toolbar.fill/muted`; `:1180-1183` and `:1280-1283` tonal chips get explicit `ButtonDefaults.filledTonalButtonColors(palette.chip…)`.
- `:1299-1300`, `:1308` notice surface → `palette.notice` / `palette.errorNotice`, with `contentColor` provided at the Surface boundary.

### `components/AssistantResponseCard.kt`
- `:167-183` query bubble, `:200-219` answer text and typing dots (`palette.answer.indicator`, the fix for break 2), `:244-286` thinking surface + badge, `:298-324` Continue-in-App chip — all from the resolved palette.

### `app/src/test/java/com/materialchat/assistant/ui/AssistantContrastTest.kt` (new, 7 tests)
- Enumerates 11 static palettes × {light, dark, pitch-black} + 3 dynamic-seed-like schemes (blue/green/amber built with `lightColorScheme`/`darkColorScheme`) × the same three, each under Opaque and Sea Glass = 84 themes.
- `everyResolvedPairMeetsWcagAaInEveryTheme`: every `pairs()` entry >= 4.5:1 (text) / 3:1 (icons) on every fill.
- `legacyRoleOnlyColoursFailWhereTheFillLeavesTheRole`: re-derives HEAD's role-only assignments, asserts they fail (97 pairs, printed to stdout), that the failures include a dark-content-on-darkened-fill case (light theme under Sea Glass + scrim) and a low-contrast light-theme opaque case (typing dots), that all of them are light themes (records the negative finding), and that the resolver passes on exactly those themes.
- `thePreferredRoleIsKeptWhereverItAlreadyReads`: on opaque VIOLET the pill keeps `onSurface`/`onSurfaceVariant`/`onPrimary` unchanged, so no theme that was fine is repainted.
- Reference values: 21:1 white/black, 4.48:1 #777 on white, compositing, pick fallback.

## Verification
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin` — BUILD SUCCESSFUL after each edit round.
- `:app:testGithubDebugUnitTest` — tally from `app/build/test-results/testGithubDebugUnitTest/*.xml`: **503 tests, 0 failures, 0 errors, 0 skipped** (7 of them new).
- `:app:compileGithubDebugAndroidTestKotlin` — BUILD SUCCESSFUL.
- `scripts/check-assistant-window.sh` read before editing; none of the grepped strings (`assistant_sheet`, `assistant_attachments`, `assistant_pill`, `End assistant session`, `Stop listening`, `Hide keyboard`, `Keyboard`, `Microphone`, NeuralParticles log lines) changed. Pill height stays `heightIn(min = 76.dp)` / row `height(76.dp)`, corner 38dp.
- `./gradlew --stop` run at the end. No emulator, no push, no release workflow.

## Not verified (needs a device / hosted run)
- The on-screen appearance of the Sea Glass case: the resolver models the glass as a 28% tint over the backdrop; the real SeaGlass renderer adds frost, dispersion and the captured ambient image, so the measured ratios on screen will differ from the model. The chosen colours are conservative (checked against both the plain and the scrimmed backdrop) but not pixel-verified.
- Whether the user's "white on white" was the preference-flash case; the fix makes the content follow whatever fill is composed, but the flash itself (light defaults before the saved theme arrives) was not reproduced or changed here.
- Real dynamic (`dynamicLightColorScheme`/`dynamicDarkColorScheme`) output: the JVM test uses three seed-shaped schemes, not the platform's Material You tone generator.
- API 26/31 rendering paths (blur vs geometric feather) draw the same `featherColor`, so the fill colour the resolver uses is the same on both, but this is reasoned, not captured.
