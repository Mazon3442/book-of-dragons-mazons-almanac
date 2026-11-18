"""Preview of the Berk field journal design: one menu screen plus the 16x16 staff sprite."""
import sys
from PIL import Image, ImageDraw
from render_ui import draw_text, text_width, tex

def C(h): h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
INK, FADED, PARCH, SHADE, LIGHT = C("3B2A1A"), C("6B5238"), C("D9C59A"), C("C9B283"), C("E8D9B4")
WD, WM, WL = C("3A2616"), C("5A3A22"), C("7A5230")
LEA, LEAH, STITCH = C("6B3F24"), C("8A5632"), C("C9A46A")
IRON, IRONH = C("4A4D52"), C("A9ADB2")
EMB, EMBH, EMBD = C("E8761C"), C("FFC060"), C("A43A10")
BONE = C("E6DCC3")
rgb = lambda c: c[:3]

def panel(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=WD)
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=WM)
    d.rectangle([x + 2, y + 2, x + w - 3, y + h - 3], fill=WL)
    d.rectangle([x + 3, y + 3, x + w - 4, y + h - 4], fill=WD)
    d.rectangle([x + 4, y + 4, x + w - 5, y + h - 5], fill=PARCH)
    for i in range(0, w * h // 7):  # sparse speckle
        px, py = x + 5 + (i * 37) % (w - 10), y + 5 + (i * 59 + i // 7) % (h - 10)
        if (i * 13) % 5 == 0: img.putpixel((px, py), SHADE)
        elif (i * 7) % 11 == 0: img.putpixel((px, py), LIGHT)
    for cx, cy in ((x + 2, y + 2), (x + w - 5, y + 2), (x + 2, y + h - 5), (x + w - 5, y + h - 5)):
        d.rectangle([cx, cy, cx + 2, cy + 2], fill=IRON); img.putpixel((cx, cy), IRONH)

def button(img, x, y, w, label, enabled=True, hover=False):
    d = ImageDraw.Draw(img)
    base, top = (LEA, LEAH) if enabled else (C("7A6A58"), C("8E7E6A"))
    d.rectangle([x, y, x + w - 1, y + 19], fill=WD)
    d.rectangle([x + 1, y + 1, x + w - 2, y + 18], fill=base)
    d.rectangle([x + 1, y + 1, x + w - 2, y + 2], fill=top)
    if hover: d.rectangle([x, y, x + w - 1, y + 19], outline=EMB)
    for sx in range(x + 4, x + w - 4, 3): img.putpixel((sx, y + 16), STITCH if enabled else FADED)
    for sx in range(x + 4, x + w - 4, 3): img.putpixel((sx, y + 3 + 0), base)
    col = BONE if enabled else C("B8A98F")
    draw_text(img, label, x + (w - text_width(label)) // 2, y + 6, rgb(col), enabled)

def slider(img, x, y, w, label, value_txt, frac):
    draw_text(img, label, x, y, rgb(INK)); draw_text(img, value_txt, x + w - text_width(value_txt), y, rgb(FADED))
    d = ImageDraw.Draw(img); gy = y + 12
    d.rectangle([x, gy, x + w - 1, gy + 6], fill=WD); d.rectangle([x + 1, gy + 1, x + w - 2, gy + 5], fill=WM)
    fx = x + 1 + int(frac * (w - 10))
    d.rectangle([x + 1, gy + 2, fx, gy + 4], fill=EMB); d.rectangle([x + 1, gy + 2, fx, gy + 2], fill=EMBH)
    d.rectangle([fx - 1, gy - 2, fx + 4, gy + 8], fill=WD); d.rectangle([fx, gy - 1, fx + 3, gy + 7], fill=IRON)
    d.rectangle([fx, gy - 1, fx + 3, gy], fill=IRONH)

def header(img, x, y, w, text):
    draw_text(img, text, x, y, rgb(INK))
    tw = text_width(text) + 5; d = ImageDraw.Draw(img)
    d.line([x + tw, y + 4, x + w - 5, y + 4], fill=SHADE); d.line([x + tw, y + 5, x + w - 5, y + 5], fill=LIGHT)
    d.polygon([(x + w - 3, y + 1), (x + w, y + 4), (x + w - 3, y + 7), (x + w - 6, y + 4)], fill=EMB)

def bookmark(img, x, y, item, selected):
    d = ImageDraw.Draw(img); ox = 5 if selected else 0
    x += ox; w = 22 + (0 if selected else 0)
    d.rectangle([x - 2 + 0, y, x + w, y + 19], fill=WD)
    d.rectangle([x - 1, y + 1, x + w - 1, y + 18], fill=LEA)
    d.rectangle([x - 1, y + 1, x + w - 1, y + 2], fill=LEAH)
    d.polygon([(x + w, y), (x + w - 4, y + 9), (x + w, y + 19)], fill=C("00000000"))  # notch
    if selected: d.rectangle([x + w - 3, y + 1, x + w - 1, y + 18], fill=EMB)
    for sy in range(y + 3, y + 17, 3): img.putpixel((x + 1, sy), STITCH)
    img.alpha_composite(tex(f"item/{item}.png"), (x + 3, y + 2))

def screen(w=300, h=200):
    img = Image.new("RGBA", (w, h), (27, 24, 22, 255))
    ImageDraw.Draw(img).rectangle([0, 0, w, h], fill=C("1D1A18"))
    PW, PH = 250, 160; L, T = (w - PW) // 2 - 12, (h - PH) // 2
    for i, it in enumerate(["lead", "golden_apple", "enchanted_book", "cooked_salmon"]):
        bookmark(img, L + PW - 1, T + 14 + i * 24, it, i == 0)
    panel(img, L, T, PW, PH)
    x, y = L + 11, T + 10
    header(img, x, y, 148, "Bond")
    button(img, x, y + 14, 148, "Tame instantly", True, True)
    slider(img, x, y + 42, 148, "Loyalty", "Warming up  780", 0.78)
    draw_text(img, "Trusts you and will", x, y + 66, rgb(FADED)); draw_text(img, "follow your lead.", x, y + 76, rgb(FADED))
    header(img, x, y + 94, 148, "Status")
    ImageDraw.Draw(img).rectangle([x, y + 109, x + 2, y + 111], fill=EMB)
    draw_text(img, "Injured, mends in 2 days", x + 6, y + 107, rgb(INK))
    px, py = L + PW - 11 - 80, T + 10
    d = ImageDraw.Draw(img)
    d.rectangle([px, py, px + 79, py + 91], fill=WD); d.rectangle([px + 1, py + 1, px + 78, py + 90], fill=WL)
    d.rectangle([px + 2, py + 2, px + 77, py + 89], fill=SHADE); d.rectangle([px + 3, py + 3, px + 76, py + 88], fill=C("B79F6D"))
    for ix in (px + 5, px + 72):
        d.rectangle([ix, py + 5, ix + 2, py + 7], fill=IRON); img.putpixel((ix, py + 5), IRONH)
    cx, cy = px + 40, py + 56   # silhouette placeholder
    d.polygon([(cx - 30, cy + 6), (cx - 8, cy - 6), (cx + 6, cy - 10), (cx + 20, cy - 24), (cx + 30, cy - 20), (cx + 22, cy - 12), (cx + 14, cy - 2), (cx + 18, cy + 18), (cx + 6, cy + 20), (cx, cy + 6), (cx - 14, cy + 20), (cx - 24, cy + 18)], fill=C("2A2C36"))
    d.polygon([(cx - 6, cy - 4), (cx - 26, cy - 34), (cx - 2, cy - 26), (cx + 14, cy - 6)], fill=C("3A3C48"))
    draw_text(img, "Night Fury", px + (80 - text_width("Night Fury")) // 2, py + 95, rgb(INK))
    button(img, px, T + PH - 10 - 20, 80, "Done")
    return img

def sprite():
    P = {"o": WD, "w": WM, "W": WL, "b": BONE, "B": C("B9AC8E"), "e": EMB, "E": EMBH, "r": EMBD, "l": LEA, "L": LEAH, "i": IRON, "I": IRONH, "s": STITCH}
    art = [
        "........obbo....",
        ".......obBBbo...",
        ".......obeEeb...",
        "......ob.eEreb..",
        "......oBbereb...",
        ".......oBbbBo...",
        "......oWoooo....",
        ".....oWwo.......",
        "....oWwo........",
        "...olwo.........",
        "..olLo..........",
        ".olsLo..........",
        "oiIlo...........",
        "oiIo............",
        ".oo.............",
        "................",
    ]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for yy, row in enumerate(art):
        for xx, ch in enumerate(row):
            if ch in P: im.putpixel((xx, yy), P[ch])
    return im

if __name__ == "__main__":
    S = 4; s = screen().resize((300 * S, 200 * S), Image.NEAREST)
    sp = sprite(); bg = Image.new("RGBA", (16, 16), (139, 139, 139, 255)); bg.alpha_composite(sp)
    sheet = Image.new("RGBA", (300 * S + 20 + 16 * 12, 200 * S), (0, 0, 0, 255))
    sheet.paste(s, (0, 0)); sheet.paste(bg.resize((192, 192), Image.NEAREST), (300 * S + 20, 0))
    sheet.save(sys.argv[1])
