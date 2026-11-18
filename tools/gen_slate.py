"""Dragon Editor GUI sprites, "Drago's war slate": black wood frame, riveted iron plate, red cloth tabs, ember-orange fills.

python tools/gen_slate.py          writes src/main/resources/assets/bodaddon/textures/gui/sprites/*.png (+ .mcmeta for nine-slice)

Ramps run dark to light with the shadow side leaning cool and the highlight side leaning warm. Light comes from the top left.
The layout numbers (panel size, header and footer strips, the column divider) must match DragonEditorScreen.java.
"""
import json
import os
import random

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bodaddon", "textures", "gui", "sprites")


def C(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


WOOD = [C(h) for h in ("1c0f0b", "2c1a12", "3c2418", "50321f", "6c452a")]
IRON = [C(h) for h in ("101113", "1c1d20", "28292d", "35363a", "47474c", "5d5c62", "7a787f", "a2a0a6")]
RED = [C(h) for h in ("340b0e", "521014", "6b171a", "8a1f20", "ab302a")]
EMBER = [C(h) for h in ("7e2c0e", "c25410", "fa801a", "ffae48", "ffd88a")]
BONE = [C(h) for h in ("7e7667", "a99f8c", "d0c6ad", "efe4cb", "fff6e2")]
CLEAR = (0, 0, 0, 0)

# layout shared with DragonEditorScreen.java
PANEL_W, PANEL_H = 330, 217
FRAME = 6                     # wood band thickness
HEADER = (10, 14, PANEL_W - 11, 35)
FOOTER = (10, 193, PANEL_W - 11, 209)
DIVIDER_X = 111


def save(name, im, nine=None):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"))
    meta = os.path.join(OUT, name + ".png.mcmeta")
    if nine:
        w, h, border = nine
        with open(meta, "w") as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}, f)
    elif os.path.exists(meta):
        os.remove(meta)


def rect(im, x0, y0, x1, y1, col):
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=col)


def put(im, x, y, col):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), col)


def rivet(im, x, y):
    """A flat 2 x 2 nail head: lit pixel, two body pixels, one shadow pixel."""
    put(im, x, y, IRON[7])
    put(im, x + 1, y, IRON[5])
    put(im, x, y + 1, IRON[5])
    put(im, x + 1, y + 1, IRON[2])


def clusters(im, rnd, x0, y0, x1, y1, base, tones, count, size):
    """Soft blotches of a neighbouring tone, so large flat areas are not dead flat."""
    for _ in range(count):
        cx, cy = rnd.randrange(x0, x1), rnd.randrange(y0, y1)
        tone = rnd.choice(tones)
        for dx in range(rnd.randrange(1, size + 1)):
            for dy in range(rnd.randrange(1, size + 1)):
                if x0 <= cx + dx <= x1 and y0 <= cy + dy <= y1 and im.getpixel((cx + dx, cy + dy)) == base:
                    im.putpixel((cx + dx, cy + dy), tone)


def scratches(im, rnd, x0, y0, x1, y1, base, light, count):
    """Short brushed-metal scratches, one pixel tall."""
    for _ in range(count):
        x, y = rnd.randrange(x0, x1 - 6), rnd.randrange(y0, y1)
        for i in range(rnd.randrange(3, 7)):
            if im.getpixel((x + i, y)) == base:
                im.putpixel((x + i, y), light)


