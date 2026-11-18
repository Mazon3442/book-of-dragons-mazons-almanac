"""Hand-drawn 16x16 pixel art for the Dragon Editor, built from hue-shifted tone ramps."""
from PIL import Image

def C(h): h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

# Each ramp runs dark to light; shadows lean cool/purple, highlights lean warm/yellow.
WOOD   = [C(h) for h in ("2a1a1f", "4a2c2a", "6b4132", "8f5e3a", "b98448")]
BONE   = [C(h) for h in ("5a4a56", "8c7a7c", "bfae9a", "e6d8bf", "fff6e0")]
IRON   = [C(h) for h in ("21242e", "3d4252", "5f677a", "8c94a8", "c8cfdc")]
LEATH  = [C(h) for h in ("3e2024", "6a3a2c", "94512f", "bd7340")]
EMBER  = [C(h) for h in ("6e1a14", "b8340f", "ee6a18", "ffac2e", "fff3a6")]
GLOW   = C("f0a860")

def build(art, palette):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in palette: im.putpixel((x, y), palette[ch])
    return im

PAL = {}
for key, ramp in (("w", WOOD), ("b", BONE), ("i", IRON), ("l", LEATH), ("e", EMBER)):
    for n, col in enumerate(ramp): PAL[f"{key}{n}"] = col
# one-character aliases: letter = material, digit packed into the map as a second grid row-pair is awkward,
# so the sprite below uses single glyphs from this table.
G = {
    ".": None,
    "A": WOOD[0], "B": WOOD[1], "C": WOOD[2], "D": WOOD[3], "E": WOOD[4],
    "F": BONE[0], "G": BONE[1], "H": BONE[2], "I": BONE[3], "J": BONE[4],
    "K": IRON[0], "L": IRON[1], "M": IRON[2], "N": IRON[3], "O": IRON[4],
    "P": LEATH[0], "Q": LEATH[1], "R": LEATH[2], "S": LEATH[3],
    "T": EMBER[0], "U": EMBER[1], "V": EMBER[2], "W": EMBER[3], "X": EMBER[4],
    "y": GLOW,
}
G = {k: v for k, v in G.items() if v}

SPRITE = [
    "............GF..",
    "..........FHIG..",
    ".........FHI..GF",
    "........FHUVUG.F",
    "........GUVWXUHF",
    "........HUWXWVHF",
    "........GHUVUGHF",
    ".......FGHFTFGF.",
    ".......FHGFHGF..",
    "......FGBDFF....",
    ".....KNCDEB.....",
    "....KMCDEB......",
    "...PRSDEB.......",
    "..PQRCDB........",
    ".KNLBCB.........",
    ".KOMKB..........",
]

def _staff_cells():
    """Diagonal staff matching the 3D model: iron cap, leather grip with rope bands, wood shaft, iron collar,
    three bone claws caging a glowing ember. Cells are (x, y) -> colour from G."""
    c = {}
    def put(x, y, g): c[(x, y)] = G[g]
    # one pair of cells per row; lit cell at x = 14 - y, shaded cell at x = 15 - y
    rows = {
        15: ("", "M"),                 # iron cap
        14: ("O", "M"),
        13: ("S", "R"),                # leather grip
        12: ("I", "G"),                # rope band
        11: ("S", "Q"),                # leather grip
        10: ("I", "G"),                # rope band
        9: ("E", "C"),                 # wood shaft
        8: ("E", "D"),
        7: ("E", "C"),
        6: ("D", "C"),
        5: ("O", "M"),                 # iron collar
    }
    for y, (lit, shade) in rows.items():
        if lit: put(14 - y, y, lit)
        put(15 - y, y, shade)
    put(6, 9, "B")                     # grain fleck on the shaded side
    put(9, 6, "O"); put(10, 6, "M")    # collar block
    # west claw (lit bone) rising from the collar and curling over the ember
    for (x, y, g) in ((9, 5, "I"), (8, 4, "I"), (8, 3, "J"), (8, 2, "I"), (9, 1, "J"), (10, 0, "J"), (11, 0, "I")):
        put(x, y, g)
    # east claw (shaded bone)
    for (x, y, g) in ((12, 5, "H"), (13, 4, "H"), (14, 3, "I"), (14, 2, "H"), (13, 1, "I"), (13, 0, "H"), (12, 0, "G")):
        put(x, y, g)
    # ember: 3x4 glow, hot core, dark rim on the shaded side
    for (x, y, g) in ((10, 1, "V"), (11, 1, "W"), (12, 1, "V"),
                      (10, 2, "W"), (11, 2, "X"), (12, 2, "W"),
                      (10, 3, "V"), (11, 3, "W"), (12, 3, "U"),
                      (10, 4, "U"), (11, 4, "V"), (12, 4, "T")):
        put(x, y, g)
    return c


def _outline(cells):
    out = dict(cells)
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n in cells or not (0 <= n[0] < 16 and 0 <= n[1] < 16):
                continue
            # shaded side (empty cell right of / below the filled one) gets the darkest tone; lit side a softer one
            out[n] = WOOD[0] if (dx, dy) in ((1, 0), (0, 1)) else WOOD[1]
    return out


def sprite():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), col in _outline(_staff_cells()).items():
        im.putpixel((x, y), col)
    return im


if __name__ == "__main__":
    import sys
    Z = 24
    s = sprite()
    bg = Image.new("RGBA", (16, 16), (139, 139, 139, 255)); bg.alpha_composite(s)
    dk = Image.new("RGBA", (16, 16), (40, 40, 48, 255)); dk.alpha_composite(s)
    out = Image.new("RGBA", (16 * Z * 2 + 20, 16 * Z), (0, 0, 0, 255))
    out.paste(bg.resize((16 * Z, 16 * Z), Image.NEAREST), (0, 0))
    out.paste(dk.resize((16 * Z, 16 * Z), Image.NEAREST), (16 * Z + 20, 0))
    out.save(sys.argv[1])
