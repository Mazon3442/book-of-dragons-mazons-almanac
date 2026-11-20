"""Run Latent's pixel tool over every cut-out piece at a target width (height follows the aspect ratio).

    python tools/pixelize_decor.py <latentpx.exe> <src-dir> <out-dir> <width> <colors> [thicken]

latentpx is a tiny wrapper around Latent's own pixel.rs (k-centroid downscale, palette snap, despeckle). Its size
option is the LONGER side, so the size passed is chosen to make the width come out right.

Latent keeps the colour most of each cell is made of, so pencil strokes thinner than a cell turn into see-through
cells and vanish. For line art pass `thicken` (about 0.8 to 1.2): the ink is grown to roughly that many cells wide
before the downscale, which keeps the strokes as 1 px lines.
"""
import pathlib
import subprocess
import sys
import tempfile
from PIL import Image, ImageFilter

exe, src, out, width, colors = sys.argv[1:6]
thicken = float(sys.argv[6]) if len(sys.argv) > 6 else 0
out = pathlib.Path(out)
out.mkdir(parents=True, exist_ok=True)
tmp = pathlib.Path(tempfile.mkdtemp())
width = int(width)

for f in sorted(pathlib.Path(src).glob("*.png")):
    img = Image.open(f).convert("RGBA")
    w, h = img.size
    use = f
    if thicken:
        cell = max(w, h) / (width * max(w, h) / w)          # source pixels per output pixel
        k = int(round(cell * thicken)) | 1                   # MaxFilter needs an odd size
        pad = k
        big = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0x4A, 0x2E, 0x1C, 0))
        big.paste(img, (pad, pad))
        grown = big.getchannel("A").filter(ImageFilter.MaxFilter(k))
        # strengthen weak pencil: anything that was ink stays ink at full strength
        grown = grown.point(lambda v: 255 if v > 60 else 0)
        big.putalpha(grown)
        use = tmp / f.name
        big.save(use)
        w, h = big.size
    size = max(8, round(width * max(w, h) / w))
    r = subprocess.run([exe, str(use), str(out / f.name), str(size), str(colors)], capture_output=True, text=True)
    print(f.name, (w, h), "->", r.stdout.strip() or r.stderr.strip())
