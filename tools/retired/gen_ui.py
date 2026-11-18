"""Generates the Dragon Editor GUI sprites (Berk field journal style, palette sampled from the Book of Dragons book).
Output: src/main/resources/assets/bodaddon/textures/gui/sprites/*.png (+ .mcmeta for nine-slice sprites)."""
import json
import os
import random

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "bodaddon", "textures", "gui", "sprites")


def C(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# sampled from the book: outline, cover browns, page tones
OUTLINE, OUTLINE2 = C("25261F"), C("3B291E")
COVER_D, COVER, COVER_L = C("5A3823"), C("713F28"), C("8A5233")
TAN_D, TAN, TAN_L = C("7A593B"), C("AD825B"), C("C79A6C")
PAGE_D, PAGE_M, PAGE, PAGE_L = C("D5B086"), C("EEC89E"), C("FAD7AF"), C("FEE3BF")
EMBER, EMBER_L, EMBER_D = C("E0731C"), C("FFB04A"), C("9A3A10")
BRASS, BRASS_L, BRASS_D = C("C98F34"), C("F0C060"), C("7A5320")
GREEN, RED = C("5E8F2C"), C("B03A22")
CLEAR = (0, 0, 0, 0)


def save(name, im, nine=None):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"))
    meta = os.path.join(OUT, name + ".png.mcmeta")
    if nine:
        w, h, b = nine
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": b}}}, open(meta, "w"))
    elif os.path.exists(meta):
        os.remove(meta)


def px(im, pts, col):
    for x, y in pts:
        if 0 <= x < im.width and 0 <= y < im.height:
            im.putpixel((x, y), col)


def rect(im, x0, y0, x1, y1, col):
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=col)


def rounded(im, x0, y0, x1, y1, col):
    rect(im, x0 + 1, y0, x1 - 1, y1, col)
    rect(im, x0, y0 + 1, x1, y1 - 1, col)


def speckle(im, x0, y0, x1, y1, seed, dark, light, density=0.07):
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = r.random()
            if v < density:
                im.putpixel((x, y), dark)
            elif v > 1 - density * 0.6:
                im.putpixel((x, y), light)


# --- panel: leather-bound page, 32x32, border 8 ---------------------------------------------------
def panel():
    s = 32
    im = Image.new("RGBA", (s, s), CLEAR)
    rounded(im, 0, 0, s - 1, s - 1, OUTLINE)
    rounded(im, 1, 1, s - 2, s - 2, COVER_D)
    rounded(im, 2, 2, s - 3, s - 3, COVER)
    rect(im, 3, 2, s - 4, 2, COVER_L)
    rect(im, 2, 3, 2, s - 4, COVER_L)
    rect(im, 3, s - 3, s - 3, s - 3, COVER_D)
    rect(im, s - 3, 3, s - 3, s - 3, COVER_D)
    rect(im, 4, 4, s - 5, s - 5, OUTLINE2)
    rect(im, 5, 5, s - 6, s - 6, PAGE_D)
    rect(im, 6, 6, s - 7, s - 7, PAGE)
    rect(im, 6, 6, s - 7, 6, PAGE_L)
    rect(im, 6, 6, 6, s - 7, PAGE_L)
    rect(im, 6, s - 7, s - 7, s - 7, PAGE_M)
    rect(im, s - 7, 6, s - 7, s - 7, PAGE_M)
    speckle(im, 8, 8, s - 9, s - 9, 3, PAGE_M, PAGE_L)
    for cx, cy in ((3, 3), (s - 4, 3), (3, s - 4), (s - 4, s - 4)):
        px(im, [(cx, cy)], BRASS_L)
        px(im, [(cx + 1, cy), (cx, cy + 1)], BRASS)
        px(im, [(cx + 1, cy + 1)], BRASS_D)
    return im


# --- sketch frame for the dragon preview, 24x24, border 5 -----------------------------------------
def frame():
    s = 24
    im = Image.new("RGBA", (s, s), CLEAR)
    rect(im, 0, 0, s - 1, s - 1, OUTLINE)
    rect(im, 1, 1, s - 2, s - 2, TAN_D)
    rect(im, 1, 1, s - 2, 1, TAN)
    rect(im, 1, 1, 1, s - 2, TAN)
    rect(im, 2, 2, s - 3, s - 3, OUTLINE2)
    rect(im, 3, 3, s - 4, s - 4, PAGE_D)
    rect(im, 4, 4, s - 5, s - 5, TAN)
    speckle(im, 4, 4, s - 5, s - 5, 11, TAN_D, PAGE_D, 0.09)
    rect(im, 3, 3, s - 4, 3, TAN_D)
    rect(im, 3, 3, 3, s - 4, TAN_D)
    for cx, cy in ((1, 1), (s - 3, 1)):
        px(im, [(cx, cy), (cx + 1, cy), (cx, cy + 1)], C("A9ADB2"))
        px(im, [(cx + 1, cy + 1)], C("4A4D52"))
    return im


