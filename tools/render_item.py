"""Software render of the dragon_editor 3D model (orthographic, textured, z-buffered) for preview."""
import json, math, sys
import numpy as np
from PIL import Image

RES = "src/main/resources/assets/bodaddon"
model = json.load(open(f"{RES}/models/item/dragon_editor_3d.json"))
tex = np.array(Image.open(f"{RES}/textures/item/dragon_editor_model.png").convert("RGBA"))
TW = tex.shape[1]

def rot_y(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rot_x(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

def faces(el):
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    quads = {  # name: (4 corners, shade)
        "up": ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 1.0),
        "down": ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], 0.5),
        "north": ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 0.8),
        "south": ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], 0.8),
        "west": ([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], 0.6),
        "east": ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], 0.6),
    }
    r = el.get("rotation"); out = []
    for name, f in el["faces"].items():
        pts, shade = quads[name]; pts = np.array(pts, float)
        if r and r["angle"]:
            o = np.array(r["origin"], float); a = math.radians(r["angle"])
            pts = (pts - o) @ rot_y(a).T + o
        u0, v0, u1, v1 = [c * TW / 16 for c in f["uv"]]
        out.append((pts, [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], shade))
    return out

def render(yaw, pitch, scale=14, size=(420, 520), center=(8, 6, 8), tilt=0):
    W, H = size
    img = np.zeros((H, W, 4), np.uint8); zbuf = np.full((H, W), -1e9)
    R = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw)) @ rot_x(math.radians(tilt))
    for el in model["elements"]:
        for pts, uvs, shade in faces(el):
            p = (pts - np.array(center, float)) @ R.T
            sx = W / 2 + p[:, 0] * scale; sy = H / 2 - p[:, 1] * scale; sz = p[:, 2]
            for tri in ((0, 1, 2), (0, 2, 3)):
                xs, ys = sx[list(tri)], sy[list(tri)]
                x0, x1 = int(max(0, xs.min())), int(min(W - 1, xs.max() + 1))
                y0, y1 = int(max(0, ys.min())), int(min(H - 1, ys.max() + 1))
                if x1 <= x0 or y1 <= y0: continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
                d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
                if abs(d) < 1e-9: continue
                a = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
                b = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
                c = 1 - a - b
                m = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
                z = a * sz[tri[0]] + b * sz[tri[1]] + c * sz[tri[2]]
                u = a * uvs[tri[0]][0] + b * uvs[tri[1]][0] + c * uvs[tri[2]][0]
                v = a * uvs[tri[0]][1] + b * uvs[tri[1]][1] + c * uvs[tri[2]][1]
                ui = np.clip(u.astype(int), 0, TW - 1); vi = np.clip(v.astype(int), 0, TW - 1)
                col = tex[vi, ui].astype(float); col[..., :3] *= shade
                sub = zbuf[y0:y1 + 1, x0:x1 + 1]; dst = img[y0:y1 + 1, x0:x1 + 1]
                upd = m & (z > sub) & (col[..., 3] > 0)
                sub[upd] = z[upd]; dst[upd] = col[upd].astype(np.uint8)
    return Image.fromarray(img)

if __name__ == "__main__":
    out = sys.argv[1]
    views = [render(35, 22, tilt=0), render(-60, 15, tilt=0), render(35, 22, scale=14, tilt=0)]
    # 1: showcase angle, 2: side-on, 3: tilted like held in hand
    views[2] = render(90, 10, tilt=-20)
    sheet = Image.new("RGBA", (420 * 3, 520), (70, 70, 78, 255))
    for i, v in enumerate(views): sheet.alpha_composite(v, (420 * i, 0))
    sheet.save(out)