def bevel_box(im, x0, y0, x1, y1, fill, light, dark, outline):
    """A raised block: outline, lit top and left edge, shadowed bottom and right edge, cut corners."""
    rect(im, x0, y0, x1, y1, outline)
    rect(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
    rect(im, x0 + 1, y0 + 1, x1 - 1, y0 + 1, light)
    rect(im, x0 + 1, y0 + 1, x0 + 1, y1 - 1, light)
    rect(im, x0 + 1, y1 - 1, x1 - 1, y1 - 1, dark)
    rect(im, x1 - 1, y0 + 1, x1 - 1, y1 - 1, dark)
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        put(im, x, y, CLEAR)


def recess_box(im, x0, y0, x1, y1, fill, dark, light):
    """A sunk area: dark lip on the top and left, light lip on the bottom and right."""
    rect(im, x0, y0, x1, y1, fill)
    rect(im, x0, y0, x1, y0, dark)
    rect(im, x0, y0, x0, y1, dark)
    rect(im, x0 + 1, y1, x1, y1, light)
    rect(im, x1, y0 + 1, x1, y1, light)


# --- the panel ---------------------------------------------------------------------------------

def bracket(im, x, y):
    """A 15 x 15 iron corner plate with one big rivet."""
    bevel_box(im, x, y, x + 14, y + 14, IRON[5], IRON[7], IRON[3], IRON[0])
    rect(im, x + 2, y + 2, x + 12, y + 12, IRON[5])
    for i in range(2, 13):
        put(im, x + i, y + 2, IRON[6])
        put(im, x + 2, y + i, IRON[6])
        put(im, x + i, y + 12, IRON[4])
        put(im, x + 12, y + i, IRON[4])
    rect(im, x + 5, y + 5, x + 9, y + 9, IRON[1])
    rect(im, x + 5, y + 5, x + 8, y + 8, IRON[3])
    put(im, x + 5, y + 5, IRON[6])
    put(im, x + 6, y + 5, IRON[5])
    put(im, x + 5, y + 6, IRON[5])
    for sx, sy in ((x + 3, y + 10), (x + 11, y + 3)):         # a nick of wear
        put(im, sx, sy, IRON[3])


def slate():
    w, h = PANEL_W, PANEL_H
    rnd = random.Random(11)
    im = Image.new("RGBA", (w, h), WOOD[2])
    d = ImageDraw.Draw(im)
    # black wood band: grain runs along each side, in broken runs one step lighter or darker
    for y in list(range(0, FRAME)) + list(range(h - FRAME, h)):
        x = 0
        while x < w:
            run = rnd.randrange(6, 22)
            tone = rnd.choice((WOOD[1], WOOD[2], WOOD[2], WOOD[3]))
            rect(im, x, y, min(w - 1, x + run), y, tone)
            x += run + rnd.randrange(0, 3)
    for x in list(range(0, FRAME)) + list(range(w - FRAME, w)):
        y = 0
        while y < h:
            run = rnd.randrange(6, 22)
            tone = rnd.choice((WOOD[1], WOOD[2], WOOD[2], WOOD[3]))
            rect(im, x, y, x, min(h - 1, y + run), tone)
            y += run + rnd.randrange(0, 3)
    # outer edge dark, a lit lip just inside on the top and left, a dark lip on the inner edge
    rect(im, 0, 0, w - 1, 0, WOOD[0]); rect(im, 0, h - 1, w - 1, h - 1, WOOD[0])
    rect(im, 0, 0, 0, h - 1, WOOD[0]); rect(im, w - 1, 0, w - 1, h - 1, WOOD[0])
    rect(im, 1, 1, w - 2, 1, WOOD[4]); rect(im, 1, 1, 1, h - 2, WOOD[4])
    rect(im, w - 2, 2, w - 2, h - 2, WOOD[1]); rect(im, 2, h - 2, w - 2, h - 2, WOOD[1])
    # plate
    px0, py0, px1, py1 = FRAME - 1, FRAME - 1, w - FRAME, h - FRAME
    rect(im, px0, py0, px1, py1, WOOD[0])
    rect(im, px0 + 1, py0 + 1, px1 - 1, py1 - 1, IRON[2])
    clusters(im, rnd, px0 + 1, py0 + 1, px1 - 1, py1 - 1, IRON[2], (IRON[3], IRON[1]), 70, 4)
    scratches(im, rnd, px0 + 2, py0 + 2, px1 - 2, py1 - 2, IRON[2], IRON[3], 14)
    rect(im, px0 + 1, py0 + 1, px1 - 1, py0 + 1, IRON[1])          # the plate sits in a shallow well
    rect(im, px0 + 1, py0 + 1, px0 + 1, py1 - 1, IRON[1])
    # header strip and footer strip, sunk into the plate
    for x0, y0, x1, y1 in (HEADER, FOOTER):
        recess_box(im, x0, y0, x1, y1, IRON[1], IRON[0], IRON[4])
        clusters(im, rnd, x0 + 1, y0 + 1, x1 - 1, y1 - 1, IRON[1], (IRON[2],), 12, 3)
    # a groove between the dragon column and the controls
    rect(im, DIVIDER_X, 40, DIVIDER_X, 191, IRON[0])
    rect(im, DIVIDER_X + 1, 40, DIVIDER_X + 1, 191, IRON[4])
    # corner plates last, over the frame
    for x, y in ((0, 0), (w - 15, 0), (0, h - 15), (w - 15, h - 15)):
        bracket(im, x, y)
    return im


# --- tabs ---------------------------------------------------------------------------------------

TAB_W, TAB_H, TAB_SELECTED_H = 48, 36, 44


def cloth(selected):
    """A red cloth banner nailed up by two studs, cut in a swallowtail. The selected one hangs longer, with ember stitching."""
    rnd = random.Random(5)
    h = TAB_SELECTED_H if selected else TAB_H
    im = Image.new("RGBA", (TAB_W, h), CLEAR)
    top = 3
    notch = 6
    for y in range(top, h):
        for x in range(1, TAB_W - 1):
            depth = h - 1 - y                                           # distance above the bottom edge
            if depth < notch:                                           # swallowtail: remove a V from the bottom centre
                half = (notch - depth) * 1.0
                if abs(x - TAB_W / 2 + 0.5) < half:
                    continue
            im.putpixel((x, y), RED[2])
    # weave: vertical thread streaks, short runs one tone off, then a fold shadow on the right and light on the left
    for x in range(1, TAB_W - 1):
        y = top
        while y < h:
            run = rnd.randrange(3, 8)
            tone = RED[3] if rnd.random() < 0.3 else RED[1] if rnd.random() < 0.35 else RED[2]
            if x % 2 == 0:
                for yy in range(y, min(h, y + run)):
                    if im.getpixel((x, yy)) == RED[2]:
                        im.putpixel((x, yy), tone)
            y += run
    for y in range(top, h):
        for x in range(1, 4):
            if im.getpixel((x, y))[3]:
                im.putpixel((x, y), RED[3])
        for x in range(TAB_W - 4, TAB_W - 1):
            if im.getpixel((x, y))[3]:
                im.putpixel((x, y), RED[1])
    # outline: darkest red on every edge cell that touches empty space
    solid = {(x, y) for y in range(h) for x in range(TAB_W) if im.getpixel((x, y))[3]}
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in solid:
                im.putpixel((x, y), RED[0])
                break
    # hem along the top: a darker band with stitches
    rect(im, 1, top + 1, TAB_W - 2, top + 3, RED[1])
    for x in range(3, TAB_W - 3, 3):
        put(im, x, top + 2, RED[3])
    # studs
    for sx in (5, TAB_W - 9):
        rect(im, sx, 0, sx + 3, 3, IRON[0])
        rect(im, sx + 1, 1, sx + 2, 2, IRON[5])
        put(im, sx + 1, 1, IRON[7])
    if selected:                                                        # dashed ember stitching and an underline
        for y in range(top + 6, h - notch - 1):
            if (y // 2) % 2 == 0:
                put(im, 3, y, EMBER[2])
                put(im, TAB_W - 4, y, EMBER[2])
        bar_y = h - notch - 6
        rect(im, 12, bar_y, TAB_W - 13, bar_y + 1, EMBER[2])
        rect(im, 12, bar_y, TAB_W - 13, bar_y, EMBER[3])
    return im


# --- icons --------------------------------------------------------------------------------------

def icon(draw_fn):
    im = Image.new("RGBA", (13, 13), CLEAR)
    draw_fn(im)
    shadowed = Image.new("RGBA", (13, 13), CLEAR)
    for y in range(13):
        for x in range(13):
            if im.getpixel((x, y))[3]:
                for dx, dy in ((1, 0), (0, 1), (1, 1)):
                    if 0 <= x + dx < 13 and 0 <= y + dy < 13 and not im.getpixel((x + dx, y + dy))[3]:
                        shadowed.putpixel((x + dx, y + dy), RED[0])
    shadowed.alpha_composite(im)
    return shadowed


def grid(im, rows, x0=0, y0=0):
    tones = {"#": BONE[3], "+": BONE[4], "-": BONE[2], "=": BONE[1]}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in tones:
                put(im, x0 + x, y0 + y, tones[ch])


def icon_bond(im):
    """Two interlocking rings: the left one passes over the right at the top, under it at the bottom."""
    ring_a, ring_b = Image.new("RGBA", (13, 13), CLEAR), Image.new("RGBA", (13, 13), CLEAR)
    ImageDraw.Draw(ring_a).ellipse([0, 3, 7, 10], outline=BONE[3])
    ImageDraw.Draw(ring_b).ellipse([5, 3, 12, 10], outline=BONE[2])
    im.alpha_composite(ring_b)
    for y in range(13):
        for x in range(13):
            if ring_a.getpixel((x, y))[3] and (y < 6 or not ring_b.getpixel((x, y))[3]):
                im.putpixel((x, y), ring_a.getpixel((x, y)))


def icon_vitals(im):
    grid(im, [
        "..+++...+++..",
        ".+####.+####.",
        "+###########.",
        "+###########.",
        ".###########",
        ".#########-.",
        "..#######-..",
        "...#####-...",
        "....###-....",
        ".....#-.....",
    ], 0, 1)


def icon_mind(im):
    """A brain: two rounded lobes, a fissure down the middle, a few folds, and a short stem."""
    d = ImageDraw.Draw(im)
    for box in ([1, 1, 7, 7], [5, 1, 11, 7], [1, 4, 6, 10], [6, 4, 11, 10], [3, 3, 9, 9]):
        d.ellipse(box, fill=BONE[3])
    for x, y in ((6, 1), (6, 2), (6, 3), (6, 4), (6, 5), (6, 6), (6, 7), (6, 8)):
        put(im, x, y, BONE[0])
    for x, y in ((3, 3), (4, 3), (3, 6), (4, 7), (8, 3), (9, 3), (8, 7), (9, 6), (2, 5)):
        put(im, x, y, BONE[1])
    for x, y in ((5, 10), (6, 11), (7, 10), (6, 10)):
        put(im, x, y, BONE[2])


def icon_variants(im):
    grid(im, [
        "+...........+",
        "++.........++",
        ".+++.....+++.",
        ".+###+.+###+.",
        "..+###+###+..",
        "..-+###.###-.",
        "...-+#+#+#-..",
        "....-=-#-=...",
        ".....-.#.-...",
    ], 0, 2)


def icon_social(im):
    """Three figures: a larger one in front, a smaller one behind on each side."""
    d = ImageDraw.Draw(im)
    for hx in (1, 10):                       # side figures: head 2 x 2, shoulders 3 wide
        d.rectangle([hx, 3, hx + 1, 4], fill=BONE[1])
        d.rectangle([hx - 1, 6, hx + 2, 11], fill=BONE[1])
    front = set()
    for y in range(1, 4):
        for x in range(5, 8):
            front.add((x, y))
    for y in range(5, 12):
        for x in range(3 if y > 5 else 4, 10 if y > 5 else 9):
            front.add((x, y))
    for (x, y) in list(front):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in front:
                    put(im, x + dx, y + dy, RED[0])
    for (x, y) in front:
        put(im, x, y, BONE[3] if y < 8 else BONE[2])


# --- widgets ------------------------------------------------------------------------------------

def button(kind, cheat):
    """A riveted plate. The cheat variant carries a red cloth stripe with a stamped ring on its left end."""
    w = 44 if cheat else 24
    h = 20
    body = {"normal": IRON[4], "hover": IRON[5], "disabled": IRON[3]}[kind]
    light = {"normal": IRON[6], "hover": IRON[7], "disabled": IRON[4]}[kind]
    dark = {"normal": IRON[2], "hover": IRON[3], "disabled": IRON[2]}[kind]
    im = Image.new("RGBA", (w, h), CLEAR)
    bevel_box(im, 0, 0, w - 1, h - 1, body, light, dark, IRON[0])
    rnd = random.Random(7)
    clusters(im, rnd, 2, 2, w - 3, h - 3, body, (dark,), 14, 2)
    if cheat:
        sw = 15
        rect(im, 1, 1, sw, h - 2, RED[2] if kind != "disabled" else RED[1])
        rect(im, 1, 1, sw, 1, RED[3] if kind != "disabled" else RED[2])
        rect(im, 1, 1, 1, h - 2, RED[3] if kind != "disabled" else RED[2])
        rect(im, sw, 2, sw, h - 3, RED[1])
        # stamped ring
        cx, cy = 8, 10
        for ang in range(0, 360, 20):
            import math
            x = cx + round(math.cos(math.radians(ang)) * 4)
            y = cy + round(math.sin(math.radians(ang)) * 4)
            put(im, x, y, RED[4] if kind != "disabled" else RED[3])
        put(im, cx, cy, RED[4] if kind != "disabled" else RED[3])
        put(im, cx - 1, cy, RED[3]); put(im, cx + 1, cy, RED[3]); put(im, cx, cy - 1, RED[3]); put(im, cx, cy + 1, RED[3])
        rivet(im, w - 5, 2)
        rivet(im, w - 5, h - 4)
        put(im, 0, 0, CLEAR)
    else:
        for x, y in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
            rivet(im, x, y)
    return im


def row_plate():
    """A raised plate behind a label and its control, 24 x 17 so it can be stretched."""
    w, h = 24, 17
    im = Image.new("RGBA", (w, h), CLEAR)
    bevel_box(im, 0, 0, w - 1, h - 1, IRON[4], IRON[6], IRON[3], IRON[0])
    return im


def readout():
    """A dark sunk field for status text and the option strip, 16 x 12."""
    w, h = 16, 12
    im = Image.new("RGBA", (w, h), CLEAR)
    rect(im, 0, 0, w - 1, h - 1, IRON[1])
    rect(im, 0, 0, w - 1, 0, IRON[0])
    rect(im, 0, 0, 0, h - 1, IRON[0])
    rect(im, 1, 1, w - 1, 1, IRON[0])
    rect(im, 1, h - 1, w - 1, h - 1, IRON[5])
    rect(im, w - 1, 1, w - 1, h - 1, IRON[5])
    for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        put(im, x, y, CLEAR)
    return im


def slider_track():
    w, h = 16, 8
    im = Image.new("RGBA", (w, h), CLEAR)
    rect(im, 0, 0, w - 1, h - 1, IRON[0])
    rect(im, 1, 1, w - 2, h - 3, IRON[1])
    rect(im, 1, h - 2, w - 2, h - 2, IRON[4])
    rect(im, 1, h - 1, w - 2, h - 1, IRON[0])
    for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        put(im, x, y, CLEAR)
    return im


def slider_handle():
    w, h = 8, 12
    im = Image.new("RGBA", (w, h), CLEAR)
    bevel_box(im, 0, 0, w - 1, h - 1, IRON[5], IRON[7], IRON[3], IRON[0])
    rect(im, 3, 4, 4, 7, IRON[2])
    put(im, 3, 4, IRON[6])
    put(im, 4, 7, IRON[1])
    return im


def viewport_frame():
    """The window for the dragon: a thick iron rim with a rivet in each corner and a near-black well, 24 x 24 with 6 px corners."""
    w = h = 24
    im = Image.new("RGBA", (w, h), CLEAR)
    bevel_box(im, 0, 0, w - 1, h - 1, IRON[5], IRON[7], IRON[3], IRON[0])
    rect(im, 2, 2, w - 3, h - 3, IRON[4])
    rect(im, 4, 4, w - 5, h - 5, IRON[0])
    rect(im, 5, 5, w - 6, h - 6, IRON[1])
    rect(im, 5, 5, w - 6, 5, IRON[0])
    rect(im, 5, 5, 5, h - 6, IRON[0])
    for x, y in ((1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)):
        rivet(im, x, y)
    return im


def plate(w=24, h=16, rivets=True):
    im = Image.new("RGBA", (w, h), CLEAR)
    bevel_box(im, 0, 0, w - 1, h - 1, IRON[4], IRON[6], IRON[3], IRON[0])
    clusters(im, random.Random(9), 2, 2, w - 3, h - 3, IRON[4], (IRON[3],), 8, 2)
    if rivets:
        for x, y in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
            rivet(im, x, y)
    return im


def bar():
    """The riveted strip between the name and the species."""
    w, h = 24, 8
    im = Image.new("RGBA", (w, h), CLEAR)
    bevel_box(im, 0, 0, w - 1, h - 1, IRON[3], IRON[5], IRON[2], IRON[0])
    return im


def rivet_sprite():
    im = Image.new("RGBA", (3, 3), CLEAR)
    rivet(im, 0, 0)
    return im


def dot(col):
    im = Image.new("RGBA", (5, 5), CLEAR)
    rect(im, 1, 0, 3, 4, col[2])
    rect(im, 0, 1, 4, 3, col[2])
    put(im, 1, 1, col[3])
    put(im, 2, 1, col[3])
    put(im, 1, 2, col[3])
    for x, y in ((3, 3), (2, 4), (3, 4), (4, 3), (4, 2)):
        put(im, x, y, col[1])
    return im


if __name__ == "__main__":
    save("slate", slate())
    for selected in (False, True):
        save("tab_cloth" + ("_selected" if selected else ""), cloth(selected))
    save("icon_bond", icon(icon_bond))
    save("icon_vitals", icon(icon_vitals))
    save("icon_mind", icon(icon_mind))
    save("icon_variants", icon(icon_variants))
    save("icon_social", icon(icon_social))
    for kind in ("normal", "hover", "disabled"):
        save("button_" + kind, button(kind, False), (24, 20, 5))
        save("button_cheat_" + kind, button(kind, True), (44, 20, {"left": 16, "right": 5, "top": 5, "bottom": 5}))
    save("row", row_plate(), (24, 17, 4))
    save("readout", readout(), (16, 12, 3))
    save("slider_track", slider_track(), (16, 8, 3))
    save("slider_handle", slider_handle())
    save("viewport_frame", viewport_frame(), (24, 24, 6))
    save("title_plate", plate(), (24, 16, 5))
    save("bar", bar(), (24, 8, 3))
    save("rivet", rivet_sprite())
    save("dot_ok", dot((None, (40, 70, 40, 255), (90, 160, 70, 255), (150, 210, 110, 255))))
    save("dot_warn", dot((None, (90, 20, 20, 255), RED[4], (230, 110, 90, 255))))
    save("dot_ember", dot((None, EMBER[0], EMBER[2], EMBER[4])))
    print("ok")
