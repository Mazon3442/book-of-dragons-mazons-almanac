"""Build the sketchbook bitmap fonts from Patrick Hand (SIL OFL, tools/fonts/PatrickHand-OFL.txt).

Two fonts come out: `sketchbook` (body text) and `sketchbook_title` (the page headings, a bit larger). Each glyph is
drawn big, slanted into an italic, area-averaged down to the pixel grid and snapped to solid ink or nothing, so the
text sits on the same pixel grid as the book art with no half-transparent pixels. Writes the glyph atlases, the font
definitions and a preview.

    python tools/gen_sketchbook_font.py
"""
import json
import pathlib
from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
TTF = ROOT / "tools/fonts/PatrickHand-Regular.ttf"
OUT = ROOT / "src/main/resources/assets/bodaddon"
SS = 8              # supersampling
FULL = 100          # coverage (of 255) needed for a pixel to be ink in the body font; headings use less, so they are bolder
SHEAR = 0.22        # italic slant (horizontal shift per unit of height)
COLS = 16
CHARS = [chr(c) for c in range(33, 127)]


def build(name, em, full):
    font = ImageFont.truetype(str(TTF), em * SS)

    def render(ch):
        w, h = em * 3 * SS, em * 3 * SS
        base = em * 2 * SS
        big = Image.new("L", (w, h), 0)
        ImageDraw.Draw(big).text((em * SS, base), ch, font=font, fill=255, anchor="ls")
        big = big.transform(big.size, Image.AFFINE, (1, SHEAR, -SHEAR * base, 0, 1, 0), Image.BICUBIC)
        small = big.resize((em * 3, em * 3), Image.BOX)
        return small.point(lambda v: 255 if v >= full else 0), em * 2

    glyphs, nat = {}, {}
    top = bottom = 0
    for ch in CHARS:
        img, base = render(ch)
        box = img.getbbox()
        if not box:
            continue
        glyphs[ch] = (img, base, box)
        nat[ch] = font.getlength(ch) / SS
        top = max(top, base - box[1])
        bottom = max(bottom, box[3] - base)

    def left(ch):
        return max(0, glyphs[ch][2][0] - em)

    def ink(ch):
        return glyphs[ch][2][2] - glyphs[ch][2][0]

    def width(ch):
        return max(left(ch) + ink(ch), round(nat[ch]) - 1)

    cell_w = max(width(c) for c in glyphs) + 1
    cell_h = top + bottom
    rows = (len(CHARS) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * cell_w, rows * cell_h), (255, 255, 255, 0))
    layout = [[] for _ in range(rows)]
    adv = {}
    for i, ch in enumerate(CHARS):
        r, c = divmod(i, COLS)
        layout[r].append(ch)
        if ch not in glyphs:
            continue
        img, base, box = glyphs[ch]
        cell = Image.new("RGBA", (cell_w, cell_h), (255, 255, 255, 0))
        glyph = img.crop((box[0], base - top, box[2], base + bottom))
        white = Image.new("RGBA", glyph.size, (255, 255, 255, 255))
        white.putalpha(glyph)
        cell.paste(white, (left(ch), 0))
        w = width(ch)
        if w > left(ch) + glyph.width:   # an invisible pixel widens the advance without drawing anything
            cell.putpixel((w - 1, 0), (255, 255, 255, 1))
        atlas.paste(cell, (c * cell_w, r * cell_h))
        adv[ch] = w + 1

    (OUT / "textures/font").mkdir(parents=True, exist_ok=True)
    atlas.save(OUT / f"textures/font/{name}.png")
    definition = {"providers": [
        {"type": "space", "advances": {" ": max(3, em // 3)}},
        {"type": "bitmap", "file": f"bodaddon:font/{name}.png", "ascent": top, "height": cell_h,
         "chars": ["".join(row).ljust(COLS, "\u0000") for row in layout]},
        {"type": "reference", "id": "minecraft:default"},
    ]}
    (OUT / "font").mkdir(parents=True, exist_ok=True)
    (OUT / f"font/{name}.json").write_text(json.dumps(definition, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(name, "cell", cell_w, "x", cell_h, "ascent", top)
    return atlas, glyphs, adv, cell_w, cell_h


def preview(sets, lines, scale=4):
    PAPER, INK = (0xEE, 0xC8, 0x9E), (0x4A, 0x2E, 0x1C)
    parts = []
    for (atlas, glyphs, adv, cw, ch_), text in zip(sets, lines):
        w = sum(adv.get(c, 4) for c in text) + 6
        img = Image.new("RGB", (w, ch_ + 4), PAPER)
        x = 3
        for c in text:
            if c in glyphs:
                r, col = divmod(CHARS.index(c), COLS)
                cell = atlas.crop((col * cw, r * ch_, (col + 1) * cw, (r + 1) * ch_))
                img.paste(Image.new("RGB", cell.size, INK), (x, 2), cell.getchannel("A"))
            x += adv.get(c, 4)
        parts.append(img)
    sheet = Image.new("RGB", (max(p.width for p in parts), sum(p.height for p in parts)), PAPER)
    y = 0
    for p in parts:
        sheet.paste(p, (0, y))
        y += p.height
    return sheet.resize((sheet.width * scale, sheet.height * scale), Image.NEAREST)


if __name__ == "__main__":
    body = build("sketchbook", 12, FULL)
    title = build("sketchbook_title", 16, 60)
    preview([title, title, body, body, body],
            ["Sketchbook", "Markings", "Face/Limb Color  Noct  Deep steel", "Wing Gradient Color  3 / 6",
             "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789"]
            ).save(ROOT / ".scratch/sketchbook_font_preview.png")
