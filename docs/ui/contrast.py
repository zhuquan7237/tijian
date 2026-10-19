#!/usr/bin/env python3
"""Calculate WCAG 2.1 contrast ratios for the candidate palettes.

Run: python docs/ui/contrast.py
"""
from __future__ import annotations

def srgb(c: int) -> float:
    s = c / 255
    return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4

def lum(hex_color: str) -> float:
    h = hex_color.lstrip('#')
    r, g, b = (int(h[i:i+2], 16) for i in (0, 2, 4))
    return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)

def ratio(fg: str, bg: str) -> float:
    a, b = lum(fg), lum(bg)
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)

A = dict(bg="#F5F6F8", card="#FFFFFF", ink="#12161C", ink2="#37414D",
         muted="#5A6675", line="#DCE2EA", accent="#2F4BD8",
         accent_soft="#EDF0FE", green="#2F7D63", hero="#12161C",
         hero_meta="#C3CBDA", hero_kicker="#AEB8C8", accent_on="#FFFFFF")
B = dict(bg="#FAF6EC", card="#FFFDF8", ink="#24211C", ink2="#4A4235",
         muted="#6A6153", line="#E8DFCE", accent="#B4553A",
         accent_soft="#F2E8D8", green="#4F7A5A", hero="#2A2620",
         hero_meta="#D9CFBE", hero_kicker="#C9BEAA", accent_on="#FFF9EE")
C = dict(bg="#0E1218", card="#171E29", ink="#E9EEF6", ink2="#C6D1E0",
         muted="#93A0B4", line="#243041", accent="#35D0A5",
         accent_soft="#1E2A38", green="#35D0A5", hero="#171E29",
         hero_meta="#9AA9BE", hero_kicker="#8FA0B8", accent_on="#08211A")

CHECKS = [
    ("body / background", "ink", "bg", 7.0),
    ("body / card", "ink", "card", 7.0),
    ("secondary / card", "ink2", "card", 4.5),
    ("muted / background", "muted", "bg", 4.5),
    ("muted / card", "muted", "card", 4.5),
    ("accent text / accent-soft", "accent", "accent_soft", 4.5),
    ("white on accent button", "accent_on", "accent", 4.5),
    ("accent button / background", "accent", "bg", 3.0),
    ("hero meta / hero", "hero_meta", "hero", 4.5),
    ("hero kicker / hero", "hero_kicker", "hero", 4.5),
    ("line / card (non-text 3:1)", "line", "card", 1.4),
]

def report(name, p):
    print(f"\n=== {name} ===")
    total = 0
    for label, fg, bg, target in CHECKS:
        r = ratio(p[fg], p[bg])
        ok = "PASS" if r >= target else "FAIL"
        if target > 1.5:
            total += min(r / target, 1.6)
        print(f"{label:32} {p[fg]} on {p[bg]:9} {r:5.2f}:1  target {target:4.1f}  {ok}")
    return total

if __name__ == "__main__":
    scores = {}
    for name, p in (("A 墨白 Editorial", A), ("B 暖纸 Scholar", B), ("C 夜蓝 Focus", C)):
        scores[name] = report(name, p)
    print("\n=== normalized contrast score (higher is better) ===")
    for k, v in sorted(scores.items(), key=lambda kv: -kv[1]):
        print(f"{k:22} {v:5.2f}")
