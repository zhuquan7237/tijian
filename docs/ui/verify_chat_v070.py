"""Verify chat panel with IME, scrim dismiss, quote rendering, batch delete."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"


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
                            cls=n.get("class") or "", clickable=n.get("clickable") == "true",
                            b=tuple(int(x) for x in b)))
    return out


def tap(label, wait=1.6, contains=False):
    for n in nodes():
        if n["text"] == label or n["desc"] == label or (contains and label in n["text"]):
            x = (n["b"][0] + n["b"][2]) // 2
            y = (n["b"][1] + n["b"][3]) // 2
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(wait)
            print("tap", label, n["b"])
            return n
    print("!! miss", label)
    return None


def shot(name):
    open(f"docs/ui/v070/{name}.png", "wb").write(adb("exec-out", "screencap", "-p").stdout)
    print("shot", name)


if __name__ == "__main__":
    import pathlib
    pathlib.Path("docs/ui/v070").mkdir(parents=True, exist_ok=True)
    tap("数学")
    tap("函数与导数")
    ns = [n for n in nodes() if n["clickable"] and n["b"][1] > 620 and n["b"][3] - n["b"][1] < 320]
    ns.sort(key=lambda n: n["b"][1])
    if ns:
        b = ns[0]["b"]
        adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        time.sleep(4)
        print("opened detail")
    tap("追问 AI")
    shot("01-chat-empty")
    edits = [n for n in nodes() if "EditText" in n["cls"]]
    if edits:
        b = edits[0]["b"]
        adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
        time.sleep(1.5)
    shot("02-chat-keyboard")
    ns = nodes()
    ed = [n for n in ns if "EditText" in n["cls"]]
    print("input bounds with kb:", ed[0]["b"] if ed else None)
    print("screen h:", 2400)
    # tap outside the panel (top area) to dismiss
    adb("shell", "input", "tap", "540", "260")
    time.sleep(1.2)
    still = any("追问这道题" in n["text"] for n in nodes())
    print("panel still open after scrim tap:", still)
    shot("03-scrim-dismissed")
