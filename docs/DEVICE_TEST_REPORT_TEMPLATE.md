# Device test report — template

- Device model:
- Android version / API / ColorOS build:
- SoC / GPU:
- Vulkan version / feature level:
- Refresh modes:
- Capture consent: [ ] granted [ ] cancelled [ ] revoked
- Capture mode: [ ] single app [ ] full screen
- Captured callbacks after 30s:
- Any secure/blank frames:
- Capture recursion/self-overlay:
- Did touch work under small overlay? Tap: [ ] Drag: [ ] Multi-touch: [ ]
- Orientation and app-switch test: [ ] Passed [ ] Failed
- 20-minute stability test: [ ] Passed [ ] Failed
- Crash logcat / screenshots (no personal content):
- Notes:

## v0.2 additions
- FG Lab native library loaded: [ ] yes [ ] no
- FG Lab slider from 0 to 100%: [ ] passed [ ] failed
- CPU time per 192x108 frame: ___ ms
- GPU PRIVATE mode selected: [ ] yes [ ] no
- Reported CAPTURE FORMAT: ___
- Hardware buffers counted after 30s: ___
- Timestamp issues: ___
- RGBA fallback or error: ___
- No extra `createVirtualDisplay()` on a reused token: [ ] confirmed

## v0.3 Phase 4 additions
- App built with Gradle testDebugUnitTest: [ ] passed [ ] failed
- FG Lab 30 Hz requested updates/s: ___; missed slots: ___
- FG Lab 60 Hz requested updates/s: ___; missed slots: ___
- Display actually verified with external tool? [ ] yes [ ] no; method: ___
- Vulkan probe output (paste text): ___
- AHB external memory extension advertised: [ ] yes [ ] no [ ] probe failed
- Fullscreen touch overlay visible at 35% alpha: [ ] yes [ ] no
- Game taps under fullscreen overlay: [ ] works [ ] blocked [ ] intermittent
- Game swipe / hold: [ ] works [ ] blocked [ ] intermittent
- Game multi-touch: [ ] works [ ] blocked [ ] intermittent
- Capturing content recursively (whole-display sharing) observed: [ ] yes [ ] no
- GPU thermal / battery degradation after 20 mins: ___
- IMPORTANT: Fullscreen transparent overlay pass-through != opaque framegen support
