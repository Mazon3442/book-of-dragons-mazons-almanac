"""Turn the pixelized decoration pieces into the textures the sketchbook screen draws.

    python tools/build_decor_assets.py

Paint dabs: each chosen dab shape is split by brightness into five bands and written as one strip (band 0 darkest,
band 4 lightest). The screen tints every band with a colour from the dragon's palette, so one dab shape can show any
coat. Doodles are copied as they are.
"""
import pathlib
import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "reference/hiccups-sketchbook/decor"
OUT = ROOT / "src/main/resources/assets/bodaddon/textures/gui/sketchbook/decor"
OUT.mkdir(parents=True, exist_ok=True)

DABS = ["swatch_00", "swatch_11", "swatch_22", "swatch_33"]   # four different outlines
BANDS = 5
W, H = 18, 11

for n, name in enumerate(DABS):
    img = Image.open(SRC / "swatches_18" / f"{name}.png").convert("RGBA")
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    canvas.paste(img, ((W - img.width) // 2, (H - img.height) // 2))
    px = np.array(canvas)
    solid = px[:, :, 3] > 0
    lum = px[:, :, :3].astype(float) @ np.array([0.299, 0.587, 0.114])
    edges = np.quantile(lum[solid], np.linspace(0, 1, BANDS + 1)[1:-1])
    band = np.digitize(lum, edges)
    atlas = np.zeros((H, W * BANDS, 4), dtype=np.uint8)
    for b in range(BANDS):
        m = solid & (band == b)
        atlas[:, b * W:(b + 1) * W][m] = (255, 255, 255, 255)
    Image.fromarray(atlas).save(OUT / f"dab_{n}.png")

# Doodles are the user's hand-drawn sheet pieces cut by tools/split_sheet.py and scaled to 72% by tools/shrink_doodles.py.
for n in range(1, 10):
    img = Image.open(SRC / "small" / f"doodle_{n}.png")
    img.save(OUT / f"doodle_{n}.png")
    print(f"doodle_{n}.png", img.size)
