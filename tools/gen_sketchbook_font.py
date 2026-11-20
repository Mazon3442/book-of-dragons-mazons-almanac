"""Build the sketchbook bitmap font from Kalam (SIL OFL, tools/fonts/Kalam-OFL.txt).

Draws every glyph big, area-averages down to the pixel grid and snaps to three tones (ink, half ink, none) so the
text sits on the same pixel grid as the book art. Writes the glyph atlas and font definition, and a preview.

    python tools/gen_sketchbook_font.py
"""
import json
import pathlib
from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
TTF = ROOT / "tools/fonts/Kalam-Regular.ttf"
OUT = ROOT / "src/main/resources/assets/bodaddon"
EM = 11            # pixel size of the font
SS = 8             # supersampling
FULL, HALF = 140, 50   # coverage (of 255) needed for full ink / half ink
HALF_ALPHA = 120
SPACING = 0.0      # extra pixels added to every advance (Minecraft adds 1 itself)
COLS = 16
CHARS = [chr(c) for c in range(33, 127)]

font = ImageFont.truetype(str(TTF), EM * SS)


def render(ch):
    """Return (alpha image at native size, ink bbox) with the baseline on a fixed row."""
    w, h = EM * 2 * SS, EM * 3 * SS
    big = Image.new("L", (w, h), 0)
    base = EM * 2 * SS
    ImageDraw.Draw(big).text((EM * SS, base), ch, font=font, fill=255, anchor="ls")
    small = big.resize((w // SS, h // SS), Image.BOX)
    return small.point(lambda v: 255 if v >= FULL else (HALF_ALPHA if v >= HALF else 0)), EM * 2


glyphs = {}
NAT = {}
top = bottom = 0
for ch in CHARS:
    img, base = render(ch)
    box = img.getbbox()
    if not box:
        continue
    glyphs[ch] = (img, base, box)
    NAT[ch] = font.getlength(ch) / SS
    top = max(top, base - box[1])
    bottom = max(bottom, box[3] - base)

ORIGIN = EM   # x of the font origin inside the rendered image
def left(ch):
    return max(0, glyphs[ch][2][0] - ORIGIN)   # natural left bearing, never negative
def inkw(ch):
    return glyphs[ch][2][2] - glyphs[ch][2][0]
def width(ch):   # glyph width as Minecraft will measure it: bearing + ink, widened to the font's advance
    return max(left(ch) + inkw(ch), round(NAT[ch] + SPACING) - 1)
CELL_W = max(width(c) for c in glyphs) + 1
ASCENT = top
CELL_H = top + bottom
rows = (len(CHARS) + COLS - 1) // COLS
atlas = Image.new("RGBA", (COLS * CELL_W, rows * CELL_H), (255, 255, 255, 0))
layout = [[] for _ in range(rows)]
adv = {}
for i, ch in enumerate(CHARS):
    r, c = divmod(i, COLS)
    layout[r].append(ch)
    if ch not in glyphs:
        continue
    img, base, box = glyphs[ch]
    cell = Image.new("RGBA", (CELL_W, CELL_H), (255, 255, 255, 0))
    glyph = img.crop((box[0], base - top, box[2], base + bottom))   # left edge trimmed, baseline fixed
    white = Image.new("RGBA", glyph.size, (255, 255, 255, 255))
    white.putalpha(glyph)
    cell.paste(white, (left(ch), 0))
    w = width(ch)
    if w > left(ch) + glyph.width:   # an invisible pixel widens the advance without drawing anything
        cell.putpixel((w - 1, 0), (255, 255, 255, 1))
    atlas.paste(cell, (c * CELL_W, r * CELL_H))
    adv[ch] = w + 1

font_dir = OUT / "font"
tex_dir = OUT / "textures/font"
tex_dir.mkdir(parents=True, exist_ok=True)
atlas.save(tex_dir / "sketchbook.png")
definition = {"providers": [
    {"type": "space", "advances": {" ": 4}},
    {"type": "bitmap", "file": "bodaddon:font/sketchbook.png", "ascent": ASCENT, "height": CELL_H,
     "chars": ["".join(row).ljust(COLS, "\u0000") for row in layout]},
    {"type": "reference", "id": "minecraft:default"},
]}
(font_dir / "sketchbook.json").write_text(json.dumps(definition, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("cell", CELL_W, "x", CELL_H, "ascent", ASCENT, "atlas", atlas.size)


def preview(lines, scale=4):
    PAPER, INK = (0xEE, 0xC8, 0x9E), (0x4A, 0x2E, 0x1C)
    line_h = CELL_H + 1
    width = max(sum(adv.get(c, 4) for c in l) for l in lines) + 6
    img = Image.new("RGB", (width, line_h * len(lines) + 4), PAPER)
    for n, text in enumerate(lines):
        x = 3
        for ch in text:
            if ch in glyphs:
                i = CHARS.index(ch)
                r, c = divmod(i, COLS)
                cell = atlas.crop((c * CELL_W, r * CELL_H, (c + 1) * CELL_W, (r + 1) * CELL_H))
                ink = Image.new("RGB", cell.size, INK)
                img.paste(ink, (x, 2 + n * line_h), cell.getchannel("A"))
            x += adv.get(ch, 4)
    return img.resize((img.width * scale, img.height * scale), Image.NEAREST)


preview(["Colors and markings", "Base Color  Tempest", "Underbelly Color", "Gradient Color 2  1 / 6",
         "Night Fury  Sketchbook", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz 0123456789"]
        ).save(ROOT / ".scratch/sketchbook_font_preview.png")
