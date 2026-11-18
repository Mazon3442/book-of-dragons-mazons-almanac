"""Generate vanilla-based paper-and-moss poultice and reddish jerky-strip treats."""
import io
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/assets/bodaddon/textures/item"


with zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.265-minecraft-resources-aka-client-extra.jar") as vanilla:
    def load(path):
        return Image.open(io.BytesIO(vanilla.read("assets/minecraft/textures/" + path + ".png"))).convert("RGBA")

    poultice = load("item/paper")
    paper_colors = {
        (135, 135, 135): (101, 82, 67),
        (174, 174, 174): (152, 129, 98),
        (193, 193, 193): (181, 157, 119),
        (214, 214, 214): (209, 187, 146),
        (233, 234, 235): (232, 212, 174),
        (252, 252, 242): (250, 235, 201),
    }
    poultice.putdata([paper_colors.get(p[:3], p[:3]) + (p[3],) for p in poultice.getdata()])
    moss = load("block/moss_block")
    mask = Image.new("L", (16, 16))
    ImageDraw.Draw(mask).polygon([(7, 4), (10, 5), (11, 7), (10, 9), (8, 11), (5, 9), (4, 7)], fill=255)
    poultice.paste(moss, (0, 0), mask)
    poultice.save(RES / "dragon_poultice.png")

    meat = load("item/cooked_beef")
    treat = Image.new("RGBA", (16, 16))
    strips = [
        [(1, 11), (6, 3), (8, 2), (9, 4), (4, 13), (2, 14)],
        [(6, 13), (11, 5), (13, 4), (14, 6), (10, 13), (8, 15)],
    ]
    for points in strips:
        mask = Image.new("L", (16, 16))
        ImageDraw.Draw(mask).polygon(points, fill=255)
        for y in range(16):
            for x in range(16):
                if not mask.getpixel((x, y)):
                    continue
                r, g, b, _ = meat.getpixel((5 + x % 5, 5 + y % 5))
                edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16)
                           or not mask.getpixel((x + dx, y + dy)) for dx, dy in [(-1, 0), (1, 0)])
                light = 0.72 if edge else 1.20
                treat.putpixel((x, y), (min(255, round(r * 1.12 * light)), round(g * 0.72 * light),
                                       round(b * 0.65 * light), 255))
        draw = ImageDraw.Draw(treat)
        if points == strips[0]:
            draw.line([(3, 10), (5, 7), (7, 4)], fill="#c77d4b")
        else:
            draw.line([(9, 11), (11, 8), (12, 6)], fill="#b76b40")
    treat.save(RES / "dragon_treat.png")

print("Generated vanilla-based dragon poultice and treat textures.")
