# Phase 4 increment — v0.3.0-alpha (2026-11-20)

## Exactly what was added

### VSync scheduler and visible synthetic playback

- `pacing/FramePacer.kt` is a pure Kotlin deterministic 30→30/60 Hz due-slot scheduler. It maps monotonically increasing timestamps to output slots and suppresses double presentation requests on a 120 Hz panel. Delayed callbacks skip stale slots instead of backlogging.
- `pacing/RealtimePlaybackLab.kt` builds a 30 Hz, 128×72 synthetic animation as 30 source frames plus 30 C++-interpolated midpoint frames, **offline before playback**. It uses Choreographer to request updates at 30 or 60 Hz.
- Displayed `Requested frame updates/s` and `Missed presentation slots` are *scheduler diagnostics*, not SurfaceFlinger present FPS, game FPS, measured capture-to-display latency, or proof that the GPU runs 60 Hz. Compose may coalesce display updates.
- The 60 fps sequence is precomputed on the CPU; this **is not live capture-to-FG** and it does not perform Vulkan compute. CPU utilization while precomputing can be high on weaker devices.

### Native Vulkan device capability probe

- `cpp/vulkan_probe.cpp` loads `libvulkan.so` at runtime using `dlopen` and enumerates Vulkan physical devices and `VK_ANDROID_external_memory_android_hardware_buffer` **extension advertisement**.
- The diagnostic is initiated manually from Device Diagnostics and run off the main thread.
- Extension advertisement is **not proof** of successful Android HardwareBuffer import, compatible memory format, compute pipeline execution, or refresh/present path. Vulkan instance/device creation may fail or report no devices, which should be displayed as a diagnostic rather than crash the app.

### Fullscreen transparent touch overlay test

- The Diagnostics page offers a **single** small or full-screen `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_TOUCHABLE`, and the full-screen test is explicitly 35% window opacity.
- Android 12+ may permit touch pass-through for one SAW overlay at sufficiently low opacity; OEM/game behavior can differ. Neither transparent overlay operation nor the small probe proves that a fully opaque generated-frame output can coexist with working game input.
- To test, allow `Display over other apps`, enable Fullscreen transparent touch test, launch a harmless game, and try taps, swipes, holds and multi-touch. Record whether input and display remain functional. Return to the app to turn off the test. Do not interpret input failures as permission for injection or accessibility automation.

### Tests

- `scripts/test-pacing-host.sh` builds/runs a Kotlin host test using installed `kotlinc`.
- Gradle `:app:testDebugUnitTest` runs JUnit pacing tests in GitHub Actions before assembling debug APK.
- Existing `native-tests` and JNI host smoke tests remain unchanged and must pass.
- Structural script verifies phase-4 source wiring; it is **not** a substitute for Android compilation.

## Current blockers

1. No Android SDK or test phone is present in the execution container: **Android Gradle build, APK installation and on-device touch/latency measurements remain unverified**.
2. No Vulkan Android HardwareBuffer import or GPU compute path is implemented.
3. No live screen-capture pixels are forwarded to the Frame Generation Engine. PRIVATE capture is still a metadata/buffer availability probe.
4. No frame presentation surface or robust touch solution exists for arbitrary games. Even when a translucent overlay passes touch, Android security constraints can prevent an opaque, usable FG output.
5. A real device validation report must be completed before treating Phase 4 as passed.

## Manual test on OPPO Reno15

1. Build Android debug APK via CI; install using `adb install -r app-debug.apk`.
2. Launch app → `FG Lab` → compare `30 Hz` and `60 Hz · x2` for 20 s each. Record requested update rate and skipped slots; capture a screen recording if practical (screen recorder may itself affect performance).
3. Launch app → `Diagnostics` → `Run GPU probe (native Vulkan)`; note device name and extension advertisement.
4. From Diagnostics, grant Overlay permission; enable full-screen touch probe; launch a game and test tap, swipe, hold and multitouch. Be aware the translucent window temporarily obscures the game; turn it off after the test.
5. Start a separate MediaProjection session with the target app and check PRIVATE buffer callback counts. Avoid capturing sensitive apps. Do not interpret callbacks as game FPS.
6. Rotate, cancel capture, stop from the notification, and verify no crash/leak. Record `adb logcat` on errors.

## Platform references

- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/reference/android/view/Choreographer
- https://developer.android.com/ndk/guides/graphics/getting-started
