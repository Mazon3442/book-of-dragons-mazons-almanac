"""Generate sage tree resources from the project's Minecraft 1.21.1 schemas."""
import json
import io
import argparse
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw
from native_item_art import sagefruit_icon

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
VANILLA = zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.265-minecraft-resources-aka-client-extra.jar")
parser = argparse.ArgumentParser(description=__doc__)
selection = parser.add_mutually_exclusive_group()
selection.add_argument("--leaves-only", dest="only", action="store_const", const=("sage_leaves", "sage_leaves_fruit"))
selection.add_argument("--fruit-only", dest="only", action="store_const", const=("sagefruit",))
selection.add_argument("--sapling-only", dest="only", action="store_const", const=("sage_sapling",))
ONLY = parser.parse_args().only


def write(path, value):
    if ONLY:
        return
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def vanilla(path):
    return json.loads(VANILLA.read(path))


# The tree itself is the hand-built structure data/bodaddon/structure/sage_tree.nbt, placed by SageTreeFeature.
write("data/bodaddon/worldgen/configured_feature/sage_tree.json", {"type": "bodaddon:sage_tree", "config": {}})
write("data/bodaddon/worldgen/placed_feature/sage_tree.json", {
    "feature": "bodaddon:sage_tree",
    "placement": [
        {"type": "minecraft:rarity_filter", "chance": 300},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:block_predicate_filter", "predicate": {
            "type": "minecraft:would_survive", "state": {"Name": "bodaddon:sage_sapling"}}},
        {"type": "minecraft:biome"},
    ],
})
write("data/bodaddon/tags/worldgen/biome/has_sage_tree.json", {"replace": False, "values": [
    "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
    "minecraft:old_growth_birch_forest", "minecraft:dark_forest",
]})
write("data/bodaddon/neoforge/biome_modifier/add_sage_trees.json", {
    "type": "neoforge:add_features", "biomes": "#bodaddon:has_sage_tree",
    "features": "bodaddon:sage_tree", "step": "vegetal_decoration",
})

loot = vanilla("data/minecraft/loot_table/blocks/oak_leaves.json")
loot = json.loads(json.dumps(loot).replace("minecraft:oak_leaves", "bodaddon:sage_leaves")
                  .replace("minecraft:oak_sapling", "bodaddon:sage_sapling")
                  .replace("minecraft:apple", "bodaddon:sagefruit")
                  .replace("minecraft:blocks/oak_leaves", "bodaddon:blocks/sage_leaves"))
loot["pools"][2]["entries"][0]["conditions"] = [
    {"condition": "minecraft:survives_explosion"},
    {"condition": "minecraft:block_state_property", "block": "bodaddon:sage_leaves", "properties": {"fruiting": "true"}},
]
write("data/bodaddon/loot_table/blocks/sage_leaves.json", loot)
sapling_loot = vanilla("data/minecraft/loot_table/blocks/oak_sapling.json")
sapling_loot = json.loads(json.dumps(sapling_loot).replace("minecraft:oak_sapling", "bodaddon:sage_sapling")
                          .replace("minecraft:blocks/oak_sapling", "bodaddon:blocks/sage_sapling"))
write("data/bodaddon/loot_table/blocks/sage_sapling.json", sapling_loot)
for tag, block in [("leaves", "sage_leaves"), ("saplings", "sage_sapling"), ("mineable/hoe", "sage_leaves")]:
    write(f"data/minecraft/tags/block/{tag}.json", {"replace": False, "values": [f"bodaddon:{block}"]})
for tag, item in [("leaves", "sage_leaves"), ("saplings", "sage_sapling")]:
    write(f"data/minecraft/tags/item/{tag}.json", {"replace": False, "values": [f"bodaddon:{item}"]})

for name, parent, texture_key in [("sage_leaves", "cube_all", "all"), ("sage_sapling", "cross", "cross")]:
    write(f"assets/bodaddon/blockstates/{name}.json", {"variants": {"": {"model": f"bodaddon:block/{name}"}}})
    write(f"assets/bodaddon/models/block/{name}.json", {
        "parent": f"minecraft:block/{parent}", "render_type": "minecraft:cutout",
        "textures": {texture_key: f"bodaddon:block/{name}"},
    })
    model = {"parent": "bodaddon:block/sage_leaves"} if name == "sage_leaves" else {
        "parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:block/sage_sapling"}}
    write(f"assets/bodaddon/models/item/{name}.json", model)
write("assets/bodaddon/blockstates/sage_leaves.json", {"variants": {
    "fruiting=false": {"model": "bodaddon:block/sage_leaves"},
    "fruiting=true": {"model": "bodaddon:block/sage_leaves_fruit"},
}})
write("assets/bodaddon/models/block/sage_leaves_fruit.json", {
    "parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
    "textures": {"all": "bodaddon:block/sage_leaves_fruit"},
})
write("assets/bodaddon/models/item/sagefruit.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/sagefruit"},
})

LANG = {
    "item.bodaddon.sagefruit": "Sagefruit",
    "item.bodaddon.sagefruit.desc1": "Feed to a dragon to befriend",
    "item.bodaddon.sagefruit.desc2": "every dragon within 32 blocks.",
    "block.bodaddon.sage_leaves": "Sage Leaves",
    "block.bodaddon.sage_sapling": "Sage Sapling",
    "bodaddon.sagefruit.owner_only": "Use sagefruit on a wild dragon or your own dragon.",
    "bodaddon.sagefruit.no_new_friends": "No new dragon friends nearby. Save this sagefruit for later.",
    "bodaddon.sagefruit.befriended": "This dragon formed %s lasting dragon friendships.",
}
lang = json.loads((RES / "assets/bodaddon/lang/en_us.json").read_text(encoding="utf-8"))
lang.update(LANG)
write("assets/bodaddon/lang/en_us.json", lang)

