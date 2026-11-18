"""Dragon Editor staff, redesigned as Drago Bludvist's pike: black wooden shaft, iron socket, leaf blade and a hook.

Stage 1 (this file): geometry only. `python tools/gen_pike.py OUT_DIR` writes pike_preview.png, a flat-color render from
the front, side and three-quarter views. Texturing comes after the shape is approved.
Units are model pixels (16 = one block); x east, y up, z south; the shaft stands on x = z = 8.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

# Follows the Blockbench Minecraft style guide: few elements, nothing under 1 unit, a single rotation for a slant instead of a
# stair of rotated pieces, and whole-pixel sizes so the texture keeps one pixel density (no mixels).
# element: (name, material, from, to, rotation, outline); outline is an (x, y) polygon, in model units, for plane elements.
E = []


def box(name, mat, frm, to, rot=None):
    E.append((name, mat, frm, to, rot, None))


def plane(name, mat, frm, to, outline):
    E.append((name, mat, frm, to, None, outline))


# shaft and butt
box("shaft", "wood", [7.5, -10, 7.5], [8.5, 16, 8.5])
box("butt_cap", "iron", [7, -11, 7], [9, -10, 9])
box("butt_spike", "iron", [7.5, -12, 7.5], [8.5, -11, 8.5])
# head: socket and flange, then a solid, tapering spear point (every element a whole number of pixels thick)
box("socket", "iron", [7, 15, 7], [9, 19, 9])
box("flange", "iron", [6.5, 19, 6.5], [9.5, 20, 9.5])
box("blade_base", "iron", [6.5, 20, 7], [9.5, 23, 9])
box("blade_mid", "iron", [7, 23, 7.5], [9, 26, 8.5])
box("blade_tip", "iron", [7.5, 26, 7.5], [8.5, 30, 8.5])
# hook: a thick base off the socket, an arm straight out (perpendicular to the shaft), a tip slanting down, and a small
# barb on top of the arm
box("hook_base", "iron", [5, 16, 7], [7, 19, 9])
box("hook_arm", "iron", [2, 17, 7.5], [5, 19, 8.5])
box("hook_tip", "iron", [-0.5, 17, 7.5], [2.5, 18, 8.5], {"angle": 45.0, "origin": [2.5, 17.5]})

COLOR = {"cloth": (84, 74, 70), "wood": (30, 27, 34), "iron": (110, 118, 128), "iron_lit": (158, 168, 178)}
SHADE = {"up": 1.0, "down": 0.45, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def corners(frm, to):
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return {
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    }


def view(yaw, pitch, scale, size, y_mid):
    W, H = size
    im = Image.new("RGB", size, (214, 218, 224))
    d = ImageDraw.Draw(im)
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p[0] - 8, p[1] - y_mid, p[2] - 8
        x, z = x * cy + z * sy_, -x * sy_ + z * cy          # yaw
        y, z = y * cp - z * sp, y * sp + z * cp              # pitch
        return W / 2 + x * scale, H / 2 - y * scale, z

    def turn(n, rot):
        if not rot:
            return n
        a = math.radians(rot["angle"])
        return (n[0] * math.cos(a) - n[1] * math.sin(a), n[0] * math.sin(a) + n[1] * math.cos(a), n[2])

    polys = []
    for name, mat, frm, to, rot, outline in E:
        if outline:
            pts = [proj((x, y, 8)) for x, y in outline]
            polys.append((sum(p[2] for p in pts) / len(pts), [(p[0], p[1]) for p in pts], tuple(int(c * 0.8) for c in COLOR[mat])))
            continue
        for face, quad in corners(frm, to).items():
            pts = []
            for p in quad:
                if rot:
                    a = math.radians(rot["angle"])
                    ox, oy = rot["origin"]
                    dx, dy = p[0] - ox, p[1] - oy
                    p = (ox + dx * math.cos(a) - dy * math.sin(a), oy + dx * math.sin(a) + dy * math.cos(a), p[2])
                pts.append(proj(p))
            n = turn(NORMAL[face], rot)
            nx, nz = n[0] * cy + n[2] * sy_, -n[0] * sy_ + n[2] * cy
            nyy, nzz = n[1] * cp - nz * sp, n[1] * sp + nz * cp
            if nzz <= 1e-6:
                continue
            k = SHADE[face]
            polys.append((sum(p[2] for p in pts) / 4, [(p[0], p[1]) for p in pts], tuple(int(c * k) for c in COLOR[mat])))
    for _, pts, col in sorted(polys, key=lambda t: t[0]):
        d.polygon(pts, fill=col, outline=tuple(max(0, c - 40) for c in col))
    return im


# ---- stage 2: model json, texture atlas and 2D icon -------------------------------------------------------------------
import json
import random

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bodaddon")
ATLAS = 64          # px; face uv in a block model is 0..16 over the whole texture, so uv = pixel * 16 / ATLAS

# Palettes follow the guides: small ramps, a little hue shift (cool shadows toward blue, lights toward warm), darker tones
# more saturated, nothing neon. Ramps run dark to light.
WOOD = [(12, 10, 19), (21, 18, 30), (32, 27, 44), (46, 40, 58), (63, 55, 76)]
IRON = [(18, 19, 30), (32, 35, 48), (52, 58, 72), (82, 90, 106), (122, 130, 144), (170, 176, 186)]
CLOTH = [(26, 22, 26), (42, 36, 38), (62, 54, 52), (86, 76, 70), (112, 100, 90)]
BASE_TONE = {"up": 4, "north": 3, "east": 3, "south": 3, "west": 2, "down": 2}


def paint_wood(face, w, h, rnd):
    """Black wood grain: 1 px streaks along the shaft, one step lighter or darker in short runs."""
    im = Image.new("RGBA", (w, h))
    base = {"north": 2, "east": 1, "south": 1, "west": 1, "up": 2, "down": 0}[face]
    for x in range(w):
        y = 0
        while y < h:
            run = rnd.randrange(3, 9)
            tone = max(0, min(4, base + rnd.choice((-1, 0, 0, 0, 1))))
            for yy in range(y, min(h, y + run)):
                im.putpixel((x, yy), WOOD[tone])
            y += run
    if h == 26 and w == 1:                       # the grip (y -4 to 3, rows 13-20 of the shaft) is cloth-wrapped in the texture
        off = {"north": 0, "east": 1, "south": 2, "west": 3}.get(face, 0)   # offset per side, so the weave winds round
        for r in range(13, 21):
            tone = (4, 3, 2, 3)[(r + off) % 4] + (-1 if face == "west" else 0) + (-1 if r in (13, 20) else 0)
            im.putpixel((0, r), CLOTH[max(0, min(4, tone))])
    return im


def paint_cloth(face, w, h, rnd):
    """Wrapped cloth: diagonal weave lines (a 4 px cycle), a lit left side, a dark seam at each end and a few frayed pixels."""
    im = Image.new("RGBA", (w, h))
    shift = {"north": 0, "east": 0, "south": 0, "west": -1, "up": 0, "down": -2}[face]
    for y in range(h):
        for x in range(w):
            tone = (3, 2, 1, 2)[(x + y) % 4] + shift
            if y == 0 or y == h - 1:
                tone -= 1
            im.putpixel((x, y), CLOTH[max(0, min(4, tone))])
    if w * h >= 8:
        for _ in range(2):
            x, y = rnd.randrange(w), rnd.randrange(1, max(2, h - 1))
            im.putpixel((x, y), CLOTH[max(0, min(4, 1 + rnd.choice((0, 1))))])
    return im


def paint_iron(face, w, h, rnd):
    """Guide steps: flat base, outline (lit top and left, darker bottom and right), then a little dirt as 2 px clusters."""
    base = BASE_TONE[face]
    px = [[base] * w for _ in range(h)]
    if w >= 2 and h >= 2:
        for x in range(w):
            px[0][x] = min(5, base + 1)
            px[h - 1][x] = max(0, base - 1)
        for y in range(h):
            if y < h - 1:
                px[y][0] = min(5, base + 1)
            px[y][w - 1] = max(0, base - 1)
        px[0][w - 1] = base
        px[h - 1][0] = base
    if w * h >= 6:
        for _ in range(max(1, w * h // 8)):
            x, y = rnd.randrange(max(1, w - 1)), rnd.randrange(h)
            d = rnd.choice((-1, -1, 1))
            for xx in (x, x + 1):
                if xx < w:
                    px[y][xx] = max(0, min(5, px[y][xx] + d))
    im = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            im.putpixel((x, y), IRON[px[y][x]])
    return im


def face_sizes(frm, to):
    dx, dy, dz = (round(to[i] - frm[i]) for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}


def build():
    rnd = random.Random(42)
    jobs = []
    for ei, (name, mat, frm, to, rot, _) in enumerate(E):
        for face, (w, h) in face_sizes(frm, to).items():
            jobs.append((ei, face, w, h))
    jobs.sort(key=lambda j: (-j[3], -j[2]))        # shelf packing, tallest first
    atlas = Image.new("RGBA", (ATLAS, ATLAS))
    placed = {}
    x = y = shelf = 0
    for ei, face, w, h in jobs:
        if x + w > ATLAS:
            x, y, shelf = 0, y + shelf, 0
        assert y + h <= ATLAS, "atlas full"
        mat = E[ei][1]
        tile = {"wood": paint_wood, "cloth": paint_cloth}.get(mat, paint_iron)(face, w, h, rnd)
        atlas.paste(tile, (x, y))
        placed[(ei, face)] = (x, y, x + w, y + h)
        x += w
        shelf = max(shelf, h)
    k = 16 / ATLAS
    elements = []
    for ei, (name, mat, frm, to, rot, _) in enumerate(E):
        el = {"name": name, "from": frm, "to": to, "faces": {
            f: {"uv": [v * k for v in placed[(ei, f)]], "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")}}
        if rot:
            el["rotation"] = {"angle": rot["angle"], "axis": "z", "origin": [rot["origin"][0], rot["origin"][1], 8]}
        elements.append(el)
    display = {
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [-0.5, 0, 1.5], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_lefthand": {"rotation": [-100, -90, -180], "translation": [-0.5, -0.5, 7], "scale": [0.8, 0.8, 0.8]},
        "firstperson_righthand": {"rotation": [-25, 90, 0], "translation": [0, 1, 0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_lefthand": {"rotation": [0, -90, -60], "scale": [0.8, 0.8, 0.8]},
        "ground": {"rotation": [90, 0, 90], "translation": [0, 3, 0]},
    }
    os.makedirs(os.path.join(ROOT, "models", "item"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "textures", "item"), exist_ok=True)
    with open(os.path.join(ROOT, "models", "item", "dragon_editor_3d.json"), "w") as f:
        json.dump({"textures": {"0": "bodaddon:item/dragon_editor_model", "particle": "bodaddon:item/dragon_editor_model"},
                   "elements": elements, "display": display}, f, indent=1)
    atlas.save(os.path.join(ROOT, "textures", "item", "dragon_editor_model.png"))


def icon():
    """Inventory pike: a legible hooked iron head, dark wood and a short cloth grip."""
    from pixelart import build as build_sprite, C
    with open(os.path.join(os.path.dirname(__file__), "dragon_editor.json")) as f:
        spec = json.load(f)
    assert spec["width"] == spec["height"] == len(spec["grid"]) == 16
    assert all(len(row) == 16 for row in spec["grid"])
    assert set("".join(spec["grid"])) <= set(spec["palette"]) | {"."}
    return build_sprite(spec["grid"], {key: C(value) for key, value in spec["palette"].items()})


def previews(out):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import numpy as np
    import render_block as rb
    rb.MODEL = json.load(open(os.path.join(ROOT, "models", "item", "dragon_editor_3d.json")))
    rb.TEX = {"0": np.array(Image.open(os.path.join(ROOT, "textures", "item", "dragon_editor_model.png")).convert("RGBA"))}
    rb.CENTER = np.array([8.0, 9.0, 8.0])
    tiles = [rb.render(yaw, pitch, 9.0, (300, 520)) for yaw, pitch in ((0, 0), (90, 0), (35, 18))]
    sheet = Image.new("RGB", (924, 540), (214, 218, 224))
    for i, t in enumerate(tiles):
        sheet.paste(t, (8 + i * 308, 10), t)
    sheet.save(os.path.join(out, "pike_textured.png"))
    ic = icon()
    ic.save(os.path.join(ROOT, "textures", "item", "dragon_editor.png"))
    big = Image.new("RGB", (532, 276), (120, 126, 136))
    z = ic.resize((256, 256), Image.NEAREST)
    big.paste(Image.new("RGB", (256, 256), (44, 46, 54)), (10, 10))
    big.paste(Image.new("RGB", (256, 256), (214, 218, 224)), (266, 10))
    big.paste(z, (10, 10), z)
    big.paste(z, (266, 10), z)
    big.save(os.path.join(out, "pike_icon.png"))


if __name__ == "__main__":
    out = sys.argv[1]
    if len(sys.argv) > 2 and sys.argv[2] == "build":
        build()
        previews(out)
        print("built", len(E), "elements")
        sys.exit(0)
    ymin = min(e[2][1] for e in E)
    ymax = max(e[3][1] for e in E)
    mid = (ymin + ymax) / 2
    s = 12
    H = int((ymax - ymin) * s + 40)
    views = [view(0, 0, s, (340, H), mid), view(90, 0, s, (200, H), mid), view(35, 18, s, (340, H), mid)]
    sheet = Image.new("RGB", (sum(v.width for v in views) + 8 * 4, H + 16), (120, 126, 136))
    x = 8
    for v in views:
        sheet.paste(v, (x, 8))
        x += v.width + 8
    sheet.save(os.path.join(out, "pike_preview.png"))
    print("ok", len(E), "elements, height", ymax - ymin)
