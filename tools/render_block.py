"""Software render of the feeding trough block model, in the game's inventory pose and a closer 3/4 view.

python tools/render_block.py OUT_DIR   writes trough_icon.png (16x16 pixel icon, as the inventory draws it) and
trough_render.png (large render).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "bodaddon")
MODEL = json.load(open(os.path.join(RES, "models", "item", "feeding_trough_3d.json")))
TEX = {k: np.array(Image.open(os.path.join(RES, "textures", v.split(":")[1] + ".png")).convert("RGBA"))
       for k, v in MODEL["textures"].items() if k != "particle"}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
CENTER = np.array([16.0, 7.0, 8.0])
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def quads(el):
    """Corners per face in the game's uv order: top-left, top-right, bottom-right, bottom-left, seen from outside."""
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    return {
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    }


def render(yaw, pitch, scale, size, light=True):
    W, H = size
    img = np.zeros((H, W, 4), np.uint8)
    zbuf = np.full((H, W), -1e9)
    R = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw))
    for el in MODEL["elements"]:
        q = quads(el)
        rot = el.get("rotation")
        Rr = (rot_y(-math.radians(rot["angle"])) if rot.get("axis") == "y" else rot_z(math.radians(rot["angle"]))) if rot else np.eye(3)
        origin = np.array(rot["origin"], float) if rot else np.zeros(3)
        for name, f in el["faces"].items():
            if (R @ Rr @ np.array(NORMAL[name], float))[2] <= 1e-6:
                continue   # facing away from the camera
            tex = TEX[f["texture"].lstrip("#")]
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            pts = ((np.array(q[name], float) - origin) @ Rr.T + origin - CENTER) @ R.T
            sx = W / 2 + pts[:, 0] * scale
            sy = H / 2 - pts[:, 1] * scale
            sz = pts[:, 2]
            for tri in ((0, 1, 2), (0, 2, 3)):
                xs, ys = sx[list(tri)], sy[list(tri)]
                x0, x1 = int(max(0, xs.min())), int(min(W - 1, xs.max() + 1))
                y0, y1 = int(max(0, ys.min())), int(min(H - 1, ys.max() + 1))
                if x1 <= x0 or y1 <= y0:
                    continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
                d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
                if abs(d) < 1e-9:
                    continue
                a = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
                b = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
                c = 1 - a - b
                m = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
                z = a * sz[tri[0]] + b * sz[tri[1]] + c * sz[tri[2]]
                u = a * uvs[tri[0]][0] + b * uvs[tri[1]][0] + c * uvs[tri[2]][0]
                v = a * uvs[tri[0]][1] + b * uvs[tri[1]][1] + c * uvs[tri[2]][1]
                th, tw = tex.shape[:2]
                col = tex[np.clip((v * th / 16).astype(int), 0, th - 1), np.clip((u * tw / 16).astype(int), 0, tw - 1)].astype(float)
                if light:
                    col[..., :3] *= SHADE[name]
                sel = m & (z > zbuf[y0:y1 + 1, x0:x1 + 1]) & (col[..., 3] > 0)
                region = img[y0:y1 + 1, x0:x1 + 1]
                zr = zbuf[y0:y1 + 1, x0:x1 + 1]
                region[sel] = col[sel].astype(np.uint8)
                zr[sel] = z[sel]
    return Image.fromarray(img)


if __name__ == "__main__":
    out = sys.argv[1]
    # the game's gui pose: rotate (30, 225), scale 0.625 scale 0.45 per unit, as the item model's gui display sets
    icon = render(225, 30, 0.45, (16, 16))
    icon.save(os.path.join(out, "trough_icon_16.png"))
    icon.resize((256, 256), Image.NEAREST).save(os.path.join(out, "trough_icon.png"))
    render(212, 26, 9.5, (440, 300)).save(os.path.join(out, "trough_render.png"))
