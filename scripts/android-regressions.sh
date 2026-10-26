#!/usr/bin/env bash
# Always collect screenshots, even when an instrumentation assertion fails.
set +e
bash gradlew connectedDebugAndroidTest --console=plain
result=$?
mkdir -p app/build/usability-screenshots
adb pull /sdcard/Android/data/com.zhuquan.tijian/files/usability/. app/build/usability-screenshots/ || true
exit "$result"
