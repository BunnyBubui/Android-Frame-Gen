# Universal FrameGen Lab — Phase 0–4 development prototype

**Version: v0.3.0-alpha** · **Status: Source prototype; NOT a universal game FPS booster**

An Android non-root research app to investigate capture feasibility, frame interpolation, presentation cadence and overlay input on Android 12+. Its reference frame interpolation runs on **synthetic test images** and does not currently process or display captured game frames.

## What is implemented

- **Phase 0–1:** Kotlin/Jetpack Compose device overview, launcher, permission flow, MediaProjection foreground-service lifecycle and hardware diagnostics.
- **Phase 2 partial:** `ImageReader` in RGBA diagnostic or GPU-sampleable `PRIVATE` buffer probe mode. PRIVATE buffers are inspected, counted and closed; no Vulkan AHardwareBuffer import or game-pixel streaming.
- **Phase 3 reference:** C++17 CPU block-matching/motion-compensated interpolation with JNI. FG Lab slider explores synthetic image blending and reports CPU elapsed time.
- **Phase 4 partial (NEW):** 30/60 Hz synthetic precomputed playback paced by `Choreographer`, pure Kotlin `FramePacer` with skipped-slot/duplicate suppression, native Vulkan device extension advertisement probe, and a transparent full-screen input-through overlay test (35% window opacity). **No full-screen real-game output**.
- **Tests:** CMake/CTest native interpolation, JNI host smoke test, standalone host Kotlin scheduler test, Gradle/JUnit scheduler tests, structural source validation.
- **CI:** GitHub Actions builds ARM64 debug APK (after dependencies, Android SDK and NDK install); APK is only available if CI succeeds.

## Build debug APK

Requirements: JDK 17, Android SDK platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.9 (or Android Studio with supported Android Gradle toolchain).

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The ZIP contains `gradle/wrapper/gradle-wrapper.properties` but **no wrapper binary/JAR**. Use installed Gradle 8.9 or open in Android Studio. If you want a wrapper, generate it once with `gradle wrapper --gradle-version 8.9` on your development computer and commit its scripts/JAR.

**GitHub Actions:** Push all source files into a dedicated repository, then run `.github/workflows/android-debug.yml`. After a successful job, download artifact `UniversalFrameGen-v0.3-debug`. This source ZIP does not contain a compiled APK; the current execution environment lacks the Android SDK.

## Offline checks (without Android SDK)

```bash
python3 scripts/validate_source.py
cmake -S native-tests -B build/native-tests
cmake --build build/native-tests -j2
ctest --test-dir build/native-tests --output-on-failure
bash scripts/test-jni-host.sh
bash scripts/test-pacing-host.sh   # requires kotlinc + JDK
```

## On-device test protocol

1. **FG Lab:** compare a 30 Hz synthetic sequence against a 60 Hz sequence with interpolated midpoints; `Requested frame updates/s` is not measured display FPS.
2. **Device Diagnostics:** run Native Vulkan probe to read device names and hardware buffer extension advertisement (capability **not** functional import verification).
3. **Overlay touch:** grant permission, enable fullscreen transparent touch test and check that controls respond in a benign game. This doesn't mean opaque full-screen FG is possible.
4. **Capture:** consent to a new projection session; use GPU PRIVATE buffer mode or standard RGBA diagnostic; check callback counts and stop from notification. Android 14+ requires a new user consent per session.
5. Record errors, rotation/resizing, capture revoke, crashes and heat; see `docs/DEVICE_TEST_REPORT_TEMPLATE.md`.

## Honest limitations

- **30→60 FPS is demonstrated only with precomputed synthetic source images**. No arbitrary game FPS increase, Vulkan interpolation, live output surface or working fullscreen FG exists.
- The full-screen overlay is intentionally translucent (alpha=.35) to investigate touch-through limits on Android 12+; fully opaque overlays and secure games may not work without privileged APIs.
- Neither native compute time nor Choreographer callbacks prove capture-to-display latency or SurfaceFlinger present FPS.
- Source was validated on Linux for portable Kotlin/C++ tests. **Android Gradle compilation, device tests and APK output are unverified in this runtime**.
- Capture does not store or transmit game frames or use Accessibility injection.

See `docs/PHASE4_IMPLEMENTATION.md` for exact feature scope and device test steps.
