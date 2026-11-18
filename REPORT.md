# Sea Glass removal report

Branch `chore/remove-seaglass`, based on main `ad3bdda`. Worktree `.worktrees/rm-seaglass`.

## Commits

1. `9766224` chore: remove Sea Glass Gradle modules, third_party tree, doc and androidTests
2. `b2b9aab` chore: remove Sea Glass preference value, theme wrapper, palette and colours
3. `372e710` chore: replace Sea Glass component adapters with Material 3 in ui/components and navigation
4. `b1d8677` chore: replace Sea Glass component adapters with Material 3 across screens
5. `25efc91` chore: drop Sea Glass surface mode from assistant contrast model and overlay components
6. (this commit) report and final comment cleanup in AppPreferences.kt

Diffstat vs main before the report commit: 133 files changed, 263 insertions, 22283 deletions (40 files deleted, 93 modified).

## Removed

### Build
- `settings.gradle.kts`: `:seaglass-core` and `:seaglass-compose` includes and their `projectDir` mappings.
- `app/build.gradle.kts`: `implementation(project(":seaglass-compose"))`.
- No ProGuard/R8 rules, manifest entries, native packaging config, `.gitmodules`, `.gitattributes` or `.gitignore` entries referenced Sea Glass (checked by grep; nothing to remove).

### Source
- `third_party/seaglass/` (whole tree: README, seaglass-core, seaglass-compose; 35 tracked files).
- `app/src/main/java/com/materialchat/ui/components/seaglass/SeaGlassAppearance.kt`
- `app/src/main/java/com/materialchat/ui/components/seaglass/SeaGlassMaterialComponents.kt`
- `app/src/androidTest/java/com/materialchat/ui/SeaGlassAppearanceTest.kt`
- `app/src/androidTest/java/com/materialchat/ui/SeaGlassOpticsTest.kt`
- `docs/seaglass-validation.md`

`scripts/run-connected-tests.sh` and `.github/workflows/*` did not list either androidTest class by name and have no minimum-count tied to them, so neither was changed (verified by grep for `seaglass` in `scripts/` and `.github/`). Historical reports (`docs/reports/*`, `docs/audit-performance.md`) left untouched as instructed.

### Theme and preferences
- `AppPreferences.ThemePalette.SEA_GLASS` enum value removed.
- `ui/theme/Theme.kt`: the `SeaGlassAppearance(enabled = themePalette == SEA_GLASS)` wrapper around content removed; `MaterialExpressiveTheme` now receives `content` directly. Stale comment in `selectColorScheme` removed. Dynamic colour, static palettes, pitch black and all other branches are unchanged.
- `ui/theme/ThemePalettes.kt`: `SEA_GLASS` branches in `colorScheme`, `previewColor`, `previewSecondaryColor`; `seaGlassLight`/`seaGlassDark` schemes; `seaGlassLightScheme`/`seaGlassDarkScheme` helpers.
- `ui/theme/Color.kt`: all 36 `SeaGlass*` neutral colour constants.
- `SettingsScreen.kt`: palette row description strings no longer mention SeaGlass; `prettyName()` special case for `SEA_GLASS` removed. The palette picker iterates `ThemePalette.entries`, so the Sea Glass chip disappears with the enum value. `InteractionSettingsScreen.kt` only had adapter imports (no Sea Glass UI entry).

### Assistant
- `assistant/ui/AssistantContrast.kt`: `AssistantSurfaceMode` enum, `GLASS_TRANSPARENCY`, the Sea Glass branch of `assistantFills`, the `mode` parameter on `assistantFills`, `AssistantContentColors.on`, `AssistantSurfacePalette.resolve` and the `mode` field on `AssistantSurfacePalette`; the opaque branch is kept verbatim. Unused `lerp` import removed.
- `assistant/ui/AssistantExpressiveStyle.kt`: `rememberAssistantSurfacePalette` no longer reads `LocalSeaGlassEnabled`; resolves the opaque palette only.
- `assistant/ui/AssistantOverlay.kt`, `components/AssistantInputBar.kt`, `AssistantResponseCard.kt`, `FloatingAssistantBubble.kt`: adapter `Surface` import replaced with `androidx.compose.material3.Surface`.
- `app/src/test/.../AssistantContrastTest.kt`: `Theme` no longer carries a mode; the matrix is 10 static palettes x (light, dark, pitch-black) + 3 dynamic seeds x the same three = 39 appearances (was 11 palettes x 3 x 2 modes + 3 x 3 x 2 = 84). Added `themeMatrixCoversEveryAppearance` asserting 39 distinct names. The legacy-baseline test keeps the opaque light-on-light typing-dots assertion and the "only light themes fail" negative finding; the Sea Glass dark-on-dark assertion is dropped with the mode.

