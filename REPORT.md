# Pass W1 — Assistant surface (branch `pass/w1-assistant`, rebased onto main `df77ce6`)

Scope: `app/src/main/java/com/materialchat/assistant/**`, the two `Assistant*SettingsPanel.kt` files, `app/src/test/java/com/materialchat/assistant/**`. No theme, chat, home or settings-screen file was edited. Nothing pushed.

## Commits (in order)

1. `9497a13` refactor(assistant): route motion specs through the shared ExpressiveMotion tokens
2. `b1872f1` fix(assistant): align the 2.41.0 surfaces to the pill language and cover them in the contrast matrix
3. `7c23aee` refactor(assistant): drop unreferenced legacy components, use the shared ExpressiveButton in the settings panels, tone user-visible strings
4. `efbbb56` refactor(assistant): adopt the audit's shared tokens (Spatial.fast/slow/pressMorph, Effects.fast/slow, ExpressiveMotion.Press, theme scrim role)

A first attempt at commit 1 recorded `AssistantContrast.kt` as a 0-byte blob because `git add -A` ran while the file was mid-rewrite. That history was discarded and rebuilt sequentially before anything else was based on it; every commit above carries the full file (verified with `git cat-file -s` per commit).

## What was audited

- `assistant/ui/AssistantOverlay.kt` (pill, answer surface, toolbar, attach reveal, Screen chip, Using-screen marker, unavailable notice, notice surface, chrome icon buttons)
- `assistant/ui/components/AssistantResponseCard.kt` (query bubble, answer text, thinking surface, continue chip)
- `assistant/ui/AssistantExpressiveStyle.kt` (motion / shape / type / colour tokens)
- `assistant/ui/AssistantContrast.kt` + `AssistantContrastTest.kt` (39-theme WCAG matrix)
- `assistant/ui/components/VoiceWaveform.kt`, `AssistantInputBar.kt`, `FloatingAssistantBubble.kt`
- `assistant/ui/AssistantViewModel.kt` user-visible strings
- `ui/screens/settings/AssistantScreenContextSettingsPanel.kt`, `AssistantVoiceSettingsPanel.kt`
- `docs/audit-color-contrast.md` sections 6a–6e (assistant entries)

## What changed and why

### Motion tokens (`AssistantExpressiveStyle.kt:34-63`)
Every `AssistantMotion` spec now resolves to a shared `ExpressiveMotion` token instead of a hand-rolled `spring(...)`. Mapping (old → token):
- `stateSwitch` 0.55/520 → `Spatial.fast()` (:34)
- `surfaceReveal` 0.62/300 → `Spatial.default()` (:37)
- `container` → `Spatial.container()` (:40); `playful` → `Spatial.playful()` (:43)
- `exit` NoBouncy/700 → `Spatial.collapse()` (:51) — still critically damped, still settles inside the 320 ms exit window
- `morph` 0.7/1400 → `Spatial.fast()` (:54)
- `pop` 0.45/650 → `Spatial.thresholdPop()` (:57)
- `contentSize` 0.75/340 → `Spatial.contentSize()` (:60)
- `effect` NoBouncy/1200 → `Effects.color()` (:63)
Chrome icon-button press scale uses `ExpressiveMotion.Press.Scale` (`AssistantOverlay.kt:1530`). `VoiceWaveform.kt:28-29` keeps its documented playful-up / collapse-down pair (both shared tokens). All audit section 6e assistant entries are closed; the `tween` at the old `VoiceWaveform.kt:142` and the raw springs at `:203/:210` were in `IdleWaveform`/`ProcessingWaveform`, which were unreferenced and are deleted.

### 2.41.0 surfaces aligned to the pill language (`AssistantOverlay.kt`)
- Using-screen marker icon 14dp → 16dp, the icon size every chip uses (:1219).
- Unavailable-screen settings action: bare `TextButton` with a primary-coloured label (the one text on the answer fill not resolved against it) → the same tonal `AssistantActionChip` as every other labelled action; `assistant_screen_settings` tag kept (:1243-1247).
- Open in app, Take photo, Attach photo, Open assistant settings all draw one `AssistantActionChip` (:1437): 40dp capsule or connected shape, 16dp icon, bold action label, 12dp press morph, the palette's chip pair.
- `AssistantResponseCard.kt:307-324`: the card's own continue chip (only drawn when a host passes `showContinueButton = true`; the overlay passes false) moved from a 48dp 12dp-radius surface with `labelSmall` to the chip language; label "Continue in App" → "Continue in app" (sentence case).

