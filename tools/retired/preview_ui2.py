"""Field-board layout proof using shipped sprites and Minecraft's font; dragon is a placeholder silhouette."""
import os
import sys
import re
from pathlib import Path

from PIL import Image, ImageDraw

import gen_ui as U
import gen_ui2 as skin
from preview_ui import INK, nine
from render_ui import draw_text, text_width

D = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "bodaddon", "textures", "gui", "sprites")
def spr(n): return Image.open(os.path.join(D, n + ".png")).convert("RGBA")

SOURCE = (Path(__file__).parent / '../src/main/java/local/bodaddon/client/DragonEditorScreen.java').read_text()
def constant(name):
    return int(re.search(r'private static final int ' + name + r' = (\d+);', SOURCE)[1])

PW, PH = constant('PANEL_W'), constant('PANEL_H')
MARGIN, SHEET_H, PAD = constant('SHEET_MARGIN'), constant('SHEET_H'), constant('PAD_X')
COL_W, FRAME_W, FRAME_H = constant('COL_W'), constant('FRAME_W'), constant('FRAME_H')
TRAIT_W, CHART_W = constant('TRAIT_COL_W'), constant('CHART_W')
FADED = (108,77,50)


def trim(text, width):
    while text and text_width(text) > width: text = text[:-1]
    return text


