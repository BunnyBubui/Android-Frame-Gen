# Phase 2–3 implementation — 0.2.0 alpha

## Implemented and testable without a device

### Native reference interpolator (Phase 3 **prototype**)

- Portable C++17 `interpolation_core.cpp` (motion estimation via exhaustive 8×8 block luma SAD matching, search radius 8, bi-frame forward splatting with bilinear interpolation, hole fallback crossfade).
- JNI entrypoint `NativeInterpolator.interpolateNative` and Compose `FG Lab` using two **synthetic** frames at 192×108. A slider controls interpolation time `t` from 0.0 to 1.0.
- The native library is built by Android NDK/CMake for `arm64-v8a`.
- Separate host CMake tests verify same-frame equality, endpoint exactness, actual moving image samples, and parameter validation. `ctest` passed on the source build environment. These tests DO NOT imply Android compatibility.
- Native interpolation is run on `Dispatchers.Default` to keep the UI thread responsive. Processing latency displayed by the demo is CPU shader time on synthetic data only.

### GPU buffer capture probe (Phase 2 **partial**)

- Optional `ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 3, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)` per MediaProjection session.
- A received `Image.hardwareBuffer` is **closed immediately** after counting. The `Image` is closed in `finally`. This validates buffer delivery and timestamp cadence but not Vulkan import.
- If PRIVATE `ImageReader` creation fails, falls back to RGBA reader **before** the virtual display is created. The user can inspect the reported mode and warning.
- If virtual display creation itself fails for the PRIVATE surface, capture ends: Android 14+ disallows retrying the same projection token with a second virtual display. Request fresh capture consent in RGBA mode.
- Resize uses `VirtualDisplay.resize()` and `setSurface()` without reusing the one-time `createVirtualDisplay()` permission.

## Not implemented (gates)

- Import of `AHardwareBuffer` into Vulkan external memory, GPU flow and compute shaders, hardware-accelerated interpolation.
- Actual capture frame → native interpolation bridge or real-time feed.
- Presenting generated textures on-screen over other apps, VSync pacing, and full-screen touch passthrough.
- Reliable end-to-end FPS improvements to games. Do **not** represent this prototype as a universal FPS booster.

## Android test protocol

1. CI `Android debug APK` must pass including NDK build; install artifact on an ARM64 Android 12–16 device.
2. Enter `FG Lab`, drag the slider. The checker object should shift smoothly between positions; record processing time. This is a software quality sanity check, not a performance target.
3. Switch to Home, toggle GPU buffer probe. Start a **new** MediaProjection consent session; select a benign game as capture target when offered.
4. Inspect `CAPTURE FORMAT`: it must say `GPU PRIVATE buffer` to claim GPU buffer probe enabled. `GPU BUFFERS` should become >0 when data is available. If fallback/error appears, capture did not prove GPU mode.
5. Stop from notification and restart with RGBA probe. Rotate, lock screen, revoke consent, verify frame counters stop and session releases resources.
6. Verify no private pixels are logged or retained. Do not share banking/password apps.
7. Small overlay touch test remains separate: it cannot establish full-screen FrameGen compatibility.

## Blockers and next development work

1. Device + driver feasibility: test PRIVATE buffer arrival and input/overlay occlusion on OPPO Reno15 in particular.
2. Add native Vulkan extension enumeration, external-memory import, and GPU shader path. `USAGE_GPU_SAMPLED_IMAGE` describes intent, not successful import.
3. Add isolated captured-content preview to validate capture→texture import, being mindful of capture recursion.
4. Add motion field quality tests and temporal scheduling, then only later an output compositor.

## Android API references

- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/reference/android/media/ImageReader
- https://developer.android.com/reference/android/hardware/HardwareBuffer
