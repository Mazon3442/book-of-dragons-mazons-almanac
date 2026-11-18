"""Composites the journal-style Dragon Editor from the generated sprites, mirroring the Java layout."""
import sys

from PIL import Image

import gen_ui as U
from render_ui import draw_text, text_width

INK = (59, 41, 30)
FADED = (122, 89, 59)
PW, PH = 250, 184


def nine(sprite, w, h, b):
    """GUI nine-slice: corners fixed, edges and middle tiled (the game's default for nine_slice sprites)."""
    if isinstance(b, int):
        l = r = t = bt = b
    else:
        l, r, t, bt = b["left"], b["right"], b["top"], b["bottom"]
    sw, sh = sprite.size
    out = Image.new("RGBA", (w, h))
    xs = [(0, l, 0, l), (l, w - r, l, sw - r), (w - r, w, sw - r, sw)]
    ys = [(0, t, 0, t), (t, h - bt, t, sh - bt), (h - bt, h, sh - bt, sh)]
    for dx0, dx1, sx0, sx1 in xs:
        for dy0, dy1, sy0, sy1 in ys:
            if dx1 <= dx0 or dy1 <= dy0 or sx1 <= sx0 or sy1 <= sy0:
                continue
            tile = sprite.crop((sx0, sy0, sx1, sy1))
            for ty in range(dy0, dy1, tile.height):
                for tx in range(dx0, dx1, tile.width):
                    piece = tile.crop((0, 0, min(tile.width, dx1 - tx), min(tile.height, dy1 - ty)))
                    out.alpha_composite(piece, (tx, ty))
    return out


def button(img, x, y, w, label, kind="normal"):
    img.alpha_composite(nine(U.button(kind), w, 20, 5), (x, y))
    col = {"normal": (254, 227, 191), "hover": (255, 240, 210), "disabled": (190, 170, 140)}[kind]
    draw_text(img, label, x + (w - text_width(label)) // 2, y + 7, (30, 20, 14))
    draw_text(img, label, x + (w - text_width(label)) // 2, y + 6, col)


def slider(img, x, y, w, label, value, frac):
    draw_text(img, label, x, y, INK)
    draw_text(img, value, x + w - text_width(value), y, FADED)
    ty = y + 11
    img.alpha_composite(nine(U.slider_track(), w, 8, 3), (x, ty))
    fx = x + 2 + int(frac * (w - 12))
    fill = Image.new("RGBA", (max(1, fx - x - 1), 3), U.EMBER)
    fill.paste(U.EMBER_L, (0, 0, fill.width, 1))
    img.alpha_composite(fill, (x + 2, ty + 2))
    img.alpha_composite(U.slider_handle(), (fx - 2, ty - 2))


def header(img, x, y, w, text):
    draw_text(img, text, x, y, INK)
    tw = text_width(text) + 5
    img.alpha_composite(nine(U.rule(), w - tw, 5, {"left": 2, "right": 2, "top": 0, "bottom": 0}), (x + tw, y + 2))


def screen(tab, w=330, h=210):
    img = Image.new("RGBA", (w, h), (20, 24, 22, 255))
    L, T = (w - PW) // 2 + 14, (h - PH) // 2
    tabs = [("orange", "bond"), ("teal", "vitals"), ("green", "mind"), ("blue", "social")]
    for i, (c, ic) in enumerate(tabs):
        sel = i == tab
        sp = U.ribbon(c, sel)
        tx = L - (sp.width - 9) + (0 if sel else 0)
        ty = T + 12 + i * 27
        img.alpha_composite(sp, (tx - (5 if False else 0), ty))
    img.alpha_composite(nine(U.panel(), PW, PH, 8), (L, T))
    for i, (c, ic) in enumerate(tabs):
        sel = i == tab
        sp = U.ribbon(c, sel)
        tx = L - (sp.width - 9)
        ty = T + 12 + i * 27
        img.alpha_composite(U.icon(ic), (tx + 5, ty + 5))
    x, y = L + 14, T + 12
    names = ["Bond", "Health and care", "Personality", "Friendships"]
    header(img, x, y, 136, names[tab])
    y += 15
    if tab == 0:
        button(img, x, y, 136, "Tame instantly", "hover")
        slider(img, x, y + 28, 136, "Loyalty", "Warming up", 0.78)
        for i, t in enumerate(["Trusts you and will follow", "your lead."]):
            draw_text(img, t, x, y + 54 + i * 10, FADED)
        header(img, x, y + 82, 136, "Status")
        img.alpha_composite(U.dot(U.RED, U.C("E87050")), (x, y + 99))
        draw_text(img, "Injured, mends in 2 days", x + 8, y + 98, INK)
    elif tab == 1:
        slider(img, x, y, 136, "Health", "74 / 120", 0.6)
        button(img, x, y + 28, 136, "Heal injuries")
        button(img, x, y + 52, 136, "Clear breeding timer", "disabled")
        img.alpha_composite(U.dot(U.RED, U.C("E87050")), (x, y + 83))
        draw_text(img, "Injured", x + 8, y + 82, INK)
        img.alpha_composite(U.dot(U.GREEN, U.C("9AD05A")), (x, y + 95))
        draw_text(img, "Breeding timer 5m 12s", x + 8, y + 94, INK)
    elif tab == 2:
        for i, (n, v) in enumerate([("Aggression", .4), ("Bravery", .9), ("Dominance", .6), ("Energy", .75), ("Nervousness", .25), ("Sympathy", .7)]):
            slider(img, x, y + i * 24, 136, n, f"{int(v * 100)}%", v)
    else:
        button(img, x, y, 136, "Befriend nearby dragons")
        for i, t in enumerate(["Every dragon within 32", "blocks will stop fighting", "and get along."]):
            draw_text(img, t, x, y + 28 + i * 10, FADED)
    px_, py_ = L + PW - 14 - 76, T + 12
    img.alpha_composite(nine(U.frame(), 76, 90, 5), (px_, py_))
    d = Image.new("RGBA", (1, 1))
    from PIL import ImageDraw
    dr = ImageDraw.Draw(img)
    cx, cy = px_ + 38, py_ + 54
    dr.polygon([(cx - 28, cy + 6), (cx - 8, cy - 6), (cx + 6, cy - 10), (cx + 18, cy - 22), (cx + 28, cy - 18), (cx + 20, cy - 10), (cx + 12, cy - 2), (cx + 16, cy + 16), (cx + 6, cy + 18), (cx, cy + 6), (cx - 12, cy + 18), (cx - 22, cy + 16)], fill=(37, 38, 46, 255))
    dr.polygon([(cx - 6, cy - 4), (cx - 24, cy - 32), (cx - 2, cy - 24), (cx + 12, cy - 6)], fill=(52, 54, 66, 255))
    draw_text(img, "Night Fury", px_ + (76 - text_width("Night Fury")) // 2, py_ + 94, INK)
    button(img, px_, T + PH - 12 - 20, 76, "Done")
    return img


if __name__ == "__main__":
    S = 3
    imgs = [screen(i).resize((330 * S, 210 * S), Image.NEAREST) for i in range(4)]
    sheet = Image.new("RGBA", (330 * S * 2 + 10, 210 * S * 2 + 10), (0, 0, 0, 255))
    for i, im in enumerate(imgs):
        sheet.paste(im, ((i % 2) * (330 * S + 10), (i // 2) * (210 * S + 10)))
    sheet.save(sys.argv[1])