# --- buttons: stitched leather strap, 24x20, border 5 ---------------------------------------------
def button(kind):
    w, h = 24, 20
    im = Image.new("RGBA", (w, h), CLEAR)
    body, top, bot, stitch = {
        "normal": (COVER, COVER_L, COVER_D, PAGE_M),
        "hover": (C("93562F"), C("B87344"), COVER, PAGE_L),
        "disabled": (C("8B7358"), C("9E866B"), C("6F5A44"), C("B7A284")),
    }[kind]
    rounded(im, 0, 0, w - 1, h - 1, EMBER_D if kind == "hover" else OUTLINE)
    rect(im, 1, 1, w - 2, h - 2, bot)
    rect(im, 1, 1, w - 2, h - 4, body)
    rect(im, 1, 1, w - 2, 2, top)
    if kind == "hover":
        rect(im, 1, 1, w - 2, 1, EMBER_L)
    for x in range(3, w - 3, 3):
        px(im, [(x, 3), (x + 1, 3)], stitch)
        px(im, [(x, h - 4), (x + 1, h - 4)], stitch)
    return im


# --- slider ---------------------------------------------------------------------------------------
def slider_track():
    w, h = 16, 8
    im = Image.new("RGBA", (w, h), CLEAR)
    rounded(im, 0, 0, w - 1, h - 1, OUTLINE2)
    rect(im, 1, 1, w - 2, h - 2, COVER_D)
    rect(im, 1, 1, w - 2, 2, OUTLINE)
    rect(im, 1, h - 2, w - 2, h - 2, TAN_D)
    return im


def slider_handle():
    w, h = 8, 12
    im = Image.new("RGBA", (w, h), CLEAR)
    rounded(im, 0, 0, w - 1, h - 1, OUTLINE)
    rect(im, 1, 1, w - 2, h - 2, BRASS)
    rect(im, 1, 1, 2, h - 2, BRASS_L)
    rect(im, w - 2, 1, w - 2, h - 2, BRASS_D)
    rect(im, 1, h - 2, w - 2, h - 2, BRASS_D)
    rect(im, 3, 3, 4, 5, BRASS_D)
    px(im, [(3, 3)], BRASS_L)
    rect(im, 3, 7, 4, 8, BRASS_D)
    return im


# --- section rule, 16x5 (stretches horizontally) --------------------------------------------------
def rule():
    w, h = 16, 5
    im = Image.new("RGBA", (w, h), CLEAR)
    rect(im, 0, 2, w - 1, 2, TAN)
    rect(im, 0, 3, w - 1, 3, PAGE_L)
    return im


# --- bookmark ribbons -----------------------------------------------------------------------------
RIBBONS = {"orange": ("E0731C", "FFB04A", "9A3A10"), "teal": ("1F9E96", "55D6C8", "0F5F5C"),
           "green": ("6FA83A", "A8DC62", "3F6A1E"), "blue": ("2E7FB0", "6DB8E6", "184E74")}


def ribbon(color, selected):
    mid, light, dark = (C(h) for h in RIBBONS[color])
    w, h = (36 if selected else 30), 24
    im = Image.new("RGBA", (w, h), CLEAR)
    rounded(im, 0, 0, w - 1, h - 1, OUTLINE)
    rounded(im, 1, 1, w - 2, h - 2, dark)
    rounded(im, 1, 1, w - 2, h - 4, mid)
    rect(im, 2, 1, w - 2, 2, light)
    rect(im, 1, 2, 1, h - 5, light)
    speckle(im, 3, 3, w - 3, h - 4, 40 + len(color), dark, light, 0.03)
    for y in range(4, h - 4, 3):
        px(im, [(w - 3, y)], PAGE_M)
    return im


ICONS = {
    "bond": ["..##...##..", ".####.####.", "###########", "###########", ".#########.", "..#######..", "...#####...", "....###....", ".....#....."],
    "vitals": ["....###....", "....###....", "....###....", "###########", "###########", "###########", "....###....", "....###....", "....###...."],
    "mind": ["....#....", "....#....", "...###...", "..#####..", "#########", "..#####..", "...###...", "....#....", "....#...."],
    "social": ["..##....##.", ".####..####", ".####..####", "..##....##.", "...........", "...#####...", "..#######..", "..#######..", "...#####..."],
}


def icon(name):
    rows = ICONS[name]
    im = Image.new("RGBA", (13, 13), CLEAR)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                px(im, [(x + 2, y + 2)], OUTLINE2)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                px(im, [(x + 1, y + 1)], PAGE_L if (x + y) % 5 else PAGE)
    return im


def dot(col, light):
    im = Image.new("RGBA", (5, 5), CLEAR)
    rounded(im, 0, 0, 4, 4, OUTLINE2)
    rect(im, 1, 1, 3, 3, col)
    px(im, [(1, 1)], light)
    return im


if __name__ == "__main__":
    save("panel", panel(), (32, 32, 8))
    save("frame", frame(), (24, 24, 5))
    for k in ("normal", "hover", "disabled"):
        save("button_" + k, button(k), (24, 20, 5))
    save("slider_track", slider_track(), (16, 8, 3))
    save("slider_handle", slider_handle())
    save("rule", rule(), (16, 5, {"left": 2, "right": 2, "top": 0, "bottom": 0}))
    save("dot_ember", dot(EMBER, EMBER_L))
    save("dot_ok", dot(GREEN, C("9AD05A")))
    save("dot_warn", dot(RED, C("E87050")))
    print("sprites written")
