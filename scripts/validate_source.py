#!/usr/bin/env python3
"""Offline structural checks, not an Android build or device test."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

root = Path(__file__).resolve().parents[1]
manifest = root / 'app/src/main/AndroidManifest.xml'
ET.parse(manifest)
ET.parse(root / 'app/src/main/res/values/strings.xml')
text = manifest.read_text()
service = (root / 'app/src/main/java/com/universalframegen/capture/FrameCaptureService.kt').read_text()
ui = (root / 'app/src/main/java/com/universalframegen/ui/FrameGenScreen.kt').read_text()
assert 'FOREGROUND_SERVICE_MEDIA_PROJECTION' in text
assert 'android:foregroundServiceType="mediaProjection"' in text
assert 'android:exported="false"' in text
assert 'createScreenCaptureIntent()' in (root / 'app/src/main/java/com/universalframegen/MainActivity.kt').read_text()
assert 'registerCallback(callback, worker)' in service
assert service.index('registerCallback(callback, worker)') < service.index('createVirtualDisplay(')
assert 'image.close()' in service
assert 'STOP_CAPTURE' in service
assert 'GPU buffer probe' in ui
assert 'Page.LAB' in ui
assert 'InterpolationLab()' in ui
assert 'EXTRA_GPU_MODE' in service
assert 'ImageFormat.PRIVATE' in service
assert 'HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE' in service
assert 'image.hardwareBuffer' in service
assert 'interpolateNative' in (root / 'app/src/main/java/com/universalframegen/interpolation/NativeInterpolator.kt').read_text()
assert 'add_library(framegen_native SHARED' in (root / 'app/src/main/cpp/CMakeLists.txt').read_text()
assert 'native-tests' in (root / '.github/workflows/android-debug.yml').read_text()
assert 'captureCallbackFps' in ui
assert 'QUERY_ALL_PACKAGES' not in text
pacing = (root / 'app/src/main/java/com/universalframegen/pacing/FramePacer.kt').read_text()
playback = (root / 'app/src/main/java/com/universalframegen/pacing/RealtimePlaybackLab.kt').read_text()
overlay = (root / 'app/src/main/java/com/universalframegen/overlay/OverlayProbe.kt').read_text()
assert 'fun onVsync(frameTimeNs: Long)' in pacing
assert 'Choreographer.FrameCallback' in playback
assert 'NativeInterpolator.interpolate' in playback
assert 'FLAG_NOT_TOUCHABLE' in overlay and '0.35f' in overlay
assert 'NativeVulkanProbe.report()' in ui
assert 'vulkan_probe.cpp' in (root / 'app/src/main/cpp/CMakeLists.txt').read_text()
assert (root / 'app/src/test/java/com/universalframegen/pacing/FramePacerUnitTest.kt').exists()
assert ':app:testDebugUnitTest' in (root / '.github/workflows/android-debug.yml').read_text()
assert (root / '.github/workflows/android-debug.yml').exists()
print('PASS: XML, consent/FGS lifecycle, GPU buffer probe, phase-4 pacing, Vulkan probe, JNI and CMake wiring present.')
print('NOTE: This check does not compile Kotlin, run Gradle, or validate Android runtime behavior.')
