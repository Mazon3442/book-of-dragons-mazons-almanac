"""Generate dragon root: hand-placed outlined 16x16 art, models, loot, wild patches in damp or dark woods, lang."""
import json
from pathlib import Path
from PIL import Image, ImageColor

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"

# o/d/g/G = leaf outline, shade, mid, light.  r/e/b/B = root outline, shade, mid, light.  Light is from the top left.
ART = [
    "................",
    ".......oo.......",
    "......oGgo......",
    "..oo..oGggo.oo..",
    ".oGgo.oGggo.oGgo",
    ".oGggooGgdoogggo",
    ".oogdoogggdoogdo",
    "..oogdoggdoodoo.",
    "....oodgdoo.....",
    ".....oodoo......",
    "....rrBBbrr.....",
    "...rBBBbbbbr....",
    "...rBBbbbbber...",
    "....rbbbbbeer...",
    ".....rrbbeerr...",
    ".......rrr......",
]
PAL = {
    "o": "#1b3a2c", "d": "#2f5d3a", "g": "#4b8a43", "G": "#7bb552",
    "r": "#3a1f1c", "e": "#6d3623", "b": "#9a5232", "B": "#c4703f",
}
img = Image.new("RGBA", (16, 16))
for y, row in enumerate(ART):
    assert len(row) == 16, (y, row)
    for x, c in enumerate(row):
        if c != ".":
            img.putpixel((x, y), ImageColor.getrgb(PAL[c]) + (255,))
tex = RES / "assets/bodaddon/textures/block/dragon_root.png"
tex.parent.mkdir(parents=True, exist_ok=True)
img.save(tex)
prev = Image.new("RGBA", (48, 16), "#26252a")
for i in range(3):
    prev.alpha_composite(img, (i * 16, 0))
prev.resize((384, 128), Image.Resampling.NEAREST).save(ROOT / ".scratch/dragon_root_preview.png")


def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


write("assets/bodaddon/blockstates/dragon_root.json", {"variants": {"": {"model": "bodaddon:block/dragon_root"}}})
write("assets/bodaddon/models/block/dragon_root.json", {
    "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
    "textures": {"cross": "bodaddon:block/dragon_root"}})
write("assets/bodaddon/models/item/dragon_root.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:block/dragon_root"}})
write("data/bodaddon/loot_table/blocks/dragon_root.json", {
    "type": "minecraft:block", "random_sequence": "bodaddon:blocks/dragon_root",
    "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
               "entries": [{"type": "minecraft:item", "name": "bodaddon:dragon_root"}]}]})

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
lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("Generated dragon root.")
