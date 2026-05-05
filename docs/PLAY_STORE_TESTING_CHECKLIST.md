# Play Store Testing Checklist

Use this checklist for the first Play Console upload and closed/internal testing.

## Code/build status

- Use the `play` distribution variant for Google Play.
- Play builds disable external GitHub APK update/install UI and do not request `REQUEST_INSTALL_PACKAGES`.
- GitHub builds keep external APK update support and are ad-free by default.
- Release logging is stripped by R8/ProGuard.
- Android backup is disabled to avoid cloud backup of local chat history and provider metadata.
- Manual encrypted backup/restore uses Android's system file picker, so Google Drive works without Drive API scopes.

## Required services

1. Create an AdMob app for MaterialChat.
2. Create a banner ad unit.
3. Create a rewarded ad unit.
4. In Play Console, create an active one-time in-app product:
   - Product ID: `remove_ads` unless you override `REMOVE_ADS_PRODUCT_ID`.
   - Type: one-time/non-consumable.
5. Link AdMob and Play apps if Google prompts you to do so.
6. Prepare the Play upload key/keystore and keep its passwords outside git.

## Play release build

Build the signed AAB with real ad IDs and upload-key credentials:

```bash
./gradlew bundlePlayRelease \
  -PADS_ENABLED=true \
  -PADMOB_APP_ID=ca-app-pub-xxx~yyy \
  -PADMOB_BANNER_AD_UNIT_ID=ca-app-pub-xxx/banner \
  -PADMOB_REWARDED_AD_UNIT_ID=ca-app-pub-xxx/rewarded \
  -PREMOVE_ADS_PRODUCT_ID=remove_ads \
  -PRELEASE_STORE_FILE=release.keystore \
  -PRELEASE_STORE_PASSWORD=... \
  -PRELEASE_KEY_ALIAS=... \
  -PRELEASE_KEY_PASSWORD=...
```

`validatePlayReleaseConfig` intentionally fails Play release builds that use Google's sample AdMob IDs, missing AdMob IDs, or missing release signing while ads are enabled.

## Play Console app content declarations

Prepare these before rollout:

- Privacy Policy URL.
- Ads: yes, the Play build shows ads.
- In-app purchases: yes, remove ads.
- Data Safety: disclose user-provided chat prompts/messages, uploaded images if used, app activity/settings, diagnostics if collected by Google/Firebase/AdMob, advertising ID/ads data from AdMob, and microphone use when assistant voice is enabled.
- Permissions declarations/explanations:
  - Microphone: voice assistant input.
  - Photos/media: image attachments.
  - Notifications/foreground service: long-running AI/chat/image tasks.
  - Assistant service: optional default assistant integration.
- Generative AI: provide content/safety notes if Play Console asks for AI feature details.
- Content rating questionnaire.
- Target audience: do not target children unless ads/AI flows are redesigned for Families policy.

## Testing path

1. Upload to Internal testing first.
2. Install from Play, not sideload.
3. Verify:
   - No GitHub update card/banner appears in the Play build.
   - No unknown-app-install permission is requested.
   - Banner ads load.
   - Rewarded ad grants 24-hour ad-free access.
   - `remove_ads` purchase grants permanent ad-free access.
   - Restore purchases works after reinstall/sign-in.
   - Encrypted backup creates a `.mchatbak` file through the system picker.
   - Encrypted restore previews/restores conversations and rejects a wrong password.
   - Chat, image attachments, on-device models, assistant, and notifications still work.
4. If this is a new personal developer account, complete Google's closed testing requirement before production access, which may require at least 12 opted-in testers for 14 days.

## GitHub release build

GitHub releases should keep using:

```bash
./gradlew assembleGithubRelease -PADS_ENABLED=false
```

The GitHub workflow already uses this variant.
