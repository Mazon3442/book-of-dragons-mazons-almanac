"""Builds the held (3D) Sketchbook: item/sketchbook_3d.png and the three model JSONs.

The inventory icon stays flat (sketchbook.png, drawn by hand); everything else, hands, ground, item frames,
shows the cuboid book. NeoForge's `separate_transforms` loader picks the model per display context.
"""
import json
import pathlib
import random
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/bodaddon"
TEX = 64
random.seed(7)

LEATHER = [(0x5A, 0x28, 0x1A), (0x7A, 0x38, 0x22), (0x94, 0x45, 0x28), (0xAC, 0x58, 0x32), (0xC4, 0x70, 0x42)]
SPINE = [(0x3B, 0x1F, 0x16), (0x52, 0x2C, 0x1E), (0x6A, 0x3A, 0x28), (0x80, 0x48, 0x32)]
BAND = [(0x8A, 0x4A, 0x1E), (0xB4, 0x6C, 0x2C), (0xD4, 0x8A, 0x3C), (0xEC, 0xA8, 0x58)]
PAGE = [(0xB0, 0x8A, 0x5E), (0xD5, 0xB0, 0x86), (0xEE, 0xC8, 0x9E), (0xFA, 0xD7, 0xAF)]
BRASS = [(0x6A, 0x46, 0x18), (0x9A, 0x6E, 0x26), (0xC9, 0x8F, 0x34), (0xF0, 0xC0, 0x60)]
EMBLEM = (0x4E, 0x20, 0x14)

img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
px = img.load()


