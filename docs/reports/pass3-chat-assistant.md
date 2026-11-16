# Pass 3 (chat screen + assistant window) — report

Branch `feat/p3-chat-assistant`, based on main `7f56f27` (contains pass 1: tap-through fix, connected
action row, send-button morph, fixed header tone, predictive back, assistant pill contrast). Three
feature commits, one per part, plus this report. No emulator, no push, no release workflow.
`SmoothStreamingText.kt` untouched (`git diff main -- …/SmoothStreamingText.kt` is empty). No italic
added (`git diff main | grep '^+.*FontStyle.Italic'` → 0 hits). No existing token value in `ui/theme/`
changed (the only `ui/theme` diff is zero — new tokens were added inside `assistant/` instead).
Nothing in `scripts/` or `.github/workflows/` changed (`git diff main -- scripts .github` is empty).

## Commits

| Commit | Part |
|---|---|
| `01c04b6` | 1 — bubble arrival, stream-end settle, working→content swap, thinking shape morph, code/table containers, carousel/attachment/quote press morphs |
| `b58965a` | 2 — composer focus morph + connected plus-menu, attachment chip group, model picker selected morph + LoadingIndicator, masthead weight fold, status chip shapes, scroll FAB shape morph + unread badge, tonal date pills |
| `936b6fb` | 3 — assistant pill state-colour springs, thinking indicator at pill size, attach +→× shape morph, connected toolbar/attach groups, expressive chrome buttons, waveform rise/decay springs |
| (this) | REPORT.md |