### Contrast matrix extended (`AssistantContrast.kt`, `AssistantContrastTest.kt`)
- New `attachmentRemove` pair: `onErrorContainer` on `errorContainer`, exactly as the shared `AttachmentPreviewRow` paints the remove badge (`AssistantContrast.kt:205, 253, 316`).
- New tests `surfacesAddedIn2410ReadInEveryTheme` (:112) and `attachmentRemoveIsCheckedAsTheSharedRowPaintsIt` (:130) assert the Screen chip, Using-screen marker, unavailable notice + its chip, thinking title/status, toolbar chips and the remove badge across all 39 enumerated appearances. Result: 0 failures; no colour needed repainting beyond the settings-button label already fixed above.
- Scrim: `Color.Black.copy(alpha = 0.42f)` → `MaterialTheme.colorScheme.scrim.copy(alpha = AssistantContrast.SCRIM_ALPHA)` (`AssistantOverlay.kt:353`). `scrim` is `#000000` in every palette, so the painted value is identical; audit 6b closed.

### Dead code removed
`AssistantInputBar.kt`, `FloatingAssistantBubble.kt` (incl. `PulsingRingEffect`), `IdleWaveform`, `ProcessingWaveform`: grep found no references outside their own files (main, test, androidTest). This also closes audit 6d `AssistantInputBar.kt:209` and `VoiceWaveform.kt:129` (alpha on `onSurfaceVariant`) by deletion rather than by substitution.

### Settings panels
`AssistantVoiceSettingsPanel.kt:40-43` and `AssistantScreenContextSettingsPanel.kt:133-140`: the two full-width `OutlinedButton` entry points now use the shared `ExpressiveButton(style = Outlined)` like the rest of Settings (press scale + corner morph). `assistant_screen_context_open_settings` tag kept. The rows already used `SettingsGroup`/`SettingsToggleRow`; left as is.

### Strings (`AssistantViewModel.kt`)
- :319 "Speech recognition not available" → "Speech recognition is not available on this device"
- :500 "No active AI provider. Please configure one in Settings." → "No active AI provider. Add one in Settings."
No string the JVM/androidTests match on was changed (checked by grep). No `FontStyle.Italic` exists anywhere under `assistant/**`.

## Deliberately left alone
- Pill geometry (76dp / 38dp), `AssistantKeyboardSpacing`, keep-awake, particle field physics, recognizer selection, TTS backends, action directives.
- `AssistantMotion.exit()` remains a separate name even though it now equals `Spatial.collapse()`: the call sites document the exit intent.
- `AssistantContrast` keeps its private copy of the WCAG arithmetic rather than delegating to `ThemeContrast`: the assistant resolver checks a content colour against a *list* of fills, which `ThemeContrast.pick` does not take, and the brief forbids editing `ui/theme/**`.
- `muted.copy(alpha = 0.6f)` in `AssistantContrast.kt:161` and `primaryContainer.copy(alpha = 0.7f)` at `:308` are candidate inputs to the resolver (the translucent value is only used when it passes 3:1 / 4.5:1 and the test asserts that), not raw alpha-on-text.

## Needs another owner
- `ui/screens/chat/components/MessageInput.kt` `AttachmentPreviewItem`: the remove badge reads `errorContainer`/`onErrorContainer` from roles; guaranteed by the theme gate for shipped palettes, but if a dynamic wallpaper ever broke that pair the assistant could not fix it from its side. No change requested now.
- The assistant's `AssistantContrast.pick(fills: List<Color>, …)` overload would be a natural addition to `ThemeContrast` so the two files can merge; theme owner's call.

## Verified
- `:app:compileGithubDebugKotlin` after each part: rc=0.
- `:app:testGithubDebugUnitTest`: 599 tests, 0 failures, 0 errors, 0 skipped (`AssistantContrastTest` 10/10 including the two new tests).
- `:app:compileGithubDebugAndroidTestKotlin`: rc=0.
- No test, testTag, contentDescription, script or workflow file was touched.

## Not verifiable without a device
- Visible motion feel of the token swap (fast vs. the old 1400-stiffness morph; container vs. the old 300-stiffness reveal), and that the exit still finishes visibly before the 320 ms window hides the session.
- That the settings action chip, Using-screen marker and continue chip render at the sizes stated and do not clip inside the answer surface or the opened pill.
- `ExpressiveButton` press morph on the two settings entry buttons.
- API 26 rendering of any of the above (no new API-gated effect was added; all changes are plain Compose).
