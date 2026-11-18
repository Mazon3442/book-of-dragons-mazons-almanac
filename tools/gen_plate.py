"""Horned wooden stable-sign icon, matching the placed sign. Run: python tools/gen_plate.py OUT_DIR."""
import json
import os
import sys

from PIL import Image
from native_item_art import plate_icon

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")


def icon():
    return plate_icon()


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        f.write(data)


if __name__ == "__main__":
    ic = icon()
    tex = os.path.join(ROOT, "assets", "bodaddon", "textures", "item")
    os.makedirs(tex, exist_ok=True)
    ic.save(os.path.join(tex, "stable_plate.png"))
    write("assets/bodaddon/models/item/stable_plate.json",
          json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "bodaddon:item/stable_plate"}}, indent=2))
    write("data/bodaddon/recipe/stable_plate.json", json.dumps({
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["III", "ILI"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "L": {"item": "minecraft:leather"}},
        "result": {"id": "bodaddon:stable_plate", "count": 1}}, indent=2))
    if len(sys.argv) > 1:
        z = ic.resize((256, 256), Image.NEAREST)
        sheet = Image.new("RGB", (532, 276), (120, 126, 136))
        sheet.paste(Image.new("RGB", (256, 256), (44, 46, 54)), (10, 10))
        sheet.paste(Image.new("RGB", (256, 256), (214, 218, 224)), (266, 10))
        sheet.paste(z, (10, 10), z)
        sheet.paste(z, (266, 10), z)
        sheet.save(os.path.join(sys.argv[1], "plate_icon.png"))
    print("ok")