## Verification

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --max-workers=2 :app:compileGithubDebugKotlin` — BUILD SUCCESSFUL after each part.
- `:app:compileGithubDebugAndroidTestKotlin` — BUILD SUCCESSFUL (final tree).
- `:app:testGithubDebugUnitTest` — tally from `app/build/test-results/testGithubDebugUnitTest/*.xml`: **79 result files, 536 tests, 0 failures, 0 errors, 0 skipped** (3 new test classes, 19 new tests: `MessageArrivalTrackerTest`, `ExpressivePositionShapesTest`, `ScrollToBottomFabBadgeTest`). Pass-1 suites (`MessageActionsLayoutTest`, `ComposerSendShapeTest`, `ChatHeaderToneTest`, `ChatBackHandlingTest`, `ChatListFlingTest`, `AssistantContrastTest`, `AssistantAttachRevealTest`, `AssistantKeepAwakeTest`, `AssistantKeyboardSpacingTest`) all still green.
- `bash -n scripts/check-assistant-window.sh` → syntax OK; script byte-identical to main. Every string the gate greps is still present in source: `assistant_sheet`, `assistant_pill`, `assistant_attach`, `assistant_attachments`, `End assistant session`, `Stop listening`, `Hide keyboard`, `Keyboard`, `Microphone`, `Interrupt`, `Send`, `keep-awake` log lines; `NeuralParticles: painter=` lives in the untouched particle painter. Pill geometry unchanged: `AssistantOverlay.kt:724 heightIn(min = 76.dp)`, `:768 height(76.dp)`, corners `38.dp` at `:378`/`:405`, `AssistantExpressiveStyle.kt:65 Pill = RoundedCornerShape(38.dp)`, keyboard gap `AssistantKeyboardSpacing.GAP_DP` at `:638`.
- Keep-awake: `AssistantOverlay` still holds no timers/delays (no `delay(`, no `withTimeout`, no new `LaunchedEffect` that waits); keep-awake stays in `MaterialChatVoiceInteractionSession` (file untouched).
- `./gradlew --stop` run at the end.

## material3 1.5.0-alpha15 APIs used (verified in `~/.gradle/caches/modules-2/files-2.1/androidx.compose.material3/material3-android/1.5.0-alpha15/d31e4564…/material3.aar` with `javap`)

| API | Class in AAR | Where used |
|---|---|---|
| `LoadingIndicator` | `LoadingIndicatorKt.LoadingIndicator-3IgeMak` | `RedoModelPickerSheet.kt:123` (new), `AssistantOverlay.kt:841` (resized), existing `ModelPickerDropdown.kt:257` |
| `ContainedLoadingIndicator` | `LoadingIndicatorKt.ContainedLoadingIndicator-DTcfvLk` | existing `ChatWorkingIndicator.kt` (now swapped via `AnimatedContent`, `MessageBubble.kt:486`) |
| `ButtonGroupDefaults.ConnectedSpaceBetween` | `ButtonGroupDefaults.getConnectedSpaceBetween-D9Ej5fM` | `AssistantOverlay.kt:1257` (answer toolbar), `:1347` (attach pair) |
| `IconButtonShapes` / `IconButtonDefaults.shapes(shape, pressedShape)` | `IconButtonDefaults.shapes(Shape, Shape, Composer, int, int)`, `IconButtonKt.IconButton(Function0, IconButtonShapes, …)` | `MarkdownText.kt` copy button (~`:770`), `AssistantOverlay.kt:1406 AssistantChromeIconButton` |
| `Badge` / `BadgedBox` | `BadgeKt.Badge-eopBjH0`, `BadgeKt.BadgedBox` | `ScrollToBottomFab.kt` |
| `SmallExtendedFloatingActionButton` | existing | `ScrollToBottomFab.kt` |

`ButtonGroup` (the composable) and `SplitButtonLayout` were inspected (`ButtonGroupKt`, `SplitButtonKt`, `SplitButtonDefaults`) and deliberately **not** adopted: the answer toolbar mixes a tonal chip with 48dp icon actions and a trailing close, the plus-menu is a vertical reveal, and the model pickers are a dropdown and a sheet list — none is a horizontal single-tone group or a leading+trailing pair, so the connected groups are built with the same tokens (2dp join, outer 20dp / inner 8dp, 12dp pressed) the way pass 1 built the message action row.

## Part 1 — chat bubbles and streaming (`01c04b6`)

- **Arrival only for new messages.** `MessageArrival.kt` (new): `MessageArrivalTracker` — first `sync` is the baseline (nothing animates), a sync adding > 2 ids is a bulk load (sibling swap, branch load, initial page: nothing animates), otherwise fresh ids are handed out once within 1.5 s. `ChatScreen.kt:1003` remembers the tracker, `:1112` passes `animateArrival` per bubble. `MessageBubble.kt:188` new `animateArrival` param; `:225-231` scale from `MESSAGE_ARRIVAL_SCALE` 0.96 + fade (alpha on `Effects.reveal`, scale on `Spatial.default`). The transform sits on the bubble `Surface`'s `graphicsLayer` (`:382`), not the outer Box, so the bookmark burst still overflows on API 26. `animateItem`'s `fadeOutSpec = null` ghost-overlap guard kept (`ChatScreen.kt`).
- **Stream-end settle.** `MessageBubble.kt:233-239`: when `isStreaming` flips true→false the container snaps to `STREAM_END_SETTLE_SCALE` 0.985 and springs to 1 on `Spatial.default`. The critically-damped `animateContentSize` (`spring(1.0, 520)`) on the streaming bubble is unchanged.
- **Working → content swap.** `MessageBubble.kt:470-500`: the "Thinking / Working on your answer" surface and `MessageContent` are now the two states of one `AnimatedContent` keyed on `isStreaming && content.isEmpty()`, entering with fade + scale from 0.96 on the same spatial spring (`SizeTransform` disabled: the Surface owns the resize). `ChatWorkingIndicatorTest` strings/content description unchanged.
- **Thinking block morph.** `MessageBubble.kt:1047-1120`: header radius 16dp→10dp on `Spatial.shapeMorph`, single chevron rotating 0→180° on `Spatial.default` (content descriptions "Expand thinking"/"Collapse thinking" kept — `MessageBubbleThinkingTest` still targets them), header tone on `Effects.color`, preview line now folds/crossfades via `AnimatedVisibility` on `Disclosure` specs instead of popping. Body disclosure physics unchanged. `tapThroughScroll` kept.
- **Code blocks / tables (`MarkdownText.kt`, styling only).** `:639-640` `MarkdownBlockOuterShape` 16dp / `MarkdownBlockInnerShape` 8dp; `CodeBlockView` (`:719`) uses the 16dp container, a **Copy code** icon button (`contentDescription = "Copy code"`, `:782`) with `IconButtonDefaults.shapes` press morph (circle → 8dp) next to the existing "Open in Canvas" button, and a horizontal-scroll edge fade (gradient mask on the scroll state's `canScrollForward/Backward`). `TableView` (`:1695`): 16dp outer container, header row a tonal `surfaceContainerHigh` strip (`:1706`, `:1779`) with 8dp inner corners, same edge fade on the horizontal scroll. The `horizontalScroll` modifier and the `Box`/`Column` structure the pass-1 table-drag path relies on are unchanged; `MessageBubbleTapThroughScrollTest` compiles unchanged.
- **Carousel / attachments / quote.** `ExpressivePositionShapes.kt` (new): `positionCorners(index, count, outer, inner)`, `rememberPositionMorphShape` (per-position corners tightening to `pressed` on `Spatial.shapeMorph`) and `Modifier.expressivePressScale` (`Spatial.scale`). `WebSearchSourcesCarousel.kt:264-286`: each card gets its position shape and press scale (first/last card keep 24dp outer edges, joins 8dp). `MessageBubble.kt:1427`: attachment thumbnails form a connected group (16dp outer, 8dp joins, 10dp pressed) with press scale; the `clickable` now carries the interaction source. `QuoteReplyPreview.kt`: surface radius morphs 20→14dp while pressed, close button press morph; content description "Clear quote" kept.

## Part 2 — composer and chat chrome (`b58965a`)

- **Composer field focus morph.** `MessageInput.kt:319-334`: on focus the `SquircleShape` radius tightens 28→20dp on `Spatial.shapeMorph` and the tone lifts to `surfaceContainerHighest` on `Effects.color` (was tone only). Shared-element morph for new chats untouched.
- **Plus-menu as shape-morph reveal.** `MessageInput.kt:491 ImageActionMenu`: still a `Popup` (focus/dismiss semantics kept) but the three actions are now one connected vertical group (per-position corners via `positionCorners`, 2dp joins) that grows out of the `+` button's bottom-start corner — scale from 0.6 with transform origin at the button, corners morphing from the button's 24dp to the group's outer/inner radii on `Spatial.shapeMorph`; the `+` keeps its Flower/CookieSoft token swap. Content description "Image actions" kept.
- **Attachment chips.** `MessageInput.kt:946`: preview thumbnails use `positionCorners` (16/8dp) so a row reads as one connected group; "Attached image"/"Remove attachment" descriptions kept.
- **Model pickers.** `ModelPickerDropdown.kt`: selected row morphs between the `MenuSelection` squircle and a tighter 10dp pressed shape with a 0.97 press scale; the trigger pill tightens on press (`:85-89`); the loading row keeps the `LoadingIndicator` (`:257`). `RedoModelPickerSheet.kt:118-123`: the loading state is now a `LoadingIndicator` + status line (same language as the dropdown and the top-bar `ContextWindowIndicator` loading state); rows (`:143-163`) get a tonal selected container with 16→12dp press morph and 0.97 scale; "Current model" description kept.
- **Masthead typography.** `ChatTopBar.kt:164-169`: title weight animates `animateIntAsState` 700 (expanded) → 600 (folded) as `FontWeight(titleWeight)`, applied at `:261` (compact title) and `:346` (masthead title); no italics. Fold machine, tone resolver and pulse strip untouched (`ChatHeaderToneTest`, `ChatHeaderCollapseMachineTest` green).
- **Status chips.** `ChatTopBar.kt:511-530`: `HeaderStatusChip` morphs from `CustomShapes.Pill` to 12dp on press for the clickable (Branched) chip; descriptions ("… Open mind map") kept.
- **Scroll-to-bottom FAB.** `ScrollToBottomFab.kt` rewritten: appears as a circle (`ScrollFabArrivalCorner` 24dp) and settles to the small-FAB 16dp on `Spatial.shapeMorph`, 10dp pressed, exits scaling back; `BadgedBox`/`Badge` shows `scrollFabBadgeLabel(unreadCount)` (1–99, "99+"). `ChatScreen.kt:703-712`: `unreadCount` counts messages added while `!isAtBottom`, reset on reaching the bottom; passed at `:944`. `chat_scroll_to_bottom` / `chat_new_messages` strings unchanged.
- **Day separators.** `DateSeparator.kt:57-65`: `secondaryContainer` tonal pill with `labelMedium` SemiBold, content colour from the same pair, short hairlines either side. `shouldShowDateSeparator` unchanged.

## Part 3 — assistant window (`936b6fb`)

- **Tokens added** (`AssistantExpressiveStyle.kt:86-101`, new only): `AssistantShapes.ConnectedOuterCorner` 20dp, `ConnectedInnerCorner` 8dp, `ConnectedPressedCorner` 12dp, `ConnectedPressed`, `connectedCorners(index, count)`. Nothing existing changed.
- **Pill states.** `AssistantOverlay.kt:791-800`: glyph container *and* glyph content now spring on `AssistantMotion.effect()` (`animateColorAsState`) instead of a hard swap; the content colour still comes from `palette.glyph(phase).content` (pass-1 resolver) so every intermediate colour is between two contrast-checked endpoints. Thinking: `LoadingIndicator` at `:841` sized to fill the 40dp square (34dp) so the morph reads at pill size. Idle/listening/speaking glyph pop kept.
- **Waveform.** `VoiceWaveform.kt`: `waveformBarSpec(current, target)` — bars rise on `Spatial.playful` (overshoot) and decay on `Spatial.collapse` (critically damped) so a loud syllable snaps in and the fall never re-rises. Bar count/width/height unchanged.
- **Thinking / answer surfaces.** Arrival (overshoot in on `surfaceReveal`) and exit (critically damped `exit()`) verified as already spec'd — left alone. Hierarchy refined inside `AssistantResponseSurface`: question bubble → answer → toolbar capsule with the toolbar now a **connected group** (`:1257`): per-position corners from `connectedCorners`, 2dp `ConnectedSpaceBetween` joins, press morph to 12dp on each action; "New chat", "Copy response", "End assistant session" descriptions kept.
- **Attach reveal.** Geometry untouched (the reveal still expands from the pill's bottom edge under the 38dp corners). `:970-990`: the `+` affordance's fill/content colours spring (`effect()`), and it morphs circle → 12dp rounded square while open as the glyph rotates 45° into ×; `testTag("assistant_attach")` and "Add photo"/"Close photo options" kept. `:1347`: the two attach chips are a connected pair (20dp outer edges, 8dp join, 12dp pressed). `assistant_attachments` tag kept.
- **Primary action.** `:1053-1062`: Send/Stop/Interrupt/Mic container and content colours now animate on `effect()` instead of swapping when the role changes (the pop + corner morph from before stay). All colours still `palette.primaryButton/stopButton/interruptButton`.
- **Error / limit notices, keyboard mode.** `:1439 AssistantNoticeSurface`: leading glyph (error icon on `errorNotice`, info on `notice`) inside the resolved-contrast container; shape unchanged. Keyboard toggle fold-away unchanged (`:1013`).
- **Top chrome.** `:1406 AssistantChromeIconButton`: close/keyboard/toolbar icon buttons use `IconButtonDefaults.shapes(circle, 12dp)` press morph with content colour from the enclosing surface's `LocalContentColor` — no new colour roles introduced.

## Not verified (needs a device / hosted run)

- Every motion claim above is reasoned from the springs and the Compose source, not observed: bubble arrival feel, stream-end settle amplitude (0.985 may be imperceptible or may need 0.98), the working→content `AnimatedContent` swap not fighting the Surface's `animateContentSize`, the thinking header radius/chevron/preview fold, the FAB circle→square landing, the composer radius tightening on focus, the plus-menu growing out of the `+`, the pill glyph colour springs, the waveform asymmetric springs.
- Arrival-tracker heuristics on a real stream: a send creates 2 ids (user + assistant placeholder) within the `BULK_LIMIT = 2` so both animate; a regenerate that replaces an id animates the new one. Branch loads/sibling swaps that add ≤ 2 ids would animate — acceptable, but only a device shows whether any existing flow adds exactly 1–2 ids unexpectedly.
- API 26: the bubble arrival layer is on the Surface (inside the Box the burst draws into) so the burst should still overflow; the FAB badge, `IconButtonDefaults.shapes` morphs and the scroll edge fades use no shader/blur, so no API gate is expected — not captured. No `BlurEffect`/AGSL added anywhere in this pass.
- Code-block/table horizontal scroll: the edge-fade mask is `drawWithContent` + `DstIn` gradient, added *outside* the `horizontalScroll` modifier chain, so the pass-1 inner-scroll claim path is unchanged — reasoned, not dragged on a device. `MessageBubbleTapThroughScrollTest` compiled, not run.
- Hosted gate (`scripts/check-assistant-window.sh`): all grepped tags/descriptions and the 76dp/38dp/8dp geometry are unchanged in source; the actual run (pill bounds, feather, keep-awake flag, keyboard gap) was not executed here.
- Contrast of the *intermediate* colours during the pill glyph / primary button colour springs: endpoints are the pass-1 resolved pairs; mid-animation values are blends and were not measured.

## Deliberately left alone

- `SmoothStreamingText.kt` (word-by-word haptics) — untouchable.
- Streaming bubble `animateContentSize` spec (`spring(1.0, 520)`) — retargets every frame; overshoot would bob.
- `animateItem` placement/fade specs and `fadeOutSpec = null` — the ghost-overlap guard.
- Pass-1 code: `MessageActions.kt`, `ComposerSendButton.kt`, `ChatHeaderStatus.kt` tone resolver, `ChatBackHandling.kt`, `ChatListFling.kt`, `TapThroughScroll.kt`, `AssistantContrast.kt` — not modified.
- Assistant arrival/exit springs and the thinking surface's asymmetric shape — already spec'd; verified and kept.
- `MaterialChatVoiceInteractionSession`, `AssistantKeepAwake`, `AssistantKeyboardSpacing`, particle field — keep-awake timing and geometry contracts.
- `ButtonGroup`/`SplitButtonLayout` composables — see the API table for why the groups are hand-built with the same tokens.
- Existing italic uses in `MarkdownText.kt` (markdown `*em*`, blockquotes, code comments) and `FusionProgressView`/`FusionSourcesSection` — pre-existing, outside the "no new italics" rule and the fusion files are outside this brief.
- `ui/screens/conversations/**`, `ui/screens/settings/**`, non-chat `ui/components/**` (other than `MarkdownText.kt` styling) — owned by the pass-2 agent.
