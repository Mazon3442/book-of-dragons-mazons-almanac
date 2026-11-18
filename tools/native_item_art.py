"""Vanilla material samples for the addon's trough, stable plate and pear sprites."""
import io
import json
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
VANILLA = ROOT / "build/moddev/artifacts/neoforge-21.1.265-minecraft-resources-aka-client-extra.jar"


def texture(name):
    with zipfile.ZipFile(VANILLA) as archive:
        return Image.open(io.BytesIO(archive.read(f"assets/minecraft/textures/{name}.png"))).convert("RGBA")


def shade(pixel, amount):
    return tuple(min(255, round(c * amount)) for c in pixel[:3]) + (pixel[3],)


def trough_icon():
    spec = json.loads((ROOT / "tools/feeding_trough.json").read_text())
    wood = texture("block/spruce_planks")
    iron = texture("block/iron_block")
    gold = texture("item/gold_ingot")
    im = Image.new("RGBA", (16, 16))
    wood_light = {"A": 0.38, "B": 0.66, "C": 0.94, "D": 1.18, "E": 1.4}
    iron_light = {"K": 0.24, "L": 0.42, "M": 0.66, "N": 0.94}
    for y, row in enumerate(spec["grid"]):
        for x, symbol in enumerate(row):
            if symbol in wood_light:
                im.putpixel((x, y), shade(wood.getpixel((x, y)), wood_light[symbol]))
            elif symbol in iron_light:
                im.putpixel((x, y), shade(iron.getpixel((x, y)), iron_light[symbol]))
            elif symbol == "g":
                im.putpixel((x, y), gold.getpixel((7, 7)))
    return im


def plate_icon():
    im = Image.new("RGBA", (16, 16))
    wood = texture("block/dark_oak_planks")
    post = texture("block/spruce_log")
    iron = texture("block/iron_block")
    horns = Image.new("L", (16, 16))
    draw = ImageDraw.Draw(horns)
    left = [(4, 0), (5, 0), (5, 3), (6, 4), (6, 5), (2, 5), (2, 4), (4, 4)]
    draw.polygon(left, fill=255)
    draw.polygon([(15 - x, y) for x, y in left], fill=255)
    for y in range(6):
        for x in range(16):
            if horns.getpixel((x, y)):
                im.putpixel((x, y), shade(post.getpixel((x, y)), 1.35 if x in (4, 10) else 0.85))
    for x in range(2, 14):
        im.putpixel((x, 4), shade(wood.getpixel((x, 4)), 1.45))
    for y in range(5, 13):
        for x in range(1, 15):
            if y in (5, 12) or x in (1, 14):
                light = 0.34 if y == 5 or x == 1 else 0.17
                im.putpixel((x, y), shade(iron.getpixel((x, y)), light))
            else:
                light = 0.75 if y in (8, 10) else 1.20
                im.putpixel((x, y), shade(wood.getpixel((x, y)), light))
    for y in range(13, 16):
        im.putpixel((7, y), shade(post.getpixel((7, y)), 1.35))
        im.putpixel((8, y), shade(post.getpixel((8, y)), 0.65))
    return im


def sagefruit_icon(pear):
    apple = texture("item/apple")
    gradient = [(111, 154, 69), (169, 182, 83), (224, 187, 76), (231, 142, 49), (191, 83, 43)]
    im = pear.copy()
    for y in range(3, 15):
        xs = [x for x in range(16) if pear.getpixel((x, y))[3]]
        samples = [apple.getpixel((x, y)) for x in range(16) if apple.getpixel((x, y))[3]]
        if not xs or not samples:
            continue
        position = (y - 3) / 11 * (len(gradient) - 1)
        index = min(int(position), len(gradient) - 2)
        blend = position - index
        color = tuple(round(a * (1 - blend) + b * blend) for a, b in zip(gradient[index], gradient[index + 1]))
        for i, x in enumerate(xs):
            sample = samples[round(i / max(1, len(xs) - 1) * (len(samples) - 1))]
            light = 0.48 + sample[0] / 255 * 0.65
            im.putpixel((x, y), shade(color + (255,), light))
    return im
