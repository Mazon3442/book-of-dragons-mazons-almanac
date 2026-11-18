"""Composites a preview of the Dragon Editor screen from vanilla GUI sprites and the vanilla bitmap font.
The dragon preview is a placeholder silhouette (the real one is the live entity renderer)."""
import io, os, sys, zipfile
from PIL import Image, ImageDraw

JAR = os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
z = zipfile.ZipFile(JAR)
def tex(p): return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + p))).convert("RGBA")

FONT = tex("font/ascii.png")
GLYPH = {}
for code in range(32, 127):
    cx, cy = (code % 16) * 8, (code // 16) * 8
    g = FONT.crop((cx, cy, cx + 8, cy + 8)); w = 0
    for x in range(8):
        if any(g.getpixel((x, y))[3] for y in range(8)): w = x + 1
    GLYPH[chr(code)] = (g, w if w else 3)

def text_width(s): return sum(GLYPH[c][1] + 1 for c in s)
def draw_text(img, s, x, y, color, shadow=False):
    def put(ox, oy, col):
        cx = x + ox
        for c in s:
            g, w = GLYPH[c]
            tinted = Image.new("RGBA", g.size, col + (255,)); tinted.putalpha(g.getchannel("A"))
            img.alpha_composite(tinted, (cx, y + oy)); cx += w + 1
    if shadow: put(1, 1, tuple(c // 4 for c in color))
    put(0, 0, color)

def nine(sprite, w, h):
    sw, sh = sprite.size; out = Image.new("RGBA", (w, h))
    half = w // 2
    out.paste(sprite.crop((0, 0, min(half, sw // 2), sh)), (0, 0))
    out.paste(sprite.crop((sw - (w - half), 0, sw, sh)), (half, 0))
    return out

BTN, BTN_OFF, SLIDER, HANDLE = (tex("gui/sprites/widget/button.png"), tex("gui/sprites/widget/button_disabled.png"),
                                tex("gui/sprites/widget/slider.png"), tex("gui/sprites/widget/slider_handle.png"))

def button(img, x, y, label, enabled=True, w=150):
    img.alpha_composite(nine(BTN if enabled else BTN_OFF, w, 20), (x, y))
    draw_text(img, label, x + (w - text_width(label)) // 2, y + 6, (255, 255, 255) if enabled else (160, 160, 160), True)

def slider(img, x, y, frac, label, w=150):
    img.alpha_composite(nine(SLIDER, w, 20), (x, y))
    img.alpha_composite(HANDLE, (x + int(frac * (w - 8)), y))
    draw_text(img, label, x + (w - text_width(label)) // 2, y + 6, (255, 255, 255), True)

def panel(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=(0, 0, 0, 255))
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=(255, 255, 255, 255))
    d.rectangle([x + 3, y + 3, x + w - 2, y + h - 2], fill=(85, 85, 85, 255))
    d.rectangle([x + 3, y + 3, x + w - 4, y + h - 4], fill=(198, 198, 198, 255))

def inset(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=(255, 255, 255, 255))
    d.rectangle([x, y, x + w - 2, y + h - 2], fill=(55, 55, 55, 255))
    d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=(27, 27, 27, 255))

TABS = [("lead", "Bond"), ("golden_apple", "Health and Care"), ("enchanted_book", "Personality"), ("cooked_salmon", "Friendships")]
def screen(tab, w=320, h=220):
    img = Image.new("RGBA", (w, h), (24, 28, 30, 255))
    d = ImageDraw.Draw(img)
    for y in range(0, h, 16):  # faint world-blur backdrop
        d.rectangle([0, y, w, y + 8], fill=(30, 36, 38, 255))
    PW, PH, PAD = 254, 160, 8
    left, top = (w - PW) // 2, (h - PH) // 2 + 12
    panel(img, left, top, PW, PH)
    for i, (item, _) in enumerate(TABS):
        sel = i == tab
        sp = tex(f"gui/sprites/container/creative_inventory/tab_top_{'selected' if sel else 'unselected'}_{i + 1}.png")
        img.alpha_composite(sp, (left + 6 + i * 27, top - 28))
        img.alpha_composite(tex(f"item/{item}.png"), (left + 6 + i * 27 + 5, top - 28 + 9))
    x, y = left + PAD, top + PAD
    draw_text(img, TABS[tab][1], x, y, (64, 64, 64))
    y += 12
    if tab == 0:
        button(img, x, y, "Already Tamed", False)
        slider(img, x, y + 26, 0.78, "Loyalty: Warming Up (780)")
        for i, line in enumerate(["Trusts you and will follow", "your lead."]): draw_text(img, line, x, y + 52 + i * 10, (64, 64, 64))
    elif tab == 1:
        slider(img, x, y, 0.6, "Health: 74 / 120")
        button(img, x, y + 26, "Heal Injuries")
        button(img, x, y + 50, "Clear Breeding Timer")
        draw_text(img, "Injured", x, y + 78, (64, 64, 64)); draw_text(img, "Breeding timer: 5m 12s", x, y + 90, (64, 64, 64))
    elif tab == 2:
        for i, (n, v) in enumerate([("Aggression", .4), ("Bravery", .9), ("Dominance", .6), ("Energy", .75), ("Nervousness", .25), ("Sympathy", .7)]):
            slider(img, x, y + i * 22, v, f"{n}: {int(v * 100)}%")
    else:
        button(img, x, y, "Befriend Nearby Dragons")
        for i, line in enumerate(["Every dragon within 32 blocks", "will stop fighting and get along."]): draw_text(img, line, x, y + 28 + i * 10, (64, 64, 64))
    px, py = left + PW - PAD - 80, top + PAD
    inset(img, px, py, 80, 92)
    # placeholder dragon silhouette
    d = ImageDraw.Draw(img)
    cx, cy = px + 40, py + 56
    d.polygon([(cx - 30, cy + 6), (cx - 8, cy - 6), (cx + 6, cy - 10), (cx + 20, cy - 24), (cx + 30, cy - 20), (cx + 22, cy - 12), (cx + 14, cy - 2), (cx + 18, cy + 18), (cx + 6, cy + 20), (cx, cy + 6), (cx - 14, cy + 20), (cx - 24, cy + 18)], fill=(40, 42, 52, 255))
    d.polygon([(cx - 6, cy - 4), (cx - 26, cy - 34), (cx - 2, cy - 26), (cx + 14, cy - 6)], fill=(52, 54, 66, 255))
    d.rectangle([cx + 22, cy - 19, cx + 24, cy - 18], fill=(110, 230, 120, 255))
    draw_text(img, "Night Fury", px + (80 - text_width("Night Fury")) // 2, py + 96, (64, 64, 64))
    button(img, px, top + PH - PAD - 20, "Done", True, 80)
    return img

if __name__ == "__main__":
    out = sys.argv[1]
    S = 3
    imgs = [screen(i).resize((320 * S, 220 * S), Image.NEAREST) for i in range(4)]
    sheet = Image.new("RGBA", (320 * S * 2 + 10, 220 * S * 2 + 10), (0, 0, 0, 255))
    for i, im in enumerate(imgs): sheet.paste(im, ((i % 2) * (320 * S + 10), (i // 2) * (220 * S + 10)))
    sheet.save(out)
