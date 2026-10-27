"""Generate 题笺 launcher icons from the master artwork.

Master: docs/ui/icon-source.png — one flat-blue mark (two mirrored page shapes
sharing a split spine) on pure white. The white matte is turned into alpha via
the red channel, so the mark can sit on any background without halos.

Outputs:
  app/src/main/res/drawable-nodpi/ic_launcher_fg.png    adaptive foreground (transparent)
  app/src/main/res/drawable-nodpi/ic_launcher_mono.png  Material You monochrome layer
  app/src/main/res/mipmap-*/ic_launcher[_round].png     legacy icons (white tile)
  docs/ui/icon-512.png                                  README / store artwork
  docs/ui/icon-48-preview.png                           small-size sanity check
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/ui/icon-source.png"
OUT = ROOT / "app/src/main/res"

BLUE = (0x00, 0x6E, 0xFD)
WHITE = (0xFF, 0xFF, 0xFF)

BASE = 108           # adaptive icon viewport, dp
SS = 8               # supersampling for the tile shapes
FG_GLYPH_DP = 61     # glyph width: stays inside the 66dp adaptive safe zone
TILE_GLYPH_DP = 68   # legacy icons are not masked, so the mark can breathe


def load_glyph() -> Image.Image:
    """Return the mark as RGBA (flat blue, alpha taken from the white matte)."""
    rgb = np.asarray(Image.open(SRC).convert("RGB"), dtype=np.float32)
    alpha = np.clip(255.0 - rgb[..., 0], 0.0, 255.0)  # blue has R=0, paper R=255
    ys, xs = np.where(alpha > 6)
    alpha = alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    glyph = Image.new("RGBA", (alpha.shape[1], alpha.shape[0]), BLUE + (0,))
    glyph.putalpha(Image.fromarray(alpha.astype(np.uint8), "L"))
    return glyph


def place(canvas: Image.Image, glyph: Image.Image, width_dp: float, color=None) -> Image.Image:
    """Center the glyph on a square canvas, sized by its width in dp units."""
    px = round(canvas.width / BASE * width_dp)
    h = round(px * glyph.height / glyph.width)
    g = glyph.resize((px, h), Image.LANCZOS)
    if color is not None:
        arr = np.array(g)
        arr[..., :3] = color
        g = Image.fromarray(arr)
    canvas.alpha_composite(g, ((canvas.width - px) // 2, (canvas.height - h) // 2))
    return canvas


def tile(size: int, radius_ratio, width_dp: float) -> Image.Image:
    """Legacy icon: white tile (rounded square or circle) plus the blue mark."""
    px = BASE * SS
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if radius_ratio is None:
        d.ellipse((0, 0, px - 1, px - 1), fill=WHITE)
    else:
        d.rounded_rectangle((0, 0, px - 1, px - 1), radius=int(px * radius_ratio), fill=WHITE)
    place(img, load_glyph(), width_dp)
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    glyph = load_glyph()

    # adaptive foreground: transparent, mark inside the safe zone
    fg_px = BASE * 4
    fg = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
    place(fg, glyph, FG_GLYPH_DP).save(OUT / "drawable-nodpi/ic_launcher_fg.png")

    # monochrome layer: same silhouette in flat white, tinted by the launcher
    mono = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
    place(mono, glyph, FG_GLYPH_DP, color=WHITE).save(OUT / "drawable-nodpi/ic_launcher_mono.png")

    for density, size in {
        "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
    }.items():
        d = OUT / f"mipmap-{density}"
        d.mkdir(parents=True, exist_ok=True)
        tile(size, 0.22, TILE_GLYPH_DP).save(d / "ic_launcher.png")
        tile(size, None, TILE_GLYPH_DP - 4).save(d / "ic_launcher_round.png")
        print("wrote", d)

    tile(512, 0.22, TILE_GLYPH_DP).save(ROOT / "docs/ui/icon-512.png")
    tile(48, 0.22, TILE_GLYPH_DP).resize((192, 192), Image.NEAREST).save(
        ROOT / "docs/ui/icon-48-preview.png"
    )
    print("wrote docs/ui/icon-512.png and icon-48-preview.png")


if __name__ == "__main__":
    main()
