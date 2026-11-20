#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}" 
BUILD="${TMPDIR:-/tmp}/universal-framegen-jni-host"
mkdir -p "$BUILD/classes"
c++ -std=c++17 -O2 -fPIC -shared \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$ROOT/app/src/main/cpp/interpolation_core.cpp" \
  "$ROOT/app/src/main/cpp/framegen_jni.cpp" \
  -o "$BUILD/libframegen_native.so"
find "$ROOT/native-tests/jni-smoke" -name '*.java' -print0 | xargs -0 javac -d "$BUILD/classes"
java -Djava.library.path="$BUILD" -cp "$BUILD/classes" Run
