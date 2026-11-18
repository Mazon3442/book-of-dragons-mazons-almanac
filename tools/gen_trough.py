"""Feeding trough (two blocks long): models, inventory icon, blockstate, loot, recipe.

The approved 32x32 block textures are authored PNG assets and are not regenerated here.
Run: python tools/gen_trough.py
"""
import json
import os
import random

from PIL import Image

from pixelart import IRON, C
from native_item_art import trough_icon

WALNUT = [C(h) for h in ("221a10", "3b3020", "453623", "523f27", "634c30")]   # dark-oak range: narrow and low contrast

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
TEX = os.path.join(ROOT, "assets", "bodaddon", "textures", "block")
MODELS = os.path.join(ROOT, "assets", "bodaddon", "models")
DATA = os.path.join(ROOT, "data", "bodaddon")


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


def streaks(im, rnd, x0, y0, x1, y1, vertical, mid):
    """Stripped-log grain: parallel streaks 1-3 px wide, each broken into segments one step lighter, the same or one step
    darker than `mid`, and a thin dark line where some segments meet their neighbour. Never more than one step from mid."""
    n_cross = (x1 - x0) if vertical else (y1 - y0)
    n_along = (y1 - y0) if vertical else (x1 - x0)
    c = 0
    while c < n_cross:
        w = rnd.choice((1, 2, 2, 3))
        a = 0
        while a < n_along:
            seg = rnd.randrange(4, 11)
            tone = rnd.choice((mid - 1, mid, mid, mid + 1, mid + 1))
            for cc in range(c, min(c + w, n_cross)):
                line = cc == c and c > 0 and rnd.random() < .5
                for aa in range(a, min(a + seg, n_along)):
                    xy = (x0 + cc, y0 + aa) if vertical else (x0 + aa, y0 + cc)
                    im.putpixel(xy, WALNUT[mid - 1 if line else tone])
            a += seg
        c += w


def board(im, rnd, top, hi, mid, lo):
    """One 7-row plank starting at row `top`. Pass 1 (blocking): seam, lit edge, body, shaded edge, all flat.
    Pass 2 (blending): log-style streaks across the body, then the light and the shade feather into it."""
    for x in range(16):
        im.putpixel((x, top), WALNUT[0])
        im.putpixel((x, top + 1), WALNUT[hi])
        for y in range(top + 2, top + 7):
            im.putpixel((x, y), WALNUT[mid])
        im.putpixel((x, top + 7), WALNUT[lo])
    streaks(im, rnd, 0, top + 2, 16, top + 7, False, mid)
    for x in range(16):
        if rnd.random() < .45:
            im.putpixel((x, top + 2), WALNUT[hi])
        if rnd.random() < .45:
            im.putpixel((x, top + 6), WALNUT[lo])


def planks():
    """Two boards: block and blend each, then the details: scuffs, a knot, iron nails. The first board is what the side
    faces show."""
    rnd = random.Random(5)
    im = Image.new("RGBA", (16, 16))
    board(im, rnd, 0, 3, 2, 1)
    board(im, rnd, 8, 3, 2, 1)
    for top in (0, 8):
        for _ in range(2):                                    # scuffs on the lit edge
            im.putpixel((rnd.randrange(16), top + 1), WALNUT[2])
        kx, ky = (9, top + 4) if top == 0 else (5, top + 4)   # knot: dark core, one-step ring
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            im.putpixel((kx + dx, ky + dy), WALNUT[1])
        im.putpixel((kx, ky), WALNUT[0])
    for x, y in ((2, 3), (13, 3), (2, 11), (13, 11)):         # iron nails: a lit head with a shadow pixel under it
        im.putpixel((x, y), IRON[3])
        im.putpixel((x, y + 1), WALNUT[0])
    return im


def post():
    """End caps and legs. Blocking: flat body. Blending: vertical log streaks, the left edge leaning lighter and the right
    darker. Details: lit cut at the top, shadowed foot, chipped corners."""
    rnd = random.Random(9)
    im = Image.new("RGBA", (16, 16), WALNUT[2])
    streaks(im, rnd, 0, 0, 16, 16, True, 2)
    for y in range(16):
        for x in (0, 1):
            if rnd.random() < .5:
                im.putpixel((x, y), WALNUT[3])
        for x in (14, 15):
            if rnd.random() < .5:
                im.putpixel((x, y), WALNUT[1])
    for x in range(16):
        im.putpixel((x, 0), WALNUT[3])
        im.putpixel((x, 15), WALNUT[1])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 15), (14, 0)):
        im.putpixel((x, y), WALNUT[0])
    return im


