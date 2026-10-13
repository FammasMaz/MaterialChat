# SeaGlass integration validation

Upstream pinned source and local compatibility/lifecycle changes are documented in
`third_party/seaglass/README.md`. Optical appearance uses the active Material 3
scheme (including wallpaper dynamic colors), independently of palette resolution.
82 app source files use shared optical adapters; native Material interactions,
shape contours and content colors remain in use. Disabled appearance uses normal
Material components.

Verified locally with Java 17 and Android API 36:

- `./gradlew :app:assembleGithubRelease :app:testGithubDebugUnitTest` passed,
  including R8 and release lint. No Android/Compose version upgrades.
- `SeaGlassOpticsTest` passed on Android emulator through the production Surface
  adapter and upstream renderer. The test changes only backdrop color with the
  same scheme/material/geometry, requires >=8 averaged RGB levels of interior
  transmission, and >=15 summed RGB levels of bevel/interior difference.
- Manual selection with Dynamic Color on visibly rendered frosted/translucent
  controls, refractive rims and app backdrop. Selecting the original palette
  restores normal Material appearance.
- Manual testing caught navigation reset on appearance selection. The theme root
  now preserves foreground composition identity instead of swapping content into
  a conditional StageHost.

Limitations: the full activity automation is not passing yet (scroll/accessibility
synchronization and first-launch recreation). Do not claim comprehensive device
acceptance or complete lifecycle/chat-stream regression coverage. Emulated
SwiftShader performance is not a real-device performance measurement. The
upstream native GLES binary described in its README is absent; Compose uses
platform Canvas/RenderEffect/RuntimeShader, not that native binary.

Local evidence is under ignored `artifacts/seaglass/` (release-build-final.log,
optics screenshots, manual screenshots, device-direct-final.log). Tests are
checked in under `app/src/androidTest/java/com/materialchat/ui/` for reproduction.
