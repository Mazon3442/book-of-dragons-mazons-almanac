"""Layout proof of the war slate editor from the shipped sprites and the vanilla bitmap font (a grey block stands in for the dragon).

python tools/preview_slate.py OUT_DIR       writes slate_vitals.png, slate_mind.png, slate_variants.png, slate_bond.png at 3x
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

from render_ui import draw_text, text_width

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bodaddon", "textures", "gui", "sprites")
INK, FADED, EMBER, EMBER_L = (239, 228, 203), (169, 159, 140), (250, 128, 26), (255, 174, 72)
PW, PH = 330, 217
LABEL_COL, VALUE_COL, COL_W = 66, 54, 204


def spr(n):
    return Image.open(os.path.join(D, n + ".png")).convert("RGBA")


def nine(name, w, h):
    sp = spr(name)
    meta = os.path.join(D, name + ".png.mcmeta")
    if not os.path.exists(meta):
        return sp
    b = json.load(open(meta))["gui"]["scaling"]["border"]
    l, r, t, bt = (b["left"], b["right"], b["top"], b["bottom"]) if isinstance(b, dict) else (b, b, b, b)
    sw, sh = sp.size
    out = Image.new("RGBA", (w, h))

    def tile(src_box, dst_box):
        sx0, sy0, sx1, sy1 = src_box
        dx0, dy0, dx1, dy1 = dst_box
        piece = sp.crop(src_box)
        for y in range(dy0, dy1, max(1, sy1 - sy0)):
            for x in range(dx0, dx1, max(1, sx1 - sx0)):
                out.alpha_composite(piece.crop((0, 0, min(sx1 - sx0, dx1 - x), min(sy1 - sy0, dy1 - y))), (x, y))

    tile((0, 0, l, t), (0, 0, l, t))
    tile((sw - r, 0, sw, t), (w - r, 0, w, t))
    tile((0, sh - bt, l, sh), (0, h - bt, l, h))
    tile((sw - r, sh - bt, sw, sh), (w - r, h - bt, w, h))
    tile((l, 0, sw - r, t), (l, 0, w - r, t))
    tile((l, sh - bt, sw - r, sh), (l, h - bt, w - r, h))
    tile((0, t, l, sh - bt), (0, t, l, h - bt))
    tile((sw - r, t, sw, sh - bt), (w - r, t, w, h - bt))
    tile((l, t, sw - r, sh - bt), (l, t, w - r, h - bt))
    return out


def blit(img, name, x, y, w=None, h=None):
    img.alpha_composite(spr(name) if w is None else nine(name, w, h), (x, y))


def text(img, s, x, y, col, shadow=False):
    draw_text(img, s, x, y, col, shadow)


def slider(img, x, y, label, value, frac):
    blit(img, "row", x, y, COL_W, 17)
    text(img, label, x + 6, y + 5, INK)
    text(img, value, x + COL_W - 6 - text_width(value), y + 5, INK)
    tx, tw = x + LABEL_COL, COL_W - LABEL_COL - VALUE_COL - 4
    ty = y + 4
    blit(img, "slider_track", tx, ty, tw, 8)
    fill_end = tx + 2 + int(frac * (tw - 12))
    d = ImageDraw.Draw(img)
    d.rectangle([tx + 2, ty + 2, max(tx + 2, fill_end - 1), ty + 4], fill=EMBER)
    d.rectangle([tx + 2, ty + 2, max(tx + 2, fill_end - 1), ty + 2], fill=EMBER_L)
    img.alpha_composite(spr("slider_handle"), (fill_end - 2, ty - 2))


def status(img, x, y, label, dot, value):
    blit(img, "row", x, y, COL_W, 17)
    text(img, label, x + 6, y + 5, INK)
    blit(img, "readout", x + LABEL_COL, y + 3, COL_W - LABEL_COL - 4, 11)
    img.alpha_composite(spr(dot), (x + LABEL_COL + 4, y + 6))
    text(img, value, x + LABEL_COL + 13, y + 4, INK)


def button(img, x, y, w, h, label, cheat=False, kind="normal"):
    blit(img, "button_" + ("cheat_" if cheat else "") + kind, x, y, w, h)
    left = x + (16 if cheat else 0)
    tw = w - (16 if cheat else 0)
    tx = left + (tw - text_width(label)) // 2
    ty = y + (h - 7) // 2
    text(img, label, tx, ty + 1, (16, 17, 19))
    text(img, label, tx, ty, (239, 228, 203) if kind != "disabled" else (138, 132, 120))


def variant(img, x, y, label, option, hot=False):
    blit(img, "row", x, y, COL_W, 19)
    text(img, label, x + 6, y + 5, INK if hot else FADED)
    fx, fw = x + LABEL_COL, COL_W - LABEL_COL - 4
    blit(img, "readout", fx, y + 3, fw, 13)
    text(img, option, fx + 6, y + 6, INK)
    text(img, ">", fx + fw - 4 - text_width(">"), y + 6, EMBER_L if hot else FADED)


def frame(tab, title, bodyfn, picker=False):
    img = Image.new("RGBA", (PW, PH + 26), (20, 20, 24, 255))
    top = 26
    img.alpha_composite(spr("slate"), (0, top))
    tabs = [("bond", "Bond"), ("vitals", "Vitals"), ("mind", "Mind"), ("variants", "Variants"), ("social", "Social")]
    for i, (icon, label) in enumerate(tabs):
        sel = i == tab
        x = 48 + i * 52
        img.alpha_composite(spr("tab_cloth_selected" if sel else "tab_cloth"), (x, 0))
        img.alpha_composite(spr("icon_" + icon), (x + 17, 9))
        text(img, label, x + (48 - text_width(label)) // 2, 24, INK if sel else FADED, True)
    name, species = "Toothless", "Night Fury"
    big = Image.new("RGBA", (text_width(name) + 2, 10), (0, 0, 0, 0))
    text(big, name, 0, 0, INK, True)
    big = big.resize((big.width * 2, big.height * 2), Image.NEAREST)
    img.alpha_composite(big, (16, top + 14 + 3))
    sx = PW - 16 - text_width(species)
    text(img, species, sx, top + 14 + 7, INK, True)
    bx, be = 16 + text_width(name) * 2 + 10, sx - 10
    blit(img, "bar", bx, top + 21, be - bx, 8)
    for rx in range(bx + 5, be - 6, 18):
        img.alpha_composite(spr("rivet"), (rx, top + 23))
    px = 116 + (COL_W - 150) // 2
    blit(img, "title_plate", px, top + 41, 150, 16)
    t = title.upper()
    text(img, t, px + (150 - text_width(t)) // 2, top + 45, INK)
    text(img, "Changes apply instantly", 14, top + 197, FADED)
    tag = "CREATIVE"
    tw = text_width(tag) + 16
    tx = PW - 12 - 56 - 4 - tw
    blit(img, "title_plate", tx, top + 194, tw, 14)
    text(img, tag, tx + 8, top + 197, INK)
    button(img, PW - 12 - 56, top + 194, 56, 14, "Done")
    vh = 126 if picker else 82
    blit(img, "viewport_frame", 10, top + 41, 98, vh)
    ImageDraw.Draw(img).rectangle([40, top + 50, 78, top + 41 + vh - 12], fill=(60, 60, 70, 255))
    if not picker:
        cx, cy, r = 59, top + 158, 22
        d = ImageDraw.Draw(img)
        vals = [0.8, 0.5, 0.65, 0.4, 0.7, 0.55]
        for ring in (1, 2, 3):
            rp = [(cx + math.cos(math.radians(-90 + 60 * i)) * r * ring / 3, cy + math.sin(math.radians(-90 + 60 * i)) * r * ring / 3) for i in range(6)]
            d.polygon(rp, outline=(93, 92, 98) if ring == 3 else (53, 54, 58))
        pts = []
        for i, v in enumerate(vals):
            rr = r * (0.06 + 0.94 * v)
            pts.append((cx + math.cos(math.radians(-90 + 60 * i)) * rr, cy + math.sin(math.radians(-90 + 60 * i)) * rr))
        ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(ov).polygon(pts, fill=(250, 128, 26, 102), outline=EMBER)
        img.alpha_composite(ov)
        for i, lab in enumerate(["AGG", "BRV", "ENR", "SYM", "NRV", "DOM"]):
            vx = cx + math.cos(math.radians(-90 + 60 * i)) * r
            vy = cy + math.sin(math.radians(-90 + 60 * i)) * r
            w = text_width(lab)
            x = vx - w / 2 if i in (0, 3) else (vx + 3 if i in (1, 2) else vx - w - 2)
            x = max(10, min(10 + 98 - w, x))
            y = vy - 10 if i == 0 else vy + 3 if i == 3 else (vy - 8 if i in (1, 5) else vy + 1)
            text(img, lab, int(x), int(y), FADED)
    else:
        for k, s in enumerate(("Drag to turn", "Scroll to zoom")):
            text(img, s, 10 + (98 - text_width(s)) // 2, top + 41 + vh + 4 + k * 10, FADED)
    bodyfn(img, 116, top + 63)
    return img


def vitals(img, x, y):
    slider(img, x, y, "Max health", "60", 0.5)
    slider(img, x, y + 21, "Health", "58 / 60", 0.97)
    status(img, x, y + 42, "Injury", "dot_ok", "Not injured")
    status(img, x, y + 63, "Breeding", "dot_ember", "79m 59s left")
    button(img, x, y + 84, COL_W, 20, "Heal injuries", True, "disabled")
    button(img, x, y + 108, COL_W, 20, "Clear breeding timer", True, "hover")


def mind(img, x, y):
    for i, (n, v) in enumerate((("Aggression", 0.8), ("Bravery", 0.5), ("Dominance", 0.65), ("Energy", 0.4), ("Nerves", 0.7), ("Sympathy", 0.55))):
        slider(img, x, y + i * 21, n, f"{int(v * 100)}%", v)


def variants(img, x, y):
    for i, (a, b) in enumerate((("Head 1", "Plain Night"), ("Head 2", "Dusk"), ("Wings", "Storm Stripe"), ("Body", "Plain"), ("Tail", "Ash"))):
        variant(img, x, y + i * 21, a, b, hot=i == 1)
    button(img, x, y + 107, 28, 16, "<", False, "disabled")
    button(img, x + COL_W - 28, y + 107, 28, 16, ">")
    s = "Page 1 of 3"
    text(img, s, x + (COL_W - text_width(s)) // 2, y + 111, FADED)


def bond(img, x, y):
    button(img, x, y, COL_W, 20, "Already tamed", True, "disabled")
    slider(img, x, y + 25, "Loyalty", "Devoted", 1.0)
    text(img, "Bonded for life. Fully devoted to its rider.", x, y + 52, FADED)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name, img in (("slate_vitals", frame(1, "Health and care", vitals)), ("slate_mind", frame(2, "Personality", mind)),
                      ("slate_variants", frame(3, "Colors and markings", variants)), ("slate_bond", frame(0, "Bond", bond))):
        img.resize((img.width * 3, img.height * 3), Image.NEAREST).save(os.path.join(out, name + ".png"))
    print("ok")
