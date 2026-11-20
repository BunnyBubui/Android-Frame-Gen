# Build & validation record — 2026-11-20

Environment: Linux host; Java 21; g++ and CMake available. **Android SDK, Android Gradle plugin dependencies and a device are not available**; outbound network DNS is unavailable.

| Test | Observed result |
|---|---|
| `cmake -S native-tests -B /tmp/fg-native-build` | PASS |
| `cmake --build /tmp/fg-native-build` | PASS (`-Wall -Wextra -Wpedantic -Werror`) |
| `ctest --test-dir /tmp/fg-native-build` | PASS 1/1 (identity, endpoint, sample motion, invalid inputs) |
| `bash scripts/test-jni-host.sh` | PASS — host Java JNI bridge and validation |
| `python3 scripts/validate_source.py` | PASS — XML, permissions and source structural assertions |
| `bash scripts/build-debug.sh` | NOT RUN — Gradle not installed (no APK produced) |
| On-device screen capture and overlay input | NOT TESTED |

This is a source prototype. The GitHub workflow contains all instructions to build the Android APK on an internet-enabled runner; its success remains **unverified** until run.
