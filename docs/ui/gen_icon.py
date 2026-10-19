"""Generate 题笺 launcher icons.

Design: an ink-black field, a folded paper slip, two text rules and a cobalt
check mark — "a wrong answer, worked through".
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("app/src/main/res")
INK = (0x14, 0x17, 0x1F, 255)
PAPER = (0xFC, 0xFB, 0xF7, 255)
FOLD = (0xC3, 0xCE, 0xDC, 255)
FOLD_EDGE = (0x93, 0xA2, 0xB6, 255)
RULE_WRONG = (0xC0, 0x4A, 0x53, 255)
RULE = (0xB8, 0xC2, 0xD2, 255)
ACCENT = (0x2F, 0x4B, 0xD8, 255)

# design is authored in a 108x108 viewport, rendered at SS x
SS = 8
BASE = 108


def draw_icon(size: int, round_mask: bool = False) -> Image.Image:
    px = BASE * SS
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # background: full bleed rounded square (mask) — adaptive icon supplies its own bg
    if round_mask:
        d.ellipse((0, 0, px - 1, px - 1), fill=INK)
    else:
        r = int(px * 0.22)
        d.rounded_rectangle((0, 0, px - 1, px - 1), radius=r, fill=INK)

    u = px / BASE

    def s(v):
        return int(round(v * u))

    # paper slip with folded top-right corner
    left, top, right, bottom, rad = 27, 21, 81, 87, 5
    d.rounded_rectangle((s(left), s(top), s(right), s(bottom)), radius=s(rad), fill=PAPER)
    fold = 13
    # cut the corner visually with a triangle of background, then draw the fold
    d.polygon(
        [(s(right - fold), s(top)), (s(right), s(top)), (s(right), s(top + fold))],
        fill=INK,
    )
    d.polygon(
        [(s(right - fold), s(top)), (s(right), s(top + fold)), (s(right - fold), s(top + fold))],
        fill=FOLD,
    )
    d.line([(s(right - fold), s(top)), (s(right), s(top + fold))],
           fill=FOLD_EDGE, width=max(1, s(0.9)))
    # one wrong rule (red) + one correct rule (grey)
    d.rounded_rectangle((s(38), s(38), s(69), s(43.5)), radius=s(2.2), fill=RULE_WRONG)
    d.rounded_rectangle((s(38), s(49), s(61), s(54.5)), radius=s(2.2), fill=RULE)
    # cobalt check, fully inside the paper
    w = s(7.5)
    pts = [(s(41.5), s(67)), (s(51.5), s(76.5)), (s(69), s(58.5))]
    d.line(pts, fill=ACCENT, width=w, joint="curve")
    for x, y in (pts[0], pts[2]):
        rr = w / 2
        d.ellipse((s(x) - rr, s(y) - rr, s(x) + rr, s(y) + rr), fill=ACCENT)

    return img.resize((size, size), Image.LANCZOS)


def main():
    for density, size in {
        "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
    }.items():
        d = OUT / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        draw_icon(size).save(d / "ic_launcher.png")
        draw_icon(size, round_mask=True).save(d / "ic_launcher_round.png")
        print("wrote", d)
    # store / README artwork
    draw_icon(512).save("docs/ui/icon-512.png")
    draw_icon(48).resize((192, 192), Image.NEAREST).save("docs/ui/icon-48-preview.png")
    print("wrote docs/ui/icon-512.png and icon-48-preview.png")


if __name__ == "__main__":
    main()
