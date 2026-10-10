#!/usr/bin/env bash
set -euo pipefail

mkdir -p android/test-artifacts
result=0

echo 'Running Android instrumentation tests on emulator...'
gradle -p android :app:connectedDebugAndroidTest --no-daemon --stacktrace || result=$?

# Gradle often removes the app APK when instrumentation finishes.
# Install it again before taking screenshots or inspecting runtime behavior.
echo 'Reinstalling and launching the real app for screenshot verification...'
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop io.github.yvichyi.sciencegallery || true
adb shell am start -W -n io.github.yvichyi.sciencegallery/.MainActivity |
  tee android/test-artifacts/launch-result.txt
sleep 10

adb shell pidof io.github.yvichyi.sciencegallery |
  tee android/test-artifacts/app-pid.txt

adb shell dumpsys activity activities > android/test-artifacts/activity-state.txt
grep -E 'topResumedActivity|mResumedActivity' android/test-artifacts/activity-state.txt |
  tee android/test-artifacts/foreground-activity.txt

grep -q 'io.github.yvichyi.sciencegallery/.MainActivity' android/test-artifacts/foreground-activity.txt

adb exec-out screencap -p > android/test-artifacts/gallery-home.png
test -s android/test-artifacts/gallery-home.png
adb shell uiautomator dump /sdcard/gallery-hierarchy.xml || true
adb pull /sdcard/gallery-hierarchy.xml android/test-artifacts/gallery-hierarchy.xml || true
adb logcat -d -v time > android/test-artifacts/logcat.txt || true

echo "Instrumentation tests exit status: $result"
exit "$result"
