#!/usr/bin/env bash
set -euo pipefail
if [ -x ./gradlew ]; then
  ./gradlew :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle :app:assembleDebug
else
  echo 'Gradle not installed. Open this project in Android Studio, or push to GitHub to run the workflow.' >&2
  exit 1
fi
