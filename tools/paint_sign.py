"""Paints the Stable Sign textures to match the mockup: warm brown planks with a seam, a dragon crest carved in pale wood,
flat iron rivets at the corners, dark iron chains, and the wall bracket post.

python tools/paint_sign.py OUT_DIR       (OUT_DIR gets a preview sheet of the two textures)

Follows docs/texturing_guide.md: block the flat colours, blend within a material, then the details (crest, rivets) last.
The palette is a small warm ramp, hue-shifted (cooler, redder shadows, yellower lights), nothing saturated to the point of glare.
Layouts retain vanilla island origins; see tools/gen_sign.py. The approved 64x32 PNGs are authored at one texel per model unit.
Running this script previews the current textures without overwriting them; the legacy painting helpers remain below.
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bodaddon", "textures", "block")

WOOD = [(46, 26, 20), (78, 44, 28), (112, 66, 38), (142, 88, 48), (172, 116, 66)]      # dark to light
CARVE = [(150, 104, 66), (196, 148, 98), (222, 176, 120)]                            # the crest: carved, paler wood
IRON = [(24, 22, 28), (44, 40, 46), (66, 60, 66), (92, 86, 92), (128, 122, 128)]
CREST = ["#..#....#..#", ".##.####.##.", "..########..", "...##..##...", "....#..#...."]   # 12 x 5, horned


def streaks(im, rnd, x0, y0, w, h, horizontal, mid):
    """Grain: parallel 1-2 px streaks, each broken into short runs one step lighter or darker than mid."""
    cross, along = (h, w) if horizontal else (w, h)
    c = 0
    while c < cross:
        thick = rnd.choice((1, 1, 2))
        a = 0
        while a < along:
            run = rnd.randrange(3, 9)
            tone = max(0, min(4, mid + rnd.choice((-1, 0, 0, 1))))
            for cc in range(c, min(c + thick, cross)):
                for aa in range(a, min(a + run, along)):
                    xy = (x0 + aa, y0 + cc) if horizontal else (x0 + cc, y0 + aa)
                    im.putpixel(xy, WOOD[tone] + (255,))
            a += run
        c += thick


def wood_panel(im, rnd, x, y, w, h, seam=None, lit=True):
    """Flat body, grain, then edge light: a lit top row and darker bottom row, and an optional seam between two planks."""
    for yy in range(h):
        for xx in range(w):
            im.putpixel((x + xx, y + yy), WOOD[2] + (255,))
    streaks(im, rnd, x, y, w, h, True, 2)
    if lit:
        for xx in range(w):
            im.putpixel((x + xx, y), WOOD[3] + (255,))
            im.putpixel((x + xx, y + h - 1), WOOD[1] + (255,))
    if seam is not None:                       # dark gap with a lit lip under it
        for xx in range(w):
            im.putpixel((x + xx, y + seam), WOOD[0] + (255,))
            if seam + 1 < h - 1:
                im.putpixel((x + xx, y + seam + 1), WOOD[3] + (255,))


def end_grain(im, rnd, x, y, w, h):
    """A cut face: darker than the long faces, with a few lighter flecks in short runs rather than a checker."""
    for yy in range(h):
        for xx in range(w):
            im.putpixel((x + xx, y + yy), WOOD[1] + (255,))
    for _ in range(max(1, w * h // 8)):
        fx, fy = rnd.randrange(w), rnd.randrange(h)
        for dx in (0, 1):
            if fx + dx < w:
                im.putpixel((x + fx + dx, y + fy), WOOD[2] + (255,))


def rivet(im, x, y):
    """A flat nail head, 2 x 2: a lit pixel, two body pixels and a shadow pixel."""
    im.putpixel((x, y), IRON[4] + (255,))
    im.putpixel((x + 1, y), IRON[3] + (255,))
    im.putpixel((x, y + 1), IRON[2] + (255,))
    im.putpixel((x + 1, y + 1), IRON[1] + (255,))


def crest(im, x, y):
    """The horned dragon crest carved last: a lit lip under each cut so it reads as carved, the body in two pale tones."""
    cut = {(c, r) for r, row in enumerate(CREST) for c, ch in enumerate(row) if ch == "#"}
    for c, r in cut:
        im.putpixel((x + c, y + r), CARVE[2 if r < 2 else 1] + (255,))
    for c, r in cut:
        if (c, r + 1) not in cut:
            im.putpixel((x + c, y + r + 1), WOOD[0] + (255,))      # shadow under the carving


def board_face(im, rnd, x, y, w, h, rivets, crest_at):
    """One broad face of a board: planks with a seam, the crest, rivets at the corners. Bottom rows are left calm for the name."""
    wood_panel(im, rnd, x, y, w, h, seam=h // 2 - 1)
    crest(im, x + crest_at[0], y + crest_at[1])
    for rx, ry in rivets:
        rivet(im, x + rx, y + ry)


def chain_tile(im, x, y, phase):
    """A 3 x 6 piece of chain: a ring seen face-on, then a link seen edge-on. phase shifts which comes first."""
    rows = [".#.", "#.#", "#.#", ".#.", ".#.", ".#."]
    for r in range(6):
        row = rows[(r + phase) % 6]
        for c, ch in enumerate(row):
            if ch == "#":
                tone = IRON[4] if c == 0 else IRON[2]
                if (r + phase) % 6 == 3:
                    tone = IRON[1]                      # the underside of the ring
                im.putpixel((x + c, y + r), tone + (255,))


def v_chain(im, x, y):
    """The V plane (12 x 6): two chains meeting at the top centre, drawn as a diagonal run of links 2 px thick."""
    for i in range(6):
        for px in (i, 11 - i):
            py = 5 - i
            tone = IRON[3] if i % 2 else IRON[2]
            im.putpixel((x + px, y + py), tone + (255,))
            nx = px + 1 if px < 6 else px - 1
            im.putpixel((x + nx, y + py), IRON[1] + (255,))


def hanging(rnd):
    im = Image.new("RGBA", (64, 32))
    # plank 16 x 2 x 4 at (0,0): top (4,0) 16x4, bottom (20,0), front (4,4) 16x2, west (0,4) 4x2, east (20,4) 4x2, back (24,4) 16x2
    wood_panel(im, rnd, 4, 0, 16, 4, lit=False)
    for xx in range(16):
        im.putpixel((4 + xx, 3), WOOD[3] + (255,))
    wood_panel(im, rnd, 20, 0, 16, 4, lit=False)
    for xx in range(16):
        im.putpixel((20 + xx, 3), WOOD[1] + (255,))
    for ox in (4, 24):
        wood_panel(im, rnd, ox, 4, 16, 2)
    end_grain(im, rnd, 0, 4, 4, 2)
    end_grain(im, rnd, 20, 4, 4, 2)
    # chains
    chain_tile(im, 0, 6, 0)
    chain_tile(im, 3, 6, 0)
    chain_tile(im, 6, 6, 3)
    chain_tile(im, 9, 6, 3)
    v_chain(im, 14, 6)
    v_chain(im, 26, 6)
    # board 14 x 10 x 2 at (0,12): top (2,12) 14x2, bottom (16,12), front (2,14) 14x10, west (0,14) 2x10, east (16,14), back (18,14)
    end_grain(im, rnd, 2, 12, 14, 2)
    end_grain(im, rnd, 16, 12, 14, 2)
    rivets = [(0, 0), (12, 0), (0, 8), (12, 8)]
    for ox in (2, 18):
        board_face(im, rnd, ox, 14, 14, 10, rivets, (1, 2))
    end_grain(im, rnd, 0, 14, 2, 10)
    end_grain(im, rnd, 16, 14, 2, 10)
    # bracket post 2 x 7 x 4 at (40,12): top (44,12) 2x4, bottom (46,12), front (44,16) 2x7, west (40,16) 4x7, east (46,16) 4x7, back (50,16)
    end_grain(im, rnd, 44, 12, 2, 4)
    end_grain(im, rnd, 46, 12, 2, 4)
    for ox, w in ((44, 2), (50, 2)):
        wood_panel(im, rnd, ox, 16, w, 7)
    wood_panel(im, rnd, 40, 16, 4, 7)
    wood_panel(im, rnd, 46, 16, 4, 7)                   # the face you see: two nails
    rivet(im, 47, 17)
    rivet(im, 47, 20)
    return im


def standing(rnd):
    im = Image.new("RGBA", (64, 32))
    # board 24 x 12 x 2 at (0,0): top (2,0) 24x2, bottom (26,0), front (2,2) 24x12, west (0,2) 2x12, east (26,2), back (28,2)
    end_grain(im, rnd, 2, 0, 24, 2)
    end_grain(im, rnd, 26, 0, 24, 2)
    rivets = [(1, 0), (21, 0), (1, 10), (21, 10)]
    for ox in (2, 28):
        board_face(im, rnd, ox, 2, 24, 12, rivets, (6, 2))
    end_grain(im, rnd, 0, 2, 2, 12)
    end_grain(im, rnd, 26, 2, 2, 12)
    # stick 2 x 14 x 2 at (0,14): top (2,14), bottom (4,14), front (2,16), west (0,16), east (4,16), back (6,16)
    end_grain(im, rnd, 2, 14, 2, 2)
    end_grain(im, rnd, 4, 14, 2, 2)
    for ox in (0, 2, 4, 6):
        wood_panel(im, rnd, ox, 16, 2, 14, lit=False)
        streaks(im, rnd, ox, 16, 2, 14, False, 2)
    return im


if __name__ == "__main__":
    h = Image.open(os.path.join(ROOT, "stable_sign_hanging.png")).convert("RGBA")
    s = Image.open(os.path.join(ROOT, "stable_sign_standing.png")).convert("RGBA")
    if len(sys.argv) > 2:
        sheet = Image.new("RGB", (64 * 8 * 2 + 30, 32 * 8 + 20), (120, 126, 136))
        for i, im in enumerate((h, s)):
            big = im.resize((512, 256), Image.NEAREST)
            bg = Image.new("RGB", big.size, (214, 218, 224))
            bg.paste(big, (0, 0), big)
            sheet.paste(bg, (10 + i * 522, 10))
        sheet.save(os.path.join(sys.argv[2], "sign_textures.png"))
    print("ok")
