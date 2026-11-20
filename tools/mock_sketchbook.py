"""Mockup v2 of Hiccup's Sketchbook (Variants-only editor). Native 256x168, shown at 4x.
Goals from review: layered page stacks with depth, nothing perfectly square, hand-made stitching.
Techniques: random-walk edges (wobble that persists, not per-pixel jitter), stacked page layers with staggered edges,
pages that bow toward the gutter, paper cards with torn edges + drop shadow, irregular stitches, clustered noise only.
The dragon in the frame is a placeholder for the live 3D preview."""
import io
import math
import os
import random
import zipfile
from PIL import Image, ImageDraw

W, H, SCALE = 256, 168, 4
JAR = os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
z = zipfile.ZipFile(JAR)
FONT = Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
GLYPH = {}
for code in range(32, 127):
    cx, cy = (code % 16) * 8, (code // 16) * 8
    g = FONT.crop((cx, cy, cx + 8, cy + 8))
    w = 0
    for x in range(8):
        if any(g.getpixel((x, y))[3] for y in range(8)):
            w = x + 1
    GLYPH[chr(code)] = (g, w if w else 3)


def text_w(s):
    return sum(GLYPH[c][1] + 1 for c in s)


def text(img, s, x, y, col):
    for c in s:
        g, w = GLYPH[c]
        t = Image.new("RGBA", g.size, col + (255,))
        t.putalpha(g.getchannel("A"))
        img.alpha_composite(t, (x, y))
        x += w + 1


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


INK, GRAPH, GRAPH_L = rgb("#3b291e"), rgb("#5b5148"), rgb("#8a7e70")
PAGE = [rgb(c) for c in ("#a8805a", "#c79f74", "#d9b68b", "#eec89e", "#fad7af", "#fee3bf")]
LEATHER = [rgb(c) for c in ("#1d1a15", "#3b291e", "#5a3823", "#713f28", "#8a5233", "#a06a40")]
BRASS = [rgb(c) for c in ("#7a5320", "#c98f34", "#f0c060")]
rnd = random.Random(11)


def clusters(w, h, cell, levels, seed):
    r = random.Random(seed)
    sw, sh = w // cell + 2, h // cell + 2
    g = Image.new("L", (sw, sh))
    g.putdata([r.randrange(256) for _ in range(sw * sh)])
    g = g.resize((sw * cell, sh * cell), Image.BICUBIC)
    return [[min(levels - 1, g.getpixel((x, y)) * levels // 256) for x in range(w)] for y in range(h)]


def walk(n, seed, amp=1.6, step=0.45):
    """Persistent wobble: a smoothed random walk, so an edge drifts instead of jittering."""
    r = random.Random(seed)
    v, out = 0.0, []
    for _ in range(n):
        v = max(-amp, min(amp, v + r.uniform(-step, step)))
        out.append(v)
    sm = [sum(out[max(0, i - 2):i + 3]) / len(out[max(0, i - 2):i + 3]) for i in range(n)]
    return [int(round(a)) for a in sm]


def put(img, x, y, col):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(col[:3]) + (255,))


def shade(col, k):
    return tuple(max(0, min(255, int(c * k))) for c in col[:3])


img = Image.new("RGBA", (W, H))
bgn = clusters(W, H, 6, 3, 1)
for y in range(H):
    for x in range(W):
        put(img, x, y, [rgb("#14100c"), rgb("#1a1510"), rgb("#201a14")][bgn[y][x]])

# ======================================================= cover
CX0, CY0, CX1, CY1 = 4, 3, 251, 157
wl, wr = walk(H, 1), walk(H, 2)
wt, wb = walk(W, 3), walk(W, 4)
R = 8


def in_cover(x, y):
    if x < 0 or y < 0 or x >= W or y >= H:
        return False
    if y < CY0 + max(0, wt[x]) or y > CY1 - max(0, wb[x]):
        return False
    if x < CX0 + max(0, wl[y]) or x > CX1 - max(0, wr[y]):
        return False
    for (ax, ay) in ((CX0 + R, CY0 + R), (CX1 - R, CY0 + R), (CX0 + R, CY1 - R), (CX1 - R, CY1 - R)):
        if (x < CX0 + R if ax < 128 else x > CX1 - R) and (y < CY0 + R if ay < 80 else y > CY1 - R):
            if (x - ax) ** 2 + (y - ay) ** 2 > R * R:
                return False
    return True


for y in range(CY0 + 5, CY1 + 8):
    for x in range(CX0 + 3, CX1 + 6):
        if not in_cover(x, y) and in_cover(x - 4, y - 5):
            put(img, x, y, rgb("#0a0806"))
grain = clusters(W, H, 3, 3, 2)
scuff = clusters(W, H, 5, 5, 9)
for y in range(CY0, CY1 + 1):
    for x in range(CX0, CX1 + 1):
        if not in_cover(x, y):
            continue
        t = 3 + (grain[y][x] - 1)
        if scuff[y][x] == 4 and (x < CX0 + 9 or x > CX1 - 9 or y < CY0 + 7 or y > CY1 - 7):
            t += 1
        if not in_cover(x - 1, y - 1) or not in_cover(x - 2, y - 2):
            t += 1
        if not in_cover(x + 1, y + 1) or not in_cover(x + 2, y + 2):
            t -= 1
        if not in_cover(x - 1, y) or not in_cover(x + 1, y) or not in_cover(x, y - 1) or not in_cover(x, y + 1):
            t = 0
        put(img, x, y, LEATHER[max(0, min(5, t))])

# ======================================================= page stacks (layers with depth)
GX = 127.5
LAYERS = 4
fib = clusters(W, H, 5, 4, 3)


def layer_box(k):
    """k=0 is the bottom sheet (reaches furthest out); the current page is k=LAYERS-1."""
    return 10 + k * 3, 8 + k, 152 - k


def paper_tone(x, y, k):
    gx = abs(x - GX)
    t = 2 + (1 if fib[y][x] == 3 else 0) + (1 if k == LAYERS - 1 else 0)
    if k < LAYERS - 1:
        t -= 1 + (k % 2)
    if gx < 14 and ((x + y) % 2 == 0 or gx < 6):
        t -= 1
    if gx < 5:
        t -= 1
    return max(0, min(5, t))


for left in (True, False):
    for k in range(LAYERS):
        out, top, bot = layer_box(k)
        seed = 20 + k * 3 + (0 if left else 11)
        e_top, e_bot, e_out = walk(256, seed, 1.2, .4), walk(256, seed + 1, 1.2, .4), walk(H, seed + 2, 1.3, .35)
        xs = range(out, 127) if left else range(128, 256 - out)
        span = max(1, 127 - out)
        for x in xs:
            u = min(1.0, abs(x - GX) / span)
            bow = int(round(3.0 * (1 - u) ** 2))
            ytop = top + bow + max(0, e_top[x])
            ybot = bot - int(round(2.0 * (1 - u) ** 2)) - max(0, e_bot[x])
            for y in range(ytop, ybot + 1):
                ox = out + max(0, e_out[y])
                if (left and x < ox) or ((not left) and x > 255 - ox):
                    continue
                t = paper_tone(x, y, k)
                on_edge = y == ytop or y == ybot or x == (ox if left else 255 - ox)
                near_edge = y == ytop + 1 or y == ybot - 1 or x == (ox + 1 if left else 254 - ox)
                if on_edge:
                    put(img, x, y, shade(PAGE[0], .62))          # dark line where this sheet ends: the depth cue
                elif near_edge and k < LAYERS - 1:
                    put(img, x, y, PAGE[4])                       # catch-light on the sheet edge
                else:
                    put(img, x, y, PAGE[t])
for _ in range(260):
    x, y = rnd.randint(20, 235), rnd.randint(16, 142)
    if abs(x - GX) < 6:
        continue
    for k in range(rnd.randint(2, 4)):
        put(img, x + k, y, shade(img.getpixel((x + k, y)), 0.93))

# spine seam, uneven thread, dark notch where the cover shows below the pages
for y in range(11, 152):
    put(img, 127, y, LEATHER[1])
    put(img, 128, y, LEATHER[0])
for cx in range(118, 138):
    h = int(round(4 * math.sqrt(max(0, 1 - ((cx - 127.5) / 10.5) ** 2))))
    for dy in range(h):
        put(img, cx, 151 + dy, LEATHER[0] if dy == h - 1 else LEATHER[1])
sr = random.Random(5)
y = 18
while y < 138:
    off = sr.choice([0, 0, 1, -1])
    for k in range(sr.randint(3, 6)):
        put(img, 126 + off, y + k, rgb("#c79a6c") if sr.random() > .25 else rgb("#a77b52"))
        put(img, 130 - off, y + k + sr.choice([0, 0, 1]), rgb("#c79a6c") if sr.random() > .25 else rgb("#a77b52"))
    y += sr.randint(13, 21)


# ======================================================= paper cards (torn edge + shadow)
def card(w, h, seed, tone=3):
    top, bot = walk(w, seed, 1.0, .4), walk(w, seed + 1, 1.0, .4)
    lft, rgt = walk(h, seed + 2, 1.0, .4), walk(h, seed + 3, 1.0, .4)
    m = clusters(w, h, 4, 3, seed)
    c = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            if y < 1 + max(0, top[x]) or y > h - 2 - max(0, bot[x]) or x < 1 + max(0, lft[y]) or x > w - 2 - max(0, rgt[y]):
                continue
            put(c, x, y, PAGE[min(5, tone + (1 if m[y][x] == 2 else 0))])
    rim = []
    for y in range(h):
        for x in range(w):
            if c.getpixel((x, y))[3] and any(not (0 <= x + dx < w and 0 <= y + dy < h) or not c.getpixel((x + dx, y + dy))[3]
                                             for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                rim.append((x, y))
    for (x, y) in rim:
        put(c, x, y, PAGE[1])
    return c


def stick(c, x, y, angle):
    big = c.rotate(angle, resample=Image.NEAREST, expand=True)
    a = big.getchannel("A").point(lambda v: 140 if v else 0)
    shd = Image.new("RGBA", big.size, (12, 8, 6, 0))
    shd.putalpha(a)
    img.alpha_composite(shd, (x + 2, y + 2))
    img.alpha_composite(big, (x, y))


pv = card(104, 78, 61, 4)
for y in range(6, 72):
    for x in range(6, 98):
        if (x + y) % 5 == 0 and (x * 3 + y) % 7 < 3:
            put(pv, x, y, PAGE[3])
dr = Image.new("L", (100, 75), 0)
dd = ImageDraw.Draw(dr)
dd.ellipse((30, 33, 66, 50), fill=255)
dd.ellipse((14, 33, 34, 47), fill=255)
dd.polygon([(30, 38), (22, 34), (14, 33), (14, 45), (30, 48)], fill=255)
dd.polygon([(16, 33), (12, 25), (21, 31)], fill=255)
dd.polygon([(22, 32), (21, 24), (28, 31)], fill=255)
dd.polygon([(44, 36), (36, 12), (58, 20), (76, 11), (70, 38)], fill=255)
dd.polygon([(36, 12), (44, 20), (47, 14)], fill=255)
dd.polygon([(60, 42), (84, 52), (96, 50), (90, 57), (82, 59), (60, 49)], fill=255)
dd.polygon([(40, 48), (36, 59), (41, 59), (46, 49)], fill=255)
dd.polygon([(54, 48), (52, 59), (57, 59), (60, 49)], fill=255)
ox, oy = 3, 2
for y in range(75):
    for x in range(100):
        if dr.getpixel((x, y)):
            under = y > 40 or x > 62
            hatch = (x - y) % 3 == 0 and under
            hatch2 = (x + y) % 5 == 0 and y > 46
            put(pv, ox + x, oy + y, GRAPH if (hatch or hatch2) else (rgb("#cfb590") if (x + y) % 7 else rgb("#c4a67e")))
for y in range(75):
    for x in range(100):
        if not dr.getpixel((x, y)):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 100 and 0 <= ny < 75 and dr.getpixel((nx, ny)):
                    put(pv, ox + x, oy + y, rgb("#2c261f"))
                    break
put(pv, ox + 20, oy + 38, rgb("#1c1712"))
put(pv, ox + 21, oy + 38, rgb("#1c1712"))
put(pv, ox + 20, oy + 37, rgb("#f0e0b8"))
for _ in range(24):
    put(pv, ox + 46 + rnd.randint(-8, 18), oy + 62 + rnd.randint(0, 3), rgb("#a99c8a"))
stick(pv, 19, 34, 1.4)
d = ImageDraw.Draw(img)
for (tx, ty) in ((15, 32), (104, 34)):
    d.rectangle((tx, ty, tx + 11, ty + 4), fill=rgb("#e3cd9a"))
    d.line((tx, ty + 4, tx + 11, ty + 4), fill=rgb("#bfa56f"))
    for k in range(0, 12, 3):
        put(img, tx + k, ty, rgb("#cdb783"))

text(img, "Coat sketch", 21, 16, INK)
for x in range(21, 21 + text_w("Coat sketch")):
    put(img, x, 26 + (1 if (x // 3) % 2 else 0), GRAPH_L)
text(img, "Night Fury", 24, 114, INK)
for i in range(5):
    col = rgb("#1c2236") if i == 1 else rgb("#e9e2d2")
    bx, by = 24 + i * 11 + (1 if i % 2 else 0), 125 + (i % 2)
    d.ellipse((bx, by, bx + 8, by + 7), fill=col, outline=INK)
    put(img, bx + 2, by + 1, tuple(min(255, c + 25) for c in col))
text(img, "Current coat", 24, 136, GRAPH)

rc = card(112, 104, 71, 4)
stick(rc, 131, 29, -0.8)
text(img, "Colors and markings", 133, 15, INK)
for x in range(133, 133 + text_w("Colors and markings")):
    put(img, x, 25 + (1 if (x // 4) % 2 else 0), GRAPH_L)
rows = [("Base color", "Leucistic warm", rgb("#e9e2d2")), ("Dorsal color", "Melanistic cool", rgb("#1c2236")),
        ("Underbelly", "Leucistic warm", rgb("#e9e2d2")), ("Gradient color", "Leucistic warm", rgb("#e9e2d2")),
        ("Body markings", "Leucistic warm", rgb("#e9e2d2"))]
for i, (lab, val, col) in enumerate(rows):
    y = 35 + i * 19 + (i % 2)
    text(img, lab, 138 + (1 if i % 3 == 1 else 0), y - 1, GRAPH)
    for x in range(138, 238):
        put(img, x, y + 16 + (1 if (x // 8 + i) % 3 == 0 else 0), GRAPH_L)
    ay = y + 11
    d.line((139, ay, 142, ay - 3), fill=INK)
    d.line((139, ay, 142, ay + 3), fill=INK)
    d.line((238, ay, 235, ay - 3), fill=INK)
    d.line((238, ay, 235, ay + 3), fill=INK)
    d.ellipse((146, ay - 3, 155, ay + 3), fill=col, outline=INK)
    put(img, 148, ay - 2, tuple(min(255, c + 30) for c in col))
    text(img, val, 160, y + 7, INK)
text(img, "Page 1 of 4", 162, 134, GRAPH)
for (sx, dr_) in ((150, 1), (222, -1)):
    d.line((sx, 138, sx + 3 * dr_, 135), fill=INK)
    d.line((sx, 138, sx + 3 * dr_, 141), fill=INK)

# peeling corner
c = 14
for j in range(c):
    for i in range(c - j):
        put(img, 240 - i, 146 - j, PAGE[5] if (i + j) > c - 4 else PAGE[4])
for i in range(c + 1):
    x, y = 240 - i, 146 - (c - i)
    put(img, x, y, PAGE[1])
    put(img, x - 1, y - 1, PAGE[0])
    put(img, x + 2, y + 2, shade(PAGE[1], .6))


# ======================================================= leather: irregular stitching along the cover inset
def stitches(points, seed):
    r = random.Random(seed)
    i = 0
    while i < len(points) - 4:
        if r.random() < .06:
            i += r.randint(4, 6)
            continue
        ln = r.choice([2, 2, 3, 3, 4])
        col = rgb(r.choice(["#ecd5a4", "#ecd5a4", "#d2b27c", "#f6e6bd"]))
        for k in range(ln):
            x, y = points[min(len(points) - 1, i + k)]
            put(img, x, y, col)
            put(img, x + 1, y + 1, LEATHER[0])
        i += ln + r.randint(1, 3)


ins = 4
top_pts = [(x, CY0 + ins + max(0, wt[x]) + (1 if (x // 11) % 3 == 0 else 0)) for x in range(CX0 + 10, CX1 - 9)]
bot_pts = [(x, CY1 - ins - max(0, wb[x]) - (1 if (x // 13) % 3 == 1 else 0)) for x in range(CX0 + 10, CX1 - 9)]
lft_pts = [(CX0 + ins + max(0, wl[y]), y) for y in range(CY0 + 10, CY1 - 9)]
rgt_pts = [(CX1 - ins - max(0, wr[y]), y) for y in range(CY0 + 10, CY1 - 9)]
for k, pts in enumerate((top_pts, bot_pts, lft_pts, rgt_pts)):
    stitches(pts, 80 + k)

for n, (ax, ay, sx, sy) in enumerate(((CX0, CY0, 1, 1), (CX1, CY0, -1, 1), (CX0, CY1, 1, -1), (CX1, CY1, -1, -1))):
    size = [8, 6, 7, 6][n]
    for i in range(size):
        for j in range(size - i):
            if not in_cover(ax + sx * i, ay + sy * j):
                continue
            tone = 2 if i + j < 2 else (1 if i + j < size - 2 else 0)
            if n == 2 and (i, j) in ((2, 2), (2, 3), (3, 2)):
                tone = 0
            put(img, ax + sx * i, ay + sy * j, BRASS[tone])
    put(img, ax + sx * 1, ay + sy * 1, rgb("#fff0b0"))

for y in range(148, 165):
    off = int(round(1.5 * math.sin((y - 148) / 4.0)))
    for k, x in enumerate(range(125, 131)):
        put(img, x + off, y, rgb(["#7a1f12", "#b23a1c", "#d4552a", "#d4552a", "#b23a1c", "#7a1f12"][k]))
for dx, dy in ((0, 0), (2, 1), (4, 0), (1, 2), (3, 2)):
    put(img, 126 + dx, 164 - dy, rgb("#17120e"))

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".scratch")
os.makedirs(root, exist_ok=True)
img.resize((W * SCALE, H * SCALE), Image.NEAREST).save(os.path.join(root, "sketchbook_mock.png"))
img.save(os.path.join(root, "sketchbook_mock_native.png"))
