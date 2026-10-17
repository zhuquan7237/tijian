#!/usr/bin/env bash
# 用法: scripts/release.sh 0.1.0 "更新说明"
set -euo pipefail

VER="${1:?version required, e.g. 0.1.0}"
NOTES="${2:-题笺更新}"
CODE="$(python - "$VER" <<'PY'
import sys
p=[int(x) for x in sys.argv[1].split('.')]
while len(p)<3: p.append(0)
print(p[0]*10000+p[1]*100+p[2])
PY
)"

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
REPO="zhuquan7237/tijian"

echo "==> 版本 $VER (versionCode $CODE)"
python - "$VER" "$CODE" <<'PY'
import re,sys
ver,code=sys.argv[1],sys.argv[2]
p='app/build.gradle.kts'
s=open(p,encoding='utf-8').read()
s=re.sub(r'versionCode = \d+', f'versionCode = {code}', s)
s=re.sub(r'versionName = "[^"]*"', f'versionName = "{ver}"', s)
open(p,'w',encoding='utf-8').write(s)
PY

export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot}"
export ANDROID_HOME="${ANDROID_HOME:-C:/Users/zhuquan/AppData/Local/Android/Sdk}"
./gradlew.bat assembleRelease --console=plain

mkdir -p build
APK="build/tijian-$VER.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c%s "$APK")
URL="https://github.com/$REPO/releases/download/v$VER/tijian-$VER.apk"

python - "$VER" "$CODE" "$URL" "$SHA" "$SIZE" "$NOTES" <<'PY' > build/update.json
import json,sys
ver,code,url,sha,size,notes=sys.argv[1:7]
print(json.dumps({"version":ver,"versionCode":int(code),"apkUrl":url,
  "sha256":sha,"size":int(size),"notes":notes}, ensure_ascii=False, indent=1))
PY

echo "==> APK $APK ($SIZE bytes, sha256 $SHA)"
cat build/update.json

if git rev-parse --git-dir >/dev/null 2>&1 && git remote get-url origin >/dev/null 2>&1; then
  echo "==> 创建 GitHub Release v$VER"
  gh release create "v$VER" "$APK" build/update.json \
    --repo "$REPO" --title "题笺 $VER" --notes "$NOTES" --latest
  echo "==> 校验远端资产"
  gh release view "v$VER" --repo "$REPO" --json assets \
    --jq '.assets[] | "\(.name) \(.size)"'
fi
