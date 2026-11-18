"""Generate the eel and dragon repellent: entity skin, item and block art, models, recipes, fishing and spawn data, lang.

Art follows the Blockbench Minecraft style guide: a midtone shape, a significantly darker outline, light from the top left,
hue-shifted ramps (shadows cool, highlights warm), no noise.
"""
import json
from pathlib import Path
from PIL import Image, ImageColor

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/bodaddon"
DATA = RES / "data/bodaddon"


def rgb(h):
    return ImageColor.getrgb(h) + (255,)


def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def save(im, rel):
    target = ASSETS / "textures" / rel
    target.parent.mkdir(parents=True, exist_ok=True)
    im.save(target)


def from_grid(rows, palette):
    im = Image.new("RGBA", (len(rows[0]), len(rows)))
    for y, row in enumerate(rows):
        assert len(row) == len(rows[0]), (y, row)
        for x, c in enumerate(row):
            if c != ".":
                im.putpixel((x, y), rgb(palette[c]))
    return im


# Visuals live separately so they can be regenerated without rewriting gameplay data.
from eel_art import generate

generate()

# ---------------------------------------------------------------- effect icon: the root with a prohibition slash
icon = Image.new("RGBA", (18, 18))
root_art = Image.open(ASSETS / "textures/block/dragon_root.png").convert("RGBA")
icon.alpha_composite(root_art, (1, 1))
for i in range(2, 16):
    for dx, c in ((0, "#e0561b"), (1, "#e0561b")):
        x, y = 16 - i + dx - 1, i
        if 0 <= x < 18:
            icon.putpixel((x, y), rgb(c))
    if 0 <= 17 - i < 18:
        icon.putpixel((17 - i, i), rgb("#ffb04a"))
save(icon, "mob_effect/dragon_scent.png")

preview = Image.new("RGBA", (16 * 5 + 18 + 16, 18), "#26252a")
for i, im in enumerate((Image.open(ASSETS / "textures/item/eel.png"), Image.open(ASSETS / "textures/item/eel_bucket.png"),
                        Image.open(ASSETS / "textures/item/dragon_repellent.png"),
                        Image.open(ASSETS / "textures/block/dragon_repellent_block.png"))):
    preview.alpha_composite(im.convert("RGBA"), (i * 16, 1))
preview.alpha_composite(icon, (64, 0))
preview = preview.resize((preview.width * 8, preview.height * 8), Image.Resampling.NEAREST)
preview.save(ROOT / ".scratch/eel_preview.png")
Image.open(ASSETS / "textures/entity/eel.png").resize((256, 256), Image.Resampling.NEAREST).save(ROOT / ".scratch/eel_skin_preview.png")

# ---------------------------------------------------------------- models, block, loot, recipes
write("assets/bodaddon/models/item/eel.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/eel"}})
write("assets/bodaddon/models/item/eel_bucket.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/eel_bucket"}})
write("assets/bodaddon/models/item/dragon_repellent.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/dragon_repellent"}})
write("assets/bodaddon/models/item/eel_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/eel_spawn_egg"}})
write("assets/bodaddon/blockstates/dragon_repellent_block.json", {"variants": {"": {"model": "bodaddon:block/dragon_repellent_block"}}})
write("assets/bodaddon/models/block/dragon_repellent_block.json", {"parent": "minecraft:block/cube_column",
                                                                   "textures": {"side": "bodaddon:block/dragon_repellent_block",
                                                                                "end": "bodaddon:block/dragon_repellent_block_top"}})
write("assets/bodaddon/models/item/dragon_repellent_block.json", {"parent": "bodaddon:block/dragon_repellent_block"})
write("data/bodaddon/loot_table/blocks/dragon_repellent_block.json", {
    "type": "minecraft:block", "random_sequence": "bodaddon:blocks/dragon_repellent_block",
    "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
               "entries": [{"type": "minecraft:item", "name": "bodaddon:dragon_repellent_block"}]}]})
write("data/bodaddon/recipe/dragon_repellent.json", {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [{"item": "bodaddon:eel"}, {"item": "bodaddon:dragon_root"}, {"item": "minecraft:glass_bottle"}],
    "result": {"id": "bodaddon:dragon_repellent", "count": 1}})
write("data/bodaddon/recipe/dragon_repellent_block.json", {
    "type": "minecraft:crafting_shapeless", "category": "building",
    "ingredients": [{"item": "bodaddon:dragon_repellent"}, {"item": "bodaddon:dragon_repellent"},
                    {"item": "minecraft:moss_block"}, {"item": "minecraft:moss_block"}],
    "result": {"id": "bodaddon:dragon_repellent_block", "count": 1}})

# ---------------------------------------------------------------- where eels live and how they are caught
write("data/bodaddon/tags/worldgen/biome/has_eel.json", {"replace": False, "values": [
    "minecraft:river", "minecraft:swamp", "minecraft:mangrove_swamp"]})
write("data/bodaddon/neoforge/biome_modifier/add_eel_spawns.json", {
    "type": "neoforge:add_spawns", "biomes": "#bodaddon:has_eel",
    "spawners": {"type": "bodaddon:eel", "weight": 2, "minCount": 1, "maxCount": 1}})
write("data/neoforge/loot_modifiers/global_loot_modifiers.json", {"replace": False, "entries": ["bodaddon:eel_fishing"]})
write("data/bodaddon/loot_modifiers/eel_fishing.json", {
    "type": "bodaddon:eel_fishing", "chance": 0.12,
    "conditions": [
        {"condition": "neoforge:loot_table_id", "loot_table_id": "minecraft:gameplay/fishing"},
        {"condition": "minecraft:location_check", "predicate": {"biomes": "#bodaddon:has_eel"}}]})

lang_path = RES / "assets/bodaddon/lang/en_us.json"
lang = json.loads(lang_path.read_text(encoding="utf-8"))
lang.update({
    "entity.bodaddon.eel": "Eel",   # black and yellow banded
    "item.bodaddon.eel": "Eel",
    "item.bodaddon.eel_bucket": "Bucket of Eel",
    "item.bodaddon.eel_spawn_egg": "Eel Spawn Egg",
    "item.bodaddon.dragon_repellent": "Dragon Repellent",
    "item.bodaddon.dragon_repellent.desc1": "Drink for Dragon Scent: wild dragons",
    "item.bodaddon.dragon_repellent.desc2": "keep away from you for 8 minutes.",
    "block.bodaddon.dragon_repellent_block": "Dragon Repellent Block",
    "effect.bodaddon.dragon_scent": "Dragon Scent",
})
lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("Generated eel and dragon repellent.")
