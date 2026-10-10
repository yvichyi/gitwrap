#!/usr/bin/env bash
set -uo pipefail

mkdir -p android/test-artifacts
result=0

echo 'Running Android instrumentation tests on emulator...'
gradle -p android :app:connectedDebugAndroidTest --no-daemon --stacktrace || result=$?

echo 'Capturing a fresh launch of the gallery for visual review...'
adb shell am force-stop io.github.yvichyi.sciencegallery || true
adb shell am start -W -n io.github.yvichyi.sciencegallery/.MainActivity || true
sleep 10
adb exec-out screencap -p > android/test-artifacts/gallery-home.png || true
adb shell uiautomator dump /sdcard/gallery-hierarchy.xml || true
adb pull /sdcard/gallery-hierarchy.xml android/test-artifacts/gallery-hierarchy.xml || true
adb logcat -d -v time > android/test-artifacts/logcat.txt || true
echo "Instrumentation test exit status: $result"
exit "$result"
