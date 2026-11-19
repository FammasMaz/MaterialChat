# Thinking glyph asymmetry under the EXPRESSIVE bubble style

Branch `pass/thinking-chat`, base `b44e3f2a` (v2.41.9). Worktree `.worktrees/thinking-chat`.

## Verdict

Candidate 1 (`ChatWorkingIndicator.kt`) is the asymmetry the user sees; candidate 2
(`StreamingTailEffect`) is a second, smaller one on the same screen. Candidate 3 (the thinking
header chevron) was already symmetric; it only gained named constants, the outlined icon family and
a pin. Candidate 4: nothing in `MessageBubble.kt` draws the thinking state differently per bubble
style; only `bubbleShapeFor` (`MessageBubble.kt:948-984`) varies, so the style reaches the glyphs
only through the corner radii they sit against.

"Expressive shapes on" was read as `AppPreferences.ChatBubbleStyle.EXPRESSIVE`
(`AppPreferences.kt:135-171`, the default). Two other "expressive" switches exist and were ruled
out as the subject (see "Other switches" below).

## Geometry (pre-fix, measured from source)

Bubble content padding for every non-COMPACT style: 16dp horizontal, 12dp vertical
(`MessageBubble.kt:880-881`). EXPRESSIVE assistant corners (`ui/theme/Shapes.kt:415-460`):

| position | topStart | topEnd | bottomStart | bottomEnd |
|---|---|---|---|---|
| Single | 8 | 28 | 28 | 28 |
| First / Middle | 8 | 24 | 8 | 24 |
| Last | 8 | 24 | 24 | 24 |

ROUNDED is 34dp everywhere; COMPACT 6/18/18/18; GEOMETRIC 4/18/10/18. So the leading edge of an
EXPRESSIVE assistant bubble is a near-square 8dp corner on top and a 28dp (Single) arc below, which
is exactly where the working indicator sits.

### 1. `ChatWorkingIndicator.kt` (pre-fix line 66) — asymmetric, the reported one

`Row.padding(end = 8.dp, top = 2.dp, bottom = 2.dp)`, no start inset, `Arrangement.spacedBy(12)`,
48dp `ContainedLoadingIndicator` (M3 `LoadingIndicatorDefaults.ContainerWidth/Height` = 48dp,
`IndicatorSize` = 38dp, container shape `CornerFull`, from the material3 1.5.0-alpha15 AAR).

Inside the bubble this put the circle 16dp from the bubble's leading edge (16 + 0) and the status
text 24dp from the trailing edge (16 + 8): a 16 : 24 horizontal imbalance, the whole block shifted
toward the start. Vertically 14 : 14. The bubble is 12 + 2 + 48 + 2 + 12 = 76dp tall, so the circle
(y 14..62) overlaps the bottom-start 28dp arc (y 48..76) while sitting under the 8dp top-start
corner: above-left of the circle there is an almost square empty region, below-left the bubble edge
curves in to meet it. With the circle already pushed 8dp toward that side, the circle reads as
crowded into the origin corner and off-centre in its slot. Under ROUNDED (34dp all round) both
corners wrap it the same way, so the imbalance is far less visible there — matching "not okay when
expressive shapes are on".

The polygon set is the M3 indeterminate list (SoftBurst, Cookie9Sided, Pentagon, Pill, Sunny,
Cookie4Sided, Oval) drawn unrotated by the component; it is not the source of the asymmetry.
The assistant's reference (`AssistantResponseCard.kt:237-280`) insets its thinking surface by
`AssistantSpacing.Tight` (4dp) on start/top/bottom and draws no indicator of its own (the pill's
32dp `LoadingIndicator` owns the motion), so the container/polygon parameters have no direct
counterpart there; the inset value is what was reused.

**Fix:** `ChatWorkingIndicatorGeometry` — `Inset = 4.dp` on all four sides
(`PaddingValues(all)`), `Container = ExpressiveSizes.IndicatorHero` (48dp), `Gap = 12.dp`,
`TextGap = 2.dp`. The circle is now 20dp from the leading edge and the text 20dp from the trailing
edge; 16 : 16 vertically. Transform origin unchanged (0,1).