# Pear gradient, top to bottom: green fading through yellow to orange.
PEAR = [(111, 154, 69), (150, 172, 80), (196, 176, 80), (226, 160, 64), (222, 118, 52), (190, 82, 44)]


def pear_tone(t, k=1.0):
    """Colour at t in 0..1 down the fruit; k < 1 darkens (outline, shade), k > 1 lightens (highlight)."""
    pos = t * (len(PEAR) - 1)
    i = min(int(pos), len(PEAR) - 2)
    f = pos - i
    rgb = [PEAR[i][c] * (1 - f) + PEAR[i + 1][c] * f for c in range(3)]
    if k > 1:
        rgb = [c + (255 - c) * (k - 1) for c in rgb]
    else:
        rgb = [c * k for c in rgb]
    return tuple(int(c) for c in rgb) + (255,)


def pear(draw, x, y, rows):
    """rows = [(left, right), ...] relative spans from the top; shaded by row, lit on the left, dark rim."""
    n = len(rows)
    for r, (a, b) in enumerate(rows):
        t = r / (n - 1)
        for px in range(a, b + 1):
            rim = px in (a, b) or r in (0, n - 1)
            k = 0.62 if rim else (1.25 if px == a + 1 and 0 < r < n - 1 else 0.88 if px == b - 1 else 1.0)
            draw.point((x + px, y + r), fill=pear_tone(t, k))


# Reuse vanilla azalea's clustered foliage and cutout mask; recolor its four native tones to sage.
leaves = Image.open(io.BytesIO(VANILLA.read("assets/minecraft/textures/block/azalea_leaves.png"))).convert("RGBA")
SAGE_COLORS = {
    (58, 76, 38): (46, 65, 56),
    (80, 105, 44): (70, 94, 73),
    (108, 128, 49): (101, 125, 91),
    (112, 146, 45): (143, 163, 117),
}
leaves.putdata([SAGE_COLORS.get(pixel[:3], pixel[:3]) + (pixel[3],) for pixel in leaves.getdata()])


def fruit_blob(draw, x, y):
    """Small hanging pear, 4 wide and 6 tall."""
    pear(draw, x, y, [(1, 2), (1, 2), (1, 2), (0, 3), (0, 3), (1, 2)])


leaves_fruit = leaves.copy()
d = ImageDraw.Draw(leaves_fruit)
for x, y in [(2, 3), (9, 1), (7, 9)]:
    fruit_blob(d, x, y)

# The sapling has its own editable silhouette, rather than inheriting a vanilla tree's shape.
sapling_art = json.loads((ROOT / "tools/sage_sapling.json").read_text(encoding="utf-8"))
sapling_grid = sapling_art["grid"]
if len(sapling_grid) != 16 or any(len(row) != 16 for row in sapling_grid):
    raise ValueError("Sage sapling artwork must be a 16x16 pixel grid.")
sapling_palette = {key: tuple(bytes.fromhex(value.lstrip("#"))) + (255,)
                   for key, value in sapling_art["palette"].items()}
sapling = Image.new("RGBA", (16, 16))
for y, row in enumerate(sapling_grid):
    for x, symbol in enumerate(row):
        if symbol != ".":
            sapling.putpixel((x, y), sapling_palette[symbol])

# Item sprite, hand placed per the Blockbench Minecraft style guide: midtone shape, clearly darker outline that shifts
# with the fruit (green, olive, red-brown), light from the top left, hue-shifted ramps (cool shadows, warm highlights).
FRUIT_ART = [
    "................",
    ".......W.lLL....",
    ".......WlLLl....",
    "......aaaa......",
    "......aGga......",
    "......aGga......",
    ".....aGgyha.....",
    ".....bGYyzb.....",
    "....bGYyyzzb....",
    "...bGYyyoozzb...",
    "...cYyoOooppc...",
    "...cyoOoooppc...",
    "....cooOoopc....",
    ".....cppppc.....",
    "......cccc......",
    "................",
]
FRUIT_PAL = {c: tuple(bytes.fromhex(h)) + (255,) for c, h in {
    "a": "2f4a30", "b": "58502a", "c": "6e3128",      # outline
    "G": "a9d06a", "g": "78a84a", "h": "4d7a40",      # green: light, mid, shadow
    "Y": "e6e08c", "y": "c3b84e", "z": "88883f",      # yellow
    "O": "f8c260", "o": "e08a3a", "p": "b2542f",      # orange
    "W": "5a3d28", "w": "9a7340", "l": "34502f", "L": "76a64a",  # stem, leaf
}.items()}
fruit = Image.new("RGBA", (16, 16))
for y, row in enumerate(FRUIT_ART):
    assert len(row) == 16, (y, row)
    for x, ch in enumerate(row):
        if ch in FRUIT_PAL:
            fruit.putpixel((x, y), FRUIT_PAL[ch])
fruit = sagefruit_icon(fruit)

for name, im, folder in [("sage_leaves", leaves, "block"), ("sage_leaves_fruit", leaves_fruit, "block"), ("sage_sapling", sapling, "block"), ("sagefruit", fruit, "item")]:
    if ONLY and name not in ONLY:
        continue
    dest = RES / f"assets/bodaddon/textures/{folder}/{name}.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    im.save(dest)
preview = Image.new("RGBA", (64, 16), "#26252a")
for n, im in enumerate([fruit, sapling, leaves, leaves_fruit]):
    preview.alpha_composite(im, (16 * n, 0))
preview.resize((512, 128), Image.Resampling.NEAREST).save(ROOT / ".scratch/sage_preview.png")
print(f"Generated {', '.join(ONLY)} textures." if ONLY else "Generated sagefruit, sage tree assets, loot and world generation.")