def inner():
    """Scooped floor: a darker, quieter version of the same grain, with a few pale specks of feed."""
    rnd = random.Random(13)
    im = Image.new("RGBA", (16, 16), WALNUT[1])
    streaks(im, rnd, 0, 0, 16, 16, False, 1)
    for _ in range(3):
        im.putpixel((rnd.randrange(16), rnd.randrange(16)), WALNUT[3])
    return im


def corner():
    """Corner post with an iron strap. Blocking: two flat iron rows. Blending: they step along the iron ramp with
    dithering. Details: the rivets."""
    rnd = random.Random(21)
    im = post()
    for x in range(16):
        im.putpixel((x, 2), IRON[3])
        im.putpixel((x, 3), IRON[2])
        im.putpixel((x, 4), WALNUT[0])
    for x in range(16):
        if rnd.random() < .4:
            im.putpixel((x, 2), IRON[4])
        if rnd.random() < .4:
            im.putpixel((x, 3), IRON[1])
    for x in (1, 14):
        im.putpixel((x, 2), IRON[4])
        im.putpixel((x, 3), IRON[1])
    return im


EMBLEM = ["#..#....#..#", ".##.####.##.", "..########..", "...##..##...", "....#..#...."]


def emblem():
    """A clean blended board, then the horned crest carved last, 12 wide and split across the seam: the left half sits at
    columns 10-15 (the foot half's plate) and the right half at columns 0-5 (the head half's plate)."""
    rnd = random.Random(33)
    im = Image.new("RGBA", (16, 16), WALNUT[2])
    board(im, rnd, 0, 3, 2, 1)
    cut = {(c, 2 + r) for r, row in enumerate(EMBLEM) for c, ch in enumerate(row) if ch == "#"}
    col = lambda c: c + 10 if c < 6 else c - 6
    for c, y in cut:
        im.putpixel((col(c), y), WALNUT[0])
    for c, y in cut:                                          # lit lip under each cut, so it reads as carved in
        if (c, y + 1) not in cut and y + 1 <= 7:
            im.putpixel((col(c), y + 1), WALNUT[4])
    return im


def icon():
    return trough_icon()


