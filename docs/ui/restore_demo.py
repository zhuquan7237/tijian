"""Restore demo data on the emulator: install debug build, seed AI settings,
import the bundled sample through the mock model, then reinstall release."""
import re
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"
PKG = "com.zhuquan.tijian"
DB_LOCAL = Path("build/demo.db")


def adb(*a, timeout=180):
    return subprocess.run([ADB, *a], capture_output=True, timeout=timeout)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "replace")
    raw = re.sub(r'standalone="yes" \?', 'standalone="yes"?', raw)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"-?\d+", n.get("bounds", ""))
        if len(b) == 4:
            out.append(dict(text=n.get("text") or "", desc=n.get("content-desc") or "",
                            clickable=n.get("clickable") == "true",
                            b=tuple(int(x) for x in b)))
    return out


def tap(label, wait=1.6, min_y=-1):
    ns = nodes()
    hits = [n for n in ns if n["text"] == label or n["desc"] == label]
    if min_y >= 0:
        hits = [n for n in hits if n["b"][1] >= min_y] or hits
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    if not hits:
        print("!! not found", label)
        return False
    x = (hits[0]["b"][0] + hits[0]["b"][2]) // 2
    y = (hits[0]["b"][1] + hits[0]["b"][3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    print("tap", label)
    return True


def seed_settings():
    raw = adb("exec-out", "run-as", PKG, "cat", "databases/tijian.db").stdout
    if not raw or len(raw) < 1000:
        print("db pull failed", len(raw))
        return False
    DB_LOCAL.write_bytes(raw)
    con = sqlite3.connect(DB_LOCAL)
    for k, v in {
        "ai.baseUrl": "http://10.0.2.2:9008/v1",
        "ai.apiKey": "mock",
        "ai.model": "mock-vision",
        "ai.protocol": "chat",
        "ai.models": '["mock-vision","mock-fast","mock-reasoning"]',
    }.items():
        con.execute("INSERT OR REPLACE INTO setting(k,v) VALUES(?,?)", (k, v))
    con.commit()
    print("settings rows:", con.execute("SELECT k,v FROM setting").fetchall())
    con.close()
    adb("push", str(DB_LOCAL), "/data/local/tmp/demo.db")
    adb("shell", f"run-as {PKG} sh -c 'cat /data/local/tmp/demo.db > databases/tijian.db'")
    return True


if __name__ == "__main__":
    print(adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk").stdout.decode("utf-8", "replace").strip())
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    if not seed_settings():
        raise SystemExit("cannot seed settings")

    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)
    tap("导入错题", 1.5)
    tap("导入示例图（调试）", 2.0)
    time.sleep(8)
    # review page: confirm import
    for _ in range(3):
        if tap("确认入库", 3.0):
            break
        time.sleep(2)
    time.sleep(3)
    print(adb("shell", "am", "force-stop", PKG))