## Component substitutions

With Sea Glass disabled every adapter in `SeaGlassMaterialComponents.kt` forwarded all arguments unchanged (same parameter names, same defaults) to the Material 3 composable of the same name; `materialGlass` returned `this` and `glassColors()`/`glassContainer()` returned the input. The substitution is therefore import-level only: `com.materialchat.ui.components.seaglass.X` -> `androidx.compose.material3.X`, with no call-site argument changes, and every action, state, `testTag` and `contentDescription` preserved.

| Adapter (removed) | Material 3 replacement |
|---|---|
| `seaglass.Scaffold` | `androidx.compose.material3.Scaffold` (same defaults; the only glass behaviour was forcing `containerColor` transparent when enabled) |
| `seaglass.Surface` (4 overloads) | `material3.Surface` |
| `seaglass.Card` / `ElevatedCard` | `material3.Card` / `ElevatedCard` |
| `seaglass.Button` / `FilledTonalButton` / `OutlinedButton` / `TextButton` | `material3.*` same names |
| `seaglass.IconButton` / `FilledIconButton` | `material3.IconButton` / `FilledIconButton` |
| `seaglass.ExtendedFloatingActionButton` | `material3.ExtendedFloatingActionButton` |
| `seaglass.TopAppBar` / `CenterAlignedTopAppBar` / `LargeTopAppBar` | `material3.*` same names |
| `seaglass.HorizontalFloatingToolbar` | `material3.HorizontalFloatingToolbar` |
| `seaglass.DropdownMenu` / `DropdownMenuItem` | `material3.DropdownMenu` / `DropdownMenuItem` (adapter default `PopupProperties(focusable = true)` equals the Material default) |
| `seaglass.OutlinedTextField` (String and TextFieldValue) | `material3.OutlinedTextField` |
| `seaglass.AlertDialog` / `BasicAlertDialog` | `material3.AlertDialog` / `BasicAlertDialog` |
| `seaglass.ModalBottomSheet` | `material3.ModalBottomSheet` |
| `seaglass.Snackbar` | `material3.Snackbar` |
| `seaglass.Slider` (3 overloads) | `material3.Slider` |
| `seaglass.Switch` / `Checkbox` | `material3.Switch` / `Checkbox` |
| `seaglass.Badge` | `material3.Badge` |
| `seaglass.AssistChip` / `FilterChip` / `ElevatedFilterChip` / `SuggestionChip` | `material3.*` same names |

Files touched by the substitution: 11 in `ui/components` + `ui/navigation` (Part 3) and 69 under `ui/screens` (Part 4), plus 4 assistant files (Part 5). A stale comment in `AddProviderSheet.kt` ("The SeaGlass Button wrapper has no shapes parameter") was rewritten; the code there already used `androidx.compose.material3.Button` directly. `MarkdownText.kt` comment "(e.g. SeaGlass)" trimmed.

## Preference migration

`AppPreferences.themePalette` already wrapped `ThemePalette.valueOf(name)` in `try/catch(IllegalArgumentException)` returning `DEFAULT_THEME_PALETTE` (VIOLET). A persisted `"SEA_GLASS"` string therefore reads as VIOLET without crashing; the stored value is left as-is and is overwritten the next time the user picks a palette. No DataStore schema change was needed. `setThemePalette` can no longer be called with the removed value because the enum entry is gone.

## Verification

All with `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2`, one invocation at a time, `./gradlew --stop` afterwards. No emulator, AVD or device was started.

