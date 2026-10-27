#!/usr/bin/env bash
# Keep emulator diagnostics even if instrumentation cannot start.
set +e
mkdir -p app/build/usability-screenshots app/build/android-diagnostics
adb shell input keyevent 82
adb shell wm dismiss-keyguard
adb logcat -c
adb logcat -v threadtime > app/build/android-diagnostics/logcat.txt 2>&1 &
logger=$!
bash gradlew connectedDebugAndroidTest --console=plain --max-workers=2
result=$?
kill "$logger" 2>/dev/null || true
adb shell dumpsys activity processes > app/build/android-diagnostics/processes.txt
adb pull /sdcard/Android/data/com.zhuquan.tijian/files/usability/. app/build/usability-screenshots/ || true
if [ "$result" -ne 0 ]; then
  rg -n -A 18 'FATAL EXCEPTION|AndroidRuntime|ANR in|am_crash' app/build/android-diagnostics/logcat.txt | tail -160 || true
fi
exit "$result"
