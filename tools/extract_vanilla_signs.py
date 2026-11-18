"""Pulls the vanilla sign and hanging sign textures out of the 1.21.1 client jar and writes their models as Blockbench-ready JSON.

python tools/extract_vanilla_signs.py

The vanilla signs are not JSON models: they are built in code (SignRenderer.createSignLayer and
HangingSignRenderer.createHangingSignLayer) on 64x32 textures. The boxes below are read from those methods, then placed in
block space the way the renderers place them (the hanging sign pivots 10 px up from the block bottom; the standing sign
pivots at the middle of the block and is scaled by 2/3). The output goes to reference/vanilla_signs for study; it is not
part of the mod and the vanilla textures must not be shipped in it.

Open a JSON in Blockbench, then drag the matching PNG from reference/vanilla_signs/textures onto the Textures panel. UV is in
64x32 texture pixels (texture_size is set), laid out the way entity boxes unwrap.
"""
import io
import json
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "reference", "vanilla_signs")
CLIENT = os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")
WOODS = ("oak", "spruce", "dark_oak")

TWO_THIRDS = 2 / 3


def box_uv(u, v, dx, dy, dz):
    """Faces of an entity box that starts at texOffs(u, v): top, bottom, front, right, back, left."""
    return {
        "up": [u + dz, v, u + dz + dx, v + dz],
        "down": [u + dz + dx, v, u + dz + 2 * dx, v + dz],
        "south": [u + dz, v + dz, u + dz + dx, v + dz + dy],            # the entity's front, seen from +z after the y/z flip
        "west": [u, v + dz, u + dz, v + dz + dy],
        "north": [u + dz + dx + dz, v + dz, u + 2 * dz + 2 * dx, v + dz + dy],
        "east": [u + dz + dx, v + dz, u + 2 * dz + dx, v + dz + dy],
    }


def cube(name, frm, to, uv_box, rotation=None, faces=None):
    el = {"name": name, "from": [round(c, 4) for c in frm], "to": [round(c, 4) for c in to]}
    if rotation:
        el["rotation"] = rotation
    uvs = box_uv(*uv_box)
    el["faces"] = {f: {"uv": uvs[f], "texture": "#0"} for f in (faces or uvs)}
    return el


def plane(name, frm, to, uv_front, uv_back, rotation=None):
    el = {"name": name, "from": frm, "to": to}
    if rotation:
        el["rotation"] = rotation
    el["faces"] = {"south": {"uv": uv_front, "texture": "#0"}, "north": {"uv": uv_back, "texture": "#0"}}
    return el


def chain_planes():
    """normalChains: four flat planes, two crossed at each of x = 3 and x = 13, 3 wide and 6 tall, turned 45 degrees."""
    out = []
    for name, cx in (("L", 3), ("R", 13)):
        out.append(plane(f"chain{name}1", [cx - 1.5, 10, 8], [cx + 1.5, 16, 8], [0, 6, 3, 12], [3, 6, 6, 12],
                         {"angle": 45, "axis": "y", "origin": [cx, 16, 8]}))
        out.append(plane(f"chain{name}2", [cx - 1.5, 10, 8], [cx + 1.5, 16, 8], [6, 6, 9, 12], [9, 6, 12, 12],
                         {"angle": -45, "axis": "y", "origin": [cx, 16, 8]}))
    return out


def hanging(wall):
    """Board 14x10x2 (texOffs 0,12), plank 16x2x4 (texOffs 0,0), then chains: two crossed pairs (wall) or one V plane (ceiling)."""
    els = [cube("board", [1, 0, 7], [15, 10, 9], (0, 12, 14, 10, 2))]
    if wall:
        els.append(cube("plank", [0, 14, 6], [16, 16, 10], (0, 0, 16, 2, 4)))
        els += chain_planes()
    else:
        els.append(plane("vChains", [2, 10, 8], [14, 16, 8], [14, 6, 26, 12], [26, 6, 38, 12]))
    return els


def standing():
    """Board 24x12x2 (texOffs 0,0) and stick 2x14x2 (texOffs 0,14), drawn at 2/3 scale about the middle of the block."""
    s = TWO_THIRDS
    return [
        cube("sign", [8 - 12 * s, 8 + 2 * s, 8 - 1 * s], [8 + 12 * s, 8 + 14 * s, 8 + 1 * s], (0, 0, 24, 12, 2)),
        cube("stick", [8 - 1 * s, 8 - 12 * s, 8 - 1 * s], [8 + 1 * s, 8 + 2 * s, 8 + 1 * s], (0, 14, 2, 14, 2)),
    ]


def wall_sign():
    """The same board with no stick, moved 5 px down and 7 px toward the wall (the renderer's translate 0, -0.3125, -0.4375)."""
    s = TWO_THIRDS
    el = standing()[0]
    el["from"] = [8 - 12 * s, 8 + 2 * s - 5, 8 - 1 * s + 7]
    el["to"] = [8 + 12 * s, 8 + 14 * s - 5, 8 + 1 * s + 7]
    return [el]


def model(elements, texture_name):
    return {"texture_size": [64, 32], "textures": {"0": texture_name, "particle": texture_name}, "elements": elements}


if __name__ == "__main__":
    z = zipfile.ZipFile(CLIENT)
    tex_dir = os.path.join(OUT, "textures")
    os.makedirs(tex_dir, exist_ok=True)
    for wood in WOODS:
        for kind, src in (("sign", f"signs/{wood}.png"), ("hanging_sign", f"signs/hanging/{wood}.png")):
            data = z.read("assets/minecraft/textures/entity/" + src)
            Image.open(io.BytesIO(data)).save(os.path.join(tex_dir, f"{kind}_{wood}.png"))
    models = {
        "sign_standing": (standing(), "sign_oak"),
        "sign_wall": (wall_sign(), "sign_oak"),
        "hanging_sign_wall": (hanging(True), "hanging_sign_oak"),
        "hanging_sign_ceiling": (hanging(False), "hanging_sign_oak"),
    }
    for name, (els, tex) in models.items():
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(model(els, tex), f, indent=2)
    print("ok", os.path.abspath(OUT))
