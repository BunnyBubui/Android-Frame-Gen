#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
"$KOTLINC" app/src/main/java/com/universalframegen/pacing/FramePacer.kt tests/kotlin/FramePacerTest.kt -include-runtime -d "$TMP_DIR/pacer-tests.jar"
java -jar "$TMP_DIR/pacer-tests.jar"