def blotchy(x0, y0, w, h, ramp, light_edge=True):
    """Clustered noise: each pixel is its neighbour's tone or one step off, like brushed leather."""
    grid = [[len(ramp) // 2] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            near = [grid[y][x - 1]] if x else []
            near += [grid[y - 1][x]] if y else []
            base = random.choice(near) if near else len(ramp) // 2
            if random.random() < 0.35:
                base += random.choice((-1, 1))
            mid = len(ramp) // 2
            grid[y][x] = max(mid - 1, min(mid + 1, base))
    for y in range(h):
        for x in range(w):
            t = grid[y][x]
            if light_edge and (x == 0 or y == 0):
                t = min(len(ramp) - 1, t + 1)
            if light_edge and (x == w - 1 or y == h - 1):
                t = max(0, t - 1)
            px[x0 + x, y0 + y] = ramp[t] + (255,)


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = c + (255,)


def line(pts, c, x0, y0):
    for x, y in pts:
        px[x0 + x, y0 + y] = c + (255,)


# regions (x0, y0, w, h) in texture pixels
FRONT, BACK, EDGE = (0, 0, 20, 26), (22, 0, 20, 26), (44, 0, 16, 8)
SPINE_R, PAGES = (44, 10, 6, 26), (52, 10, 12, 16)
BAND_R, BRASS_R, STRAP_R = (0, 28, 12, 6), (14, 28, 8, 6), (24, 28, 8, 6)

blotchy(*FRONT, LEATHER)
blotchy(*BACK, LEATHER)
blotchy(*EDGE, LEATHER, light_edge=False)
blotchy(*SPINE_R, SPINE)
blotchy(*BAND_R, BAND)
blotchy(*BRASS_R, BRASS)
blotchy(*STRAP_R, LEATHER)
# dark rim round both covers so they read as thick boards
for (x0, y0, w, h) in (FRONT, BACK):
    for x in range(w):
        for y in (0, h - 1):
            px[x0 + x, y0 + y] = LEATHER[0] + (255,)
    for y in range(h):
        for x in (0, w - 1):
            px[x0 + x, y0 + y] = LEATHER[0] + (255,)
# pages: cream with long fibre streaks
for y in range(PAGES[3]):
    tone = random.choice((1, 2, 2, 3, 1))
    for x in range(PAGES[2]):
        t = tone if random.random() > 0.15 else max(0, tone - 1)
        px[PAGES[0] + x, PAGES[1] + y] = PAGE[t] + (255,)
# claw emblem on the front cover: a tall centre claw and two curved side claws
fx, fy = FRONT[0], FRONT[1]
centre = [(10, 7), (10, 8), (9, 9), (10, 9), (9, 10), (10, 10), (9, 11), (10, 11), (9, 12), (10, 12), (9, 13), (10, 13), (10, 14),
          (11, 9), (11, 10), (11, 11), (11, 12)]
left = [(4, 10), (4, 11), (5, 11), (4, 12), (5, 12), (5, 13), (6, 13), (6, 14), (7, 14), (7, 15), (8, 15), (8, 16), (7, 16)]
right = [(15, 10), (15, 11), (14, 11), (15, 12), (14, 12), (14, 13), (13, 13), (13, 14), (12, 14), (12, 15), (11, 15), (11, 16), (12, 16)]
line(centre + left + right, EMBLEM, fx, fy)
# worn corner highlights
for dx, dy in ((1, 1), (2, 1), (1, 2), (18, 24), (17, 24), (18, 23)):
    px[fx + dx, fy + dy] = LEATHER[4] + (255,)
img.save(ASSETS / "textures/item/sketchbook_3d.png")


def uv(region, sub=None):
    """Region (x0, y0, w, h) in pixels, optionally cropped to `sub` (x, y, w, h), as a model UV (0-16 scale)."""
    x0, y0, w, h = region
    if sub:
        x0, y0, w, h = x0 + sub[0], y0 + sub[1], sub[2], sub[3]
    k = 16 / TEX
    return [x0 * k, y0 * k, (x0 + w) * k, (y0 + h) * k]


def face(region, sub=None):
    return {"uv": uv(region, sub), "texture": "#tex"}


def cube(frm, to, faces, name):
    return {"name": name, "from": frm, "to": to, "faces": faces}


def all_faces(region, sub=None):
    return {side: face(region, sub) for side in ("north", "south", "east", "west", "up", "down")}


edge = lambda: face(EDGE, (0, 0, 8, 4))
elements = [
    cube([3, 1, 6.5], [13, 14, 7.25], {**{s: edge() for s in ("south", "east", "west", "up", "down")}, "north": face(BACK)}, "back cover"),
    cube([3, 1, 8.75], [13, 14, 9.5], {**{s: edge() for s in ("north", "east", "west", "up", "down")}, "south": face(FRONT)}, "front cover"),
    cube([2, 1, 6.5], [4, 14, 9.5], {**{s: edge() for s in ("north", "south", "up", "down", "east")}, "west": face(SPINE_R)}, "spine"),
    cube([4, 1.5, 7.25], [12.5, 13.5, 8.75], all_faces(PAGES), "pages"),
    cube([1.7, 2.5, 6.3], [4.2, 4.1, 9.7], all_faces(BAND_R), "band top"),
    cube([1.7, 6.4, 6.3], [4.2, 8.0, 9.7], all_faces(BAND_R), "band middle"),
    cube([1.7, 10.2, 6.3], [4.2, 11.8, 9.7], all_faces(BAND_R), "band bottom"),
    cube([12.6, 6.6, 6.3], [13.6, 9.4, 9.9], all_faces(STRAP_R), "clasp strap"),
    cube([11.2, 6.4, 9.5], [13.2, 9.6, 10.1], all_faces(BRASS_R), "clasp plate"),
]

BOOK_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 15], "translation": [1.1, 3.0, 1.1], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -15], "translation": [1.1, 3.0, 1.1], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
models = ASSETS / "models/item"
(models / "sketchbook_3d.json").write_text(json.dumps({
    "textures": {"tex": "bodaddon:item/sketchbook_3d", "particle": "bodaddon:item/sketchbook_3d"},
    "elements": elements,
    "display": BOOK_DISPLAY,
}, indent=2))
(models / "sketchbook_flat.json").write_text(json.dumps({
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "bodaddon:item/sketchbook"},
}, indent=2))
(models / "sketchbook.json").write_text(json.dumps({
    "loader": "neoforge:separate_transforms",
    "gui_light": "front",
    "base": {"parent": "bodaddon:item/sketchbook_3d"},
    "perspectives": {"gui": {"parent": "bodaddon:item/sketchbook_flat"}},
}, indent=2))
img.resize((TEX * 8, TEX * 8), Image.NEAREST).save(ROOT / ".scratch/sketchbook_3d_texture.png")