def element(frm, to, tex, up_tex=None, skip=(), window=1, rot=None):
    """One box. Sides sample rows window.. of the texture, with u following world position (north and east faces start at
    their far edge, as the game maps them); tops and bottoms sample a plain stretch (no nails, no seam)."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    h = y1 - y0
    bx0 = x0 % 16
    bx1 = bx0 + (x1 - x0)
    v0, v1 = window, window + h
    fv = 2 if up_tex else 5 if tex in ("post", "corner") else 9
    flat = [bx0, fv, bx1, fv + (z1 - z0)]
    faces = {"north": {"uv": [bx1, v0, bx0, v1], "texture": "#" + tex}, "south": {"uv": [bx0, v0, bx1, v1], "texture": "#" + tex},
             "east": {"uv": [z1, v0, z0, v1], "texture": "#" + tex}, "west": {"uv": [z0, v0, z1, v1], "texture": "#" + tex},
             "up": {"uv": flat, "texture": "#" + (up_tex or tex)}, "down": {"uv": flat, "texture": "#" + tex}}
    for s in skip:
        faces.pop(s, None)
    el = {"from": list(frm), "to": list(to), "faces": faces}
    if rot:
        el["rotation"] = rot
    return el


def half(head):
    """One block of the trough, long axis along x: a tub with corner posts, side boards carved with a crest, and legs
    splayed outward at the end. The foot half has its end at x=0, the head half at x=16 (a mirror)."""
    def box(x0, y0, z0, x1, y1, z1, tex, **kw):
        a, b = (16 - x1, 16 - x0) if head else (x0, x1)
        return element([a, y0, z0], [b, y1, z1], tex, **kw)

    seam = "west" if head else "east"                      # face toward the other half: never visible
    end = "east" if head else "west"                       # face toward this half's corner post
    rot = {"angle": 22.5 if head else -22.5, "axis": "z", "origin": [13.5 if head else 2.5, 6, 8]}
    return [
        box(2, 0, 2, 5, 6, 5, "post", window=0, skip=("up",), rot=dict(rot)),               # legs, splayed outward
        box(2, 0, 11, 5, 6, 14, "post", window=0, skip=("up",), rot=dict(rot)),
        box(3, 3, 5, 16, 5, 11, "wood", skip=(seam, "up")),                                  # belly brace between the legs
        box(0, 5, 4, 16, 7, 12, "wood", up_tex="inner", skip=(seam, "north", "south")),      # scooped floor
        box(3, 5, 2, 16, 12, 4, "wood", skip=(seam, end)),                                   # side boards
        box(3, 5, 12, 16, 12, 14, "wood", skip=(seam, end)),
        box(3, 12, 1, 16, 13, 4, "wood", skip=(seam, end)),                                  # rim, overhanging
        box(3, 12, 12, 16, 13, 15, "wood", skip=(seam, end)),
        box(0, 5, 4, 2, 13, 12, "post", window=0, skip=("north", "south")),                  # end board
        box(0, 5, 1, 3, 14, 4, "corner", window=0),                                          # corner posts, proud of the rim
        box(0, 5, 12, 3, 14, 15, "corner", window=0),
        box(10, 5, 1.5, 16, 12, 2, "emblem", skip=("south", "east", "west", "up", "down")),  # carved crest, across the seam
        box(10, 5, 14, 16, 12, 14.5, "emblem", skip=("north", "east", "west", "up", "down")),
    ]


def build():
    os.makedirs(TEX, exist_ok=True)
    textures = {"wood": "bodaddon:block/feeding_trough", "post": "bodaddon:block/feeding_trough_post",
                "inner": "bodaddon:block/feeding_trough_inner", "corner": "bodaddon:block/feeding_trough_corner",
                "emblem": "bodaddon:block/feeding_trough_emblem", "particle": "bodaddon:block/feeding_trough_post"}

    put(os.path.join(MODELS, "block", "feeding_trough_foot.json"), {"textures": textures, "elements": half(False)})
    put(os.path.join(MODELS, "block", "feeding_trough_head.json"), {"textures": textures, "elements": half(True)})

    # the item shows the whole 32-unit trough: the head half shifted one block along x
    shifted = []
    for el in half(True):
        el = json.loads(json.dumps(el))
        el["from"][0] += 16
        el["to"][0] += 16
        if "rotation" in el:
            el["rotation"]["origin"][0] += 16
        shifted.append(el)
    put(os.path.join(MODELS, "item", "feeding_trough_3d.json"), {
        "textures": textures, "elements": half(False) + shifted,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 90, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.22, 0.22, 0.22]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.28, 0.28, 0.28]},
        }})

    ITEM_TEX = os.path.join(ROOT, "assets", "bodaddon", "textures", "item")
    os.makedirs(ITEM_TEX, exist_ok=True)
    icon().save(os.path.join(ITEM_TEX, "feeding_trough.png"))
    put(os.path.join(MODELS, "item", "feeding_trough_2d.json"),
        {"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/feeding_trough"}})
    # flat icon in the inventory, on the ground and in frames; the 3D trough when held
    put(os.path.join(MODELS, "item", "feeding_trough.json"), {
        "parent": "minecraft:item/handheld", "loader": "neoforge:separate_transforms",
        "base": {"parent": "bodaddon:item/feeding_trough_3d"},
        "perspectives": {k: {"parent": "bodaddon:item/feeding_trough_2d"} for k in ("gui", "ground", "fixed")}})

    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for part in ("foot", "head"):
            v = {"model": f"bodaddon:block/feeding_trough_{part}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},part={part}"] = v
    put(os.path.join(ROOT, "assets", "bodaddon", "blockstates", "feeding_trough.json"), {"variants": variants})

    put(os.path.join(DATA, "loot_table", "blocks", "feeding_trough.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bodaddon:feeding_trough"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"},
                                  {"condition": "minecraft:block_state_property", "block": "bodaddon:feeding_trough",
                                   "properties": {"part": "head"}}]}],
    })
    put(os.path.join(DATA, "recipe", "feeding_trough.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["S S", "SSS", "L L"],
        "key": {"S": {"tag": "minecraft:wooden_slabs"}, "L": {"item": "minecraft:stick"}},
        "result": {"id": "bodaddon:feeding_trough"},
    })
    put(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "axe.json"),
        {"values": ["bodaddon:feeding_trough"]})


if __name__ == "__main__":
    build()
    print("trough written")
