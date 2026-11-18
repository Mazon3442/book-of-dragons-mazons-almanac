"""Eel UV painting and vanilla-derived repellent materials; no gameplay/data writes."""
import json
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw
from native_item_art import texture, shade

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/bodaddon'


def color(value):
    return ImageColor.getrgb(value) + (255,)


def skin():
    atlas = Image.new('RGBA', (32, 32))
    # Each band uses a global body coordinate, including across hinged joins.
    dark = ['#354348', '#253337', '#172429']
    gold = ['#ebce68', '#c6a345', '#947732']

    def tone(z, face):
        return color((gold if z >= 3 and z % 4 == 0 else dark)[face])

    def box(u, v, w, h, depth, start):
        for z in range(depth):
            for x in range(w):
                # Cube's horizontal face UVs run backwards along depth.
                atlas.putpixel((u + depth + x, v + depth - 1 - z), tone(start + z, 0))
                atlas.putpixel((u + depth + w + x, v + depth - 1 - z), tone(start + z, 2))
            for y in range(h):
                atlas.putpixel((u + depth - 1 - z, v + depth + y), tone(start + z, 1 + y))
                atlas.putpixel((u + depth + w + z, v + depth + y), tone(start + z, 1 + y))
        for offset, z in ((u + depth, start), (u + 2 * depth + w, start + depth - 1)):
            for y in range(h):
                for x in range(w):
                    atlas.putpixel((offset + x, v + depth + y), tone(z, 1 + y))

    box(0, 0, 2, 2, 3, 0)
    box(0, 6, 2, 2, 5, 3)
    box(0, 14, 2, 2, 5, 8)
    box(0, 22, 2, 2, 4, 13)
    atlas.putpixel((2, 3), color('#e5ce81'))
    atlas.putpixel((5, 3), color('#e5ce81'))
    for x in (3, 4):
        atlas.putpixel((x, 4), color('#142126'))
    # Zero-width vertical fins: visible sides are at v+depth, not at v.
    for v, start in ((8, 3), (14, 8)):
        for z in range(5):
            for x in (20 - z, 21 + z):
                atlas.putpixel((x, v + 5), tone(start + z, 1))
    for y in range(4):
        for z in range(3):
            for x in (16 + z, 19 + z):
                atlas.putpixel((x, 3 + y), color('#b49a48' if y in (0, 3) else '#35494b'))
    # Pectoral fins are zero-height boxes: paint both top and bottom UVs.
    for v in (0, 2):
        for y in range(2):
            for x in range(26, 30):
                atlas.putpixel((x, v + y), color('#526361' if y == 0 else '#2b3f43'))
    return atlas


def raw_eel():
    spec = json.loads((ROOT / 'tools/eel_item.json').read_text())
    assert len(spec['grid']) == 16 and all(len(row) == 16 for row in spec['grid'])
    image = Image.new('RGBA', (16, 16))
    for y, row in enumerate(spec['grid']):
        for x, glyph in enumerate(row):
            if glyph != '.':
                image.putpixel((x, y), color(spec['palette'][glyph]))
    return image


def bucket():
    image = texture('item/water_bucket')
    # The water occupies rows 3-5. Keep the entire metal bucket untouched.
    rows = ['.hdYdo..', 'Yd...dYo', '.oYdo...']
    palette = {'h': '#596867', 'd': '#303e42', 'o': '#182328', 'Y': '#ddbd55'}
    for y, row in enumerate(rows, 3):
        for x, glyph in enumerate(row, 4):
            if glyph != '.':
                base = image.getpixel((x, y))
                if base[2] > base[0] * 1.25 and base[2] > base[1] * 1.1:
                    image.putpixel((x, y), color(palette[glyph]))
    return image


def bottle():
    liquid = texture('item/potion_overlay')
    for y in range(16):
        for x in range(16):
            r, g, b, a = liquid.getpixel((x, y))
            if a:
                liquid.putpixel((x, y), (round(r * .58), round(g * .68), round(b * .24), a))
    liquid.alpha_composite(texture('item/potion'))
    return liquid


def spawn_egg():
    image = Image.new('RGBA', (16, 16))
    # Keep vanilla's egg silhouette and shading, with the eel's charcoal/gold colors.
    for name, tint in [('spawn_egg', (53, 67, 72)), ('spawn_egg_overlay', (235, 206, 104))]:
        layer = texture('item/' + name)
        for y in range(16):
            for x in range(16):
                pixel = layer.getpixel((x, y))
                if pixel[3]:
                    layer.putpixel((x, y), tuple(round(pixel[i] * tint[i] / 255) for i in range(3)) + (pixel[3],))
        image.alpha_composite(layer)
    return image


def repellent_face(top=False):
    # A slatted timber crate with narrow openings onto the packed moss and roots.
    moss = texture('block/moss_block')
    wood = texture('block/spruce_planks')
    image = wood.copy()
    draw = ImageDraw.Draw(moss)
    roots = [[(5, 3), (6, 5), (5, 8), (6, 12)], [(10, 3), (9, 7), (10, 10), (9, 12)]]
    for path in roots:
        draw.line(path, fill='#ad814b', width=1)
    for y in range(16):
        for x in range(16):
            inset = 2 <= x <= 13 and 2 <= y <= 13
            if inset and x in (5, 6, 9, 10):
                image.putpixel((x, y), shade(moss.getpixel((x, y)), .68))
            else:
                # Rotate plank grain to follow the vertical boards and corner posts.
                light = 1.12 if x in (2, 7, 11) else .72 if x in (4, 8, 13) else .94
                if not inset:
                    light = 1.35 if y in (0, 14) or x == 0 else .65 if y in (1, 15) or x == 15 else 1.05
                image.putpixel((x, y), shade(wood.getpixel((y, x)), light))
    # Broad diagonal face brace; the lid uses straight end rails instead.
    if not top:
        for y in range(2, 14):
            for x in range(2, 14):
                diagonal = x + y
                if 14 <= diagonal <= 16:
                    light = 1.4 if diagonal == 14 else .69 if diagonal == 16 else 1.18
                    image.putpixel((x, y), shade(wood.getpixel((x, y)), light))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        image.putpixel((x, y), color('#34332d'))
    if not top:
        for x, y in ((3, 12), (12, 3)):
            image.putpixel((x, y), color('#b5b19b'))
    return image


def generate():
    images = {'entity/eel.png': skin(), 'item/eel.png': raw_eel(),
              'item/eel_spawn_egg.png': spawn_egg(),
              'item/eel_bucket.png': bucket(), 'item/dragon_repellent.png': bottle(),
              'block/dragon_repellent_block.png': repellent_face(),
              'block/dragon_repellent_block_top.png': repellent_face(top=True)}
    for path, image in images.items():
        assert image.mode == 'RGBA' and set(image.getchannel('A').getdata()) <= {0, 255}
        image.save(ASSETS / 'textures' / path)


if __name__ == '__main__':
    generate()