### 2. `SmoothStreamingText.kt` `StreamingTailEffect` (pre-fix 187-193) — asymmetric, secondary

`MaterialShapes.SoftBurst.toShape(startAngle = 18)` at 12dp with `padding(top = 2, start = 2)`.
Two one-sided nudges: the polygon was rotated 18 degrees (that is the *determinate* indicator's
orientation in the AAR — `DeterminateIndicatorPolygons = [Circle, SoftBurst rotated 18]`; the
indeterminate set draws SoftBurst at 0), and the slot was padded only on the start and top, so the
glyph sat 2dp toward the end/bottom of a 14x14 box.

**Fix:** `StreamingTailGeometry` — `StartAngle = 0`, `Inset = 2.dp` on every side, `Size = 12.dp`.
The slot is now 16x16 (2dp wider and taller than before); the reveal/haptic code above it is
untouched (diff is confined to `StreamingTailEffect`, verified with `git diff`).

### 3. `MessageBubble.kt` `ThinkingSection` header (1098-1120) — already symmetric

`heightIn(min = 40)`, `padding(horizontal = 8, vertical = 6)`, 18dp chevron,
`verticalAlignment = CenterVertically`, 6dp gap to the title column. 8 : 8 horizontally, 6 : 6
vertically, chevron centred in the strip with 11dp above and below at the 40dp minimum. The
16 -> 10dp header-radius morph is uniform on all four corners. No asymmetry.

Changes: values moved into `ThinkingHeaderGeometry`; chevron is now
`Icons.Outlined.KeyboardArrowDown` (brief's icon-family request). Pinned.

## Other switches

- `ControlShapeStyle.EXPRESSIVE` (Settings -> "Control shape intensity", default BALANCED) rotates
  the composer's reasoning (lightbulb) button polygon via `startAngle` 45 / 8 / 12 in
  `MessageInput.kt:733-741` (Cookie4Sided / Puffy / Flower). Those are deliberately rotated
  polygons; if the user's "thinking icon" is that button, it is a different, intentional
  asymmetry and was not changed. Flagged for confirmation on the phone.
- `NEURAL_EXPRESSIVE` ("Neural expressive edges") reaches only the assistant overlay, not chat.

## Pins

`app/src/test/java/com/materialchat/ui/screens/chat/components/ChatThinkingGeometryTest.kt`
(5 tests): working-indicator inset equal on all four sides and equal to `AssistantSpacing.Tight`;
container == `ExpressiveSizes.IndicatorHero` == M3 `LoadingIndicatorDefaults.ContainerWidth` ==
`ContainerHeight`, `IndicatorSize` == 38dp; tail glyph `StartAngle == 0`, inset 2dp, size 12dp;
header insets 8/6, chevron 18dp fits the 40dp strip. Stash check: with the pre-fix values
restored on disk, 2 of 5 pins fail (`PREFIX_EXIT=1`).

Gate: `:app:compileGithubDebugKotlin`, `:app:compileGithubDebugAndroidTestKotlin` green;
`:app:testGithubDebugUnitTest` 883 tests, 0 failures.

## Not verifiable here (no device, no emulator)

- The rendered working indicator inside an EXPRESSIVE bubble: the geometry argument above is a
  hypothesis until seen on the phone.
- Whether `Icons.Outlined.KeyboardArrowDown` is visibly different from `Icons.Default` in
  material-icons-extended 1.7.6 (the two chevron paths are expected to be the same glyph).
- The 2dp growth of the streaming tail slot and whether it shifts the last line of a streaming
  bubble perceptibly.
- Which of the two "expressive" switches the user actually has on.

## For other owners

No theme token was needed. The 4dp inset is a local value equal to the assistant's
`AssistantSpacing.Tight`; the theme owner may want a shared token if the assistant surface and
the chat indicator are meant to stay equal by name rather than by pin.
