"""Stable Sign: block models and blockstate, built from the vanilla sign and hanging sign boxes.

python tools/gen_sign.py        (writes models and the blockstate only; the textures are painted by tools/paint_sign.py)

The boxes come from tools/extract_vanilla_signs.py (read out of SignRenderer and HangingSignRenderer), so the sign has the vanilla
shapes, with one addition: the wall bracket post from the mockup. UV footprints match the final world-space dimensions,
including the standing sign's 2/3 scale, so every surface uses one texture pixel per model unit.

Four placements:
  wall     hanging sign on a bracket off the side of a block (vanilla wall hanging sign + the post). Modelled facing east.
  ceiling  hanging sign under a block (vanilla ceiling hanging sign, V chains). Modelled facing south.
  floor    the vanilla standing sign. Modelled facing south.
  flat     the vanilla wall sign, flat on the side of a block (sneak while placing). Modelled facing south.
"Facing south" means the front of the board, the face that matches the front of the texture, looks south.

Two textures, both 64x32, using the original island origins with uniformly scaled UV footprints:
  textures/block/stable_sign_hanging.png   plank (0,0), chains (0,6) (6,6) (14,6), board (0,12), and the new post at (40,12)
  textures/block/stable_sign_standing.png  board (0,0), stick (0,14)
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import extract_vanilla_signs as V

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bodaddon")
HANGING = "bodaddon:block/stable_sign_hanging"
STANDING = "bodaddon:block/stable_sign_standing"

POST_UV = (40, 12, 2, 7, 4)          # the bracket post: box 2 (into the wall) x 7 tall x 4 wide, unwrapped at (40, 12)


def normalise(elements, texture):
    """Keep atlas origins; sample one image pixel per final model unit along both face directions."""
    out = []
    for el in elements:
        el = json.loads(json.dumps(el))
        dx, dy, dz = [b - a for a, b in zip(el["from"], el["to"])]
        for direction, face in el["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            width, height = ((dx, dy) if direction in ("north", "south") else
                             (dz, dy) if direction in ("east", "west") else (dx, dz))
            u1 = u0 + (width if u1 >= u0 else -width)
            v1 = v0 + (height if v1 >= v0 else -height)
            face["uv"] = [round(u0 / 4, 6), round(v0 / 2, 6), round(u1 / 4, 6), round(v1 / 2, 6)]
        out.append(el)
    return {"textures": {"0": texture, "particle": texture}, "elements": out}


def flat():
    """The vanilla wall-sign board, against the north side of the block, front looking south."""
    s = 2 / 3
    el = V.standing()[0]
    el["from"] = [8 - 12 * s, 8 + 2 * s - 5, 0]
    el["to"] = [8 + 12 * s, 8 + 14 * s - 5, 2 * s]
    return [el]


def models():
    return {
        "wall": normalise(V.hanging(True) + [V.cube("post", [0, 9, 6], [2, 16, 10], POST_UV)], HANGING),
        "ceiling": normalise(V.hanging(False), HANGING),
        "floor": normalise(V.standing(), STANDING),
        "flat": normalise(flat(), STANDING),
    }


def blockstate():
    variants = {}
    for facing, d in (("south", 0), ("west", 1), ("north", 2), ("east", 3)):
        for mount in ("ceiling", "floor", "flat"):          # modelled facing south
            entry = {"model": f"bodaddon:block/stable_sign_{mount}"}
            if d:
                entry["y"] = d * 90
            variants[f"facing={facing},mount={mount}"] = entry
        wall = {"model": "bodaddon:block/stable_sign_wall"}   # modelled facing east
        turns = (d + 1) % 4
        if turns:
            wall["y"] = turns * 90
        variants[f"facing={facing},mount=wall"] = wall
    return {"variants": variants}


def save(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(obj, f, indent=2)


if __name__ == "__main__":
    for mount, data in models().items():
        save(f"models/block/stable_sign_{mount}.json", data)
    save("blockstates/stable_sign.json", blockstate())
    print("ok")