- `:app:compileGithubDebugKotlin` on the committed tree: FAILS with 30 errors, all in the two excluded files (see below). No errors anywhere else.
- To exercise the rest of the build, the same import substitution was applied to `MessageBubble.kt` and `MessageInput.kt` as an uncommitted working-tree change only, then both files were restored with `git checkout` so no commit touches them:
  - `:app:compileGithubDebugKotlin`: exit 0.
  - `:app:testGithubDebugUnitTest`: exit 0. Tally from `app/build/test-results/testGithubDebugUnitTest/*.xml` (80 suite files): tests=541, failures=0, errors=0, skipped=0. `AssistantContrastTest`: 8 tests, 0 failures.
  - `:app:compileGithubDebugAndroidTestKotlin`: exit 0.
- Final grep (brief step 7) `grep -ril seaglass app/ settings.gradle.kts build.gradle.kts gradle/` returns only `app/build/**` caches and the two excluded files. `sea_glass`, `glassy`, `GlassSurface`, `materialGlass`: no hits. `app/src/androidTest`, `scripts/`, `.github/`: no hits.

## Remaining compile errors in the two excluded files (not mine to edit)

All are the unresolved adapter imports plus the cascading unresolved-call errors they cause. The owner of those files needs to replace exactly these imports with the `androidx.compose.material3` equivalents:

- `MessageBubble.kt:72` `import com.materialchat.ui.components.seaglass.DropdownMenu`
- `MessageBubble.kt:79` `import com.materialchat.ui.components.seaglass.Surface`
- `MessageInput.kt:58` `import com.materialchat.ui.components.seaglass.IconButton`
- `MessageInput.kt:61` `import com.materialchat.ui.components.seaglass.OutlinedTextField`
- `MessageInput.kt:63` `import com.materialchat.ui.components.seaglass.Surface`

No shim was left; with those five imports changed the branch compiles, as shown by the temporary working-tree check above.

## Not verifiable without a device

- Visual identity of every screen with Sea Glass previously OFF. By construction the adapters were argument-for-argument pass-throughs when disabled, so no rendering difference is expected, but this is a code-level argument, not a rendered capture.
- Behaviour of an installed build whose DataStore still holds `SEA_GLASS` (fallback path verified only by reading the existing `valueOf` try/catch, not by running the app).
- The two deleted androidTests were not run anywhere; the remaining androidTest sources only compile-checked.

## Deleted files (from git)
- app/src/androidTest/java/com/materialchat/ui/SeaGlassAppearanceTest.kt
- app/src/androidTest/java/com/materialchat/ui/SeaGlassOpticsTest.kt
- app/src/main/java/com/materialchat/ui/components/seaglass/SeaGlassAppearance.kt
- app/src/main/java/com/materialchat/ui/components/seaglass/SeaGlassMaterialComponents.kt
- docs/seaglass-validation.md
- third_party/seaglass/README.md
- third_party/seaglass/seaglass-compose/build.gradle.kts
- third_party/seaglass/seaglass-compose/consumer-rules.pro
- third_party/seaglass/seaglass-compose/src/main/AndroidManifest.xml
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassComponents.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassDebugMenu.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassDynamicMorph.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassFloatingSheet.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassFluidGroup.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassIos27Components.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassIosMenus.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassSkipBridge.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassStage.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassTheme.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassTiltState.kt
- third_party/seaglass/seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassUiKit.kt
- third_party/seaglass/seaglass-core/build.gradle.kts
- third_party/seaglass/seaglass-core/consumer-rules.pro
- third_party/seaglass/seaglass-core/src/main/AndroidManifest.xml
- third_party/seaglass/seaglass-core/src/main/cpp/seaglass_backend_abi.h
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassColorMath.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassContourPathBuilder.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassDeviceCornerOptics.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassDeviceTier.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassHdrSupport.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassMaterial.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassPalette.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassSandGrainTexture.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassSdfEngine.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassShapeMorph.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/SeaGlassVelvetGrainTexture.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/backend/SeaGlassAgslBinding.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/backend/SeaGlassRendererBackend.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/backend/SeaGlassShaderCompiler.kt
- third_party/seaglass/seaglass-core/src/main/kotlin/dev/seaglass/core/backend/SeaGlassUniformBlock.kt
