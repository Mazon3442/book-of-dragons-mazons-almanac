"""Cut the generated decoration sheets into one transparent PNG per piece.

    python tools/split_decor.py <swatch-sheet> <doodle-sheet> <out-dir>

Swatches: the sheet is a 5x4 grid of paint blobs on a pale background. Each blob is kept as one shape (stray flecks
dropped) with the background removed. Doodles: pencil drawings on a pale background; each is turned into brown ink
whose opacity follows how dark the pencil was. Pieces are named swatch_<row><col>.png and doodle_<n>.png.
"""
import sys
import pathlib
import numpy as np
from PIL import Image, ImageFilter, ImageDraw

INK = np.array([0x4A, 0x2E, 0x1C], dtype=np.float32)


def background(rgb):
    """Pale paper colour: the median of the border pixels."""
    border = np.concatenate([rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)])
    return np.median(border, axis=0)


def runs(profile, min_gap, min_val):
    """Index ranges where profile > min_val, joining ranges closer than min_gap."""
    out, start, last = [], None, None
    for i, v in enumerate(profile):
        if v > min_val:
            if start is None:
                start = i
            last = i
        elif start is not None and i - last > min_gap:
            out.append((start, last + 1))
            start = None
    if start is not None:
        out.append((start, last + 1))
    return out


def cells(mask, row_gap, col_gap, min_val):
    """Boxes (y0, x0, y1, x1) of the pieces on a sheet: rows first, then columns inside each row."""
    boxes = []
    for y0, y1 in runs(mask.mean(axis=1), row_gap, min_val):
        for x0, x1 in runs(mask[y0:y1].mean(axis=0), col_gap, min_val):
            boxes.append((y0, x0, y1, x1))
    return boxes


def label(mask):
    """4-connected components of a boolean mask (iterative flood fill). Returns (labels, count)."""
    h, w = mask.shape
    lab = np.zeros((h, w), dtype=np.int32)
    n = 0
    for sy, sx in zip(*np.nonzero(mask)):
        if lab[sy, sx]:
            continue
        n += 1
        stack = [(sy, sx)]
        lab[sy, sx] = n
        while stack:
            y, x = stack.pop()
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = n
                    stack.append((ny, nx))
    return lab, n


def fill_holes(mask):
    img = Image.fromarray((mask * 255).astype(np.uint8))
    pad = Image.new("L", (img.width + 2, img.height + 2), 0)
    pad.paste(img, (1, 1))
    ImageDraw.floodfill(pad, (0, 0), 128)
    out = np.array(pad)[1:-1, 1:-1]
    return out != 128


def grow(mask, size):
    return np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(size))) > 0


def split_swatches(path, out):
    full = np.array(Image.open(path).convert("RGBA"))
    rgb = full[:, :, :3]
    dist = full[:, :, 3].astype(np.float32)        # the sheet is already transparent: alpha is the blob
    mask = dist > 40
    boxes = [b + (0,) for b in cells(mask, 30, 18, 0.01)]
    k = 0
    for (y0, x0, y1, x1, _) in boxes:
        sub = rgb[y0:y1, x0:x1]
        m = fill_holes(grow(dist[y0:y1, x0:x1] > 40, 3))
        # keep only the biggest piece (the blob), drop flecks
        sm = np.array(Image.fromarray((m * 255).astype(np.uint8)).resize((max(1, m.shape[1] // 4), max(1, m.shape[0] // 4)), Image.BOX)) > 128
        lb, c = label(sm)
        if c > 1:
            sizes = [(lb == j).sum() for j in range(1, c + 1)]
            keep = np.array(Image.fromarray(((lb == (1 + int(np.argmax(sizes)))) * 255).astype(np.uint8)).resize((m.shape[1], m.shape[0]), Image.NEAREST)) > 0
            m &= grow(keep, 9)
        ys, xs = np.nonzero(m)
        a = np.array(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)))
        rgba = np.dstack([sub, a])[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        r, c_ = divmod(k, 5)
        Image.fromarray(rgba.astype(np.uint8)).save(out / f"swatch_{r}{c_}.png")
        k += 1
    return k


def split_doodles(path, out):
    full = np.array(Image.open(path).convert("RGBA")).astype(np.float32)
    dark = full[:, :, 3] / 255.0                           # the sheet is already transparent: alpha is the ink
    dark[dark < 0.12] = 0                                  # scan noise
    mask = dark > 0
    pieces = cells(mask, 30, 22, 0.002)
    for k, (y0, x0, y1, x1) in enumerate(pieces):
        a = dark[y0:y1, x0:x1]
        ys, xs = np.nonzero(a > 0)
        a = a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        rgba = np.dstack([np.tile(INK, (*a.shape, 1)), np.clip(a * 1.6, 0, 1) * 255])   # a little stronger so thin lines survive
        Image.fromarray(rgba.astype(np.uint8)).save(out / f"doodle_{k}.png")
    return len(pieces)


if __name__ == "__main__":
    sw, dd, dest = sys.argv[1:4]
    out = pathlib.Path(dest)
    out.mkdir(parents=True, exist_ok=True)
    print("swatches", split_swatches(sw, out), "doodles", split_doodles(dd, out))
