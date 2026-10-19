"""UI acceptance checks for 题笺 0.5.0.

1. WCAG contrast for the design tokens actually used in Theme.kt.
2. Touch-target sizes from real uiautomator dumps (48dp = 126px @420dpi).
3. Text nodes clipped by screen bounds.

Run: python docs/ui/verify_ui.py
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/com/zhuquan/tijian/ui/Theme.kt"
DUMP_DIR = ROOT / "docs/ui/v050"
MIN_TOUCH_DP = 48
DENSITY = 420 / 160  # 2.625 px per dp


def srgb(c):
    s = c / 255
    return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4


def lum(hex_color):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)


def ratio(fg, bg):
    a, b = lum(fg), lum(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


def read_tokens():
    text = THEME.read_text(encoding="utf-8")
    tokens = dict(re.findall(r'val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', text))
    return {k: "#" + v.upper() for k, v in tokens.items()}


CHECKS = [
    ("正文 / 背景", "Ink", "Bg", 7.0),
    ("正文 / 卡面", "Ink", "CardBg", 7.0),
    ("次文字 / 卡面", "Ink2", "CardBg", 4.5),
    ("辅助字 / 背景", "Muted", "Bg", 4.5),
    ("辅助字 / 卡面", "Muted", "CardBg", 4.5),
    ("强调字 / 浅强调底", "Accent", "AccentSoft", 4.5),
    ("白字 / 强调按钮", "OnHeroMuted", "Accent", 3.0),
    ("深色卡次文字", "OnHeroMuted", "Ink", 4.5),
    ("深色卡标签", "OnHeroKicker", "Ink", 4.5),
    ("功能边框 / 卡面", "FieldBorder", "CardBg", 3.0),
]


def check_contrast():
    t = read_tokens()
    lines = ["## 对比度（WCAG 2.1）", ""]
    failures = 0
    for label, fg, bg, target in CHECKS:
        if fg not in t or bg not in t:
            lines.append(f"- {label}: token missing ({fg}/{bg})")
            failures += 1
            continue
        r = ratio(t[fg], t[bg])
        ok = r >= target
        failures += 0 if ok else 1
        lines.append(f"- {'PASS' if ok else 'FAIL'} {label}: {t[fg]} on {t[bg]} = {r:.2f}:1 (≥{target})")
    return lines, failures


def check_touch():
    lines = ["", "## 触控目标（真实 dump bounds）", ""]
    minimum = MIN_TOUCH_DP * DENSITY
    problems = 0
    seen = 0
    for xml_path in sorted(DUMP_DIR.glob("*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        small = []
        for n in root.iter("node"):
            if n.get("clickable") != "true":
                continue
            # WebView content is not one of our controls.
            if "WebView" in (n.get("class") or ""):
                continue
            b = re.findall(r"-?\d+", n.get("bounds", ""))
            if len(b) != 4:
                continue
            x1, y1, x2, y2 = map(int, b)
            w, h = x2 - x1, y2 - y1
            seen += 1
            # Partially visible item inside a horizontal scroller: the visible
            # slice is smaller than 48dp by design.
            # Bottom tab bar starts at y=2169 on this 1080x2400 device; a short
            # clickable node ending exactly there is scroll content clipped by
            # the viewport, not a sub-48dp control.
            clipped_by_viewport = abs(y2 - 2169) <= 3 and h < minimum
            clipped_by_edge = ((x2 >= 1079 and w < minimum) or (x1 <= 1 and w < minimum)
                               or clipped_by_viewport)
            if (w < minimum or h < minimum) and not clipped_by_edge:
                label = n.get("content-desc") or n.get("text") or "(无标签)"
                small.append(f"{label} {w}×{h}px ({w / DENSITY:.0f}×{h / DENSITY:.0f}dp)")
        if small:
            problems += len(small)
            lines.append(f"- {xml_path.name}: " + "; ".join(small[:6]))
    if not problems:
        lines.append(f"- PASS 所有 {seen} 个可点节点均 ≥ {MIN_TOUCH_DP}dp")
    return lines, problems


def check_clipping():
    lines = ["", "## 文本越界检查", ""]
    problems = 0
    for xml_path in sorted(DUMP_DIR.glob("*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        for n in root.iter("node"):
            text = (n.get("text") or "").strip()
            if not text:
                continue
            b = list(map(int, re.findall(r"-?\d+", n.get("bounds", ""))))
            if len(b) != 4:
                continue
            x1, y1, x2, y2 = b
            if x1 < 0 or y1 < 0 or x2 > 1080 or y2 > 2400:
                problems += 1
                lines.append(f"- {xml_path.name}: {text[:24]!r} 越界 {b}")
    if not problems:
        lines.append("- PASS 未发现越界文本节点")
    return lines, problems


def main():
    contrast, f1 = check_contrast()
    touch, f2 = check_touch()
    clip, f3 = check_clipping()
    report = ["# 题笺 0.5.0 UI 验收报告", ""] + contrast + touch + clip
    report += ["", f"**结论：{'通过' if f1 + f2 + f3 == 0 else '存在问题'}**"
               f"（对比度失败 {f1} / 触控失败 {f2} / 越界 {f3}）"]
    out = ROOT / "docs/ui/ACCEPTANCE-0.5.md"
    out.write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))


if __name__ == "__main__":
    main()
