"""Generate the wild dragon nip grass: 2-block plant art, models, sword/shears loot, world generation and tags."""
import json
import io
import sys
import zipfile
from pathlib import Path
from PIL import Image
from gen_nip import tint_foliage

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
VANILLA = zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.265-minecraft-resources-aka-client-extra.jar")
TEXTURES_ONLY = "--textures-only" in sys.argv


def write(path, value):
    if TEXTURES_ONLY:
        return
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def vanilla(path):
    return json.loads(VANILLA.read(path))


# Vanilla's tall-grass blades already join correctly across the two-block seam.
upper = tint_foliage(Image.open(io.BytesIO(VANILLA.read("assets/minecraft/textures/block/tall_grass_top.png"))))
lower = tint_foliage(Image.open(io.BytesIO(VANILLA.read("assets/minecraft/textures/block/tall_grass_bottom.png"))))
canvas = Image.new("RGBA", (16, 32))
canvas.alpha_composite(upper, (0, 0))
canvas.alpha_composite(lower, (0, 16))
for name, im in [("dragon_nip_grass_bottom", lower), ("dragon_nip_grass_top", upper)]:
    dest = RES / f"assets/bodaddon/textures/block/{name}.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    im.save(dest)
preview = Image.new("RGBA", (48, 32), "#26252a")
preview.alpha_composite(canvas, (0, 0))
preview.alpha_composite(upper, (16, 0))
preview.alpha_composite(lower, (16, 16))
preview.alpha_composite(upper, (32, 0))
preview.alpha_composite(lower, (32, 16))
preview.resize((384, 256), Image.Resampling.NEAREST).save(ROOT / ".scratch/dragon_nip_grass_preview.png")

# ---- models ----
write("assets/bodaddon/blockstates/dragon_nip_grass.json", {"variants": {
    "half=lower": {"model": "bodaddon:block/dragon_nip_grass_bottom"},
    "half=upper": {"model": "bodaddon:block/dragon_nip_grass_top"},
}})
for half in ("bottom", "top"):
    write(f"assets/bodaddon/models/block/dragon_nip_grass_{half}.json", {
        "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
        "textures": {"cross": f"bodaddon:block/dragon_nip_grass_{half}"},
    })
write("assets/bodaddon/models/item/dragon_nip_grass.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:block/dragon_nip_grass_top"},
})

# ---- loot: a sword cuts nip, shears collect the plant, anything else gets nothing ----
loot = vanilla("data/minecraft/loot_table/blocks/tall_grass.json")
loot = json.loads(json.dumps(loot).replace("minecraft:tall_grass", "bodaddon:dragon_nip_grass"))
for pool in loot["pools"]:
    pool["entries"] = [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": "bodaddon:dragon_nip",
         "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:swords"}}],
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}, "add": False}]},
        {"type": "minecraft:item", "name": "bodaddon:dragon_nip_grass",
         "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}]},
    ]}]
loot["random_sequence"] = "bodaddon:blocks/dragon_nip_grass"
write("data/bodaddon/loot_table/blocks/dragon_nip_grass.json", loot)
write("data/minecraft/tags/block/sword_efficient.json", {"replace": False, "values": ["bodaddon:dragon_nip_grass"]})

# ---- world generation: patches in open, cool or temperate grassland ----
patch = vanilla("data/minecraft/worldgen/configured_feature/patch_tall_grass.json")
patch["config"]["feature"]["feature"]["config"]["to_place"]["state"]["Name"] = "bodaddon:dragon_nip_grass"
patch["config"]["tries"] = 24
patch["config"]["xz_spread"] = 5
write("data/bodaddon/worldgen/configured_feature/dragon_nip_patch.json", patch)
write("data/bodaddon/worldgen/placed_feature/dragon_nip_patch.json", {
    "feature": "bodaddon:dragon_nip_patch",
    "placement": [
        {"type": "minecraft:rarity_filter", "chance": 40},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
        {"type": "minecraft:biome"},
    ],
})
write("data/bodaddon/tags/worldgen/biome/has_dragon_nip.json", {"replace": False, "values": [
    "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
    "minecraft:taiga", "minecraft:windswept_hills", "minecraft:windswept_forest",
]})
write("data/bodaddon/neoforge/biome_modifier/add_dragon_nip.json", {
    "type": "neoforge:add_features", "biomes": "#bodaddon:has_dragon_nip",
    "features": "bodaddon:dragon_nip_patch", "step": "vegetal_decoration",
})

# ---- lang ----
lang = json.loads((RES / "assets/bodaddon/lang/en_us.json").read_text(encoding="utf-8"))
lang["block.bodaddon.dragon_nip_grass"] = "Dragon Nip Grass"
lang["item.bodaddon.dragon_nip_grass"] = "Dragon Nip Grass"
lang["item.bodaddon.dragon_nip.desc1"] = "Feed to a dragon to calm it"
lang["item.bodaddon.dragon_nip.desc2"] = "and ease taming for 5 minutes."
write("assets/bodaddon/lang/en_us.json", lang)
print("Generated dragon nip grass.")
