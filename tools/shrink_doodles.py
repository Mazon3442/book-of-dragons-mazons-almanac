"""Scale the hand-drawn doodle pieces down with Latent's pixel tool (a little mixel is accepted).

    python tools/shrink_doodles.py <latentpx.exe> <src-dir> <out-dir> <scale> [colors]

Colours stay high (default 24) and the ink is not thickened, so the shading of the drawings survives.
"""
import pathlib, subprocess, sys
from PIL import Image

exe, src, out, scale = sys.argv[1], pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3]), float(sys.argv[4])
colors = sys.argv[5] if len(sys.argv) > 5 else "24"
out.mkdir(parents=True, exist_ok=True)
for f in sorted(src.glob("doodle_*.png")):
    w, h = Image.open(f).size
    size = round(max(w, h) * scale)
    r = subprocess.run([exe, str(f), str(out / f.name), str(size), colors], capture_output=True, text=True)
    print(f.name, (w, h), "->", Image.open(out / f.name).size, r.stderr.strip())