def header(img,x,y,w,text):
    draw_text(img,trim(text,w),x,y,INK)
    ImageDraw.Draw(img).rectangle([x,y+11,x+w-1,y+11],fill=(184,138,85))
    d=ImageDraw.Draw(img)
    d.line([(x+5,y+13),(x+w//2-1,y+13)],fill=(200,156,101))
    d.line([(x+w//2+4,y+13),(x+w-19,y+13)],fill=(200,156,101))


def button(img,x,y,w,label,kind='normal'):
    img.alpha_composite(nine(spr('button_'+kind),w,20,3),(x,y))
    text=trim(label,w-8)
    draw_text(img,text,x+(w-text_width(text))//2,y+6,(240,216,181) if kind=='disabled' else (254,227,191))


def slider(img,x,y,w,label,value,frac):
    draw_text(img,trim(label,w-text_width(value)-5),x,y,INK)
    draw_text(img,value,x+w-text_width(value),y,FADED)
    ty=y+11
    img.alpha_composite(nine(spr('slider_track'),w,8,3),(x,ty))
    fx=x+2+int(frac*(w-12))
    d=ImageDraw.Draw(img)
    d.rectangle([x+2,ty+2,max(x+2,fx-1),ty+4],fill=(224,115,28))
    d.line([(x+2,ty+2),(max(x+2,fx-1),ty+2)],fill=(255,176,74))
    img.alpha_composite(spr('slider_handle'),(fx-2,ty-2))


def radar(img, cx, cy, r, vals):
    import math
    names = ["Aggression", "Bravery", "Dominance", "Energy", "Nerves", "Sympathy"]
    d = ImageDraw.Draw(img)
    def pt(i, rr):
        a = math.radians(-90 + 60 * i)
        return (cx + round(math.cos(a) * rr), cy + round(math.sin(a) * rr))
    for ring in (1, 2, 3):
        pts = [pt(i, r * ring // 3) for i in range(6)]
        d.polygon(pts, outline=(166, 124, 78, 255) if ring == 3 else (208, 174, 126, 255))
    for i in range(6):
        d.line([(cx, cy), pt(i, r)], fill=(214, 184, 135, 255))
    pts = [pt(i, r * (0.06 + 0.94 * vals[i])) for i in range(6)]
    over = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(over).polygon(pts, fill=(113, 63, 40, 153))
    img.alpha_composite(over)
    d.polygon(pts, outline=(90, 56, 35, 255))
    for (x, y) in pts:
        d.rectangle([x - 1, y - 1, x, y], fill=(59, 41, 30, 255))
    for i, n in enumerate(names):
        vx, vy = pt(i, r)
        w = text_width(n)
        x = vx - w // 2 if i in (0, 3) else (vx if i in (1, 2) else vx - w)
        x = max(cx-CHART_W//2,min(x,cx+CHART_W//2-w))
        y = vy - 10 if i in (0, 1, 5) else vy + 3
        draw_text(img, n, x, y, INK)


def screen(tab_in, w=360, h=250):
    picker = tab_in == 5
    tab = 3 if picker else tab_in
    img = Image.new("RGBA", (w, h), (22, 26, 24, 255))
    L, T = (w - PW) // 2 + 12, (h - PH) // 2
    tabs = [("orange", "bond"), ("teal", "vitals"), ("green", "mind"), ("purple", "variants"), ("blue", "social")]
    for i, (c, _) in enumerate(tabs):
        sp = spr("tab_" + c + ("_selected" if i == tab else ""))
        img.alpha_composite(sp, (L - (sp.width - 9), T + 38 + i * 29))
    img.alpha_composite(nine(spr("satchel"), PW, PH, 10), (L, T))
    sx, sy, sw = L + MARGIN, T + constant('SHEET_TOP'), PW - 2 * MARGIN
    img.alpha_composite(nine(spr("sheet"), sw, SHEET_H, 6), (sx, sy))
    img.alpha_composite(spr('editor_crest'),(L+17,T+10))
    draw_text(img,'Dragon editor',L+36,T+12,(254,227,191))
    draw_text(img,'Night Fury',L+PW-17-text_width('Night Fury'),T+12,(254,227,191))
    draw_text(img,'Changes apply immediately',L+20,T+210,(254,227,191))
    for i, (c, ic) in enumerate(tabs):
        sp = spr("tab_" + c + ("_selected" if i == tab else ""))
        img.alpha_composite(spr("icon_" + ic), (L - (sp.width - 9) + 5, T + 38 + i * 29 + 5))
    x, y = sx + PAD, sy + PAD
    names = ["Bond", "Health and care", "Personality", "Colors and markings", "Friendships"]
    header(img, x, y, TRAIT_W if tab==2 else COL_W, "Dorsal color" if picker else names[tab])
    y += 15
    if tab == 0:
        button(img, x, y, COL_W, "Tame instantly", "hover")
        slider(img, x, y + 28, COL_W, "Loyalty", "Warming up", 0.78)
        for i, t in enumerate(["Trusts you and will follow", "your lead."]):
            draw_text(img, t, x, y + 52 + i * 10, FADED)
    elif tab == 1:
        slider(img, x, y, COL_W, "Max health", "104", 0.67)
        for k in range(4):
            nx = x + 4 + round(k * (COL_W - 12) / 3)
            ImageDraw.Draw(img).rectangle([nx, y + 17, nx, y + 18], fill=(214, 184, 135, 255))
        slider(img, x, y + 26, COL_W, "Health", "74 / 104", 0.7)
        button(img, x, y + 52, COL_W, "Heal injuries")
        button(img, x, y + 76, COL_W, "Clear breeding timer", "disabled")
        img.alpha_composite(U.dot(U.RED, U.C("E87050")), (x, y + 105))
        draw_text(img, "Injured", x + 8, y + 104, INK)
        img.alpha_composite(U.dot(U.GREEN, U.C("9AD05A")), (x, y + 117))
        draw_text(img, "Breeding timer 5m 12s", x + 8, y + 116, INK)
    elif tab == 2:
        traits = [("Aggression", .4), ("Bravery", .9), ("Dominance", .6), ("Energy", .75), ("Nervousness", .25), ("Sympathy", .7)]
        for i, (n, v) in enumerate(traits):
            slider(img, x, y + i * 22, TRAIT_W, n, f"{int(v * 100)}%", v)
    elif tab == 3:
        if picker:
            header_label = "Dorsal Color"
            names = ["Flint", "Dust", "Stone", "Argent", "Ash", "Deep crystal", "Soft gray", "Steel blue"]
            ramp = [(26, 26, 43, 255), (44, 45, 67, 255), (70, 72, 98, 255), (104, 108, 140, 255), (146, 150, 180, 255), (190, 194, 214, 255)]
            d = ImageDraw.Draw(img)
            for k, nm in enumerate(names):
                ry = y + k * 14
                if k == 3:
                    d.rectangle([x, ry, x + COL_W - 11, ry + 12], fill=(227, 188, 130, 255))
                    d.rectangle([x, ry, x + 1, ry + 12], fill=(166, 124, 78, 255))
                d.rectangle([x + 4, ry + 2, x + 5 + 18, ry + 10], fill=(90, 56, 35, 255))
                for c, col in enumerate(ramp):
                    d.rectangle([x + 5 + c * 3, ry + 3, x + 7 + c * 3, ry + 9], fill=col)
                draw_text(img, nm, x + 27, ry + 3, INK if k == 3 else FADED)
            d.rectangle([x + COL_W - 4, y, x + COL_W - 1, y + 111], fill=(214, 184, 135, 255))
            d.rectangle([x + COL_W - 4, y + 30, x + COL_W - 1, y + 52], fill=(166, 124, 78, 255))
            rows = []
        else:
            rows = [("Base Color", "Raven", 3, 40), ("Dorsal Color", "Flint", 12, 35), ("Underbelly Color", "Argent", 7, 35),
                ("Gradient Color", "Soot", 9, 19), ("Eye Color", "Eye common 3", 3, 30)]
        for i, (lab, opt, cur, n) in enumerate(rows):
            ry = y + i * 22
            draw_text(img, lab, x, ry, FADED)
            d = ImageDraw.Draw(img)
            d.rectangle([x, ry + 9, x + COL_W - 1, ry + 19], fill=(166, 124, 78, 255))
            d.rectangle([x + 1, ry + 10, x + COL_W - 2, ry + 18], fill=(235, 203, 156, 255))
            d.rectangle([x + 1, ry + 10, x + COL_W - 2, ry + 10], fill=(214, 184, 135, 255))
            ramp=[(26,26,43),(44,45,67),(70,72,98),(104,108,140),(146,150,180),(190,194,214)]
            d.rectangle([x+3,ry+10,x+22,ry+18],fill=(90,56,35))
            for c,col in enumerate(ramp): d.rectangle([x+4+c*3,ry+11,x+6+c*3,ry+17],fill=col)
            draw_text(img, ">", x + COL_W - 4 - text_width(">"), ry + 11, FADED)
            draw_text(img, trim(opt,COL_W-38), x+26, ry+11, INK)
        if not picker:
            button(img, x, y + 112, 20, "<", "disabled")
            button(img, x + COL_W - 20, y + 112, 20, ">")
            pg = "Page 1 of 4"
            draw_text(img, pg, x + (COL_W - text_width(pg)) // 2, y + 118, FADED)
    else:
        button(img, x, y, COL_W, "Befriend nearby dragons")
        for i, t in enumerate(["Every dragon within 32", "blocks will stop fighting", "and get along."]):
            draw_text(img, t, x, y + 28 + i * 10, FADED)
    px_, py_ = sx + sw - PAD - FRAME_W, sy + PAD
    if tab == 2:
        radar(img, sx + sw - PAD - CHART_W + CHART_W//2, py_ + 50, constant('CHART_R'), [v for _, v in traits])
        draw_text(img, "Trait balance", sx + sw - PAD - CHART_W + (CHART_W - text_width("Trait balance")) // 2, py_ + 96, FADED)
        button(img, px_, sy + SHEET_H - PAD - 20, FRAME_W, "Done")
        return img
    img.alpha_composite(nine(spr("card"), FRAME_W, FRAME_H, 4), (px_, py_))
    img.alpha_composite(spr("pin"), (px_ + 4, py_ + 1))
    img.alpha_composite(spr("pin"), (px_ + FRAME_W - 13, py_ + 1))
    dr = ImageDraw.Draw(img)
    cx, cy = px_ + FRAME_W//2, py_ + FRAME_H//2
    dr.polygon([(cx - 28, cy + 6), (cx - 8, cy - 6), (cx + 6, cy - 10), (cx + 18, cy - 22), (cx + 28, cy - 18), (cx + 20, cy - 10), (cx + 12, cy - 2), (cx + 16, cy + 16), (cx + 6, cy + 18), (cx, cy + 6), (cx - 12, cy + 18), (cx - 22, cy + 16)], fill=(37, 38, 46, 255))
    dr.polygon([(cx - 6, cy - 4), (cx - 24, cy - 32), (cx - 2, cy - 24), (cx + 12, cy - 6)], fill=(52, 54, 66, 255))
    if not picker:
        draw_text(img, "Dragon preview", px_ + (FRAME_W - text_width("Dragon preview")) // 2, py_ + FRAME_H + 4, INK)
    button(img, px_, sy + SHEET_H - PAD - 20, FRAME_W, "Back" if picker else "Done")
    if picker:
        for k, t in enumerate(["Drag to turn", "Scroll to zoom"]):
            draw_text(img, t, px_ + (FRAME_W - text_width(t)) // 2, py_ + FRAME_H + 4 + k * 10, FADED)
    return img


if __name__ == "__main__":
    S = 2
    sheet = Image.new('RGBA',(360*S*3,250*S*2),(22,26,24,255))
    for i in range(6):
        im=screen(i).resize((360*S,250*S),Image.Resampling.NEAREST)
        sheet.paste(im,(i%3*360*S,i//3*250*S))
    sheet.save(sys.argv[1])
