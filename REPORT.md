# Assistant pill icon family — pass/icons-assistant

Base: b44e3f2a (v2.41.9). Branch: `pass/icons-assistant`.

User report: "the assistant pill should also have the same material 3 e icons".

## Rule applied

For every icon in `assistant/**`, use the **same** `Icons.*` reference the chat composer / message action row uses for the same action. Where chat has no equivalent, use the Outlined family (the app's M3 Expressive set: 133 `Icons.Outlined.*` sites). Every outlined variant used was confirmed present in `material-icons-extended-android:1.7.6` (`outlined/GraphicEqKt`, `KeyboardKt`, `KeyboardHideKt`, `MicKt`, `AddCommentKt`, `automirrored/outlined/OpenInNewKt`), so no "no outlined variant exists" exception was needed.

`contentDescription`, `testTag`, sizes (`AssistantSizes`, `ChipIcon` 16dp, pill 76dp / 38dp), `AssistantKeyboardSpacing`, keep-awake, the particle field, recognizer/TTS selection and action directives are untouched. No `FontStyle.Italic`. Nothing outside `assistant/**` was edited.

## Commits

| Hash | Part |
|---|---|
| 5ae0f2e0 | Part 1 — migrate assistant icons to the chat-shared family |
| b1f13ae7 | Part 2 — `AssistantIconFamilyTest` on-disk scanner |
| (this commit) | Part 3 — REPORT.md |

## Per-site table

Line numbers: `old` = base b44e3f2a, `new` = HEAD. "unchanged" rows are sites that already matched the chat side; they now carry a one-line "Filled on purpose" comment citing the chat site they mirror.

### `app/src/main/java/com/materialchat/assistant/ui/AssistantOverlay.kt`

| old | new | Old glyph | New glyph | Reason |
|---|---|---|---|---|
| 924 | 924 | `Icons.Default.GraphicEq` | `Icons.Outlined.GraphicEq` | no chat equivalent → Outlined |
| 929 | 931 | `Icons.Default.AutoAwesome` | `Icons.Filled.AutoAwesome` | same glyph, named explicitly; chat model glyph is `Icons.Filled.AutoAwesome` (MessageInput.kt:581, MessageBubble.kt:1255/1672) |
| 1087 | 1091 | `Icons.Default.Add` | unchanged | chat composer attach affordance `Icons.Default.Add` (MessageInput.kt:244) |
| 1107 | 1111 | `Icons.Default.KeyboardHide` / `Icons.Default.Keyboard` | `Icons.Outlined.KeyboardHide` / `Icons.Outlined.Keyboard` | no chat equivalent → Outlined |
| 1172 | 1177 | `Icons.AutoMirrored.Filled.Send` | unchanged | chat composer send (ComposerSendButton.kt:242) |
| 1180 | 1186 | `Icons.Default.Stop` | unchanged | chat composer stop square (ComposerSendButton.kt:236) |
| 1188 | 1195 | `Icons.Default.Stop` | unchanged | chat composer stop square (ComposerSendButton.kt:236) |
| 1201 | 1208 | `Icons.Default.Mic` | `Icons.Outlined.Mic` | no chat composer mic; Settings uses `Icons.Outlined.Mic` (SettingsScreen.kt:3164) |
| 1216 | 1224 | `Icons.Default.Close` | unchanged | chat composer dismiss `Icons.Default.Close` (MessageInput.kt:1016) |
| 1298 | 1306 | `Icons.Outlined.Screenshot` | unchanged | already Outlined |
| 1391 | 1399 | `Icons.AutoMirrored.Filled.OpenInNew` | `Icons.AutoMirrored.Outlined.OpenInNew` | app-wide open-in-new is Outlined (MarkdownText.kt:803, BookmarkCard.kt:199, OnDeviceModelsScreen.kt) |
| 1404 | 1412 | `Icons.AutoMirrored.Filled.OpenInNew` | `Icons.AutoMirrored.Outlined.OpenInNew` | same |
| 1408 | 1416 | `Icons.Default.AddComment` | `Icons.Outlined.AddComment` | no chat equivalent → Outlined |
| 1412 | 1421 | `Icons.Default.ContentCopy` | unchanged | chat bubble copy action (MessageActions.kt:314, MessageBubble.kt:561) |
| 1416 | 1426 | `Icons.Default.Close` | unchanged | chat composer dismiss (MessageInput.kt:1016) |
| 1523 | 1535 | `Icons.Default.PhotoCamera` | unchanged | chat attach sheet (MessageInput.kt:562) |
| 1529 | 1541 | `Icons.Default.AddPhotoAlternate` | unchanged | chat attach sheet (MessageInput.kt:573) |
| 1627 | 1639 | `Icons.Outlined.Screenshot` | unchanged | already Outlined |
| 1639 | 1652 | `Icons.Default.Close` | unchanged | chat composer preview remove (MessageInput.kt:1016) |
| 1718 | 1731 | `Icons.Outlined.ErrorOutline` / `Icons.Outlined.Info` | unchanged | already Outlined; matches MessageBubble.kt:1716 |
| 1747 | 1761 | `Icons.Default.Refresh` | unchanged | chat bubble regenerate (MessageActions.kt:317, MessageBubble.kt:1739) |
| 1748 | 1762 | `Icons.Default.Keyboard` | `Icons.Outlined.Keyboard` | no chat equivalent → Outlined |
| 1749 | 1763 | `Icons.AutoMirrored.Filled.OpenInNew` | `Icons.AutoMirrored.Outlined.OpenInNew` | app-wide Outlined |
| 1751 | 1765 | `Icons.Outlined.Settings` | unchanged | already Outlined |

Imports changed accordingly (lines 107–116, 151–153): `automirrored.filled.OpenInNew` → `automirrored.outlined.OpenInNew`; `filled.{AddComment,GraphicEq,Keyboard,KeyboardHide,Mic}` → `outlined.{…}`. Retained filled imports: `Close`, `automirrored.filled.Send`, `Add`, `AutoAwesome`, `ContentCopy`, `Stop`, `AddPhotoAlternate`, `PhotoCamera`, `Refresh`.

### `app/src/main/java/com/materialchat/assistant/ui/components/AssistantResponseCard.kt`

| old | new | Old glyph | New glyph | Reason |
|---|---|---|---|---|
| 302 | 302 | `Icons.AutoMirrored.Filled.OpenInNew` | `Icons.AutoMirrored.Outlined.OpenInNew` | app-wide Outlined |

Import line 18: `automirrored.filled.OpenInNew` → `automirrored.outlined.OpenInNew`.

Total: 7 glyph references migrated to Outlined (6 in AssistantOverlay.kt, 1 in AssistantResponseCard.kt), 1 renamed `Default` → `Filled` with no visual change (AutoAwesome), 11 kept filled because the chat side uses the same filled glyph, 5 already Outlined.

## Allowlist (`AssistantIconFamilyTest.kt`)

| File | Glyph | Reason (chat site it mirrors) |
|---|---|---|
| AssistantOverlay.kt | `Icons.AutoMirrored.Filled.Send` | chat composer send: ComposerSendButton.kt |
| AssistantOverlay.kt | `Icons.Default.Stop` | chat composer stop square: ComposerSendButton.kt |
| AssistantOverlay.kt | `Icons.Default.Add` | chat composer attach affordance: MessageInput.kt |
| AssistantOverlay.kt | `Icons.Default.Close` | chat composer dismiss / preview remove: MessageInput.kt |
| AssistantOverlay.kt | `Icons.Default.ContentCopy` | chat bubble copy action: MessageActions.kt |
| AssistantOverlay.kt | `Icons.Default.Refresh` | chat bubble regenerate action: MessageActions.kt |
| AssistantOverlay.kt | `Icons.Filled.AutoAwesome` | chat model glyph: MessageInput.kt, MessageBubble.kt |
| AssistantOverlay.kt | `Icons.Default.PhotoCamera` | chat composer attach sheet: MessageInput.kt |
| AssistantOverlay.kt | `Icons.Default.AddPhotoAlternate` | chat composer attach sheet: MessageInput.kt |

The scanner walks every `.kt` under `assistant/**` (comments and raw strings exempt), checks both the `Icons.Default.` / `Icons.Filled.` / `Icons.AutoMirrored.Filled.` use sites and the `androidx.compose.material.icons.filled.*` / `automirrored.filled.*` import lines against the allowlist, and asserts every allowlist entry is still used (so a stale entry cannot mask a regression). Stash check: against base b44e3f2a sources it fails with exactly the 7 migrated imports listed; against HEAD it passes.

## Verification

- `:app:compileGithubDebugKotlin :app:compileGithubDebugAndroidTestKotlin` — exit 0 before both commits.
- `:app:testGithubDebugUnitTest` — 878 tests before Part 1 commit, 881 after Part 2 (3 new), 0 failures.
- No emulator or device was launched; no push.

## Not verifiable without a device

- Rendered appearance of every migrated glyph in the pill at `AssistantSizes.ChipIcon` (16dp) and at the toolbar/control sizes. Outlined glyphs at 16dp can read thinner / lighter than their filled counterparts; the affected 16dp sites are the `OpenInNew` chip icons (overlay action chip, notice chip, response-card continue chip) and the `Keyboard` notice chip. The toolbar `AddComment`, `OpenInNew`, `Keyboard`/`KeyboardHide`, `Mic` and the Speaking-phase `GraphicEq` glyph are also unverified visually.
- That the resulting mix (filled Send/Stop/Add/Close/Copy/Refresh/photo chips alongside Outlined Mic/Keyboard/AddComment/OpenInNew/GraphicEq) reads as "one app with the chat" to the user's eye — only a side-by-side device capture settles that.

## Needs another owner

- None for this pass. If the user wants the chat composer itself moved to Outlined for Send/Stop/Add/Close/Copy/Refresh/photo glyphs, that is `ui/screens/chat/**` (read-only here) and the allowlist in `AssistantIconFamilyTest.kt` would then shrink in step.
