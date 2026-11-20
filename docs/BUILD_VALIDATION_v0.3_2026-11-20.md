# Validation record — v0.3.0-alpha — 2026-11-20

Executed in the current Linux source working environment:

| Check | Status | Evidence |
| --- | --- | --- |
| `python3 scripts/validate_source.py` | PASS | XML parse + structural assertions for capture, pacing, native Vulkan probe and overlay |
| `bash scripts/test-pacing-host.sh` | PASS | `kotlinc` compiled pure Kotlin scheduler + host assertions |
| `cmake -S native-tests -B build/native-tests; cmake --build ...; ctest ...` | PASS | 1 C++ interpolation test target passed |
| `bash scripts/test-jni-host.sh` | PASS | Java/JNI bridge host smoke test |
| Android XML parse | PASS | Manifest and strings.xml |
| Android Gradle compile | NOT RUN | Android SDK / Gradle unavailable |
| Native Vulkan Android NDK build | NOT RUN | NDK/Vulkan headers unavailable here |
| Android JVM JUnit Gradle tests | NOT RUN | Android SDK / Gradle unavailable here; CI configured |
| Debug APK installation | NOT RUN | No connected Android device / APK |
| Vulkan/AHardwareBuffer real import | NOT IMPLEMENTED | Extension enumeration probe only |
| Fullscreen capture-to-FG output | NOT IMPLEMENTED | Synthetic playback only |
| Game touch under overlay | NOT RUN | Needs real device & target game |

This record makes **no claim** of 30→60 FPS improvement in a third-party game.
