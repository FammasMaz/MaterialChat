# Vendored SeaGlass Android modules

## Source and attribution

- Upstream: [mweinbach/SeaGlass](https://github.com/mweinbach/SeaGlass).
- Pinned commit: [`e9003de43434bfb361c7cf84857d5ec196603ec2`](https://github.com/mweinbach/SeaGlass/tree/e9003de43434bfb361c7cf84857d5ec196603ec2).
- Imported paths: `android/seaglass-core/src/main`, `android/seaglass-compose/src/main`, and both modules' `consumer-rules.pro`.
- Local paths: `third_party/seaglass/seaglass-core` and `third_party/seaglass/seaglass-compose`.

All production Kotlin sources, manifests, and the core C++ ABI header are retained. There are no resource directories in these modules at this commit. Tests, sample/View/Windows modules, Gradle wrappers, build caches, and upstream publishing setup are not imported. No production components, including the iOS-style components, are excluded.

The pinned upstream repository contains no LICENSE file or explicit license grant. This README provides attribution and provenance, **not** a license. Do not assume an open-source redistribution license; permission/license clarification from the rights holder is needed before redistribution.

## Local adaptations

### Build integration

Module build files are adapted for the host project rather than copied from upstream's AGP 9.0.1 build:

- Use the project's catalog Android library plugin (AGP 8.7.3), Kotlin Android plugin (2.3.0), and Compose compiler plugin (2.3.0, Compose module only).
- Retain namespaces, compile SDK 36, minimum SDK 26, Java/Kotlin JVM target 17, and consumer rules.
- Use the host Compose BOM and Material3 dependency (`1.5.0-alpha15`), without changing app Compose or AGP versions.
- Retain upstream's `androidx.graphics:graphics-shapes:1.1.0` API dependency.
- Add the host `androidx.lifecycle:lifecycle-runtime-compose` dependency for lifecycle-aware tilt handling.
- Match the host's narrow `NullSafeMutableLiveData` lint-detector ABI workaround for AGP 8.7.3 / lifecycle 2.10.0; all other release lint checks remain enabled.
- Omit publication and test configuration; these are local Android library projects, included by `settings.gradle.kts`. The app depends on `:seaglass-compose`, which exposes `:seaglass-core`.

### Tilt lifecycle and idle animation patch

The only modified upstream production file is `seaglass-compose/src/main/kotlin/dev/seaglass/compose/SeaGlassTiltState.kt`:

- Bind sensor registration to `LocalLifecycleOwner`: register on `ON_RESUME` and immediately if already resumed; guard against duplicate registrations. Unregister on `ON_PAUSE` (also stop/destroy) and disposal.
- Preserve the upstream gravity-sensor choice and accelerometer fallback, normalization, manual override, and spring smoothing.
- Default `ambientDriftEnabled` to `false`. Create the infinite drift transition only while resumed, explicitly enabled, and not overridden by manual tilt or active sensor input. With drift disabled there is no ambient animation frame ticker; spring animations still run briefly when their target changes, and real sensor input still updates tilt.
- Add no app theme override or app UI changes. The caller owns whether this state is composed/provided while glass is enabled.

No Kotlin/Compose compatibility source patches were required: the complete Compose module, including the primary renderer and iOS-style components, compiles against the host dependency stack.

The backdrop renderer remains upstream code. It records on draw; `SeaGlassStage.kt` has no unconditional frame loop. Removing the disabled drift ticker avoids that source of idle updates, but this is not a device-measured guarantee of zero frames/captures: real sensor noise, host animations, and other draw invalidations can still cause work.

## Native backend limitations

These modules declare no `externalNativeBuild`, JNI methods, native library loads, or packaged `jniLibs`. The retained `src/main/cpp/seaglass_backend_abi.h` is an ABI/reference header, not a native implementation. Compose uses Android/Compose graphics layers, Canvas, and platform `RenderEffect`/`RuntimeShader`; these platform APIs do not require a SeaGlass JNI library.

Upstream's root README describes a C++20/JNI GLES blur engine named `libseaglass_native.so`, but the pinned tree supplies neither that binary nor JNI/CMake/native implementation sources. That advertised native engine is not available through this integration. The Kotlin Vulkan backend supplies shader/layout contracts and uniform staging, not a Vulkan renderer; the Kotlin GLES backend can initialize GL resources with an externally owned active EGL context but is not wired into the Compose renderer. A dedicated Vulkan/GLES native rendering path would require additional upstream or host implementation. Device-tier labels are not proof of such a native path.

## Verification

From the MaterialChat worktree root:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :seaglass-compose:compileDebugKotlin --console=plain
```

Build output is retained under the ignored `build/reports/seaglass/` directory. `compile-debug-initial.log` records the first successful compilation, and `compile-debug-final.log` records the final forced compilation. `dependency-resolution.log` records the module graph and confirms the app resolves Material3 `1.5.0-alpha15`.

Both modules also pass `assembleDebug`; `assemble-debug.log` records that build. `vendor-audit.json` verifies all 32 production/consumer-rule files against the pinned commit: 31 are byte-identical and only the documented tilt file differs. It also verifies both debug AARs contain their original consumer rules and no JNI libraries. The audit is reproducible with `python3 build/reports/seaglass/audit_vendor.py` after assembling both modules; `tilt-lifecycle.patch` preserves the exact production source diff. Compilation/source checks do not constitute device lifecycle or idle-frame measurements.
