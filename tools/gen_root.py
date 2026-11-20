"""Generate dragon root: hand-placed outlined 16x16 art, models, loot, wild patches in damp or dark woods, lang."""
import json
from pathlib import Path
from PIL import Image, ImageColor

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"

# Leaves and root use separate ramps, with lighter leaf veins and exposed root ridges.
ART = [
    "................",
    "........o.......",
    ".......oGo...oo.",
    "..oo...oGgoooGdo",
    ".oGgo..ogvdoGgdo",
    ".ogvgod.odgvgdo.",
    "..odgggddovddoo.",
    "...ooogvdddooo..",
    ".....oovdooo....",
    "......oGdoo.....",
    ".....rrBbdor....",
    "....rBHHbber....",
    "...rBBbbebBbr...",
    "...rerbrerbBr...",
    "...r..rer..rbr..",
    "......r....rr...",
]
PAL = {
    "o": "#203721", "d": "#395535", "g": "#5a793f", "v": "#82934d", "G": "#a4ac5a",
    "r": "#432526", "e": "#6e3b35", "b": "#92503c", "B": "#be7650", "H": "#d99a68",
}
GROWTH_STAGES = [
    [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "........oG......",
        ".....oo.Gg......",
        ".....ogdd.......",
        ".......dgr......",
        "......rBbr......",
    ],
    [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "........oo......",
        ".......oGdo.....",
        "...oo.oGgo......",
        "...oGgodd.oo....",
        "....ogdvgoGgo...",
        "......odggddo...",
        ".......rBber....",
        "......rBbrer....",
    ],
    [
        "................",
        "................",
        "................",
        "................",
        "................",
        "........o.......",
        ".......oGo......",
        "...oo.oGgo..oo..",
        "..oGgoogvdoGgo..",
        "..odggddgvgddo..",
        "...ooodvdddoo...",
        "......odgdoo....",
        ".....rBHbber....",
        "....rBBebBbr....",
        "....rebrerbbr...",
        "....r..rr..rr...",
    ],
    ART,
]
SEEDS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "........rr......",
    ".....rr.rBr.....",
    "....rBbrBHer....",
    "....rBBerBer....",
    ".....rebrer.....",
    "......rr.r.rr...",
    "..........rBbr..",
    "..........rer...",
    "..........rr....",
    "................",
    "................",
]
STAGE_NAMES = ("dragon_root_stage0", "dragon_root_stage1", "dragon_root_stage2", "dragon_root")


def save_sprite(rows, name):
    image = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, color in enumerate(row):
            if color != ".":
                image.putpixel((x, y), ImageColor.getrgb(PAL[color]) + (255,))
    target = RES / "assets/bodaddon/textures" / f"{name}.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    return image


preview = Image.new("RGBA", (100, 24), "#8b8b8b")
preview.alpha_composite(save_sprite(SEEDS, "item/dragon_root_seeds"), (2, 4))
for i, (rows, name) in enumerate(zip(GROWTH_STAGES, STAGE_NAMES), start=1):
    preview.alpha_composite(save_sprite(rows, f"block/{name}"), (i * 20 + 2, 4))
scratch = ROOT / ".scratch"
scratch.mkdir(exist_ok=True)
preview.resize((600, 144), Image.Resampling.NEAREST).save(scratch / "dragon_root_preview.png")


def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


write("assets/bodaddon/blockstates/dragon_root.json", {
    "variants": {f"age={age}": {"model": f"bodaddon:block/{name}"} for age, name in enumerate(STAGE_NAMES)}})
for name in STAGE_NAMES:
    write(f"assets/bodaddon/models/block/{name}.json", {
        "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
        "textures": {"cross": f"bodaddon:block/{name}"}})
write("assets/bodaddon/models/item/dragon_root.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:block/dragon_root"}})
write("assets/bodaddon/models/item/dragon_root_seeds.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/dragon_root_seeds"}})
write("data/bodaddon/recipe/dragon_root_seeds.json", {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [{"item": "bodaddon:dragon_root"}],
    "result": {"id": "bodaddon:dragon_root_seeds", "count": 2}})
write("data/bodaddon/loot_table/blocks/dragon_root.json", {
    "type": "minecraft:block", "random_sequence": "bodaddon:blocks/dragon_root",
    "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
               "entries": [{"type": "minecraft:alternatives", "children": [
                   {"type": "minecraft:item", "name": "bodaddon:dragon_root",
                    "conditions": [{"condition": "minecraft:block_state_property", "block": "bodaddon:dragon_root",
                                    "properties": {"age": "3"}}]},
                   {"type": "minecraft:item", "name": "bodaddon:dragon_root_seeds"}]}]}]})

# ---- world generation: rare patches in damp or dark woods ----
write("data/bodaddon/worldgen/configured_feature/dragon_root_patch.json", {
    "type": "minecraft:random_patch",
    "config": {"tries": 6, "xz_spread": 4, "y_spread": 2, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": {
            "type": "minecraft:simple_state_provider", "state": {"Name": "bodaddon:dragon_root"}}}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
            "type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:would_survive", "state": {"Name": "bodaddon:dragon_root"}}]}}]}}})
write("data/bodaddon/worldgen/placed_feature/dragon_root_patch.json", {
    "feature": "bodaddon:dragon_root_patch",
    "placement": [
        {"type": "minecraft:rarity_filter", "chance": 40},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
        {"type": "minecraft:biome"}]})
write("data/bodaddon/tags/worldgen/biome/has_dragon_root.json", {"replace": False, "values": [
    "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:dark_forest",
    "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"]})
write("data/bodaddon/neoforge/biome_modifier/add_dragon_root.json", {
    "type": "neoforge:add_features", "biomes": "#bodaddon:has_dragon_root",
    "features": "bodaddon:dragon_root_patch", "step": "vegetal_decoration"})

lang_path = RES / "assets/bodaddon/lang/en_us.json"
lang = json.loads(lang_path.read_text(encoding="utf-8"))
lang["block.bodaddon.dragon_root"] = "Dragon Root"
lang["item.bodaddon.dragon_root"] = "Dragon Root"
lang["item.bodaddon.dragon_root.desc1"] = "Place it to drive off wild dragons"
lang["item.bodaddon.dragon_root.desc2"] = "within 6 blocks."
lang["item.bodaddon.dragon_root_seeds"] = "Dragon Root Seeds"
lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("Generated dragon root.")
