# Implementation specification — Universal FrameGen

## Product goals

- Android non-root feasibility of capture → interpolation → screen output, not changes to game simulation FPS.
- Keep input usable, minimize measured end-to-end lag, never mislabel FPS.
- Provide compatibility results by OS, GPU, game and capture mode.

## Pipeline (planned)

```
Target app (real frames)
  └─ Android SurfaceFlinger / MediaProjection [public API]
       └─ VirtualDisplay -> GPU buffer (phase 2)
            └─ temporal queue of timestamped frames
                 └─ motion estimation / optical flow (phase 3)
                      └─ occlusion/disocclusion confidence mask (phase 3)
                           └─ interpolated textures (phase 3)
                                └─ compositor with VSync/frame pacing (phase 4)
                                     └─ visible output surface (phase 4)
                                          └─ touch compatibility validation
```

## Phase assignment

**0 — API feasibility / benchmark.** Measure captured callback cadence, consent lifecycle, overlay input behavior, self-capture. Device test before any quality claims.

**1 — app shell.** Compose UI, launcher catalog, game profiles, local diagnostic reporting. Current implementation: UI/catalog, no persistent per-game profiles yet.

**2 — capture and GPU.** Buffer lifecycle, Android Hardware Buffer/Vulkan extensions and timestamp queue. Avoid RGB readbacks to CPU. Benchmark capture->GPU handoff.

**3 — interpolation.** Build baseline no-ML optical flow using compute shaders, motion-compensated warping; add anti-ghosting, disocclusion detection, frame consistency score. Start x2 at 720p. Implement safe fallback to real frames.

**4 — output/input.** Full-screen window strategy; investigate secure handling of touches with public APIs only. If input-to-game is blocked, mark no-root solution as unsupported on that device/game; do not route through unauthorized injection/accessibility automation.

**5 — performance.** Adaptive interpolation resolution, double/triple buffering only where justified, dynamic frame pacing, battery & thermal budget, crash recovery, telemetry.

**6 — validation/release.** Matrix of devices and 2D/3D games; automated builds, real device tests, latency + energy charts, debug/release signing.

## Acceptance metrics (not measurements)

- Phase 0/1: no permission reuse, clean stop on Android 14–16, no capture/pixels sent off-device.
- Capture stability: >= 20 minutes; session revoke and orientation change must not crash.
- Input: touch, hold, swipe and multi-touch still function in target game.
- For phase 3 x2: interpolated output >= 95% intended presentation cadence in repeatable scene with frame-time logs; render doesn't exceed GPU budget; visibly fewer artifacts than baseline duplicate frame strategy.
- Added latency: benchmark capture→display independently from game simulation. Never assert low latency from shader cost alone.

## Engineering risks requiring early decisions

1. **Touch occlusion:** TYPE_APPLICATION_OVERLAY is not a fully trusted compositor and cannot always pass gestures through to underlying game.
2. **Capture recursion:** selecting entire display may capture own overlay; choose app-only when possible and test for recursion.
3. **Secure content:** some apps use FLAG_SECURE; expect black frame, do not bypass.
4. **Refresh rate:** a 120Hz panel does not make a 30Hz source truly 120Hz; cadence must be measured.
5. **Thermal:** high GPU processing costs may slow the game itself, cancelling benefit.

## Safe roll-forward strategy

Build each phase on a feature branch with an explicit kill switch; don't merge new output path unless capture stop/restart, game input and crash tests pass on actual hardware. The app remains a diagnostics utility until Phase 4 validation succeeds.

## Implementation update v0.2.0-alpha (2026-11-20)

- Phase 2 **partial:** PRIVATE GPU-sampleable HardwareBuffer acquisition + metadata probe; **no Vulkan import**.
- Phase 3 **software reference:** C++ CPU block motion interpolation with JNI and synthetic UI preview; **not real-time game framegen**.
- Phase 0–1 Android build/device gates remain **unverified**, as no SDK or test device was accessible in this environment.
- Next gate: compile using CI, install device APK, measure buffer arrival and touch compatibility before investing in Vulkan compute/overlay output.

## Implementation update v0.3.0-alpha (2026-11-20)

- Phase 4 **partial:** `Choreographer` presentation-slot pacing on precomputed 30 Hz synthetic source with C++ midpoints, with diagnostic callback/scheduler metrics; this does not measure compositor FPS.
- Phase 4 **partial:** 35%-opacity fullscreen non-touch overlay for manual game-input compatibility check; opaque game overlay remains unproven.
- GPU **capability** probe: native Vulkan loader enumerates device names and AHB extension advertisements; no buffer import/compute yet.
- New pure Kotlin host and Gradle/JUnit tests for due-slot logic; CI builds after unit tests.
- Remaining release blockers: actual Android Gradle compile, device tests, Vulkan/AHB import, live capture-to-interpolate path and an input-compatible presentation surface.
